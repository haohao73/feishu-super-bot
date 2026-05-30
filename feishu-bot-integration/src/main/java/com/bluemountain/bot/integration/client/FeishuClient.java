package com.bluemountain.bot.integration.client;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * 飞书 API 客户端：获取 Token、发送消息、创建群聊
 */
@Slf4j
@Component
public class FeishuClient {

    private final RestTemplate restTemplate;

    @Value("${feishu.app-id}")
    private String appId;

    @Value("${feishu.app-secret}")
    private String appSecret;

    private String cachedToken;
    private long tokenExpireAt;

    private static final String FEISHU_HOST = "https://open.feishu.cn";
    private static final String TOKEN_URL = FEISHU_HOST + "/open-apis/auth/v3/tenant_access_token/internal";
    private static final String SEND_MSG_URL = FEISHU_HOST + "/open-apis/im/v1/messages?receive_id_type=chat_id";
    private static final String SEND_MSG_TO_USER_URL = FEISHU_HOST + "/open-apis/im/v1/messages?receive_id_type=open_id";
    private static final String CREATE_CHAT_URL = FEISHU_HOST + "/open-apis/im/v1/chats";
    private static final String GET_CHAT_URL = FEISHU_HOST + "/open-apis/im/v1/chats/";
    private static final String APPROVAL_LIST_URL = FEISHU_HOST + "/open-apis/approval/v4/instances";
    private static final String GET_USER_URL = FEISHU_HOST + "/open-apis/contact/v3/users/";

