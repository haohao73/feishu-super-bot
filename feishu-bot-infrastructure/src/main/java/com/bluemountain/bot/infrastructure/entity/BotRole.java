package com.bluemountain.bot.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("bot_role")
public class BotRole {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 角色编码：SUPER_ADMIN / ADMIN / USER / READONLY */
    private String roleCode;

    /** 角色显示名：超级管理员 / 管理员 / 普通用户 / 只读用户 */
    private String roleName;

    /** 角色描述 */
    private String description;

    private LocalDateTime createTime;
}
