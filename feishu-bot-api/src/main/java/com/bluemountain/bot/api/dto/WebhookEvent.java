package com.bluemountain.bot.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 飞书 Webhook 推送的事件体
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)//多发的字段直接忽略
public class WebhookEvent {

    private String schema; //版本号
    private Header header; //事件头
    private Event event; //事件体

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Header {
        @JsonProperty("event_id")  //飞书的消息格式是下划线,映射到java实体类的驼峰
        private String eventId;
        @JsonProperty("event_type")
        private String eventType;
        private String token;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Event {
        private Sender sender; //谁发的
        private Message message; //消息
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Sender {
        @JsonProperty("sender_id") //发送者的id,依旧映射
        private SenderId senderId;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SenderId {
        @JsonProperty("open_id")
        private String openId; //用户唯一标识
        @JsonProperty("union_id") //跨应用的唯一标识
        private String unionId;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Message {
        @JsonProperty("message_id")
        private String messageId;
        //群聊id,告诉飞书发到哪个群
        @JsonProperty("chat_id")
        private String chatId;
        @JsonProperty("content")
        // !!!!!!!!!JSON 字符串，需二次解析
        private String content;
    }
}
