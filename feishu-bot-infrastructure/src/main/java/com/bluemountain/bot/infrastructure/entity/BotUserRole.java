package com.bluemountain.bot.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("bot_user_role")
public class BotUserRole {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联 bot_user.id */
    private Long userId;

    /** 关联 bot_role.id */
    private Long roleId;

    private LocalDateTime createTime;
}
