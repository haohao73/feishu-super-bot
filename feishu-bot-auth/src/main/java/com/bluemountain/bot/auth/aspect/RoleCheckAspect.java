package com.bluemountain.bot.auth.aspect;

import com.bluemountain.bot.auth.annotation.RequireRole;
import com.bluemountain.bot.common.dto.CommandContext;
import com.bluemountain.bot.infrastructure.entity.BotUser;
import com.bluemountain.bot.infrastructure.mapper.BotUserMapper;
import com.bluemountain.bot.infrastructure.mapper.BotUserRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * 权限校验切面 — 在 CommandPlugin.execute() 执行前拦截校验
 *
 拦截所有实现了 CommandPlugin 接口的类的 execute 方法，
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RoleCheckAspect {

    private final BotUserMapper userMapper;
    private final BotUserRoleMapper userRoleMapper;

    /**
     * 拦截所有 CommandPlugin.execute() 方法
     */
    @Around("execution(* com.bluemountain.bot.plugin.CommandPlugin.execute(..))")
    public Object checkRole(ProceedingJoinPoint joinPoint) throws Throwable {

        // == 第1步：从方法参数里拿到 CommandContext ==
        String userId = null;
        String command = null;
        for (Object arg : joinPoint.getArgs()) {
            /**
             * 被拦截方法的所有参数,其实就CommandContext一个
             */
            if (arg instanceof CommandContext ctx) {
                userId = ctx.getUserId();
                command = ctx.getCommand();
                break;
            }
        }

        if (userId == null) {
            log.warn("权限切面：未获取到 userId，放行");
            return joinPoint.proceed();
        }

        // == 第2步：读 Handler 类上的 @RequireRole ==
        Class<?> handlerClass = joinPoint.getTarget().getClass();
        RequireRole annotation = handlerClass.getAnnotation(RequireRole.class);

        if (annotation == null) {
            // 没有注解 → 任何人可用
            return joinPoint.proceed();
        }

        String requiredRole = annotation.value();
        log.info("权限检查 | cmd={} userId={} needRole={}", command, userId, requiredRole);

        // == 第3步：查用户 ==
        //需要权限访问的指令条件下
        BotUser user = userMapper.selectByOpenId(userId);
        if (user == null) {
            log.warn("权限拒绝：用户未注册 | openId={}", userId);
            return "⚠️ 用户未注册，请先给机器人发一条消息自动注册";
        }

        if (user.getStatus() == 0) {
            log.warn("权限拒绝：用户已禁用 | userId={} name={}", user.getId(), user.getName());
            return "⚠️ 您的账号已被禁用，请联系管理员";
        }

        // == 第4步：查角色 ==
        boolean hasRole = userRoleMapper.hasRole(user.getId(), requiredRole);
        if (!hasRole) {
            log.warn("权限拒绝 | userId={} name={} needRole={}",
                    user.getId(), user.getName(), requiredRole);
            return String.format(
                "⚠️ **权限不足**\n\n指令 `/%s` 需要「%s」角色\n你的账号暂无此权限，请联系管理员",
                command, requiredRole
            );
        }

        // == 第5步：放行 ==
        log.info("权限通过 | userId={} name={} role={} cmd={}",
                user.getId(), user.getName(), requiredRole, command);
        return joinPoint.proceed();
    }
}
