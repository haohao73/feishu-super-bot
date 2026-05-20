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
import java.util.Map;

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
        // 上下文延续模式：优先从结构化参数取 time 和 title
        Map<String, String> cArgs = ctx.getContextArgs();
        String args;

        if (cArgs != null && cArgs.containsKey("time") && cArgs.containsKey("title")) {
            // 拼接回去给 TimeParser 解析："明天下午3点 讨论会议项目方案"
            args = cArgs.get("time") + " " + cArgs.get("title");
        } else {
            // 斜杠指令模式
            args = ctx.getArgs();
        }

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

        // ② 写入 MySQL
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
        } else {
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
