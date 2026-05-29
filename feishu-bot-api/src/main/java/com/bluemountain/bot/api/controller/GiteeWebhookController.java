package com.bluemountain.bot.api.controller;

import com.bluemountain.bot.core.service.GitReviewService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedReader;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Gitee Webhook 回调端点
 *
 * Gitee push 事件 → 提取 diff → AI 审查 → 发飞书群
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class GiteeWebhookController {

    private final GitReviewService gitReviewService;
    private final ExecutorService executor = Executors.newFixedThreadPool(3);

    @PostMapping("/webhook/gitee")
    public Object onPush(HttpServletRequest request) {
        String body = readBody(request);
        log.info("Gitee Webhook 收到推送 | bodyLen={}", body.length());

        // 异步处理，快速返回 200扔进线程池
        executor.submit(() -> gitReviewService.handlePushEvent(body));

        return Map.of("ok", true);
    }
//读取gitee返回的事件体,就是新推送的代码
    private String readBody(HttpServletRequest request) {
        try {
            StringBuilder sb = new StringBuilder();
            BufferedReader reader = request.getReader();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
