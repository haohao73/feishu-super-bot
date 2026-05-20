package com.bluemountain.bot.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluemountain.bot.infrastructure.entity.BotUserRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface BotUserRoleMapper extends BaseMapper<BotUserRole> {

    /**
     * 判断用户是否有某个角色
     * JOIN bot_role 表按 role_code 查询
     */
    @Select("""
        SELECT COUNT(*) FROM bot_user_role ur
        JOIN bot_role r ON ur.role_id = r.id
        WHERE ur.user_id = #{userId} AND r.role_code = #{roleCode}
    """)
    int countUserRole(Long userId, String roleCode);

    default boolean hasRole(Long userId, String roleCode) {
        // SUPER_ADMIN 拥有所有权限，不需要精确匹配
        if (countUserRole(userId, "SUPER_ADMIN") > 0) return true;
        return countUserRole(userId, roleCode) > 0;
    }
}
