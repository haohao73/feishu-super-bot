package com.bluemountain.bot.core.handler;

import com.bluemountain.bot.common.dto.CommandContext;
import com.bluemountain.bot.integration.client.GiteeClient;
import com.bluemountain.bot.plugin.CommandPlugin;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * /gitlog <仓库路径> — 查看最近提交日志
 * 示例：/gitlog haohao/feishu-bot
 */
@Slf4j
@Component
public class GitLogHandler implements CommandPlugin {

    private final GiteeClient giteeClient;

    public GitLogHandler(GiteeClient giteeClient) {
        this.giteeClient = giteeClient;
    }

    @Override
    public String name() { return "gitlog"; }

    @Override
    public String description() {
        return "查看仓库最近提交日志 — 用法：/gitlog 仓库路径";
    }

    @Override
    public String execute(CommandContext ctx) {
        String repoPath = ctx.getArgs();
        if (repoPath == null || repoPath.isBlank()) {
            return "请输入仓库路径\n用法：`/gitlog haohao/feishu-bot`";
        }

        List<Map<String, Object>> commits = giteeClient.getCommits(repoPath.trim());
        if (commits.isEmpty()) {
            return "未找到提交记录，请确认仓库路径正确\n示例：`/gitlog haohao/feishu-bot`";
        }

        StringBuilder sb = new StringBuilder("**📋 最近提交** — `" + repoPath.trim() + "`\n\n");
        for (int i = 0; i < commits.size(); i++) {
            Map<String, Object> c = commits.get(i);
            String sha = ((String) c.get("sha")).substring(0, 7);
            Map<String, Object> commit = (Map<String, Object>) c.get("commit");
            Map<String, Object> author = (Map<String, Object>) commit.get("author");
            String message = (String) commit.get("message");
            // message 可能含多行，只取第一行
            if (message != null && message.contains("\n")) {
                message = message.substring(0, message.indexOf("\n"));
            }

            sb.append(i + 1).append(". `").append(sha).append("` ");
            sb.append(message != null ? message : "");
            sb.append(" — *").append((String) author.get("name")).append("*\n");
        }

        sb.append("\n输入 `/gitdiff <sha>` 查看某次提交的差异");
        return sb.toString();
    }
}
