package com.bluemountain.bot.infrastructure.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluemountain.bot.infrastructure.entity.BotKnowledgeDoc;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface BotKnowledgeDocMapper extends com.baomidou.mybatisplus.core.mapper.BaseMapper<BotKnowledgeDoc> {

    /** LIKE 模糊搜索（/search 用） */
    default List<BotKnowledgeDoc> searchByKeyword(String keyword) {
        LambdaQueryWrapper<BotKnowledgeDoc> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BotKnowledgeDoc::getStatus, 1)
               .and(w -> w.like(BotKnowledgeDoc::getTitle, keyword)
                         .or()
                         .like(BotKnowledgeDoc::getContent, keyword))
               .orderByDesc(BotKnowledgeDoc::getCreateTime)
               .last("LIMIT 10");
        return selectList(wrapper);
    }

    /**
     * 智能搜索（/search-ai 用）：
     * 把长句子拆成词，用 OR 连接，每个词单独 LIKE
     * "数据库为什么选MySQL" → title/content 包含 "数据库" OR "MySQL" OR "PostgreSQL"...
     */
    default List<BotKnowledgeDoc> smartSearch(String question) {
        // 提取有意义的词：英文单词、中文连续字符（2字以上）
        java.util.Set<String> words = new java.util.LinkedHashSet<>();
        // 提取英文单词
        java.util.regex.Matcher enMatcher = java.util.regex.Pattern.compile("[a-zA-Z]+").matcher(question);
        while (enMatcher.find()) {
            words.add(enMatcher.group().toLowerCase());
        }
        // 提取中文：拆掉常见的停用词和标点，取单个有意义的词
        String cleaned = question.replaceAll("[，。？?！!的了吗呢是去在]", " ");
        for (String part : cleaned.split("\\s+")) {
            if (part.length() >= 2) {
                words.add(part);
            }
        }

        if (words.isEmpty()) {
            return searchByKeyword(question);
        }

        // 用 OR 连接多个 LIKE 条件
        LambdaQueryWrapper<BotKnowledgeDoc> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BotKnowledgeDoc::getStatus, 1)
               .and(w -> {
                   boolean first = true;
                   for (String word : words) {
                       if (first) {
                           w.like(BotKnowledgeDoc::getTitle, word)
                            .or()
                            .like(BotKnowledgeDoc::getContent, word);
                           first = false;
                       } else {
                           w.or().like(BotKnowledgeDoc::getTitle, word)
                            .or()
                            .like(BotKnowledgeDoc::getContent, word);
                       }
                   }
               })
               .orderByDesc(BotKnowledgeDoc::getCreateTime)
               .last("LIMIT 3");
        return selectList(wrapper);
    }
}
