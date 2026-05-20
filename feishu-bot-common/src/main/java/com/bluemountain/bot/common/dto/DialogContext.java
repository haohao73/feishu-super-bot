package com.bluemountain.bot.common.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 对话上下文 —— 存 Redis 的载体
 *
 * 一个用户一个 DialogContext，key = "dialog:{openId}"
 *
 * 存什么：上一轮指令 + 参数 + 历史对话（给 AI 看的）
 * 活多久：10 分钟无新消息自动过期
 */
@Data
public class DialogContext {

    /** 用户 open_id */
    private String openId;

    /** 上一轮指令名，如 "weather" */
    private String lastCommand;

    /** 上一轮参数，如 {"city":"北京"} */
    private Map<String, String> lastArgs;

    /** 最近几轮对话历史（发给 AI 理解意图用） */
    private List<Turn> history;

    /** 当前话题轮数 */
    private int turnCount;

    /** 最后活跃时间戳（毫秒） */
    private long lastActiveTime;

    // ==================== 常量 ====================

    /** 最大连续对话轮数，超过就清历史 */
    public static final int MAX_TURNS = 5;

    /** Redis TTL（秒） */
    public static final int TTL_SECONDS = 600;

    /** Redis Key 前缀 */
    public static final String KEY_PREFIX = "dialog:";

    // ==================== 内部类 ====================

    /** 一轮对话 */
    @Data
    public static class Turn {
        private String role;   // "user" / "assistant"
        private String text;   // 消息内容
    }

    // ==================== 工具方法 ====================

    /** 拼 Redis key */
    public static String redisKey(String openId) {
        return KEY_PREFIX + openId;
    }
}
