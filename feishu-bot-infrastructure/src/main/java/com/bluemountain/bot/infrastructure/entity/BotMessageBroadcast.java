package com.bluemountain.bot.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("bot_message_broadcast")
public class BotMessageBroadcast {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 发送者 bot_user.id */
    private Long senderId;

    /** 广播标题 */
    private String title;

    /** 广播内容 */
    private String content;

    /** 1=全员 2=指定群 3=指定部门 4=指定用户 */
    private Integer targetType;

    /** 目标ID列表，JSON 数组字符串 如 '["oc_xxx","oc_yyy"]' */
    private String targetIds;

    /** 1=待发送 2=发送中 3=已完成 4=部分失败 */
    private Integer status;

    private Integer successCount;
    private Integer failCount;
    private LocalDateTime createTime;
    private LocalDateTime completeTime;
}
