package com.bluemountain.bot.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 飞书 Webhook 推送的事件体
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WebhookEvent {

    private String schema;
    private Header header;
    private Event event;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Header {
        @JsonProperty("event_id")
        private String eventId;
        @JsonProperty("event_type")
        private String eventType;
        private String token;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Event {
        private Sender sender;
        private Message message;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Sender {
        @JsonProperty("sender_id")
        private SenderId senderId;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SenderId {
        @JsonProperty("open_id")
        private String openId;
        @JsonProperty("union_id")
        private String unionId;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Message {
        @JsonProperty("message_id")
        private String messageId;
        @JsonProperty("chat_id")
        private String chatId;
        @JsonProperty("content")
        private String content; // JSON 字符串，需二次解析
    }
}
