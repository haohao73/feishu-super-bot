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
 * 2. !!!!!!!!构建 Map<指令名, 处理器> 的索引
 * 3. /weather 北京 → 找到 WeatherHandler → 执行
 */
@Slf4j
@Component
public class CommandRouter {
//实际存进去的是 WeatherHandler、TranslateHandler 这些具体类
    /**
     *
     *
     pluginMap.get("weather")   →  拿到 WeatherHandler（value）
     ↓
     .execute(ctx)  调用它的方法

     能做到这一点的前提就是—WeatherHandler、TranslateHandler、GroupHandler……全部无一例外实现了 CommandPlugin
     接口。所以不管你拿到哪个 Handler，你都知道它一定有 execute()、name()、description()
     */
    private final Map<String, CommandPlugin> pluginMap;

    /**
     * Spring 自动注入所有 CommandPlugin Bean,/help功能
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
        //统一转成小写查找指令
        String cmd = ctx.getCommand().toLowerCase();
        CommandPlugin plugin = pluginMap.get(cmd);

        if (plugin == null) {
            return "未知指令 `/"+ctx.getCommand()+"`\n输入 `/help` 查看可用指令";
        }

        log.info("执行指令 | cmd={} args={} user={}", cmd, ctx.getArgs(), ctx.getUserId());
        try {
            /**
             * luginMap.get(cmd)   // 从 Map 拿出对应的 Handler
             *            .execute(ctx)  // 调这个 Handler 的 execute() 方法
             *
             *   cmd = "weather" 时，pluginMap.get("weather") 拿到的是 WeatherHandler 对象。然后 .execute(ctx) 就是执行你写的
             *   WeatherHandler.execute() 方法——拿城市名、调 Open-Meteo API、拼 Markdown 回复。
             */
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
