package com.bluemountain.bot.core.handler;

import com.bluemountain.bot.common.dto.CommandContext;
import com.bluemountain.bot.infrastructure.entity.BotKnowledgeDoc;
import com.bluemountain.bot.infrastructure.mapper.BotKnowledgeDocMapper;
import com.bluemountain.bot.plugin.CommandPlugin;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * /search <关键词> — 搜索知识库文档
 */
@Component
@RequiredArgsConstructor
public class SearchHandler implements CommandPlugin {

    private final BotKnowledgeDocMapper docMapper;

    @Override
    public String name() {
        return "search";
    }

    @Override
    public String description() {
        return "搜索知识库文档 — 用法：/search 部署";
    }

    @Override
    public String execute(CommandContext ctx) {
        String keyword = ctx.getArgs();
        if (keyword == null || keyword.isBlank()) {
            return "请输入搜索关键词\n用法：`/search 部署`";
        }

        List<BotKnowledgeDoc> docs = docMapper.searchByKeyword(keyword.trim());

        if (docs.isEmpty()) {
            return "未找到与「" + keyword + "」相关的文档";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("**🔍 找到 ").append(docs.size()).append(" 篇相关文档**\n\n");

        for (BotKnowledgeDoc doc : docs) {
            sb.append("📄 **").append(doc.getTitle()).append("**\n");
            if (doc.getSummary() != null && !doc.getSummary().isBlank()) {
                sb.append("> ").append(doc.getSummary()).append("\n");
            }
            if (doc.getTags() != null && !doc.getTags().isBlank()) {
                sb.append("标签：").append(doc.getTags()).append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }
    /**
     * 没什么好说的,拼接所有符合条件的返回给上层就行
     */
}
