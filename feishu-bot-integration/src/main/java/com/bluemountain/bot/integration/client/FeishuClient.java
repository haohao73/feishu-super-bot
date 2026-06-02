package com.bluemountain.bot.integration.client;

import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

/**
 * 飞书 API 客户端 — WebClient 版
 */
@Slf4j
@Component
public class FeishuClient {

    private final WebClient webClient;

    @Value("${feishu.app-id}")
    private String appId;

    @Value("${feishu.app-secret}")
    private String appSecret;

    private String cachedToken;
    private long tokenExpireAt;

    private static final String FEISHU_HOST = "https://open.feishu.cn";

    public FeishuClient() {
        this.webClient = WebClient.builder()
                .baseUrl(FEISHU_HOST)
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
    }

    // ==================== Token ====================

    public synchronized String getTenantToken() {
        if (cachedToken != null && System.currentTimeMillis() < tokenExpireAt) {
            return cachedToken;
        }

        log.info("换租户 Token | appId={}", appId);
        Map<String, String> body = Map.of("app_id", appId, "app_secret", appSecret);

        try {
            Map<String, Object> resp = webClient.post()
                    .uri("/open-apis/auth/v3/tenant_access_token/internal")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            if (resp == null || (int) resp.get("code") != 0) {
                log.error("获取 tenant_token 失败 | 响应={}", resp);
                throw new RuntimeException("飞书 Token 换取失败");
            }

            cachedToken = (String) resp.get("tenant_access_token");
            int expire = (int) resp.get("expire");
            tokenExpireAt = System.currentTimeMillis() + (expire - 300) * 1000L;
            log.info("Token 刷新成功 | 有效期={}秒", expire);
            return cachedToken;

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取 tenant_token 异常", e);
            throw new RuntimeException("飞书 Token 换取失败", e);
        }
    }

    // ==================== 发送消息 ====================

