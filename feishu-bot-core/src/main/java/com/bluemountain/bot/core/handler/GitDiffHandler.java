package com.bluemountain.bot.core.handler;

import com.bluemountain.bot.common.dto.CommandContext;
import com.bluemountain.bot.integration.client.GiteeClient;
import com.bluemountain.bot.plugin.CommandPlugin;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * /gitdiff <仓库路径> <commit-hash> — 查看某次提交的代码差异
 * 示例：/gitdiff haohao/feishu-bot abc123
 */
@Slf4j
@Component
public class GitDiffHandler implements CommandPlugin {

    private final GiteeClient giteeClient;

    public GitDiffHandler(GiteeClient giteeClient) {
        this.giteeClient = giteeClient;
    }

    @Override
    public String name() { return "gitdiff"; }

    @Override
    public String description() {
        return "查看提交差异 — 用法：/gitdiff 仓库路径 sha";
    }

    @Override
    public String execute(CommandContext ctx) {
        String args = ctx.getArgs();
        if (args == null || args.isBlank()) {
            return "请输入仓库路径和 commit SHA\n用法：`/gitdiff haohao/feishu-bot abc123def`";
        }

        String[] parts = args.trim().split("\\s+", 2);
        if (parts.length < 2) {
            return "需要两个参数：仓库路径 和 commit SHA\n示例：`/gitdiff haohao/feishu-bot abc123def`";
        }

        String repoPath = parts[0];
        String sha = parts[1];

        String diff = giteeClient.getCommitDiff(repoPath, sha);
        if (diff == null || diff.isBlank()) {
            return "未找到该 commit 的差异\n请确认仓库路径和 SHA 正确";
        }

        StringBuilder sb = new StringBuilder("**📝 代码差异** — `").append(sha).append("`\n\n");
        // 截断过长的 diff（飞书消息有长度限制）
        if (diff.length() > 3000) {
            diff = diff.substring(0, 3000) + "\n\n...（diff 过长已截断，完整内容请查看仓库）";
        }
        sb.append("```diff\n").append(diff).append("\n```");
        return sb.toString();
    }
}
