package com.bluemountain.bot.core.router;

import com.bluemountain.bot.common.dto.CommandContext;
import com.bluemountain.bot.plugin.CommandPlugin;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 指令路由器：根据指令名找到对应的 Handler 并执行
 *
 * 工作方式：
 * 1. 所有 CommandPlugin 实现类自动注入
 * 2. 构建 Map<指令名, 处理器> 的索引
 * 3. /weather 北京 → 找到 WeatherHandler → 执行
 */
@Slf4j
@Component
public class CommandRouter {

    private final Map<String, CommandPlugin> pluginMap;

    /**
     * Spring 自动注入所有 CommandPlugin Bean
     */
    public CommandRouter(List<CommandPlugin> plugins) {
        this.pluginMap = plugins.stream()
                .collect(Collectors.toMap(CommandPlugin::name, p -> p));
        log.info("已注册 {} 个指令：{}", pluginMap.size(), pluginMap.keySet());
    }

    /**
     * 路由指令并执行
     * @param ctx 指令上下文
     * @return 回复消息内容
     */
    public String route(CommandContext ctx) {
        if (ctx == null || ctx.getCommand() == null) {
            return null;
        }

        String cmd = ctx.getCommand().toLowerCase();
        CommandPlugin plugin = pluginMap.get(cmd);

        if (plugin == null) {
            return "未知指令 `/"+ctx.getCommand()+"`\n输入 `/help` 查看可用指令";
        }

        log.info("执行指令 | cmd={} args={} user={}", cmd, ctx.getArgs(), ctx.getUserId());
        try {
            return plugin.execute(ctx);
        } catch (Exception e) {
            log.error("指令执行异常 | cmd={}", cmd, e);
            return "指令执行失败，请稍后重试";
        }
    }

    /**
     * 获取所有已注册的指令列表（/help 用）
     */
    public List<CommandPlugin> getAllPlugins() {
        return List.copyOf(pluginMap.values());
    }
}
