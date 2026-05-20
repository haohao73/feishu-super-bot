package com.bluemountain.bot.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("bot_approval_reminder")
public class BotApprovalReminder {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 飞书审批实例 ID */
    private String approvalId;

    /** 审批人 bot_user.id */
    private Long approverId;

    /** 申请人姓名 */
    private String applicantName;

    /** 审批标题 */
    private String title;

    /** 1=待审批 2=已通过 3=已拒绝 */
    private Integer status;

    /** 上次提醒时间 */
    private LocalDateTime lastRemindTime;

    /** 已提醒次数 */
    private Integer remindCount;

    private LocalDateTime createTime;
}
