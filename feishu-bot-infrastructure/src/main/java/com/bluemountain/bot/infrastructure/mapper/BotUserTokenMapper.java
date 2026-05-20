package com.bluemountain.bot.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluemountain.bot.infrastructure.entity.BotUserToken;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BotUserTokenMapper extends BaseMapper<BotUserToken> {
}
