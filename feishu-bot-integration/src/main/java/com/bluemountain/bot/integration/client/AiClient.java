package com.bluemountain.bot.integration.client;

import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * AI 大模型客户端 — DeepSeek
 */
@Slf4j
@Component
public class AiClient {

    @Value("${ai.api-key:}")
    private String apiKey;

    @Value("${ai.model:deepseek-chat}")
    private String model;

    @Value("${ai.base-url:https://api.deepseek.com}")
    private String baseUrl;

    private final RestTemplate restTemplate;

    public AiClient() {
        this.restTemplate = new RestTemplate();
        this.restTemplate.getMessageConverters()
                .add(0, new org.springframework.http.converter.StringHttpMessageConverter(StandardCharsets.UTF_8));
    }

    /**
     * 发送对话请求
     *
     * @param systemPrompt 系统提示词（设定 AI 角色）
     * @param userMessage  用户消息（文档内容 + 问题）
     * @return AI 回复，失败返回 null
     */
    @SuppressWarnings("unchecked")
    public String chat(String systemPrompt, String userMessage) {
        if (apiKey == null || apiKey.isBlank()) {
            log.error("AI API Key 未配置");
            return null;
        }

        try {
            Map<String, Object> body = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userMessage)
                    ),
                    "temperature", 0.3,
                    "max_tokens", 1024
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey.trim());
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            log.info("AI 请求 | model={} prompt长度={}", model, userMessage.length());

            String url = baseUrl + "/v1/chat/completions";
            String json = restTemplate.postForObject(url, request, String.class);

            Map<String, Object> resp = JSONUtil.parseObj(json);
            List<Map<String, Object>> choices = (List<Map<String, Object>>) resp.get("choices");
            if (choices == null || choices.isEmpty()) {
                log.error("AI 返回异常 | 响应={}", json);
                return null;
            }

            Map<String, Object> msg = (Map<String, Object>) choices.get(0).get("message");
            String content = (String) msg.get("content");
            log.info("AI 回复 | 长度={}", content != null ? content.length() : 0);
            return content;

        } catch (Exception e) {
            log.error("AI 请求失败", e);
            return null;
        }
    }
}
