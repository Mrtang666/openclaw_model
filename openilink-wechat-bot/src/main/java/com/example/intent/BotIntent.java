package com.example.intent;

public enum BotIntent {
    VOICE_MODE_ON("voice", "com.example.voice + com.example.tts", "开启语音回复模式"),
    VOICE_MODE_OFF("voice", "com.example.voice", "关闭语音回复模式"),
    WEATHER("weather", "com.example.WeatherService", "天气查询"),
    ROUTE_MAP("route_map", "com.example.routegen", "路线图生成"),
    IMAGE_GENERATION("image_generation", "com.example.imagegen", "图片生成"),
    FILE_GENERATION("file_generation", "com.example.file", "文件生成"),
    CHAT("chat", "com.example.LocalLLMService", "普通聊天");

    private final String code;
    private final String packageName;
    private final String description;

    BotIntent(String code, String packageName, String description) {
        this.code = code;
        this.packageName = packageName;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getDescription() {
        return description;
    }
}
