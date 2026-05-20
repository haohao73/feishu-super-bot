package com.bluemountain.bot.core.handler;

import com.bluemountain.bot.common.dto.CommandContext;
import com.bluemountain.bot.integration.client.WeatherClient;
import com.bluemountain.bot.integration.dto.WeatherResponse;
import com.bluemountain.bot.plugin.CommandPlugin;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * /weather <城市> — 查询实时天气
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherHandler implements CommandPlugin {

    private final WeatherClient weatherClient;

    @Override
    public String name() {
        return "weather";
    }

    @Override
    public String description() {
        return "查询实时天气 — 用法：/weather 北京";
    }

    @Override
    public String execute(CommandContext ctx) {
        // 1. 获取城市名
        // 上下文延续模式优先从结构化参数取，斜杠指令从 getArgs() 取
        String cityName;
        if (ctx.getContextArgs() != null && ctx.getContextArgs().containsKey("city")) {
            cityName = ctx.getContextArgs().get("city");
        } else {
            cityName = ctx.getArgs();
        }

        if (cityName == null || cityName.isBlank()) {
            return "请指定城市名，例如：`/weather 北京`\n\n支持的城市：北京、上海、广州、深圳、杭州、成都、武汉、南京、重庆、西安、长沙、苏州、郑州、天津、厦门、青岛、大连、哈尔滨、昆明、三亚";
        }

        // 2. 调用天气 API
        WeatherResponse weather = weatherClient.getNow(cityName);
        if (weather == null || weather.getNow() == null) {
            return "查询「" + cityName + "」天气失败\n可能原因：城市名不在支持列表中，或天气服务异常";
        }

        // 3. 格式化为回复消息
        WeatherResponse.Now now = weather.getNow();
        return String.format(
                "**%s 实时天气**\n\n" +
                "☁ 天气：%s\n" +
                "🌡 温度：%s℃（体感 %s℃）\n" +
                "💧 湿度：%s%%\n" +
                "💨 风向：%s\n\n" +
                "数据更新时间：%s",
                cityName,
                now.getText(),
                now.getTemp(), now.getFeelsLike(),
                now.getHumidity(),
                now.getWindDir(),
                weather.getUpdateTime()
        );
    }
}
