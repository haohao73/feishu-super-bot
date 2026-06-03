package com.bluemountain.bot.core.handler;

import com.bluemountain.bot.common.dto.CommandContext;
import com.bluemountain.bot.integration.client.GiteeClient;
import com.bluemountain.bot.plugin.CommandPlugin;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * /mergestatus <仓库路径> <PR编号> — 查看 Pull Request 状态
 * 示例：/mergestatus haohao73/feishu_bot 1
 */
@Component
public class MergeStatusHandler implements CommandPlugin {

    private final GiteeClient giteeClient;

    public MergeStatusHandler(GiteeClient giteeClient) {
        this.giteeClient = giteeClient;
    }

    @Override
    public String name() {
        return "mergestatus";
    }

    @Override
    public String description() {
        return "查看 PR 状态 — 用法：/mergestatus 仓库路径 PR编号";
    }

    @Override
    public String execute(CommandContext ctx) {
        String args = ctx.getArgs();
        if (args == null || args.isBlank()) {
            return "请指定仓库路径和 PR 编号\n用法：`/mergestatus haohao73/feishu_bot 1`";
        }

        String[] parts = args.trim().split("\\s+");
        if (parts.length < 2) {
            return "参数不足\n用法：`/mergestatus haohao73/feishu_bot 1`";
        }

        String repoPath = parts[0];
        int prNumber;
        try {
            prNumber = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return "PR 编号必须是数字，你输入的是：「" + parts[1] + "」";
        }

        Map<String, Object> pr = giteeClient.getPullRequest(repoPath, prNumber);
        if (pr == null) {
            return "未找到 PR #" + prNumber + "\n请确认仓库路径和编号正确\n示例：`" + repoPath + "`";
        }

        // 翻译状态
        String state = (String) pr.getOrDefault("state", "unknown");
        String stateText = switch (state) {
            case "open" -> "🟡 待合并";
            case "merged" -> "🟢 已合并";
            case "closed" -> "🔴 已关闭";
            default -> "❓ " + state;
        };

        String mergeable = "❓ 未知";
        if (pr.get("mergeable") instanceof Boolean m) {
            mergeable = m ? "✅ 无冲突，可合并" : "⚠️ 存在冲突，需解决";
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) pr.get("user");
        String author = user != null ? (String) user.getOrDefault("login", "未知") : "未知";

        String headLabel = (String) pr.getOrDefault("head", Map.of()).toString();
        String baseLabel = (String) pr.getOrDefault("base", Map.of()).toString();

        return String.format("""
                **📋 PR #%d** — `%s`

                标题：%s
                状态：%s
                合并状态：%s
                作者：%s
                分支：`%s`
                创建时间：%s

                [查看详情](%s)""",
                prNumber, repoPath,
                pr.getOrDefault("title", "未知"),
                stateText,
                mergeable,
                author,
                headLabel,
                pr.getOrDefault("created_at", "未知"),
                pr.getOrDefault("html_url", "")
        );
    }
}
