package com.bluemountain.bot.core.handler;

import com.bluemountain.bot.common.dto.CommandContext;
import com.bluemountain.bot.integration.client.TranslateClient;
import com.bluemountain.bot.plugin.CommandPlugin;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * /translate <文本> [to <语言>] — 翻译文本
 *
 * 用法：
 *   /translate Hello                      → 自动识别 → 中文
 *   /translate 你好 to 英文               → 中文 → 英文
 *   /translate Bonjour to 中文            → 自动识别 → 中文
 */
@Slf4j
@Component
public class TranslateHandler implements CommandPlugin {

    private final TranslateClient translateClient;

    public TranslateHandler(TranslateClient translateClient) {
        this.translateClient = translateClient;
    }

    @Override
    public String name() {
        return "translate";
    }

    @Override
    public String description() {
        return "翻译文本 — 用法：/translate Hello [to 英文/日文/...] 默认译成中文";
    }

    @Override
    public String execute(CommandContext ctx) {
        String text = ctx.getArgs();
        if (text == null || text.isBlank()) {
            return "请输入要翻译的文本\n"
                    + "用法：`/translate Hello` → 自动识别翻译成中文\n"
                    + "指定语言：`/translate 你好 to 英文`";
        }

        // ---- 解析：文本 + 目标语言 ----
        String sourceText;
        // 智能默认：原文含中文→译成英文，原文不含中文→译成中文
        String targetLang = TranslateClient.containsChinese(text) ? "en" : "zh";

        String lower = text.toLowerCase();
        int toIdx = lower.indexOf(" to ");
        if (toIdx > 0) {
            sourceText = text.substring(0, toIdx).trim();
            String langName = text.substring(toIdx + 4).trim();
            String code = TranslateClient.toLangCode(langName);
            if (code == null) {
                return "不支持的语言「" + langName + "」\n"
                        + "支持：中文、英文、日文、韩文、法文、德文、西班牙文、俄文、葡萄牙文、意大利文";
            }
            targetLang = code;
        } else {
            sourceText = text.trim();
        }

        if (sourceText.isBlank()) {
            return "请输入要翻译的文本";
        }

        // ---- 调用翻译 API ----
        String result = translateClient.translate(sourceText, targetLang);
        if (result == null) {
            return "翻译失败，请稍后重试";
        }

        return String.format("**翻译**\n\n原文：%s\n译文：%s\n方向：→ %s",
                sourceText, result, TranslateClient.toDisplayName(targetLang));
    }
    /**
     *
     * 仍然是解析参数,发送请求,返回数据给上层
     * 这里解析参数比较智能,如果是中文就翻译成英文,只要不是中文的文本都翻译成中文
     * 用户输入空白或者不支持的语言都提醒
     */
}
