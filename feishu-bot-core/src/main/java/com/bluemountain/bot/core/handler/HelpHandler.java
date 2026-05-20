package com.bluemountain.bot.core.handler;

import com.bluemountain.bot.common.dto.CommandContext;
import com.bluemountain.bot.plugin.CommandPlugin;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * /help — 显示所有可用指令
 */
@Component
public class HelpHandler implements CommandPlugin {

    private final List<CommandPlugin> allPlugins;

    /**
     * 直接注入所有 CommandPlugin，不经过 CommandRouter
     * （避免 HelpHandler → CommandRouter → List<CommandPlugin> 循环依赖）
     */
    public HelpHandler(List<CommandPlugin> allPlugins) {
        this.allPlugins = allPlugins;
    }

    @Override
    public String name() {
        return "help";
    }

    @Override
    public String description() {
        return "显示可用指令列表";
    }

    @Override
    public String execute(CommandContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("**🤖 飞书超级助手 — 可用指令**\n\n");

        for (CommandPlugin plugin : allPlugins) {
            sb.append("> **`/").append(plugin.name()).append("`**\n");
            sb.append("> ").append(plugin.description()).append("\n\n");
        }

        sb.append("---\n");
        sb.append("输入 `/help` 随时查看此菜单");

        return sb.toString();
    }
}
