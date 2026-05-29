package com.bluemountain.bot.core.handler;

import com.bluemountain.bot.common.dto.CommandContext;
import com.bluemountain.bot.common.util.TimeParser;
import com.bluemountain.bot.infrastructure.entity.BotSchedule;
import com.bluemountain.bot.infrastructure.entity.BotUserToken;
import com.bluemountain.bot.infrastructure.mapper.BotScheduleMapper;
import com.bluemountain.bot.infrastructure.mapper.BotUserTokenMapper;
import com.bluemountain.bot.integration.client.FeishuClient;
import com.bluemountain.bot.plugin.CommandPlugin;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
public class ScheduleHandler implements CommandPlugin {

    private final BotScheduleMapper scheduleMapper;
    private final BotUserTokenMapper tokenMapper;
    private final FeishuClient feishuClient;

    @Value("${feishu.app-id}")
    private String appId;

    @Value("${feishu.oauth.redirect-uri:}")
    private String redirectUri;

    public ScheduleHandler(BotScheduleMapper scheduleMapper,
                           BotUserTokenMapper tokenMapper,
                           FeishuClient feishuClient) {
        this.scheduleMapper = scheduleMapper;
        this.tokenMapper = tokenMapper;
        this.feishuClient = feishuClient;
    }

    @Override
    public String name() { return "schedule"; }

    @Override
    public String description() {
        return "创建日程 — 用法：/schedule 明天 15:00 项目评审";
    }

    @Override
    public String execute(CommandContext ctx) {
        String args = ctx.getArgs();

        if (args == null || args.isBlank()) {
            return "请输入日程信息\n用法：\n`/schedule 明天 15:00 项目评审`";
        }

        // ① 解析时间 + 标题
        TimeParser.ParseResult result = TimeParser.parse(args);
        if (result == null) {
            return "无法识别时间格式\n"
                    + "请使用：`/schedule 明天 15:00 项目评审`\n"
                    + "或：`/schedule 2026-05-20 15:00 项目评审`";
        }

        // 首先存入数据库,保存数据,哪怕没有申请到飞书日历但是记录还在
        BotSchedule schedule = new BotSchedule();
        schedule.setUserId(ctx.getUserId());
        schedule.setTitle(result.title);
        schedule.setScheduleTime(result.time);
        schedule.setRemindBeforeMin(15);
        schedule.setReminded(0);
        schedule.setCreateTime(LocalDateTime.now());
        scheduleMapper.insert(schedule);

        String timeStr = result.time.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm"));
        StringBuilder reply = new StringBuilder();
        reply.append("**日程已创建** ✅\n\n时间：").append(timeStr).append("\n内容：").append(result.title);
/**
 *
 * 二次授权: tenant_token = 公司工牌，换 user_token 时用
 *   user_token = 钥匙，表示用户本人同意
 *   bot_user_token 表 存入表中
 *
 *表中有用户授权的token,才表示用户亲自同意了,否则就是未授权或者过期了,需要重新授权,返回链接
 *
 */
        // ③ 检查是否授权了日历
        BotUserToken token = tokenMapper.selectById(ctx.getUserId());
        if (token != null) {
            String eventId = feishuClient.createCalendarEvent(
                    token.getAccessToken(), result.title, result.time);
            if (eventId != null) {
                schedule.setFeishuCalendarId(eventId);
                scheduleMapper.updateById(schedule);
                reply.append("\n\n📅 飞书日历已同步");
            } else {
                // token 过期或无效 → 重新发授权链接
                reply.append("\n\n⚠ [重新授权日历同步](").append(buildAuthUrl(ctx.getUserId())).append(")");
            }
        }
        //表示表中没有token,用户之前从未授权,是第一次使用
        else {
            reply.append("\n\n⚠ [点击授权日历同步](").append(buildAuthUrl(ctx.getUserId())).append(")");
        }

        return reply.toString();
    }

    private String buildAuthUrl(String userId) {
        return "https://open.feishu.cn/open-apis/authen/v1/authorize"
                + "?app_id=" + appId
                + "&redirect_uri=" + (redirectUri != null ? redirectUri : "")
                + "&scope=calendar:calendar"
                + "&state=" + userId;
    }
}
