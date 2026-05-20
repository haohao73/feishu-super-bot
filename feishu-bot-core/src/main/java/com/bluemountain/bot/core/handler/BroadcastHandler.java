package com.bluemountain.bot.core.handler;

import cn.hutool.json.JSONUtil;
import com.bluemountain.bot.auth.annotation.RequireRole;
import com.bluemountain.bot.common.dto.CommandContext;
import com.bluemountain.bot.infrastructure.entity.BotMessageBroadcast;
import com.bluemountain.bot.infrastructure.entity.BotUser;
import com.bluemountain.bot.infrastructure.mapper.BotMessageBroadcastMapper;
import com.bluemountain.bot.infrastructure.mapper.BotUserMapper;
import com.bluemountain.bot.core.service.GroupRegistry;
import com.bluemountain.bot.integration.client.FeishuClient;
import com.bluemountain.bot.plugin.CommandPlugin;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
@RequireRole("ADMIN")
public class BroadcastHandler implements CommandPlugin {

    private final FeishuClient feishuClient;
    private final BotMessageBroadcastMapper broadcastMapper;
    private final BotUserMapper userMapper;
    private final GroupRegistry groupRegistry;

    public BroadcastHandler(FeishuClient feishuClient,
                            BotMessageBroadcastMapper broadcastMapper,
                            BotUserMapper userMapper,
                            GroupRegistry groupRegistry) {
        this.feishuClient = feishuClient;
        this.broadcastMapper = broadcastMapper;
        this.userMapper = userMapper;
        this.groupRegistry = groupRegistry;
    }

    @Override
    public String name() { return "broadcast"; }

    @Override
    public String description() {
        return "消息广播（管理员） — 用法：/broadcast [群ID,...] | 标题 | 内容";
    }

    @Override
    public String execute(CommandContext ctx) {
        String args = ctx.getArgs();
        if (args == null || args.isBlank()) {
            return "用法：`/broadcast [群名或群ID,...] | 标题 | 内容`\n"
                 + "不指定目标则发到当前群\n"
                 + "示例：`/broadcast 项目讨论组,技术团队 | 系统通知 | 今晚22点升级`";
        }

        // ===== ① 解析参数：按 | 分割 =====
        // 去掉可能的前导 |（如 "/broadcast | 标题 | 内容"）
        String cleanArgs = args.startsWith("|") ? args.substring(1).trim() : args;
        List<String> targetIds;
        String title, content;
        String[] parts = cleanArgs.split("\\s*\\|\\s*");

        if (parts.length == 2) {
            // 没指定群ID → 默认发当前群
            targetIds = List.of(ctx.getChatId());
            title = parts[0].trim();
            content = parts[1].trim();
        } else if (parts.length >= 3) {
            // 指定了目标：群名或群ID | 标题 | 内容
            // 逐个解析：群名 → Redis查chat_id，oc_开头直接当chat_id
            List<String> unresolved = new ArrayList<>();
            for (String raw : parts[0].trim().split("\\s*,\\s*")) {
                String chatId = groupRegistry.resolve(raw.trim());
                if (chatId != null) {
                    unresolved.add(chatId);
                } else {
                    log.warn("无法解析目标 | input={}", raw);
                }
            }
            if (unresolved.isEmpty()) {
                return "目标群未找到，请确认群名正确\n"
                     + "群名需要先在群里发一条消息让机器人采集";
            }
            targetIds = unresolved;
            title = parts[1].trim();
            content = parts[2].trim();
        } else {
            return "格式错误，请使用：`/broadcast [群名或群ID,...] | 标题 | 内容`";
        }

        if (title.isEmpty() || content.isEmpty()) {
            return "标题和内容不能为空";
        }

        // ===== ② 查发送者信息 =====
        BotUser sender = userMapper.selectByOpenId(ctx.getUserId());
        Long senderId = sender != null ? sender.getId() : null;

        // ===== ③ 创建广播记录（status=发送中） =====
        BotMessageBroadcast broadcast = new BotMessageBroadcast();
        broadcast.setSenderId(senderId);
        broadcast.setTitle(title);
        broadcast.setContent(content);
        broadcast.setTargetType(targetIds.size() > 1 ? 2 : 1);
        broadcast.setTargetIds(JSONUtil.toJsonStr(targetIds));
        broadcast.setStatus(2); // 发送中
        broadcast.setCreateTime(LocalDateTime.now());
        broadcastMapper.insert(broadcast);

        log.info("广播开始 | senderId={} title={} targets={}", senderId, title, targetIds.size());

        // ===== ④ 逐群发送 =====
        int success = 0, fail = 0;
        String formattedMsg = String.format("**【%s】**\n\n%s", title, content);

        for (String chatId : targetIds) {
            try {
                feishuClient.sendTextMessage(chatId.trim(), formattedMsg);
                success++;
            } catch (Exception e) {
                log.error("广播发送失败 | chatId={}", chatId, e);
                fail++;
            }
        }

        // ===== ⑤ 更新广播记录 =====
        broadcast.setSuccessCount(success);
        broadcast.setFailCount(fail);
        broadcast.setStatus(fail == 0 ? 3 : 4); // 3=已完成 4=部分失败
        broadcast.setCompleteTime(LocalDateTime.now());
        broadcastMapper.updateById(broadcast);

        log.info("广播完成 | broadcastId={} success={} fail={}", broadcast.getId(), success, fail);

        // ===== ⑥ 回复管理员 =====
        return String.format(
            "📢 **消息广播完成**\n\n标题：%s\n目标：%d 个群\n成功：%d / 失败：%d",
            title, targetIds.size(), success, fail
        );
    }
}
