package com.bluemountain.bot.api.controller;

import com.bluemountain.bot.infrastructure.entity.BotUserToken;
import com.bluemountain.bot.infrastructure.mapper.BotUserTokenMapper;
import com.bluemountain.bot.integration.client.FeishuClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * OAuth 2.0 回调端点
 */
@Slf4j
@RestController
@RequestMapping("/oauth")
@RequiredArgsConstructor
public class OAuthController {

    private final BotUserTokenMapper tokenMapper;
    private final FeishuClient feishuClient;
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 飞书授权回调
     * GET /oauth/callback?code=xxx&state=ou_xxx
     */
    @GetMapping("/callback")
    public String callback(@RequestParam("code") String code,
                           @RequestParam(value = "state", required = false) String openId) {
        log.info("OAuth 回调 | code={} openId={}", code, openId);

        try {
            // 先拿 tenant_access_token
            String tenantToken = feishuClient.getTenantToken();

            // 用 code + tenant_token 换 user_access_token
            Map<String, String> body = Map.of(
                    "grant_type", "authorization_code",
                    "code", code
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(tenantToken);   // ← 飞书要求用 tenant token 鉴权
            HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restTemplate.postForObject(
                    "https://open.feishu.cn/open-apis/authen/v1/oidc/access_token",
                    request, Map.class);

            // token 在 data 里面：{code:0, data:{access_token:..., refresh_token:...}}
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) resp.get("data");
            if (resp == null || data == null || data.get("access_token") == null) {
                log.error("换取 token 失败 | 响应={}", resp);
                return "<h1>授权失败</h1><p>请重试</p>";
            }

            String accessToken = (String) data.get("access_token");
            String refreshToken = (String) data.get("refresh_token");
            int expiresIn = (int) data.getOrDefault("expires_in", 7200);

            // 用 access_token 获取用户 open_id
            String tokenOpenId = getOpenId(accessToken);
            if (tokenOpenId == null) return "<h1>授权失败</h1><p>无法获取用户信息</p>";

            // 存入数据库
            BotUserToken token = new BotUserToken();
            token.setOpenId(tokenOpenId);
            token.setAccessToken(accessToken);
            token.setRefreshToken(refreshToken);
            token.setExpiresAt(LocalDateTime.now().plusSeconds(expiresIn - 300));
            token.setCreateTime(LocalDateTime.now());

            if (tokenMapper.selectById(tokenOpenId) != null) {
                tokenMapper.updateById(token);
            } else {
                tokenMapper.insert(token);
            }

            log.info("Token 保存成功 | openId={}", tokenOpenId);
            return "<h1>授权成功 ✅</h1><p>你现在可以用 /schedule 创建日程，系统会自动同步到飞书日历。</p>";

        } catch (Exception e) {
            log.error("OAuth 回调异常", e);
            return "<h1>授权失败</h1><p>系统异常，请重试</p>";
        }
    }

    @SuppressWarnings("unchecked")
    private String getOpenId(String accessToken) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            HttpEntity<Void> request = new HttpEntity<>(headers);

            Map<String, Object> resp = restTemplate.exchange(
                    "https://open.feishu.cn/open-apis/authen/v1/user_info",
                    HttpMethod.GET, request, Map.class).getBody();

            if (resp != null && resp.get("data") != null) {
                Map<String, Object> data = (Map<String, Object>) resp.get("data");
                return (String) data.get("open_id");
            }
        } catch (Exception e) {
            log.error("获取用户信息失败", e);
        }
        return null;
    }
}
