package com.example.intent;

import java.util.regex.Pattern;

public class IntentClassifier {

    private static final Pattern VOICE_MODE_ON = Pattern.compile(
            ".*(开始|开启|打开|进入).{0,6}(语音回复模式|语音模式|语音回答模式).*");
    private static final Pattern VOICE_MODE_OFF = Pattern.compile(
            ".*(关闭|退出|停止|结束).{0,6}(语音回复模式|语音模式|语音回答模式).*");
    private static final Pattern WEATHER = Pattern.compile(
            ".*(天气|气温|温度|下雨|下雪|台风|晴天|阴天|雨天|雪天|有雨|有雪|风力|风速|紫外线|湿度).*");
    private static final Pattern ROUTE_MAP = Pattern.compile(
            ".*(路线图|行程图|导览图|攻略图|旅行路线|旅游路线|行程规划|旅游规划|旅行规划).*(生成|画|绘制|做|制作|出|图片|图).*"
                    + "|.*(生成|画|绘制|做|制作|出).*(路线图|行程图|导览图|攻略图).*");
    private static final Pattern IMAGE_NOUN = Pattern.compile(
            ".*(图片|图像|图案|照片|头像|海报|插画|壁纸|表情包|封面|logo|Logo|LOGO).*");
    private static final Pattern IMAGE_ACTION = Pattern.compile(
            ".*(生成|画|绘制|做一张|做个|制作|设计|创作|创建|出一张|来一张|给我一张).*");
    private static final Pattern FILE_ACTION = Pattern.compile(
            ".*(生成|创建|新建|制作|做一份|做一个|写一份|写一个|整理成|导出|输出|转成|转为|转换成|转换为|保存为|导出为).*");
    private static final Pattern FILE_NOUN_OR_FORMAT = Pattern.compile(
            ".*(文件|文档|表格|报告|日报|周报|月报|简历|合同|计划书|方案|纪要|清单|模板|Word|word|DOCX|docx|PDF|pdf|Excel|excel|XLSX|xlsx|CSV|csv|JSON|json|Markdown|markdown|MD|md|TXT|txt).*");

    public BotIntent classify(String text) {
        String value = normalize(text);
        if (value.isEmpty()) {
            return BotIntent.CHAT;
        }
        if (VOICE_MODE_ON.matcher(value).matches()) {
            return BotIntent.VOICE_MODE_ON;
        }
        if (VOICE_MODE_OFF.matcher(value).matches()) {
            return BotIntent.VOICE_MODE_OFF;
        }
        if (ROUTE_MAP.matcher(value).matches()) {
            return BotIntent.ROUTE_MAP;
        }
        if (WEATHER.matcher(value).matches()) {
            return BotIntent.WEATHER;
        }
        if (isFileGeneration(value)) {
            return BotIntent.FILE_GENERATION;
        }
        if (isImageGeneration(value)) {
            return BotIntent.IMAGE_GENERATION;
        }
        return BotIntent.CHAT;
    }

    public String buildImagePrompt(String text) {
        String value = text == null ? "" : text.trim();
        if (value.isBlank()) {
            return "一张清晰、美观的图片";
        }
        return value
                .replaceAll("^(请|麻烦|帮我|给我|可以|能不能|能|帮忙)", "")
                .replaceAll("(生成|画|绘制|做一张|做个|制作|设计|创作|创建|出一张|来一张|给我一张)", "")
                .trim();
    }

    private boolean isImageGeneration(String text) {
        if (text.contains("路线图") || text.contains("行程图") || text.contains("导览图")) {
            return false;
        }
        return IMAGE_ACTION.matcher(text).matches() && IMAGE_NOUN.matcher(text).matches();
    }

    private boolean isFileGeneration(String text) {
        if (text.contains("图片") || text.contains("图像") || text.contains("海报") || text.contains("路线图")
                || text.contains("行程图") || text.contains("导览图")) {
            return false;
        }
        boolean hasFileAction = FILE_ACTION.matcher(text).matches();
        boolean hasFileNounOrFormat = FILE_NOUN_OR_FORMAT.matcher(text).matches();
        return hasFileAction && hasFileNounOrFormat;
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("[\\s，。！？,.?；;：:、]+", "").trim();
    }
}
