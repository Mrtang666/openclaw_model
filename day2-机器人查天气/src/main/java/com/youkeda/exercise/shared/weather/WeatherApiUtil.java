package com.youkeda.exercise.shared.weather;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import java.io.InputStream;

import java.net.URLEncoder;

public class WeatherApiUtil {
    // ========= 在这里填入你的【私钥】=========
    private static final String SECRET_KEY = "SvrNBNL1PWbX-I9Ot";

    public static String getSecretKey() {
        return SECRET_KEY;
    }

    /**
     * 发送Http Get请求
     */
    public static String sendHttpGet(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        InputStream inputStream;
        if (conn.getResponseCode() >= 200 && conn.getResponseCode() <= 299) {
            inputStream = conn.getInputStream();
        } else {
            inputStream = conn.getErrorStream();
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        conn.disconnect();
        return sb.toString();
    }

    /**
     * 中文城市编码
     */
    public static String encodeCity(String cityName) throws Exception {
        return URLEncoder.encode(cityName, StandardCharsets.UTF_8.name());
    }

    /**
     * 简易JSON文本截取工具（增加安全判断，不会数组越界崩溃）
     */
    public static String extractJson(String jsonText, String field) {
        String searchStr = "\"" + field + "\":";
        int pos = jsonText.indexOf(searchStr);
        // 找不到字段直接返回null
        if (pos == -1) {
            return null;
        }
        pos += searchStr.length();
        char firstChar = jsonText.charAt(pos);
        int endIndex;
        if (firstChar == '"') {
            // 字符串类型
            pos++;
            endIndex = jsonText.indexOf("\"", pos);
        } else {
            // 数字类型，查找逗号或者大括号作为结尾
            endIndex = jsonText.indexOf(",", pos);
            int bracePos = jsonText.indexOf("}", pos);
            if (endIndex == -1 || (bracePos != -1 && bracePos < endIndex)) {
                endIndex = bracePos;
            }
        }
        if (endIndex <= pos) {
            return null;
        }
        return jsonText.substring(pos, endIndex);
    }
}