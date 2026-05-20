package com.bluemountain.bot.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 飞书 Webhook HMAC 签名验证器
 *
 * ============================================================
 * 什么是 HMAC 签名验证？
 * ============================================================
 *
 * 想象飞书给你寄了一封信。你怎么知道这封信真的是飞书写来的
 * 而不是别人伪造的？
 *
 * 方法：飞书和你约好一个"暗号"（EncryptKey），只有你俩知道。
 * 飞书寄信时，用暗号 + 信的内容算出一个"签名"，写在信封上。
 * 你收到信后，用同一个暗号 + 信的内容也算一遍签名。
 * 两个签名一样 → 信是真的。不一样 → 伪造的，扔掉。
 *
 * 数学上这个"算签名"的过程就是 HMAC-SHA256 ——
 * 一个单向函数，给定密钥和消息，生成唯一签名。
 * 没有密钥的人无法伪造签名。
 *
 * ============================================================
 * JWT vs HMAC 的区别
 * ============================================================
 *
 * 苍穹外卖的 JWT：
 *   - 用户登录 → 你签发 Token 给用户 → 用户每次请求带 Token
 *   - 你是"签发者"，你验证自己签发的 Token
 *
 * 飞书的 HMAC：
 *   - 飞书是"签发者"，你用飞书的密钥验证飞书的签名
 *   - 你不是签发者，你是"验证者"
 *
 * 两者的代码本质一模一样：都是拿密钥去验证一段数据是否被篡改。
 * 只是 JWT 是你签发你验证，HMAC 是别人签发你验证。
 * ============================================================
 */

/**
 *
 * 这个类用来解析
 */
@Slf4j
@Component
public class SignatureVerifier {

    @Value("${feishu.encrypt-key:}")
    private String encryptKey;

    @Value("${feishu.signature-verification-enabled:false}")
    private boolean verificationEnabled;

    /**
     * 验证飞书 Webhook 签名
     *
     * @param timestamp 飞书 Header: X-Lark-Request-Timestamp（Unix 秒级时间戳）
     * @param nonce     飞书 Header: X-Lark-Request-Nonce（随机字符串）
     * @param body      原始请求体（JSON 字符串）
     * @param signature 飞书 Header: X-Lark-Signature（Base64 编码的 HMAC-SHA256）
     * @return true = 验证通过 / false = 签名不符或过期
     */
    public boolean verify(String timestamp, String nonce, String body, String signature) {
        // ============================================================
        // 如果还没有在飞书后台配置加密密钥，暂时跳过验证
        // 这是开发阶段的临时开关，上线后必须开启！
        // ============================================================
        if (!verificationEnabled || encryptKey == null || encryptKey.isBlank()) {
            log.warn("飞书签名验证未启用 — 上线前必须开启！");
            return true;
        }

        // ============================================================
        // 第1步：防重放攻击（Replay Attack）
        // 检查时间戳是否在 1 小时内，防止有人抓包后重复发送
        // ============================================================
        long now = System.currentTimeMillis() / 1000;  // 当前时间的 Unix 秒
        long requestTime = Long.parseLong(timestamp);   // 飞书发来的时间
        if (Math.abs(now - requestTime) > 3600) {       // 差值超过 1 小时
            log.warn("飞书签名时间戳过期 | 本地={} 飞书={} 差值={}秒", now, requestTime, Math.abs(now - requestTime));
            return false;
        }

       //拼接字符串 把他和飞书发过来的签名做比较
        String rawString = timestamp + nonce + encryptKey + body;

        // ============================================================
        // 第3步：用 HMAC-SHA256 计算签名
        // HmacSHA256(密钥=encryptKey, 消息=rawString) → 二进制字节
        // ============================================================
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(
                    encryptKey.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            );
            mac.init(secretKey);
            byte[] signBytes = mac.doFinal(rawString.getBytes(StandardCharsets.UTF_8));

            // ============================================================
            // 第4步：Base64 编码后和飞书发来的签名比较
            // HMAC 输出是二进制字节，需要 Base64 编码成字符串才能比较
            // ============================================================
            String computedSignature = Base64.getEncoder().encodeToString(signBytes);

            boolean valid = computedSignature.equals(signature);
            if (!valid) {
                log.warn("飞书签名验证失败 | 期望={} 计算={}", signature, computedSignature);
            }
            return valid;

        } catch (Exception e) {
            log.error("HMAC 签名计算异常", e);
            return false;
        }
    }
}
