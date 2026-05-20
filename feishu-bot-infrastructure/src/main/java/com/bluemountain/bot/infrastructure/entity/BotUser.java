package com.bluemountain.bot.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("bot_user")
public class BotUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 飞书用户 open_id（唯一索引） */
    private String feishuOpenId;

    /** 飞书统一 ID（跨应用） */
    private String feishuUnionId;

    /** 用户姓名 */
    private String name;

    /** 头像 URL */
    private String avatarUrl;

    /** 部门名称 */
    private String department;

    /** 1=正常 0=禁用 */
    private Integer status;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
