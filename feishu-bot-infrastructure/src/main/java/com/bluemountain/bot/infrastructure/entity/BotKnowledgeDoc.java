package com.bluemountain.bot.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * bot_knowledge_doc 知识库文档表
 */
@Data
@TableName("bot_knowledge_doc")
public class BotKnowledgeDoc {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String content;

    private String summary;

    private String category;

    private String sourceUrl;

    private String tags;

    private Integer viewCount;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
