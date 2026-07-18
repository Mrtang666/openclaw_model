package com.youkeda.exercise.shared.weather;

import com.youkeda.exercise.shared.weather.WeatherApiUtil;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 天气服务类 - 封装天气查询逻辑
 */
@Service
public class WeatherService {

    private static final String SECRET_KEY = "SvrNBNL1PWbX-I9Ot";

    /**
     * 查询天气并返回格式化的结果
     *
     * @param cityName 城市名称，如：杭州、北京
     * @return 格式化后的天气信息字符串
     */
    public String queryWeather(String cityName) {
        try {
            // 1. 编码城市名
            String cityEncode = URLEncoder.encode(cityName, StandardCharsets.UTF_8.name());

            // 2. 构建请求URL（心知天气V3标准接口）
            String url = String.format(
                    "https://api.seniverse.com/v3/weather/daily.json?key=%s&location=%s&language=zh-Hans&unit=c",
                    SECRET_KEY, cityEncode
            );

            // 3. 发送HTTP请求
            String json = WeatherApiUtil.sendHttpGet(url);

            // 4. 检查是否出错
            if (json.contains("\"status\":\"error\"")) {
                String statusMsg = WeatherApiUtil.extractJson(json, "status_msg");
                if (statusMsg == null) {
                    return "❌ 查询失败，请检查城市名是否正确";
                }
                return "❌ 查询失败：" + statusMsg;
            }

            // 5. 提取天气数据
            String city = WeatherApiUtil.extractJson(json, "name");
            String date = WeatherApiUtil.extractJson(json, "date");
            String textDay = WeatherApiUtil.extractJson(json, "text_day");
            String textNight = WeatherApiUtil.extractJson(json, "text_night");
            String high = WeatherApiUtil.extractJson(json, "high");
            String low = WeatherApiUtil.extractJson(json, "low");
            String windDir = WeatherApiUtil.extractJson(json, "wind_direction");
            String humidity = WeatherApiUtil.extractJson(json, "humidity");

            // 6. 检查城市是否找到
            if (city == null || city.isEmpty()) {
                return "❌ 未找到城市：「" + cityName + "」，请检查城市名是否正确";
            }

            // 7. 格式化输出
            return String.format(
                    "🌤️ %s 天气预报\n" +
                            "─────────────────\n" +
                            "📅 日期：%s\n" +
                            "☀️ 白天：%s\n" +
                            "🌙 夜间：%s\n" +
                            "🌡️ 温度：%s℃ ~ %s℃\n" +
                            "💨 风向：%s\n" +
                            "💧 湿度：%s%%\n" +
                            "─────────────────\n" +
                            "💡 发送「天气 城市名」查询其他城市",
                    city, date, textDay, textNight, low, high, windDir, humidity
            );

        } catch (Exception e) {
            return "❌ 查询天气异常：" + e.getMessage() + "\n请稍后重试或检查网络连接";
        }
    }
}