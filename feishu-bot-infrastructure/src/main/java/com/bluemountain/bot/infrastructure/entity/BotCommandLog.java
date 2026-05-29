package com.bluemountain.bot.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * bot_command_log 指令执行日志
 */
@Data
@TableName("bot_command_log")
public class BotCommandLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String userId;

    private String command;

    private String rawMessage;

    private String params;

    private Integer status;

    private String errorMsg;

    private String chatId;

    private Integer executeTimeMs;

    private LocalDateTime createTime;
}
