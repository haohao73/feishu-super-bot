package com.bluemountain.bot.core.handler;

import com.bluemountain.bot.common.dto.CommandContext;
import com.bluemountain.bot.integration.client.FeishuClient;
import com.bluemountain.bot.plugin.CommandPlugin;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * /group <群名> — 创建飞书群聊
 */
@Slf4j
@Component
public class GroupHandler implements CommandPlugin {

    private final FeishuClient feishuClient;

    public GroupHandler(FeishuClient feishuClient) {
        this.feishuClient = feishuClient;
    }

    @Override
    public String name() {
        return "group";
    }

    @Override
    public String description() {
        return "创建群聊 — 用法：/group 群名称";
    }

    @Override
    public String execute(CommandContext ctx) {
        String groupName = ctx.getArgs();
        if (groupName == null || groupName.isBlank()) {
            return "请输入群名称\n用法：`/group 项目讨论组`";
        }

        String chatId = feishuClient.createChat(groupName.trim());
        if (chatId == null) {
            return "群聊创建失败，请稍后重试";
        }

        // 把发指令的人拉进群
        feishuClient.addMemberToChat(chatId, ctx.getUserId());

        return String.format(
                "**群聊已创建**\n\n群名：%s\n\n你已被加入该群，在飞书消息列表查看",
                groupName.trim()
        );
    }
    /**
     * 依旧解析参数,调用api,拿到结果返回给上层
     */
}
