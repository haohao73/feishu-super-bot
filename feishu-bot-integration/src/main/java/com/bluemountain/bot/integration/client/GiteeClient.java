package com.bluemountain.bot.integration.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

/**
 * Gitee API 客户端 — 获取 commit diff 用于代码审查
 */
@Slf4j
@Component
public class GiteeClient {

    private final WebClient webClient;

    @Value("${gitee.token:}")
    private String giteeToken;

    private static final String GITEE_API = "https://gitee.com/api/v5/repos/";

    public GiteeClient() {
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
    }

    /** 构建带可选 Token 的 GET 请求 */
    private WebClient.RequestHeadersSpec<?> getWithAuth(String url) {
        var spec = webClient.get().uri(url);
        if (giteeToken != null && !giteeToken.isBlank()) {
            spec.header("Authorization", "Bearer " + giteeToken);
        }
        return spec;
    }

    /**
     * 获取两次提交之间的 diff
     */
    @SuppressWarnings("unchecked")
    public String getCompareDiff(String repoPath, String before, String after) {
        String url = GITEE_API + repoPath + "/compare/" + before + "..." + after;

        try {
            Map<String, Object> resp = getWithAuth(url)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            if (resp != null && resp.get("files") != null) {
                StringBuilder diff = new StringBuilder();
                for (Map<String, Object> file : (List<Map<String, Object>>) resp.get("files")) {
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

    /**
     * 获取仓库最近的提交日志
     */
    public List<Map<String, Object>> getCommits(String repoPath) {
        String url = GITEE_API + repoPath + "/commits?per_page=5";

        try {
            List<Map<String, Object>> commits = getWithAuth(url)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                    .block();

            if (commits == null) return List.of();
            log.info("获取 commits 成功 | repo={} 数量={}", repoPath, commits.size());
            return commits;

        } catch (Exception e) {
            log.warn("获取 commits 异常 | msg={}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 获取单个 commit 的代码 diff
     */
    @SuppressWarnings("unchecked")
    public String getCommitDiff(String repoPath, String sha) {
        String url = GITEE_API + repoPath + "/commits/" + sha;

        try {
            Map<String, Object> resp = getWithAuth(url)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            if (resp != null && resp.get("files") != null) {
                StringBuilder diff = new StringBuilder();
                for (Map<String, Object> file : (List<Map<String, Object>>) resp.get("files")) {
                    String patch = (String) file.get("patch");
                    if (patch != null) {
                        diff.append("文件：").append(file.get("filename")).append("\n");
                        diff.append(patch).append("\n\n");
                    }
                }
                return diff.toString();
            }
            return null;

        } catch (Exception e) {
            log.warn("获取 commit diff 异常 | msg={}", e.getMessage());
            return null;
        }
    }
}
