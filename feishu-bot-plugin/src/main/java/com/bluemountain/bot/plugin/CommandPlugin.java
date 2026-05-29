package com.bluemountain.bot.plugin;

import com.bluemountain.bot.common.dto.CommandContext;

/**
 * 指令插件接口：所有指令（/weather、/schedule 等）实现此接口
 */

/**
 * 对扩展开放,对修改关闭,core写实现层依赖plugin
 */
public interface CommandPlugin {

    /** 指令名，不含斜杠。如 "weather" */
    String name();

    /** 指令说明，/help 时展示 */
    String description();

    /**
     * 执行指令，返回要发给用户的消息内容
     * @param ctx 指令上下文（包含参数、用户、群信息）
     * @return 回复内容（支持飞书 Markdown 格式）
     */
    String execute(CommandContext ctx);
}
