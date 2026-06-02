package com.bluemountain.bot.integration.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

/**
 * AI 大模型客户端 — DeepSeek（OpenAI 兼容接口）
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

    private final WebClient webClient;

    public AiClient() {
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
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

            log.info("AI 请求 | model={} prompt长度={}", model, userMessage.length());

            Map<String, Object> resp = webClient.post()
                    .uri(baseUrl + "/v1/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey.trim())
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            List<Map<String, Object>> choices = (List<Map<String, Object>>) resp.get("choices");
            if (choices == null || choices.isEmpty()) {
                log.error("AI 返回异常 | 响应={}", resp);
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
