package com.bluemountain.bot.core.handler;

import com.bluemountain.bot.common.dto.CommandContext;
import com.bluemountain.bot.integration.client.FeishuClient;
import com.bluemountain.bot.plugin.CommandPlugin;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * /group <群名> [open_id ...] — 创建飞书群聊，可选指定成员
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
        return "创建群聊 — 用法：/group 群名称 [成员open_id ...]";
    }

    @Override
    public String execute(CommandContext ctx) {
        String args = ctx.getArgs();
        if (args == null || args.isBlank()) {
            return "请输入群名称\n用法：`/group 项目讨论组`\n指定成员：`/group 项目讨论组 ou_xxx ou_yyy`";
        }

        // 解析参数：第一个空格前是群名，后面的是成员 open_id
        String[] parts = args.trim().split("\\s+");
        String groupName = parts[0];

        List<String> memberOpenIds = new ArrayList<>();
        memberOpenIds.add(ctx.getUserId()); // 发指令的人必进群

        // 收集指定的额外成员（支持 open_id 或姓名）
        for (int i = 1; i < parts.length; i++) {
            String input = parts[i].trim();
            if (input.isBlank()) continue;

            if (input.startsWith("ou_")) {
                // 直接是 open_id
                if (!memberOpenIds.contains(input)) {
                    memberOpenIds.add(input);
                }
            } else {
                // 按姓名查找
                List<String> found = feishuClient.findOpenIdsByName(input);
                if (found.isEmpty()) {
                    log.warn("未找到用户 | name={}", input);
                }
                for (String id : found) {
                    if (!memberOpenIds.contains(id)) {
                        memberOpenIds.add(id);
                    }
                }
            }
        }

        String chatId = feishuClient.createChat(groupName.trim());
        if (chatId == null) {
            return "群聊创建失败，请稍后重试";
        }

        // 批量添加成员
        if (memberOpenIds.size() == 1) {
            feishuClient.addMemberToChat(chatId, memberOpenIds.get(0));
        } else {
            feishuClient.addMembersToChat(chatId, memberOpenIds);
        }

        return String.format(
                "**群聊已创建**\n\n群名：%s\n成员数：%d\n\n在飞书消息列表查看",
                groupName.trim(), memberOpenIds.size()
        );
    }
}
