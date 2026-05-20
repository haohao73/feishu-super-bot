package com.bluemountain.bot.infrastructure.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluemountain.bot.infrastructure.entity.BotUser;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BotUserMapper extends BaseMapper<BotUser> {

    /** 根据飞书 open_id 查用户 */
    default BotUser selectByOpenId(String openId) {
        return selectOne(new LambdaQueryWrapper<BotUser>()
                .eq(BotUser::getFeishuOpenId, openId));
    }
}
