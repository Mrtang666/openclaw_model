package com.example.wechat.agent;

import com.example.wechat.memory.ConversationMemory;
import com.example.wechat.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Component
public class AgentRouter {

    private final Map<String, Tool> tools = new HashMap<>();
    private final ConversationMemory memory;
    private final List<RouteRule> routeRules = new ArrayList<>();

    public AgentRouter(ConversationMemory memory, List<Tool> toolList) {
        this.memory = memory;
        for (Tool tool : toolList) {
            tools.put(tool.getName(), tool);
            log.info("✅ 注册工具: {}", tool.getName());
        }
        initRouteRules();
    }

    private void initRouteRules() {
        // ========== 1. TTS语音合成 - 优先级最高 ==========
        routeRules.add(new RouteRule(
                Pattern.compile("(语音形式|语音回复|用语音|说给我听|读给我听|语音播报|语音输出)"),
                "text_to_speech"
        ));

        // ========== 2. 文生图 ==========
        routeRules.add(new RouteRule(
                Pattern.compile("(生成|画|绘|制作|帮我画|给我画|来一张|来幅|创作).{0,10}(图片|画|图|小猫|小狗|猫|狗|风景|美女|帅哥|山水|人物|动物|卡通|动漫|油画|水彩|素描|插画|海报|壁纸|头像|logo|设计)"),
                "text_to_image"
        ));
        // 更精确的文生图关键词
        routeRules.add(new RouteRule(
                Pattern.compile("文生图|AI绘画|AI画图|生成图片|生成图像|画一张|画一幅|画个|画只|帮我画|给我画|来张|来幅"),
                "text_to_image"
        ));

        // ========== 3. 天气查询 ==========
        routeRules.add(new RouteRule(
                Pattern.compile(".*(天气|气温|温度|预报|下雨|下雪|刮风|阴|晴|台风|空气质量).*"),
                "query_weather"
        ));

        // ========== 4. 图生文 - 由图片消息自动触发，不需要关键词路由 ==========
        // 图生文在 WeChatMessageHandler 中通过检测 imageData 自动触发

        // OCR PDF识别
        routeRules.add(new RouteRule(
                Pattern.compile("(识别|解析|读取|提取).{0,5}(pdf|PDF|文档|文件)"),
                "ocr_pdf"
        ));

        // PDF生成 - 放在前面优先匹配
        routeRules.add(new RouteRule(
                Pattern.compile("(生成|导出|制作).{0,5}(pdf|PDF|文档|文件)"),
                "generate_pdf"
        ));
        routeRules.add(new RouteRule(
                Pattern.compile("(生成|导出).{0,5}(报告|总结|文档)"),
                "generate_pdf"
        ));
    }

    public RouteResult route(String userId, String userInput) {
        log.info("路由用户输入: {}", userInput);

        // 按顺序匹配规则
        for (RouteRule rule : routeRules) {
            if (rule.pattern.matcher(userInput).find()) {
                String toolName = rule.toolName;
                Tool tool = tools.get(toolName);
                if (tool != null) {
                    log.info("✅ 匹配到工具: {}", toolName);
                    return new RouteResult(toolName, tool, extractParams(userInput, toolName));
                }
            }
        }

        // 默认走聊天
        log.info("未匹配到工具，走聊天模式");
        return new RouteResult("chat", null, Collections.emptyMap());
    }

    public Tool getTool(String toolName) {
        return tools.get(toolName);
    }

    private Map<String, Object> extractParams(String input, String toolName) {
        Map<String, Object> params = new HashMap<>();

        switch (toolName) {
            case "query_weather":
                String city = extractCity(input);
                params.put("city", city != null ? city : "北京");
                break;

            case "text_to_image":
                String prompt = extractImagePrompt(input);
                if (prompt == null || prompt.isEmpty()) {
                    prompt = "一只可爱的小猫";
                }
                params.put("prompt", prompt);
                log.info("提取到文生图提示词: {}", prompt);
                break;

            case "text_to_speech":
                String ttsText = extractTtsText(input);
                if (ttsText == null || ttsText.isEmpty()) {
                    // 如果没有提取到具体文本，去除触发词后使用完整输入
                    ttsText = input.replaceAll("(?i)(语音形式|语音回复|用语音|说给我听|读给我听|语音播报|语音输出)", "").trim();
                }
                if (ttsText == null || ttsText.isEmpty()) {
                    ttsText = "你好，我是你的智能语音助手。";
                }
                params.put("text", ttsText);
                log.info("提取到TTS合成文本: {}", ttsText);
                break;
        }
        return params;
    }

    /**
     * 从输入中提取城市名
     */
    private String extractCity(String input) {
        String[] cities = {"北京", "上海", "广州", "深圳", "杭州", "成都", "武汉", "南京", "天津",
                "重庆", "西安", "长沙", "郑州", "青岛", "大连", "厦门", "苏州", "宁波", "无锡",
                "佛山", "东莞", "沈阳", "昆明", "济南", "福州", "合肥", "南昌", "南宁", "贵阳",
                "兰州", "银川", "西宁", "拉萨", "乌鲁木齐", "呼和浩特", "哈尔滨", "长春",
                "海口", "三亚", "台北", "香港", "澳门"};

        for (String city : cities) {
            if (input.contains(city)) {
                return city;
            }
        }
        return null;
    }

    /**
     * 从输入中提取文生图提示词
     */
    private String extractImagePrompt(String input) {
        // 移除各种触发词
        String cleaned = input.replaceAll("(?i)(生成|画|绘|制作|帮我画|给我画|来一张|来幅|创作|文生图|AI绘画|AI画图|生成图片|生成图像|画一张|画一幅|画个|画只|帮我|给我|来张|来幅)\\s*", "");
        // 移除标点
        cleaned = cleaned.replaceAll("[，,。.!！?？、]", "").trim();
        // 如果去除后为空，使用默认描述
        if (cleaned.isEmpty()) {
            return null;
        }
        return cleaned;
    }

    /**
     * 从输入中提取TTS要合成的文本
     */
    private String extractTtsText(String input) {
        // 去除触发词
        String cleaned = input.replaceAll("(?i)(语音形式|语音回复|用语音|说给我听|读给我听|语音播报|语音输出)", "").trim();
        // 去除标点符号开头
        cleaned = cleaned.replaceAll("^[，,。.、：:]", "").trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        return cleaned;
    }

    /**
     * 路由规则内部类
     */
    private static class RouteRule {
        final Pattern pattern;
        final String toolName;

        RouteRule(Pattern pattern, String toolName) {
            this.pattern = pattern;
            this.toolName = toolName;
        }
    }

    /**
     * 路由结果类
     */
    public static class RouteResult {
        private final String toolName;
        private final Tool tool;
        private final Map<String, Object> params;

        public RouteResult(String toolName, Tool tool, Map<String, Object> params) {
            this.toolName = toolName;
            this.tool = tool;
            this.params = params;
        }

        public String getToolName() { return toolName; }
        public Tool getTool() { return tool; }
        public Map<String, Object> getParams() { return params; }
        public boolean isChat() { return tool == null; }
    }
}