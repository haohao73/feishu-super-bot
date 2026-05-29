package com.bluemountain.bot.common.dto;

import lombok.Data;

/**
 * 指令上下文：解析后传递给 Handler 的数据载体
 */
@Data
public class CommandContext {

    /** 指令名，如 "weather"、"schedule" */
    private String command;

    /** 指令后面的参数原文，如 "北京"、"明天下午3点 开会" */
    private String args;

    /** 按空格分割的参数数组，args="北京 海淀" → ["北京","海淀"] */
    private String[] argArray;

    /** 飞书群 ID，回复消息时要用 */
    private String chatId;

    /** 发送者的飞书 open_id */
    private String userId;

    /** 原始消息全文（调试用） */
    private String rawMessage;

    /** 飞书消息唯一 ID（去重用） */
    private String messageId;

    /**
     * 从原始消息解析出指令上下文
     */
    public static CommandContext parse(String chatId, String userId, String messageId, String messageText) {
        CommandContext ctx = new CommandContext();
        ctx.setChatId(chatId);
        ctx.setUserId(userId);
        ctx.setMessageId(messageId);
        ctx.setRawMessage(messageText);

        String text = messageText.trim(); //去掉首尾的空格和换行符

        // 去掉 @机器人 前缀（群聊里 @ 机器人后消息格式是 "@_user_1 /weather 北京"）
        if (text.contains("@")) {
            int slashIdx = text.indexOf("/");
            if (slashIdx < 0) return null; // 没有斜杠指令
            text = text.substring(slashIdx); // 从第一个 / 开始截
        }

        if (!text.startsWith("/")) {
            return null; // 不是指令，不处理,但是要考虑上下文延续的问题
        }

        text = text.substring(1); // 去掉开头的 /
        int spaceIdx = text.indexOf(" ");
        if (spaceIdx > 0) {
            ctx.setCommand(text.substring(0, spaceIdx));
            ctx.setArgs(text.substring(spaceIdx + 1).trim());
            ctx.setArgArray(ctx.getArgs().split("\\s+"));//数组
        } else {
            ctx.setCommand(text);
            ctx.setArgs("");
            ctx.setArgArray(new String[0]);
        }

        return ctx;
    }
}