    public void sendTextMessage(String chatId, String content) {
        String jsonContent = JSONUtil.toJsonStr(Map.of("text", content));
        Map<String, Object> body = Map.of(
                "receive_id", chatId,
                "msg_type", "text",
                "content", jsonContent
        );

        try {
            Map<String, Object> resp = webClient.post()
                    .uri("/open-apis/im/v1/messages?receive_id_type=chat_id")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + getTenantToken())
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            if (resp != null && (int) resp.get("code") == 0) {
                log.info("消息发送成功 | chatId={}", chatId);
            } else {
                log.error("消息发送失败 | 响应={}", resp);
            }
        } catch (Exception e) {
            log.error("消息发送异常 | chatId={}", chatId, e);
        }
    }

    // ==================== 创建群聊 ====================

    public String createChat(String name) {
        Map<String, Object> body = Map.of("name", name, "chat_type", "group");

        try {
            Map<String, Object> resp = webClient.post()
                    .uri("/open-apis/im/v1/chats")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + getTenantToken())
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            if (resp != null && (int) resp.get("code") == 0) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) resp.get("data");
                String chatId = (String) data.get("chat_id");
                log.info("群聊创建成功 | name={} chatId={}", name, chatId);
                return chatId;
            } else {
                log.error("群聊创建失败 | 响应={}", resp);
                return null;
            }
        } catch (Exception e) {
            log.error("群聊创建异常 | name={}", name, e);
            return null;
        }
    }

    // ==================== 添加成员到群聊 ====================

    public void addMemberToChat(String chatId, String openId) {
        addMembersToChat(chatId, List.of(openId));
    }

    public void addMembersToChat(String chatId, List<String> openIds) {
        Map<String, Object> body = Map.of("id_list", openIds);

        try {
            Map<String, Object> resp = webClient.post()
                    .uri("/open-apis/im/v1/chats/" + chatId + "/members?member_id_type=open_id")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + getTenantToken())
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            if (resp != null && (int) resp.get("code") == 0) {
                log.info("成员添加成功 | chatId={} count={}", chatId, openIds.size());
            } else {
                log.error("成员添加失败 | 响应={}", resp);
            }
        } catch (Exception e) {
            log.error("成员添加异常", e);
        }
    }

    // ==================== 创建日历事件 ====================

    public String createCalendarEvent(String userAccessToken, String summary,
                                       java.time.LocalDateTime startTime) {
        java.time.LocalDateTime endTime = startTime.plusHours(1);
        long startSec = startTime.atZone(java.time.ZoneId.of("Asia/Shanghai")).toEpochSecond();
        long endSec = endTime.atZone(java.time.ZoneId.of("Asia/Shanghai")).toEpochSecond();

        Map<String, Object> body = Map.of(
                "summary", summary,
                "start_time", Map.of("timestamp", String.valueOf(startSec), "timezone", "Asia/Shanghai"),
                "end_time", Map.of("timestamp", String.valueOf(endSec), "timezone", "Asia/Shanghai")
        );

        try {
            Map<String, Object> resp = webClient.post()
                    .uri("/open-apis/calendar/v4/calendars/primary/events")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + userAccessToken)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            if (resp != null && (int) resp.get("code") == 0) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) resp.get("data");
                String eventId = (String) data.get("event_id");
                log.info("日历事件创建成功 | eventId={}", eventId);
                return eventId;
            } else {
                log.error("日历事件创建失败 | 响应={}", resp);
                return null;
            }
        } catch (Exception e) {
            log.error("日历事件创建异常", e);
            return null;
        }
    }

    // ==================== 查询群信息 ====================

    public String getChatName(String chatId) {
        try {
            Map<String, Object> resp = webClient.get()
                    .uri("/open-apis/im/v1/chats/" + chatId)
                    .header("Authorization", "Bearer " + getTenantToken())
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            if (resp != null && (int) resp.get("code") == 0) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) resp.get("data");
                String name = (String) data.get("name");
                log.info("获取群名成功 | chatId={} name={}", chatId, name);
                return name;
            }
            log.warn("获取群名失败 | chatId={} resp={}", chatId, resp);
            return null;

        } catch (Exception e) {
            log.warn("获取群名异常 | chatId={} msg={}", chatId, e.getMessage());
            return null;
        }
    }

    // ==================== 发送私聊消息 ====================

    public void sendTextToUser(String openId, String content) {
        String jsonContent = JSONUtil.toJsonStr(Map.of("text", content));
        Map<String, Object> body = Map.of(
                "receive_id", openId,
                "msg_type", "text",
                "content", jsonContent
        );

        try {
            webClient.post()
                    .uri("/open-apis/im/v1/messages?receive_id_type=open_id")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + getTenantToken())
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();

            log.info("私聊消息发送 | openId={}", openId);
        } catch (Exception e) {
            log.error("私聊消息发送失败 | openId={}", openId, e);
        }
    }

    // ==================== 用户信息 ====================

    @SuppressWarnings("unchecked")
    public List<String> findOpenIdsByName(String name) {
        try {
            Map<String, Object> resp = webClient.get()
                    .uri("/open-apis/contact/v3/users?query=" + name + "&page_size=5")
                    .header("Authorization", "Bearer " + getTenantToken())
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            if (resp != null && (int) resp.get("code") == 0) {
                Map<String, Object> data = (Map<String, Object>) resp.get("data");
                List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("items");
                if (items != null) {
                    return items.stream()
                            .map(u -> (String) u.get("open_id"))
                            .filter(id -> id != null)
                            .toList();
                }
            }
            log.warn("查找用户失败 | name={} resp={}", name, resp);
        } catch (Exception e) {
            log.error("查找用户异常 | name={}", name, e);
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    public String getUnionIdByOpenId(String openId) {
        try {
            Map<String, Object> resp = webClient.get()
                    .uri("/open-apis/contact/v3/users/" + openId)
                    .header("Authorization", "Bearer " + getTenantToken())
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            if (resp != null && (int) resp.get("code") == 0) {
                Map<String, Object> data = (Map<String, Object>) resp.get("data");
                Map<String, Object> user = (Map<String, Object>) data.get("user");
                if (user != null) {
                    return (String) user.get("union_id");
                }
            }
            log.warn("获取用户 union_id 失败 | openId={} resp={}", openId, resp);
        } catch (Exception e) {
            log.error("获取用户 union_id 异常 | openId={}", openId, e);
        }
        return null;
    }

    public void sendTextToUnionId(String unionId, String content) {
        String jsonContent = JSONUtil.toJsonStr(Map.of("text", content));
        Map<String, Object> body = Map.of(
                "receive_id", unionId,
                "msg_type", "text",
                "content", jsonContent
        );

        try {
            webClient.post()
                    .uri("/open-apis/im/v1/messages?receive_id_type=union_id")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + getTenantToken())
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();

            log.info("私聊消息发送(union_id) | unionId={}", unionId);
        } catch (Exception e) {
            log.error("私聊消息发送失败(union_id) | unionId={}", unionId, e);
        }
    }

    // ==================== 审批 API ====================

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> fetchApprovalInstances(String approvalCode,
                                                             long startTime, long endTime) {
        StringBuilder urlBuilder = new StringBuilder(
                "/open-apis/approval/v4/instances?page_size=50"
                + "&start_time=" + startTime * 1000
                + "&end_time=" + endTime * 1000);
        if (approvalCode != null && !approvalCode.isBlank()) {
            urlBuilder.append("&approval_code=").append(approvalCode);
        }

        try {
            Map<String, Object> resp = webClient.get()
                    .uri(urlBuilder.toString())
                    .header("Authorization", "Bearer " + getTenantToken())
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            if (resp != null && (int) resp.get("code") == 0) {
                Map<String, Object> data = (Map<String, Object>) resp.get("data");
                List<Map<String, Object>> list =
                        (List<Map<String, Object>>) data.get("instance_list");
                log.info("拉取审批成功 | count={} approvalCode={}",
                        list != null ? list.size() : 0, approvalCode);
                return list != null ? list : List.of();
            }
            log.warn("拉取审批失败 | code={} msg={}",
                    resp != null ? resp.get("code") : -1,
                    resp != null ? resp.get("msg") : "null");
            return List.of();

        } catch (Exception e) {
            log.error("拉取审批异常", e);
            return List.of();
        }
    }
}
