package com.bluemountain.bot.integration.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
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
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(15000);
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * 获取两次提交之间的 diff
     * GET https://gitee.com/api/v5/repos/{owner}/{repo}/compare/{before}...{after}
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
//把文件名和具体改动的代码拼在一起
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

    /**
     * 获取仓库最近的提交日志
     * GET https://gitee.com/api/v5/repos/{owner}/{repo}/commits
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getCommits(String repoPath) {
        String url = "https://gitee.com/api/v5/repos/" + repoPath + "/commits?per_page=5";

        HttpHeaders headers = new HttpHeaders();
        if (giteeToken != null && !giteeToken.isBlank()) {
            headers.setBearerAuth(giteeToken);
        }
        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            Map<String, Object>[] commits = restTemplate.exchange(
                    url, HttpMethod.GET, request, Map[].class).getBody();
            if (commits == null) return List.of();

            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> c : commits) {
                result.add(c);
            }
            log.info("获取 commits 成功 | repo={} 数量={}", repoPath, result.size());
            return result;

        } catch (Exception e) {
            log.warn("获取 commits 异常 | msg={}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 获取单个 commit 的代码 diff
     * GET https://gitee.com/api/v5/repos/{owner}/{repo}/commits/{sha}
     */
    @SuppressWarnings("unchecked")
    public String getCommitDiff(String repoPath, String sha) {
        String url = "https://gitee.com/api/v5/repos/" + repoPath + "/commits/" + sha;

        HttpHeaders headers = new HttpHeaders();
        if (giteeToken != null && !giteeToken.isBlank()) {
            headers.setBearerAuth(giteeToken);
        }
        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            Map<String, Object> resp = restTemplate.exchange(
                    url, HttpMethod.GET, request, Map.class).getBody();

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
