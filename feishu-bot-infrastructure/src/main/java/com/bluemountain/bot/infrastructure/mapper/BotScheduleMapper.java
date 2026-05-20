package com.bluemountain.bot.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluemountain.bot.infrastructure.entity.BotSchedule;
import org.apache.ibatis.annotations.Mapper;

/**
 * bot_schedule 表 Mapper — 继承 BaseMapper 自带 CRUD
 */
@Mapper
public interface BotScheduleMapper extends BaseMapper<BotSchedule> {
}
