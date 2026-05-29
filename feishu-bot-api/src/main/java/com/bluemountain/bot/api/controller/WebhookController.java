package com.bluemountain.bot.api.controller;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.bluemountain.bot.api.dto.WebhookEvent;
import com.bluemountain.bot.auth.SignatureVerifier;
import com.bluemountain.bot.auth.interceptor.UserAutoRegister;
import com.bluemountain.bot.common.dto.CommandContext;
import com.bluemountain.bot.core.router.CommandRouter;
import com.bluemountain.bot.core.service.ApprovalReminderService;
import com.bluemountain.bot.core.service.DialogService;
import com.bluemountain.bot.core.service.GroupRegistry;
import com.bluemountain.bot.infrastructure.entity.BotCommandLog;
import com.bluemountain.bot.infrastructure.mapper.BotCommandLogMapper;
import com.bluemountain.bot.integration.client.FeishuClient;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 飞书 Webhook 回调端点
 *
 * 所有飞书事件（用户消息、按钮点击等）都从这个入口进来
 *
 * 飞书将接收到的信息发送到公网(natapp隧道,转发到localhost:8080)
 */
@Slf4j
@RestController
@RequestMapping("/webhook")
@RequiredArgsConstructor
public class WebhookController {

    private final SignatureVerifier signatureVerifier; //验签
    private final CommandRouter commandRouter;   //指令路由
    private final FeishuClient feishuClient;   //调用飞书api发消息
    private final UserAutoRegister userAutoRegister; //自动注册用户
    private final DialogService dialogService; //多轮对话,上下文
    private final GroupRegistry groupRegistry; //采集群名,存入redis,实现广播
    private final ApprovalReminderService approvalReminderService;//审批提醒
    private final BotCommandLogMapper commandLogMapper; //指令日志

    /** 指令处理线程池：复用线程，限制并发数 */
    private final ExecutorService executor = Executors.newFixedThreadPool(10);

    /**
     * 飞书在配置事件回调的时候,返回一个challenge,飞书就知道这个url是本人的了,飞书
     * 开发者中心可以测试
     * @param challenge
     * @param type
     * @return
     */
    @GetMapping("/event")
    public Object urlVerify(@RequestParam(value = "challenge", required = false) String challenge,
                            @RequestParam(value = "type", required = false) String type) {
        if ("url_verification".equals(type) && challenge != null) {
            log.info("飞书 URL 验证 | challenge={}", challenge);
            return new ChallengeResponse(challenge);
        }
        // 必须返回 JSON，不能返回纯字符串
        return Map.of("ok", true);
    }


    //验签成功后马山返回200,因为飞书的要求必须在3秒内返回,后续的处理异步进行

