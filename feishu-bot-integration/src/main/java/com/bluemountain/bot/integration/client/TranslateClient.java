package com.bluemountain.bot.integration.client;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Random;

/**
 * 翻译客户端 — 百度通用翻译 API
 *
 * 国内直连，免费版每月 200 万字符
 */
@Slf4j
@Component
public class TranslateClient {

    private final RestTemplate restTemplate;

    @Value("${integration.translate.baidu.app-id:}")
    private String appId;

    @Value("${integration.translate.baidu.secret-key:}")
    private String secretKey;

    /** 语言名 → 百度语言代码 */
    private static final Map<String, String> LANG_MAP = Map.ofEntries(
            Map.entry("中文", "zh"),    Map.entry("chinese", "zh"),
            Map.entry("英文", "en"),    Map.entry("english", "en"),
            Map.entry("日文", "jp"),    Map.entry("japanese", "jp"),
            Map.entry("韩文", "kor"),   Map.entry("korean", "kor"),
            Map.entry("法文", "fra"),   Map.entry("french", "fra"),
            Map.entry("德文", "de"),    Map.entry("german", "de"),
            Map.entry("西班牙文", "spa"), Map.entry("spanish", "spa"),
            Map.entry("俄文", "ru"),    Map.entry("russian", "ru"),
            Map.entry("葡萄牙文", "pt"), Map.entry("portuguese", "pt"),
            Map.entry("意大利文", "it"), Map.entry("italian", "it")
    );

    private static final String API_URL = "https://fanyi-api.baidu.com/api/trans/vip/translate";

    public TranslateClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        this.restTemplate = new RestTemplate(factory);
        this.restTemplate.getMessageConverters()
                //调试了很多遍,强制指定编码格式防止乱码
                //  // 位置 0 = 最高优先级，盖掉默认的编码
                //  this.restTemplate.getMessageConverters()
                //          .add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));
                .add(0, new org.springframework.http.converter.StringHttpMessageConverter(StandardCharsets.UTF_8));
    }

    /**
     * 翻译文本（百度 API）
     *
     * 签名规则：MD5(appid + q + salt + key)
     */
    public String translate(String text, String targetLang) {
        if (appId == null || appId.trim().isBlank() || secretKey == null || secretKey.trim().isBlank()) {
            log.error("百度翻译未配置 app-id 或 secret-key");
            return null;
        }

        try {
            // 显式指定源语言（百度 auto 不稳定）
            String sourceLang = containsChinese(text) ? "zh" : "en";
            // 源和目标相同则无意义，交换目标语言
            if (sourceLang.equals(targetLang)) {
                targetLang = "zh".equals(sourceLang) ? "en" : "zh";
            }

            // 签名：MD5(appid + q + salt + key)，q 用原始文本
            String appIdTrim = appId.trim();
            String keyTrim = secretKey.trim();
            String salt = String.valueOf(new Random().nextInt(100000));
            String signRaw = appIdTrim + text + salt + keyTrim;
            String sign = DigestUtil.md5Hex(signRaw);

            log.info("翻译请求 | text={} {}→{}", text, sourceLang, targetLang);

            // 用 POST + x-www-form-urlencoded，避免 GET URL 编码问题
            //设置请求头,想要表单格式的数据
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            //构造表单参数
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("q", text);
            body.add("from", sourceLang);
            body.add("to", targetLang);
            body.add("appid", appIdTrim);
            body.add("salt", salt);
            body.add("sign", sign);
            //把参数和请求头都打包成一个请求对象
            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
            //url,请求对象 ,返回的数据格式
            String json = restTemplate.postForObject(API_URL, request, String.class);
            log.info("翻译API原始返回 | {}", json);
            /**
             *
             *       解析数据并返回
             */
            // 百度返回格式：{"from":"en","to":"zh","trans_result":[{"src":"Hello","dst":"你好"}]}
            JSONObject root = JSONUtil.parseObj(json);

            if (root.containsKey("error_code")) {
                log.error("百度翻译 API 错误 | code={} msg={}", root.getStr("error_code"), root.getStr("error_msg"));
                return null;
            }

            JSONArray transResult = root.getJSONArray("trans_result");
            String result = transResult.getJSONObject(0).getStr("dst");
            // 百度偶发返回 URL 编码的文本，解码兜底
            try {
                String decoded = java.net.URLDecoder.decode(result, StandardCharsets.UTF_8);
                if (!decoded.equals(result)) {
                    result = decoded;
                }
            } catch (Exception ignored) {}
            log.info("翻译结果 | {} → {}", text, result);
            return result;

        } catch (Exception e) {
            log.error("翻译失败 | text={}", text, e);
            return null;
        }
    }

    public static boolean containsChinese(String text) {
        if (text == null) return false;
        for (char c : text.toCharArray()) {
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                return true;
            }
        }
        return false;
    }

    public static String toLangCode(String name) {
        if (name == null) return null;
        String lower = name.trim().toLowerCase();
        return LANG_MAP.getOrDefault(lower, LANG_MAP.get(name.trim()));
    }

    public static String toDisplayName(String code) {
        return Map.of(
                "zh", "中文", "en", "英文", "jp", "日文",
                "kor", "韩文", "fra", "法文", "de", "德文"
        ).getOrDefault(code, code);
    }
}
