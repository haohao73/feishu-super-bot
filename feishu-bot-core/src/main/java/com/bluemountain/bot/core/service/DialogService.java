package com.bluemountain.bot.core.service;

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
 * 多轮对话服务——AI 直接拼完整指令走正常路由
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DialogService {

    private final StringRedisTemplate redisTemplate;
    private final CommandRouter commandRouter;
    private final AiClient aiClient;

    public DialogContext getContext(String openId) {
        String json = redisTemplate.opsForValue().get(DialogContext.redisKey(openId));
        if (json == null) return null;
        return JSONUtil.toBean(json, DialogContext.class);
    }

    public void saveContext(String openId, String command,
                            Map<String, String> args,
                            String userMessage, String assistantReply) {
        DialogContext ctx = getContext(openId);
        if (ctx == null) {
            ctx = new DialogContext();
            ctx.setOpenId(openId);
            ctx.setTurnCount(0);
            ctx.setHistory(new ArrayList<>());
        }

        ctx.setLastCommand(command);
        ctx.setLastArgs(args);
        ctx.setTurnCount(ctx.getTurnCount() + 1);

        if (ctx.getTurnCount() > DialogContext.MAX_TURNS) {
            ctx.setTurnCount(1);
            ctx.setHistory(new ArrayList<>());
        }

        ctx.getHistory().add(new DialogContext.Turn("user", userMessage));
        ctx.getHistory().add(new DialogContext.Turn("assistant", assistantReply));
        ctx.setLastActiveTime(System.currentTimeMillis());

        redisTemplate.opsForValue().set(
                DialogContext.redisKey(openId),
                JSONUtil.toJsonStr(ctx),
                Duration.ofSeconds(DialogContext.TTL_SECONDS));

        log.info("上下文已保存 | openId={} cmd={} turn={}", openId, command, ctx.getTurnCount());
    }

    public void clearContext(String openId) {
        redisTemplate.delete(DialogContext.redisKey(openId));
    }

    /**
     * AI 直接返回完整指令字符串（如 "/weather 北京 明天"），
     * 然后走 standard 的 parse -> route 流程。
     */
    public String handleContinuation(String openId, String chatId, String messageText) {
        DialogContext ctx = getContext(openId);
        if (ctx == null) return null;
/*
 *ai 会返回以/开头的标准指令,不用花费格外的精力清洗规范化ai返回的用户指令解析
 */
        String prompt = buildPrompt(ctx, messageText);
        String aiResponse = aiClient.chat(
                "你是飞书机器人指令解析器。只输出一个完整的指令字符串，不要多余内容。", prompt);
        log.info("AI 意图 | input=[{}] output=[{}]", messageText, aiResponse);

        if (aiResponse == null) return null;

        String cleaned = aiResponse.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("```\\w*|```", "").trim();
        }
        if (!cleaned.startsWith("/")) {
            log.info("AI 未识别意图 | text={}", messageText);
            return null;
        }

        CommandContext cmdCtx = CommandContext.parse(chatId, openId, null, cleaned);
        if (cmdCtx == null) return null;

        try {
            String reply = commandRouter.route(cmdCtx);
            if (reply != null) {
                Map<String, String> args = new HashMap<>();
                if (cmdCtx.getArgs() != null && !cmdCtx.getArgs().isBlank()) {
                    args.put("text", cmdCtx.getArgs());
                }
                //保存指令
                saveContext(openId, cmdCtx.getCommand(), args, messageText, reply);
            }
            return reply;
        } catch (Exception e) {
            log.error("上下文指令执行失败", e);
            return "抱歉，处理请求时出错了，请稍后重试";
        }
    }

    /**
     * 把历史会话一起发送给ai
     * @param ctx
     * @param userMessage
     * @return
     */
    private String buildPrompt(DialogContext ctx, String userMessage) {
        StringBuilder history = new StringBuilder();
        if (ctx.getHistory() != null) {
            for (DialogContext.Turn turn : ctx.getHistory()) {
                history.append(turn.getRole()).append("：").append(turn.getText()).append("\n");
            }
        }

        return String.format("""
                根据对话历史，把用户的模糊消息补全为完整的斜杠指令。

                ## 对话历史
                %s

                ## 可用指令
                /weather <城市> [今天|明天|后天]
                /translate <文本> [to 语言]
                /schedule <时间描述> <事件标题>
                /search <关键词>

                ## 用户消息
                %s

                ## 要求
                只输出一行指令。识别不了输出 NO。
                示例：
                用户"那明天呢" → /weather 北京 明天
                用户"上海呢"   → /weather 上海
                用户"翻成日文" → /translate 你好 to 日文
                """, history.toString(), userMessage);
    }
}
