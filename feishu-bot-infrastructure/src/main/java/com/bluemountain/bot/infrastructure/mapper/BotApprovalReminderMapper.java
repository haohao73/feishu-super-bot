package com.bluemountain.bot.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluemountain.bot.infrastructure.entity.BotApprovalReminder;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BotApprovalReminderMapper extends BaseMapper<BotApprovalReminder> {
}
