package com.bluemountain.bot.core.service;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.bluemountain.bot.integration.client.AiClient;
import com.bluemountain.bot.integration.client.FeishuClient;
import com.bluemountain.bot.integration.client.GiteeClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Git 代码审查服务
 *
 * Gitee push Webhook → 提取 diff → 调 AI 审查 → 发飞书群
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GitReviewService {

    private final GiteeClient giteeClient;
    private final AiClient aiClient;
    private final FeishuClient feishuClient;

    @Value("${feishu.review-chat-id:}")
    private String reviewChatId;

    private static final String REVIEW_PROMPT = """
        你是一个资深代码审查专家。审查以下 Git diff，重点检查：

        1. 空指针风险（NPE）—— 新增代码中参数/返回值未判空
        2. 资源泄漏 —— 文件流/连接未关闭、未用 try-with-resources
        3. 异常处理 —— catch 块为空、吞异常不记录日志
        4. 线程安全 —— 共享变量无同步、非线程安全类误用
        5. 代码质量 —— 命名不规范、硬编码、重复代码

        输出格式：
        ## 🔍 代码审查报告

        **仓库**：{repo}
        **提交者**：{author}
        **提交信息**：{message}

        **评分**：X/10

        **风险清单**：
        - [高危/中危/低危] 问题描述
          → 修改建议

        **优点**：做得好的地方
        """;

    /**
     * 处理 Gitee Push Webhook
     */
    @SuppressWarnings("unchecked")
    public void handlePushEvent(String body) {
        if (reviewChatId == null || reviewChatId.isBlank()) {
            log.warn("未配置 review-chat-id，跳过推送");
            return;
        }

        JSONObject root = JSONUtil.parseObj(body);

        // 提取基本信息
        String before = root.getStr("before");
        String after = root.getStr("after");
        JSONObject repo = root.getJSONObject("repository");
        String repoPath = repo != null ? repo.getStr("path") : "";
        String repoName = repo != null ? repo.getStr("name") : "";
//可能commit了三次push了一次
        List<Map<String, Object>> commits =
                (List<Map<String, Object>>) root.get("commits");
        if (commits == null || commits.isEmpty()) return;

        log.info("收到 Gitee Push | repo={} commits={}", repoName, commits.size());

        // 获取实际代码 diff,不能是第一次push
        String diff = null;
        if (repoPath != null && !repoPath.isBlank()
                && before != null && !"0000000000000000000000000000000000000000".equals(before)) {
            diff = giteeClient.getCompareDiff(repoPath, before, after);
        }

        // 审查每个 commit
        for (Map<String, Object> commit : commits) {
            String message = (String) commit.get("message");
            Map<String, String> author = (Map<String, String>) commit.get("author");
            String authorName = author != null ? author.get("name") : "未知";

            // 拼审查内容
            StringBuilder reviewContent = new StringBuilder();
            if (diff != null && !diff.isBlank()) {
                reviewContent.append("代码 Diff：\n").append(diff);
            } else {
                // 没有 diff → 用文件列表 + commit message
                reviewContent.append("本次提交信息：").append(message).append("\n\n");
                reviewContent.append("修改文件：\n");
                appendList(reviewContent, (List<String>) commit.get("modified"), "修改");
                appendList(reviewContent, (List<String>) commit.get("added"), "新增");
                appendList(reviewContent, (List<String>) commit.get("removed"), "删除");
            }

            // 调 AI 审查
            String prompt = REVIEW_PROMPT
                    .replace("{repo}", repoName)
                    .replace("{author}", authorName)
                    .replace("{message}", message);
            String review = aiClient.chat(prompt, reviewContent.toString());

            if (review != null) {
                feishuClient.sendTextMessage(reviewChatId, review.trim());
                log.info("审查结果已发送 | repo={} author={}", repoName, authorName);
            }
        }
    }

    private void appendList(StringBuilder sb, List<String> list, String label) {
        if (list != null && !list.isEmpty()) {
            sb.append(label).append("：").append(String.join(", ", list)).append("\n");
        }
    }
}
