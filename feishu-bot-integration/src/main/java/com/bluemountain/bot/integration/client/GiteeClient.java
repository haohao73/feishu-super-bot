package com.bluemountain.bot.integration.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Gitee API 客户端 — 获取 commit diff 用于代码审查
 */
@Slf4j
@Component
public class GiteeClient {

    private final RestTemplate restTemplate;

    @Value("${gitee.token:}")
    private String giteeToken;

    public GiteeClient() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * 获取两次提交之间的 diff
     *
     * GET https://gitee.com/api/v5/repos/{owner}/{repo}/compare/{before}...{after}
     *
     * @param repoPath 仓库路径，如 "haohao/my-project"
     * @param before   旧 commit SHA
     * @param after    新 commit SHA
     * @return 统一 diff 格式文本，失败返回 null
     */
    @SuppressWarnings("unchecked")
    public String getCompareDiff(String repoPath, String before, String after) {
        String url = "https://gitee.com/api/v5/repos/" + repoPath
                + "/compare/" + before + "..." + after;

        HttpHeaders headers = new HttpHeaders();
        if (giteeToken != null && !giteeToken.isBlank()) {
            headers.setBearerAuth(giteeToken);
        }
        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            Map<String, Object> resp = restTemplate.exchange(
                    url, HttpMethod.GET, request, Map.class).getBody();

            if (resp != null && resp.get("files") != null) {
                // Gitee compare API 返回 files 列表，每个 file 含 patch 字段
                StringBuilder diff = new StringBuilder();
                for (Map<String, Object> file : (Iterable<Map<String, Object>>) resp.get("files")) {
                    String patch = (String) file.get("patch");
                    if (patch != null) {
                        diff.append("文件：").append(file.get("filename")).append("\n");
                        diff.append(patch).append("\n\n");
                    }
                }
                return diff.toString();
            }
            log.warn("获取 diff 失败 | resp={}", resp);
            return null;

        } catch (Exception e) {
            log.warn("获取 diff 异常 | msg={}", e.getMessage());
            return null;
        }
    }
}
