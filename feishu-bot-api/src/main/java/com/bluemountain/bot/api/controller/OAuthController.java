package com.bluemountain.bot.api.controller;

import com.bluemountain.bot.infrastructure.entity.BotUserToken;
import com.bluemountain.bot.infrastructure.mapper.BotUserTokenMapper;
import com.bluemountain.bot.integration.client.FeishuClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

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
    private final WebClient webClient = WebClient.builder()
            .baseUrl("https://open.feishu.cn")
            .build();

    @GetMapping("/callback")
    public String callback(@RequestParam("code") String code,
                           @RequestParam(value = "state", required = false) String openId) {
        log.info("OAuth 回调 | code={} openId={}", code, openId);

        try {
            String tenantToken = feishuClient.getTenantToken();

            Map<String, String> body = Map.of(
                    "grant_type", "authorization_code",
                    "code", code
            );

            Map<String, Object> resp = webClient.post()
                    .uri("/open-apis/authen/v1/oidc/access_token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + tenantToken)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) resp.get("data");
            if (resp == null || data == null || data.get("access_token") == null) {
                log.error("换取 token 失败 | 响应={}", resp);
                return "<h1>授权失败</h1><p>请重试</p>";
            }

            String accessToken = (String) data.get("access_token");
            String refreshToken = (String) data.get("refresh_token");
            int expiresIn = (int) data.getOrDefault("expires_in", 7200);

            String tokenOpenId = getOpenId(accessToken);
            if (tokenOpenId == null) return "<h1>授权失败</h1><p>无法获取用户信息</p>";

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
            Map<String, Object> resp = webClient.get()
                    .uri("/open-apis/authen/v1/user_info")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

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
