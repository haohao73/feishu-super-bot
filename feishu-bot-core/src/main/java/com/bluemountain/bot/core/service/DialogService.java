package com.bluemountain.bot.core.service;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.bluemountain.bot.common.dto.CommandContext;
import com.bluemountain.bot.common.dto.DialogContext;
import com.bluemountain.bot.core.router.CommandRouter;
import com.bluemountain.bot.integration.client.AiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;

/**
 * 多轮对话服务 —— 让机器人有"短期记忆"
 *
 * ============================================================
 * 三大职责：
 * ============================================================
 * 1. 上下文存取 —— Redis 读写对话上下文（get / save / clear）
 * 2. 意图解析 —— 非 / 开头消息发给 AI，解析用户想干什么
 * 3. 指令执行 —— AI 返回意图后，构造 CommandContext 走正常路由
 *
 * ============================================================
 * 两个入口：
 * ============================================================
 * saveContext()  → 斜杠指令执行成功后调用（记下这轮对话）
 * handleContinuation() → 非 / 开头消息进来时调用（尝试延续对话）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DialogService {

    private final StringRedisTemplate redisTemplate;
    private final CommandRouter commandRouter;
    private final AiClient aiClient;

    // ================================================================
    // 第一部分：上下文存取（Redis 读写）
    // ================================================================

    /**
     * 从 Redis 读取对话上下文
     *
     * 对应 SQL 思维：SELECT value FROM Redis WHERE key = "dialog:{openId}"
     * 没取到 → 返回 null（这人没在对话中 / 上下文已过期）
     */
    public DialogContext getContext(String openId) {
        String json = redisTemplate.opsForValue().get(DialogContext.redisKey(openId));
        if (json == null) return null;
        return JSONUtil.toBean(json, DialogContext.class);
    }

    /**
     * 保存对话上下文到 Redis
     *
     * 什么时候调：斜杠指令或上下文延续指令执行成功后
     * 存什么：
     *   - 上一轮指令名 + 参数（下次延续对话时补全用）
     *   - 对话历史（发给 AI 理解意图用，包含 user 说了什么 + assistant 回复了什么）
     * 存多久：10 分钟（超过没新消息自动过期）
     */
    public void saveContext(String openId, String command,
                            Map<String, String> args,
                            String userMessage, String assistantReply) {
        // ① 取出旧上下文（如果是新话题则为空）
        DialogContext ctx = getContext(openId);
        if (ctx == null) {
            ctx = new DialogContext();
            ctx.setOpenId(openId);
            ctx.setTurnCount(0);
            ctx.setHistory(new ArrayList<>());
        }

        // ② 更新核心字段
        ctx.setLastCommand(command);
        ctx.setLastArgs(args);
        ctx.setTurnCount(ctx.getTurnCount() + 1);

        // ③ 如果轮数超上限，清历史重来（防止给 AI 的历史太长）
        if (ctx.getTurnCount() > DialogContext.MAX_TURNS) {
            ctx.setTurnCount(1);
            ctx.setHistory(new ArrayList<>());
            log.debug("上下文轮数超限，重置 | openId={}", openId);
        }

        // ④ 追加本轮对话到历史（给 AI 看的）
        DialogContext.Turn userTurn = new DialogContext.Turn();
        userTurn.setRole("user");
        userTurn.setText(userMessage);
        ctx.getHistory().add(userTurn);

        DialogContext.Turn assistantTurn = new DialogContext.Turn();
        assistantTurn.setRole("assistant");
        assistantTurn.setText(assistantReply);
        ctx.getHistory().add(assistantTurn);

        ctx.setLastActiveTime(System.currentTimeMillis());

        // ⑤ 写入 Redis
        redisTemplate.opsForValue().set(
                DialogContext.redisKey(openId),
                JSONUtil.toJsonStr(ctx),
                Duration.ofSeconds(DialogContext.TTL_SECONDS));

        log.info("上下文已保存 | openId={} cmd={} turn={}",
                openId, command, ctx.getTurnCount());
    }

    /**
     * 清空上下文（用户手动清 / 新斜杠指令覆盖时可选调）
     */
    public void clearContext(String openId) {
        redisTemplate.delete(DialogContext.redisKey(openId));
        log.info("上下文已清空 | openId={}", openId);
    }

    // ================================================================
    // 第二部分：上下文延续处理（非 / 开头消息的入口）
    // ================================================================

    /**
     * 处理上下文延续消息（非 / 开头）
     *
     * 完整链路：
     * 1. 从 Redis 取上下文 → 没有就返回 null（不回复）
     * 2. 拼 Prompt 发给 AI → 拿到意图 JSON
     * 3. 补全参数（AI 没填的用 Redis 上下文补）
     * 4. 构造 CommandContext → 走正常 CommandRouter 路由
     * 5. 回复用户 + 更新 Redis 上下文
     *
     * @return 回复内容，null 表示不回复（没上下文 / AI 没理解）
     */
    public String handleContinuation(String openId, String chatId, String messageText) {
        // ---- 第①层：读上下文 ----
        DialogContext ctx = getContext(openId);
        if (ctx == null) {
            log.debug("无上下文，忽略非指令消息 | openId={} text={}", openId, messageText);
            return null;
        }

        // ---- 第②层：拼 Prompt → 调 AI ----
        String prompt = buildPrompt(ctx, messageText);
        String aiResponse = aiClient.chat("你是一个指令解析器，只输出 JSON。", prompt);
        log.info("AI 意图解析 | openId={} input=[{}] output=[{}]",
                openId, messageText, aiResponse);

        // ---- 第③层：解析 AI 返回的 JSON ----
        Intent intent = parseIntent(aiResponse);
        if (intent == null) {
            log.info("AI 未识别意图 | openId={} text={}", openId, messageText);
            return null;
        }

        // ---- 第④层：补全参数 ----
        // 规则：只有指令相同时才合并旧参数（同一话题延续）
        //       指令不同 → 只用 AI 返回的新参数（话题切换了）
        Map<String, String> args = intent.getArgs();
        if (args == null) args = new HashMap<>();
        if (ctx.getLastCommand() != null && ctx.getLastCommand().equals(intent.getCommand())
                && ctx.getLastArgs() != null) {
            for (Map.Entry<String, String> entry : ctx.getLastArgs().entrySet()) {
                args.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }
        intent.setArgs(args);

        log.info("补全参数 | openId={} cmd={} args={}", openId, intent.getCommand(), args);

        // ---- 第⑤层：构造 CommandContext → 走正常路由 ----
        CommandContext cmdCtx = new CommandContext();
        cmdCtx.setCommand(intent.getCommand());
        cmdCtx.setUserId(openId);
        cmdCtx.setChatId(chatId);
        cmdCtx.setContextArgs(args);       // 结构化参数透传
        cmdCtx.setArgs(toArgsString(args)); // 兼容只用 getArgs() 的 Handler
        cmdCtx.setArgArray(args.values().toArray(new String[0]));

        try {
            String reply = commandRouter.route(cmdCtx);

            // ---- 第⑥层：回复后更新上下文 ----
            if (reply != null) {
                saveContext(openId, intent.getCommand(), args, messageText, reply);
            }

            return reply;
        } catch (Exception e) {
            log.error("上下文指令执行失败 | openId={} cmd={}", openId, intent.getCommand(), e);
            return "抱歉，处理请求时出错了，请稍后重试";
        }
    }

    // ================================================================
    // 第三部分：AI Prompt 构建（把对话历史 + 当前消息拼成 prompt）
    // ================================================================

    /**
     * 拼发给 AI 的 Prompt
     *
     * Prompt 结构：
     * - 角色设定：你是指令解析器
     * - 对话历史：用户和助手聊了什么
     * - 当前消息：用户刚发的非指令消息
     * - 可用指令列表 + 参数说明
     * - 输出格式限制：只要一行 JSON
     */
    private String buildPrompt(DialogContext ctx, String userMessage) {
        StringBuilder history = new StringBuilder();
        if (ctx.getHistory() != null) {
            for (DialogContext.Turn turn : ctx.getHistory()) {
                history.append(turn.getRole()).append("：").append(turn.getText()).append("\n");
            }
        }

        return String.format("""
                你是一个指令解析器，服务于飞书企业效率机器人。
                根据对话历史和用户最新消息，判断用户想执行什么指令。

                ## 对话历史
                %s

                ## 用户最新消息
                %s

                ## 可用指令
                - weather：查询天气。参数：city(城市名)、time(可选，今天/明天/后天)
                - translate：翻译文本。参数：text(待翻译)、targetLang(目标语言如英文/日文)
                - schedule：创建日程。参数：title(事件标题)、time(时间描述)
                - search：搜索知识库。参数：keyword(关键词)

                ## 输出格式（只输出一行 JSON，不要其他文字）
                {"command":"指令名","args":{"参数名":"值",...}}
                如果用户消息和所有指令都无关：{"command":"none"}

                ## 示例
                历史：用户查了北京天气
                用户："那明天呢" → {"command":"weather","args":{"time":"明天"}}
                用户："那上海呢" → {"command":"weather","args":{"city":"上海"}}
                用户："帮我翻译成英文" → {"command":"translate","args":{"targetLang":"英文"}}
                用户："今天天气真好" → {"command":"none"}
                """, history.toString(), userMessage);
    }

    // ================================================================
    // 第四部分：AI 返回解析（JSON 字符串 → Intent 对象）
    // ================================================================

    /**
     * 从 AI 返回的文本中提取意图
     *
     * AI 返回示例：{"command":"weather","args":{"time":"明天"}}
     *
     * 关键：AI 可能多返回文字，所以先清理掉 markdown 代码块标记，
     * 再尝试 parse JSON。解析失败 → 返回 null（降级，不回复）
     */
    private Intent parseIntent(String aiResponse) {
        if (aiResponse == null) return null;
        try {
            String json = aiResponse
                    .replaceAll("```json", "")
                    .replaceAll("```", "")
                    .trim();

            JSONObject obj = JSONUtil.parseObj(json);
            String command = obj.getStr("command");
            if (command == null || "none".equals(command)) {
                return null;
            }

            Intent intent = new Intent();
            intent.setCommand(command);

            JSONObject argsObj = obj.getJSONObject("args");
            if (argsObj != null) {
                Map<String, String> args = new HashMap<>();
                for (String key : argsObj.keySet()) {
                    args.put(key, argsObj.getStr(key));
                }
                intent.setArgs(args);
            }
            return intent;

        } catch (Exception e) {
            log.warn("AI 返回解析失败 | response={}", aiResponse, e);
            return null;
        }
    }

    // ================================================================
    // 第五部分：工具方法
    // ================================================================

    /**
     * Map 参数 → 字符串（兼容只用 getArgs() 的 Handler）
     * {"city":"北京","time":"明天"} → "北京 明天"
     */
    private String toArgsString(Map<String, String> args) {
        if (args == null || args.isEmpty()) return "";
        return String.join(" ", args.values());
    }

    /**
     * AI 解析出的意图（内部数据类，只在 DialogService 内流转）
     */
    @lombok.Data
    static class Intent {
        private String command;
        private Map<String, String> args;
    }
}
