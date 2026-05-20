package com.bluemountain.bot.core.service;

import com.bluemountain.bot.integration.client.FeishuClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 群名注册中心 —— 自动维护「群名 ↔ chat_id」映射
 *
 * 每次收到消息时静默采集群名存 Redis，
 * 广播指令用群名替代 oc_xxx 这种人类不可读的 ID。
 *
 * Redis 结构：
 *   bot:group:name:项目讨论组 → oc_xxx          （按名查 ID）
 *   bot:group:id:oc_xxx       → 项目讨论组       （按 ID 查名，调试用）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupRegistry {

    private final StringRedisTemplate redisTemplate;
    private final FeishuClient feishuClient;

    private static final String NAME_PREFIX = "bot:group:name:";
    private static final String ID_PREFIX = "bot:group:id:";
    private static final Duration TTL = Duration.ofDays(7);  // 7 天不用就过期

    /**
     * 采集群名（失败不影响主流程，只记日志）
     * 在 processCommand 中每次收到消息时调用
     */
    public void collect(String chatId) {
        try {
            String name = feishuClient.getChatName(chatId);
            if (name != null && !name.isBlank()) {
                register(chatId, name);
            }
        } catch (Exception e) {
            log.debug("群名采集失败 | chatId={}", chatId);
        }
    }

    /**
     * 注册群名映射
     */
    public void register(String chatId, String name) {
        redisTemplate.opsForValue().set(NAME_PREFIX + name, chatId, TTL);
        redisTemplate.opsForValue().set(ID_PREFIX + chatId, name, TTL);
        log.info("群名已注册 | name=[{}] chatId={}", name, chatId);
    }

    /**
     * 解析群标识（兼容群名和 chat_id）
     * 输入 "项目讨论组" → 从 Redis 查出 oc_xxx
     * 输入 "oc_xxx"   → 直接返回 oc_xxx
     * 输入 null/空    → 返回 null
     */
    public String resolve(String input) {
        if (input == null || input.isBlank()) return null;
        String trimmed = input.trim();
        // 已经是 chat_id（oc_ 开头）→ 直接返回
        if (trimmed.startsWith("oc_")) return trimmed;
        // 当成群名 → 从 Redis 查
        String chatId = redisTemplate.opsForValue().get(NAME_PREFIX + trimmed);
        if (chatId != null) {
            log.debug("群名解析 | name=[{}] → chatId={}", trimmed, chatId);
            return chatId;
        }
        log.warn("群名未注册 | input=[{}]", trimmed);
        return null;
    }
}