    @PostMapping("/event")
    /**
     * 这里用HttpServletRequest 是为了拿到原始的请求体,如果用requestbody就是json反序列化了,拿不到原始的字符串
     */
    public Object onEvent(HttpServletRequest request) {
        // ---- 第1步：读原始请求体 ----
        String body = readBody(request);
        log.info("POST body: {}", body); // 调试

        // ---- get,post都返回challenge,飞书某些规定 ----
        JSONObject bodyJson = JSONUtil.parseObj(body);
        if ("url_verification".equals(bodyJson.getStr("type")) && bodyJson.containsKey("challenge")) {
            String challenge = bodyJson.getStr("challenge");
            log.info("飞书 URL 验证(POST) | challenge={}", challenge);
            return new ChallengeResponse(challenge);
        }

        // ---- 第2步：验签 ----
        /**
         * 飞书的验签就是时间戳,随机字符串,签名,请求体
         */
        String timestamp = request.getHeader("X-Lark-Request-Timestamp");
        String nonce = request.getHeader("X-Lark-Request-Nonce");
        String signature = request.getHeader("X-Lark-Signature");

        if (!signatureVerifier.verify(timestamp, nonce, body, signature)) {
            log.warn("签名验证失败 | IP={}", request.getRemoteAddr());
            return errorResponse("签名验证失败");
        }

        // ---- 第3步：判断事件类型 ----
        String eventType = bodyJson.getJSONObject("header")
                .getStr("event_type", "");

        // 审批事件 → 写表后返回
        if (eventType.contains("approval_instance")) {
            log.info("收到审批事件 | type={}", eventType);
            executor.submit(() -> approvalReminderService.handleWebhookEvent(body));
            return Map.of("ok", true);
        }

        // ---- 第4步：解析消息事件 ----
        WebhookEvent event;
        try {
            event = JSONUtil.toBean(body, WebhookEvent.class);//hutool包的用法,将请求体映射到我们写的webhook对象
        } catch (Exception e) {
            log.error("解析事件体失败 | body={}", body, e);
            return errorResponse("事件解析失败");
        }

        // ---- 第5步：只看消息事件 ----
        if (event.getEvent() == null || event.getEvent().getMessage() == null) {
            return Map.of("ok", true);
        }

        // ---- 第5步：解析消息内容 ----
        String chatId = event.getEvent().getMessage().getChatId();
        String messageId = event.getEvent().getMessage().getMessageId();
        String openId = event.getEvent().getSender().getSenderId().getOpenId();

        // 这里的content需要二次解析,因为仍然是json
        String messageText = extractText(event.getEvent().getMessage().getContent());
        if (messageText == null) return Map.of("ok", true);

        log.info("收到消息 | openId={} chat={} text={}", openId, chatId, messageText);

        // ---- 第6步：立刻返回 200，不等业务处理 ----
        // 飞书要求 3 秒内响应，慢指令（翻译、部署等）会超时导致飞书重试
        // 所以验签通过后立刻返回，业务处理异步执行
        final String finalChatId = chatId;
        final String finalOpenId = openId;
        final String finalMessageText = messageText;
        final String finalMessageId = messageId;
        executor.submit(() -> processCommand(finalChatId, finalOpenId, finalMessageId, finalMessageText));

        return Map.of("ok", true);//立马返回,丢给异步做,防止超时
    }

    /**
     * 关键!!异步处理指令（在子线程中执行，不阻塞 Webhook 响应）
     */
    private void processCommand(String chatId, String openId, String messageId, String messageText) {

        /**
         *
         *  startMs：记录指令开始处理的时间戳，最后算执行耗时写入日志。status = 3：预设是"失败"，执行成功才改成
         *   1。这叫"悲观预设"——路径上只要有一个地方抛异常，finally 块里写入的就是失败状态。
         */
        long startMs = System.currentTimeMillis();
        String cmd = null;
        int status = 3; // 默认失败
        String errorMsg = null;

        try {
            // ① 自动注册用户（首次发消息时在 bot_user 表创建记录）

            /**
             * 查到角色就直接返回,没有查到就自动注册进用户表,初始的权限都是user,管理员和超级管理员都是手动修改数据库
             */
            userAutoRegister.ensureUser(openId, null);

            // ② 解析指令
            CommandContext ctx = CommandContext.parse(chatId, openId, messageId, messageText);

            if (ctx == null) {
                // ===== 这里原先不是斜杠指令直接返回了,但是为了实现上下文的延续功能,哪怕不是以/开头的指令也不能立马返回 =====
                String reply = dialogService.handleContinuation(openId, chatId, messageText);
                if (reply != null) {
                    feishuClient.sendTextMessage(chatId, reply);
                }
                return;
                //拿出上下文发给ai判断意图,没解析出意图就返回,解析出了就直接调用飞书api发送到群里就行
            }

            cmd = ctx.getCommand();

            // ③ 斜杠指令 → 正常路由
            /**
             * CommandRouter.route(ctx)
             *     ↓ 查 Map："weather" → WeatherHandler
             *   WeatherHandler.execute(ctx)
             *     ↓ 拿城市名 "北京"
             *   WeatherClient.getNow("北京")
             *     ↓ HTTP GET api.open-meteo.com
             *   返回天气 JSON
             *     ↓ 格式化为 Markdown
             *   返回 "**北京 实时天气**\n☁ 晴\n🌡 25℃..."
             *     ↓ FeishuClient.sendTextMessage()
             *   发到飞书群
             */
            String reply = commandRouter.route(ctx);
            if (reply == null) return;

            feishuClient.sendTextMessage(chatId, reply);

            // ④ 执行成功 → 保存上下文到 Redis（下次 "那明天呢" 就能用了）
            dialogService.saveContext(
                    openId,
                    ctx.getCommand(),
                    extractArgs(ctx),
                    messageText,
                    reply);

            // ⑤ 静默采集群名（失败不报错，广播时用）
            groupRegistry.collect(chatId);

            status = 1; // 成功

        } catch (Exception e) {
            log.error("指令处理异常", e);
            errorMsg = e.getMessage();
            if (errorMsg != null && errorMsg.length() > 1024) {
                errorMsg = errorMsg.substring(0, 1020) + "...";
            }
        } finally {
            // 写入指令日志（静默，不阻塞主流程）
            try {
                BotCommandLog logEntry = new BotCommandLog();
                logEntry.setUserId(openId);
                logEntry.setCommand(cmd);
                logEntry.setRawMessage(messageText);
                logEntry.setChatId(chatId);
                logEntry.setStatus(status);
                logEntry.setErrorMsg(errorMsg);
                logEntry.setExecuteTimeMs((int) (System.currentTimeMillis() - startMs));
                logEntry.setCreateTime(LocalDateTime.now());
                commandLogMapper.insert(logEntry);
            } catch (Exception ignored) {
                // 日志写入失败不影响主流程
            }
        }
    }

