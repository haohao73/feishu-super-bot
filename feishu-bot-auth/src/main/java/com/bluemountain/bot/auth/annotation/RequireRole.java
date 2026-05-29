package com.bluemountain.bot.auth.annotation;

import java.lang.annotation.*;

/**
 * 权限注解：标在 CommandPlugin 实现类上，声明该指令需要什么角色。
 *
 * 不标 = 所有已登录用户都能用（如 /weather、/translate）
 *
 * 用法：
 *   @RequireRole("ADMIN")       需要 ADMIN 或更高角色
 *   @RequireRole("SUPER_ADMIN") 需要 SUPER_ADMIN
 *
 * AOP 切面 RoleCheckAspect 会在 execute() 执行前自动校验。
 */

/**

 *定义注解里唯一的一个属性，类型是 String。使用时：
 *
 *   @RequireRole("ADMIN")        // 等价于 @RequireRole(value = "ADMIN")
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireRole {

    /** 角色编码：SUPER_ADMIN / ADMIN / USER / READONLY */
    String value();
}
