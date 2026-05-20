package com.bluemountain.bot.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户飞书 OAuth Token
 */
@Data
@TableName("bot_user_token")
public class BotUserToken {

    /** 飞书 open_id，作为主键 */
    @TableId(type = IdType.INPUT)
    private String openId;

    /** 飞书 user_access_token */
    private String accessToken;

    /** 飞书 refresh_token */
    private String refreshToken;

    /** access_token 过期时间 */
    private LocalDateTime expiresAt;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
