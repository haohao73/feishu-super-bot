package com.bluemountain.bot.integration.client;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.bluemountain.bot.integration.dto.WeatherResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Open-Meteo 天气客户端 — 免费、无需注册、无需 API Key,和风天气调试出错了换个简单点的
 */
@Slf4j
@Component
public class WeatherClient {
    /**
     * 就是 Java 的 HTTP 客户端，用来发 HTTP 请求的。
     */
    private final RestTemplate restTemplate;

    /** 城市 → 经纬度 */
    private static final Map<String, double[]> CITY_MAP = Map.ofEntries(
            Map.entry("北京", new double[]{39.9042, 116.4074}),
            Map.entry("上海", new double[]{31.2304, 121.4737}),
            Map.entry("广州", new double[]{23.1291, 113.2644}),
            Map.entry("深圳", new double[]{22.5431, 114.0579}),
            Map.entry("杭州", new double[]{30.2741, 120.1551}),
            Map.entry("成都", new double[]{30.5728, 104.0668}),
            Map.entry("武汉", new double[]{30.5928, 114.3055}),
            Map.entry("南京", new double[]{32.0603, 118.7969}),
            Map.entry("重庆", new double[]{29.4316, 106.9123}),
            Map.entry("西安", new double[]{34.3416, 108.9398}),
            Map.entry("长沙", new double[]{28.2282, 112.9388}),
            Map.entry("苏州", new double[]{31.2990, 120.5853}),
            Map.entry("郑州", new double[]{34.7466, 113.6253}),
            Map.entry("天津", new double[]{39.3434, 117.3616}),
            Map.entry("厦门", new double[]{24.4798, 118.0894}),
            Map.entry("青岛", new double[]{36.0671, 120.3826}),
            Map.entry("大连", new double[]{38.9140, 121.6147}),
            Map.entry("哈尔滨", new double[]{45.8038, 126.5350}),
            Map.entry("昆明", new double[]{25.0389, 102.7183}),
            Map.entry("三亚", new double[]{18.2528, 109.5120})
    );

    /** 天气代码 → 中文 */
    private static final Map<Integer, String> WEATHER_MAP = Map.ofEntries(
            Map.entry(0, "晴"),
            Map.entry(1, "多云"), Map.entry(2, "多云"), Map.entry(3, "阴"),
            Map.entry(45, "雾"), Map.entry(48, "雾凇"),
            Map.entry(51, "小雨"), Map.entry(53, "小雨"), Map.entry(55, "中雨"),
            Map.entry(61, "小雨"), Map.entry(63, "中雨"), Map.entry(65, "大雨"),
            Map.entry(71, "小雪"), Map.entry(73, "中雪"), Map.entry(75, "大雪"),
            Map.entry(80, "阵雨"), Map.entry(81, "阵雨"), Map.entry(82, "阵雨"),
            Map.entry(85, "阵雪"), Map.entry(86, "阵雪"),
            Map.entry(95, "雷暴"), Map.entry(96, "雷暴+冰雹"), Map.entry(99, "强雷暴")
    );

    public WeatherClient() {
        //创建http连接工厂
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        //5秒内连不上就放弃
        factory.setConnectTimeout(5000);
        //10秒后没返回数据也放弃
        factory.setReadTimeout(10000);
        this.restTemplate = new RestTemplate(factory);
    }

    public WeatherResponse getNow(String cityName) {
        double[] coords = CITY_MAP.get(cityName);
        if (coords == null) {
            log.warn("不支持的城市：{}", cityName);
            return null;
        }

        String url = String.format(
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,relative_humidity_2m,wind_speed_10m,weather_code",
                coords[0], coords[1]
        );
        log.info("查询天气 | 城市={} URL={}", cityName, url);

        try {
            /**|
             *
             * // 第1行：发 HTTP GET 请求，返回 JSON 字符串
             *   String json = restTemplate.getForObject(java.net.URI.create(url), String.class);
             *   //           ↑ 这一行向 Open-Meteo 服务器发了 GET 请求，拿到原始 JSON 文本
             *   // json = "{\"current\":{\"temperature_2m\":25.3,\"weather_code\":0,...}}"
             *
             *   // 第2行：把 JSON 字符串解析成对象，才能操作它
             *   JSONObject root = JSONUtil.parseObj(json);
             *   // root = {current: {...}}  ← 现在可以 root.getStr("xxx") 了
             *
             *   // 第3行：从整个 JSON 里取出 "current" 这个嵌套部分
             *   JSONObject current = root.getJSONObject("current");
             *   // current = {temperature_2m: 25.3, weather_code: 0, ...}
             *
             */
            String json = restTemplate.getForObject(java.net.URI.create(url), String.class);
            JSONObject root = JSONUtil.parseObj(json);
            JSONObject current = root.getJSONObject("current");

            WeatherResponse resp = new WeatherResponse();
            resp.setCode("200");
            resp.setUpdateTime(current.getStr("time"));

            WeatherResponse.Now now = new WeatherResponse.Now();
            //从json对象中按key去字符串
            now.setTemp(current.getStr("temperature_2m"));
            now.setFeelsLike(current.getStr("temperature_2m")); // Open-Meteo 无体感温度
            now.setHumidity(current.getStr("relative_humidity_2m"));
            now.setWindDir(current.getStr("wind_speed_10m") + " km/h");

            int code = current.getInt("weather_code", 0);
            now.setText(WEATHER_MAP.getOrDefault(code, "未知"));

            resp.setNow(now);
            return resp;
            //返回给上面的handle

        } catch (Exception e) {
            log.error("天气 API 调用失败 | 城市={}", cityName, e);
            return null;
        }
    }
    /**
     *
     * 核心就是拼url参数,发请求,json解析,字段提取,封装dto并返回
     */
}
