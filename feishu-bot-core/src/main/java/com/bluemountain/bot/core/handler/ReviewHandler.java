package com.bluemountain.bot.core.handler;

import com.bluemountain.bot.common.dto.CommandContext;
import com.bluemountain.bot.integration.client.AiClient;
import com.bluemountain.bot.plugin.CommandPlugin;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * /review — AI 代码审查
 *
 * 用户贴代码 → 调 DeepSeek 审查 → 返回评分 + 问题列表 + 修改建议
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewHandler implements CommandPlugin {

    private final AiClient aiClient;

    private static final String SYSTEM_PROMPT = """
        你是一个资深代码审查专家。审查用户提供的代码，重点检查以下四类问题：

        1. 空指针风险（NPE）—— 参数或返回值未做空判断、Optional 误用
        2. 资源泄漏 —— 文件流/数据库连接未关闭、未使用 try-with-resources
        3. 异常处理不当 —— catch 块为空、吞异常不记录日志、直接 throw Exception
        4. 线程安全问题 —— 共享变量未同步、使用了非线程安全的类

        输出格式：
        ## 代码审查报告

        **评分**：X/10

        **风险清单**：
        - [高危/中危/低危] 问题描述
          → 修改建议

        **优点**：做得好的地方

        **总体建议**：一句话总结
        """;

    @Override
    public String name() { return "review"; }

    @Override
    public String description() {
        return "AI 代码审查 — 用法：/review 贴代码片段";
    }

    @Override
    public String execute(CommandContext ctx) {
        String code = ctx.getArgs();
        if (code == null || code.isBlank()) {
            return "请贴代码，用法：`/review <代码片段>`\n\n示例：/review public void foo(String s) { s.trim(); }";
        }

        log.info("代码审查 | userId={} codeLen={}", ctx.getUserId(), code.length());

        String review = aiClient.chat(SYSTEM_PROMPT, "请审查以下代码：\n\n" + code);
        if (review == null) {
            return "⚠️ 审查失败，请确认 AI Key 已配置";
        }

        return review.trim();
    }
}