    public FeishuClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(15000);
        this.restTemplate = new RestTemplate(factory);
        this.restTemplate.getMessageConverters()
                .add(0, new org.springframework.http.converter.StringHttpMessageConverter(
                        java.nio.charset.StandardCharsets.UTF_8));
    }

    // ==================== Token ====================

    public synchronized String getTenantToken() {
        if (cachedToken != null && System.currentTimeMillis() < tokenExpireAt) {
            return cachedToken;
        }

        log.info("换租户 Token | appId={}", appId);
        Map<String, String> body = Map.of("app_id", appId, "app_secret", appSecret);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restTemplate.postForObject(TOKEN_URL, request, Map.class);
            if (resp == null || (int) resp.get("code") != 0) {
                log.error("获取 tenant_token 失败 | 响应={}", resp);
                throw new RuntimeException("飞书 Token 换取失败");
            }

            cachedToken = (String) resp.get("tenant_access_token");
            int expire = (int) resp.get("expire");
            tokenExpireAt = System.currentTimeMillis() + (expire - 300) * 1000L;
            log.info("Token 刷新成功 | 有效期={}秒", expire);
            return cachedToken;

        } catch (Exception e) {
            log.error("获取 tenant_token 异常", e);
            throw new RuntimeException("飞书 Token 换取失败", e);
        }
    }

    // ==================== 发送消息 ====================

    /**
     * 发送消息到飞书群
     */
    public void sendTextMessage(String chatId, String content) {
        String token = getTenantToken();

        String jsonContent = JSONUtil.toJsonStr(Map.of("text", content));

        Map<String, Object> body = Map.of(
                "receive_id", chatId,
                "msg_type", "text",
                "content", jsonContent
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restTemplate.postForObject(SEND_MSG_URL, request, Map.class);
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

    /**
     * 创建飞书群聊
     *
     * POST /open-apis/im/v1/chats
     * Body: {"name": "群名", "chat_type": "group"}
     *
     * @param name 群名称
     * @return 创建成功返回 chat_id，失败返回 null
     */
    public String createChat(String name) {
        String token = getTenantToken();

        Map<String, Object> body = Map.of(
                "name", name,
                "chat_type", "group"
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restTemplate.postForObject(CREATE_CHAT_URL, request, Map.class);
            if (resp != null && (int) resp.get("code") == 0) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) resp.get("data");
                String chatId = (String) data.get("chat_id");
                log.info("群聊创建成功 | name={} chatId={}", name, chatId);
                //返回创建的群聊的id
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

    /**
     * 拿token,构造请求头和请求体,发送请求,解析并返回创建的群聊
     * @param chatId
     * @param openId
     */

    // ==================== 添加成员到群聊 ====================

    public void addMemberToChat(String chatId, String openId) {
        addMembersToChat(chatId, java.util.List.of(openId));
    }

    public void addMembersToChat(String chatId, java.util.List<String> openIds) {
        String token = getTenantToken();
        Map<String, Object> body = Map.of("id_list", openIds);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        String url = FEISHU_HOST + "/open-apis/im/v1/chats/" + chatId + "/members?member_id_type=open_id";

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restTemplate.postForObject(url, request, Map.class);
            if (resp != null && (int) resp.get("code") == 0) {
                log.info("成员添加成功 | chatId={} count={}", chatId, openIds.size());
            } else {
                log.error("成员添加失败 | 响应={}", resp);
            }
        } catch (Exception e) {
            log.error("成员添加异常", e);
        }
    }
/**
 *
 * 依旧拼请求头请求体,发送请求解析数据并返回
 */
    // ==================== 创建日历事件 ====================

    /**
     * 在用户主日历中创建日程事件
     *
     * POST /open-apis/calendar/v4/calendars/primary/events
     * Header: Authorization: Bearer {user_access_token}  ← 注意是用户的 token
     *
     * @param userAccessToken 用户的 access_token（OAuth 获取）
     * @param summary         事件标题
     * @param startTime       开始时间
     * @return 成功返回 event_id，失败返回 null
     */
    public String createCalendarEvent(String userAccessToken, String summary, java.time.LocalDateTime startTime) {
        // 默认时长 1 小时
        java.time.LocalDateTime endTime = startTime.plusHours(1);

        // 转成 Unix 秒
        long startSec = startTime.atZone(java.time.ZoneId.of("Asia/Shanghai")).toEpochSecond();
        long endSec = endTime.atZone(java.time.ZoneId.of("Asia/Shanghai")).toEpochSecond();

        Map<String, Object> body = Map.of(
                "summary", summary,
                "start_time", Map.of(
                        "timestamp", String.valueOf(startSec),
                        "timezone", "Asia/Shanghai"
                ),
                "end_time", Map.of(
                        "timestamp", String.valueOf(endSec),
                        "timezone", "Asia/Shanghai"
                )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(userAccessToken);

        // 用 Hutool 序列化确保 JSON 格式正确
        String jsonBody = cn.hutool.json.JSONUtil.toJsonStr(body);
        log.info("日历请求 body | {}", jsonBody);
        HttpEntity<String> request = new HttpEntity<>(jsonBody, headers);

        String url = FEISHU_HOST + "/open-apis/calendar/v4/calendars/primary/events";

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restTemplate.postForObject(url, request, Map.class);
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

    /**
     * 根据 chat_id 获取群名称
     *
     * GET /open-apis/im/v1/chats/{chat_id}
     * 返回：{"code":0, "data":{"name":"项目讨论组", "chat_id":"oc_xxx", ...}}
     *
     * @return 群名称，失败返回 null
     */
    public String getChatName(String chatId) {
        String token = getTenantToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restTemplate.exchange(
                    GET_CHAT_URL + chatId,
                    org.springframework.http.HttpMethod.GET,
                    request,
                    Map.class).getBody();

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

    /**
     * 给指定用户发私聊消息（通过 open_id）
     */
    public void sendTextToUser(String openId, String content) {
        String token = getTenantToken();
        String jsonContent = cn.hutool.json.JSONUtil.toJsonStr(Map.of("text", content));

        Map<String, Object> body = Map.of(
                "receive_id", openId,
                "msg_type", "text",
                "content", jsonContent);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            restTemplate.postForObject(SEND_MSG_TO_USER_URL, request, Map.class);
            log.info("私聊消息发送 | openId={}", openId);
        } catch (Exception e) {
            log.error("私聊消息发送失败 | openId={}", openId, e);
        }
    }

    // ==================== 用户信息 ====================

    /**
     * 通过姓名查找用户的 open_id 列表
     *
     * GET /open-apis/contact/v3/users?query=姓名&page_size=5
     */
    @SuppressWarnings("unchecked")
    public List<String> findOpenIdsByName(String name) {
        String token = getTenantToken();
        String url = FEISHU_HOST + "/open-apis/contact/v3/users?query=" + name + "&page_size=5";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            Map<String, Object> resp = restTemplate.exchange(
                    url, org.springframework.http.HttpMethod.GET,
                    request, Map.class).getBody();

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

    /**
     * 通过 open_id 获取用户 union_id（跨应用通用标识）
     */
    @SuppressWarnings("unchecked")
    public String getUnionIdByOpenId(String openId) {
        String token = getTenantToken();
        String url = GET_USER_URL + openId;

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            Map<String, Object> resp = restTemplate.exchange(
                    url, org.springframework.http.HttpMethod.GET,
                    request, Map.class).getBody();

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

    /**
     * 通过 union_id 给用户发私聊（解决跨应用 open_id 不通用问题）
     */
    public void sendTextToUnionId(String unionId, String content) {
        String token = getTenantToken();
        String jsonContent = cn.hutool.json.JSONUtil.toJsonStr(Map.of("text", content));

        Map<String, Object> body = Map.of(
                "receive_id", unionId,
                "msg_type", "text",
                "content", jsonContent);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            String url = FEISHU_HOST + "/open-apis/im/v1/messages?receive_id_type=union_id";
            restTemplate.postForObject(url, request, Map.class);
            log.info("私聊消息发送(union_id) | unionId={}", unionId);
        } catch (Exception e) {
            log.error("私聊消息发送失败(union_id) | unionId={}", unionId, e);
        }
    }

    // ==================== 审批 API ====================

    /**
     * 拉取审批实例列表
     *
     * GET /open-apis/approval/v4/instances?approval_code=xxx&start_time=xxx&page_size=50
     *
     * @param approvalCode 审批定义 code
     * @param startTime    查询起始时间（Unix秒）
     * @return 返回原始 JSON 列表，失败返回空列表
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> fetchApprovalInstances(String approvalCode, long startTime, long endTime) {
        String token = getTenantToken();

        // 去掉 approval_code 过滤，拉取所有类型的审批实例
        String url = APPROVAL_LIST_URL
                + "?page_size=50"
                + "&start_time=" + startTime * 1000
                + "&end_time=" + endTime * 1000;
        if (approvalCode != null && !approvalCode.isBlank()) {
            url += "&approval_code=" + approvalCode;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            Map<String, Object> resp = restTemplate.exchange(
                    url, org.springframework.http.HttpMethod.GET,
                    request, Map.class).getBody();

            if (resp != null && (int) resp.get("code") == 0) {
                Map<String, Object> data = (Map<String, Object>) resp.get("data");
                List<Map<String, Object>> list =
                        (List<Map<String, Object>>) data.get("instance_list");
                log.info("拉取审批成功 | count={} approvalCode={}",
                        list != null ? list.size() : 0, approvalCode);
                if (list != null && !list.isEmpty()) {
                    log.info("首个实例 | instance={}", list.get(0));
                }
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