    /**
     * 从 CommandContext 提取关键参数存入 Redis 上下文
     *
     * 根据指令类型按不同规则提取：
     *   weather   → city / time
     *   schedule  → time / title
     *   translate → text / targetLang
     *   其他       → 整个 args 作为 keyword
     */
    private Map<String, String> extractArgs(CommandContext ctx) {
        Map<String, String> args = new HashMap<>();
        String argsStr = ctx.getArgs();
        if (argsStr == null || argsStr.isBlank()) return args;

        String cmd = ctx.getCommand();
        if (cmd == null) return args;

        switch (cmd.toLowerCase()) {
            case "weather" -> {
                String[] parts = argsStr.trim().split("\\s+", 2);
                args.put("city", parts[0]);
                if (parts.length > 1) args.put("time", parts[1]);
            }
            case "schedule" -> {
                // /schedule 明天 15:00 项目评审 → 整串给 TimeParser 就够了
                args.put("time", argsStr.trim());
            }
            case "translate" -> {
                // /translate hello to 日文 → text=hello, targetLang=日文
                String text = argsStr;
                String lang = null;
                int toIdx = argsStr.toLowerCase().indexOf(" to ");
                if (toIdx > 0) {
                    text = argsStr.substring(0, toIdx).trim();
                    lang = argsStr.substring(toIdx + 4).trim();
                }
                args.put("text", text);
                if (lang != null) args.put("targetLang", lang);
            }
            default -> {
                args.put("keyword", argsStr.trim());
            }
        }
        return args;
    }

    /**
     * 从飞书消息 content 字段提取纯文本
     * content 格式：{"text":"/weather 北京"} 或 {"text":"聊天内容"}
     */
    private String extractText(String content) {
        if (content == null) return null;
        try {
            JSONObject obj = JSONUtil.parseObj(content);
            return obj.getStr("text");//取出内容里面包含的指令消息,比如/weather
        } catch (Exception e) {
            // 有些消息格式可能不含 text 字段（如图片、文件），直接忽略
            return null;
        }
    }

    private String readBody(HttpServletRequest request) {
        try {
            StringBuilder sb = new StringBuilder();
            BufferedReader reader = request.getReader();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("读取请求体失败", e);
            return "";
        }
    }

    private Object errorResponse(String msg) {
        return new ErrorResult(400, msg);
    }

    // ---- 简单响应体（不用引 common 的 ApiResult，保持独立） ----
    record ChallengeResponse(String challenge) {}
    record ErrorResult(int code, String message) {}
}
