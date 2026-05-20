package com.bluemountain.bot.integration.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 和风天气 API 返回结构
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WeatherResponse {

    private String code;
    private String updateTime;
    private Now now;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Now {
        private String temp;        // 温度（摄氏度）
        private String feelsLike;   // 体感温度
        private String text;        // 天气描述，如"晴"
        private String windDir;     // 风向，如"北风"
        private String humidity;    // 相对湿度
    }
}
