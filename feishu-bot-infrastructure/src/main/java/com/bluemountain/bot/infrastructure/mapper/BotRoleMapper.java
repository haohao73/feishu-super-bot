package com.bluemountain.bot.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluemountain.bot.infrastructure.entity.BotRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface BotRoleMapper extends BaseMapper<BotRole> {

    /** 按 role_code 查角色 */
    @Select("SELECT * FROM bot_role WHERE role_code = #{code}")
    BotRole selectByCode(String code);
}
