package com.bluemountain.bot.core.handler;

import com.bluemountain.bot.common.dto.CommandContext;
import com.bluemountain.bot.infrastructure.entity.BotKnowledgeDoc;
import com.bluemountain.bot.infrastructure.mapper.BotKnowledgeDocMapper;
import com.bluemountain.bot.integration.client.AiClient;
import com.bluemountain.bot.plugin.CommandPlugin;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * /search-ai <问题> — AI 阅读知识库文档并回答问题
 */
@Slf4j
@Component
public class SearchAiHandler implements CommandPlugin {

    private final BotKnowledgeDocMapper docMapper;
    private final AiClient aiClient;

    public SearchAiHandler(BotKnowledgeDocMapper docMapper, AiClient aiClient) {
        this.docMapper = docMapper;
        this.aiClient = aiClient;
    }

    @Override
    public String name() {
        return "search-ai";
    }

    @Override
    public String description() {
        return "AI 智能问答 — 用法：/search-ai 数据库为什么选MySQL";
    }

    @Override
    public String execute(CommandContext ctx) {
        String question = ctx.getArgs();
        if (question == null || question.isBlank()) {
            return "请提出你想问的问题\n用法：`/search-ai 数据库为什么选MySQL不选PostgreSQL`";
        }

        // ① 智能拆词搜索：把长句子拆成多个关键词，逐个 LIKE 匹配
        List<BotKnowledgeDoc> docs = docMapper.smartSearch(question.trim());
        if (docs.isEmpty()) {
            return "知识库中暂无与「" + question + "」相关的文档，无法回答。";
        }

        // ② 拼接文档内容
        StringBuilder docContext = new StringBuilder();
        for (int i = 0; i < docs.size(); i++) {
            BotKnowledgeDoc doc = docs.get(i);
            docContext.append("【文档").append(i + 1).append("：").append(doc.getTitle()).append("】\n");
            // 只取前 1500 字，避免超过 AI token 限制
            String content = doc.getContent();
            if (content != null && content.length() > 1500) {
                content = content.substring(0, 1500) + "...（内容过长已截断）";
            }
            docContext.append(content).append("\n\n");
        }

        // ③ 拼系统提示词
        String systemPrompt = "你是飞书超级助手的知识库AI。请根据提供的文档内容回答用户问题。" +
                "如果文档中没有相关信息，请如实说[知识库中暂无相关信息]。回答要简洁、准确。";

        // ④ 拼用户消息
        String userMessage = docContext + "\n【用户问题】\n" + question;

        // ⑤ 调 AI
        String answer = aiClient.chat(systemPrompt, userMessage);
        if (answer == null) {
            return "AI 服务暂时不可用，请稍后重试";
        }

        // ⑥ 拼回复
        StringBuilder reply = new StringBuilder("🤖 **AI 回答**\n\n");
        reply.append(answer);
        reply.append("\n\n---\n📎 **参考文档**：\n");
        for (BotKnowledgeDoc doc : docs) {
            reply.append("- ").append(doc.getTitle());
            if (doc.getSourceUrl() != null && !doc.getSourceUrl().isBlank()) {
                reply.append(" [查看原文](").append(doc.getSourceUrl()).append(")");
            }
            reply.append("\n");
        }

        return reply.toString();
    }
}
