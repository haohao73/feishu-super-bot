package com.bluemountain.bot.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * bot_schedule 日程表
 */
@Data
@TableName("bot_schedule")
public class BotSchedule {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户飞书 open_id（临时方案，后续关联 bot_user 表） */
    private String userId;

    /** 日程标题 */
    private String title;

    /** 日程时间 */
    private LocalDateTime scheduleTime;

    /** 提前提醒分钟数，默认 15 */
    private Integer remindBeforeMin;

    /** 0=未提醒 1=已提醒 */
    private Integer reminded;

    /** 飞书日历事件 ID（后续同步飞书日历时填入） */
    private String feishuCalendarId;

    private LocalDateTime createTime;
}
