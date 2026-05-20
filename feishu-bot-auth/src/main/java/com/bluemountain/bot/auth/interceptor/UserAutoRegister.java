package com.bluemountain.bot.auth.interceptor;

import com.bluemountain.bot.infrastructure.entity.BotUser;
import com.bluemountain.bot.infrastructure.entity.BotUserRole;
import com.bluemountain.bot.infrastructure.mapper.BotUserMapper;
import com.bluemountain.bot.infrastructure.mapper.BotUserRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户自动注册器：飞书用户第一次发消息时自动创建 bot_user 记录并分配默认角色。
 *
 * 这和苍穹外卖的"用户注册"本质一样，区别是：
 * - 苍穹外卖：用户主动填表单 → /register 接口 → insert
 * - 飞书机器人：用户发消息 → 自动触发 → ensureUser() → insert
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserAutoRegister {

    private final BotUserMapper userMapper;
    private final BotUserRoleMapper userRoleMapper;

    /**
     * 如果用户不存在 → 自动注册 + 分配默认 USER 角色。
     * 如果已存在 → 直接返回。
     *
     * @param openId 飞书用户 open_id
     * @param name   用户姓名（可选，后续可从飞书通讯录 API 获取）
     * @return 用户记录（新创建或已存在）
     */
    @Transactional
    public BotUser ensureUser(String openId, String name) {
        // == 第1步：查是否存在 ==
        BotUser user = userMapper.selectByOpenId(openId);
        if (user != null) {
            log.debug("用户已存在 | openId={} userId={} name={}",
                    openId, user.getId(), user.getName());
            return user;
        }

        // == 第2步：创建 bot_user 记录 ==
        user = new BotUser();
        user.setFeishuOpenId(openId);
        user.setName(name != null ? name : "");
        user.setStatus(1);
        userMapper.insert(user);

        log.info("【新用户注册】bot_user 写入成功 | openId={} userId={} name={}",
                openId, user.getId(), user.getName());

        // == 第3步：分配默认 USER 角色 ==
        // 预置角色：SUPER_ADMIN=1, ADMIN=2, USER=3, READONLY=4
        BotUserRole userRole = new BotUserRole();
        userRole.setUserId(user.getId());
        userRole.setRoleId(3L);  // 3 = USER 角色
        userRoleMapper.insert(userRole);

        log.info("【新用户注册】默认角色分配成功 | userId={} role=USER roleId=3", user.getId());

        return user;
    }
}
