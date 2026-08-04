package com.example.spring.wechat.conversation.agent.policy;

import com.example.spring.wechat.bot.WechatReply;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class ToolCapabilityPolicy {

    private static final Set<String> TERMINAL_ACTION_TOOLS = Set.of(
            "taxi_service",
            "reminder_create",
            "reminder_create_after",
            "reminder_update",
            "reminder_cancel",
            "reminder_complete",
            "reminder_snooze",
            "food_delivery",
            "meituan_travel",
            "email_send",
            "email_text_send",
            "browser_screenshot",
            "care_agent");

    private static final Set<String> MEDIA_OUTPUT_TOOLS = Set.of(
            "image_generation",
            "voice_synthesis",
            "document_generation",
            "browser_screenshot");

    public boolean endsAgentTurnAfterExecution(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return false;
        }
        return TERMINAL_ACTION_TOOLS.contains(toolName);
    }

    public boolean isFailureReply(String toolName, String modelText) {
        if (toolName == null || toolName.isBlank() || modelText == null) {
            return false;
        }
        if ("map_search".equals(toolName) && modelText.startsWith("地图查询失败：")) {
            return true;
        }
        return toolName.startsWith("reminder_")
                && modelText.startsWith("提醒操作未完成：");
    }

    public List<WechatReply.Part> visibleParts(String toolName, List<WechatReply.Part> parts) {
        if (parts == null || parts.isEmpty()) {
            return List.of();
        }
        if ("map_search".equals(toolName)
                && parts.stream().anyMatch(part -> part != null && part.hasImage())) {
            return parts.stream()
                    .filter(part -> part != null && (part.hasImage()
                            || (part.text() != null && !part.text().isBlank())))
                    .toList();
        }
        if (!MEDIA_OUTPUT_TOOLS.contains(toolName)) {
            return List.of();
        }
        return parts.stream()
                .filter(part -> part != null && (part.hasImage() || part.hasVoice() || part.hasFile()))
                .toList();
    }

    public String runtimeRules(Set<String> availableToolNames) {
        if (availableToolNames == null || availableToolNames.isEmpty()) {
            return "";
        }
        StringBuilder rules = new StringBuilder();
        if (hasTool(availableToolNames, "reminder_create_after")) {
            rules.append("""

                    - 用户说“几分钟后”“几小时后”或“几天后”时，必须调用 reminder_create_after，
                      原样提取 delay_value 和 delay_unit，禁止换算分钟或 execute_at。
                    """);
        }
        if (hasTool(availableToolNames, "reminder_create")) {
            rules.append("- 只有用户明确指定日期和钟点时才调用 reminder_create。")
                    .append(System.lineSeparator());
        }
        if (hasTool(availableToolNames, "reminder_snooze")) {
            rules.append("""
                    - 用户说“再提醒我”且没有指定原提醒编号或标题时，调用 reminder_snooze，
                      不传 reminder_id 和 title，由程序选择当前会话最近发送的提醒。
                    """);
        }
        if (hasTool(availableToolNames, "map_search")) {
            rules.append("""

                    地图规则：
                    - 如果地图工具提示地点存在歧义或需要补充地址，立即向用户确认，不要继续拆分调用地点详情来猜测。
                    """);
        }
        if (hasTool(availableToolNames, "knowledge_add", "knowledge_query")) {
            rules.append("""

                    知识库工具规则：
                    - 用户要求“记住、保存、加入知识库、以后参考”时，优先调用 knowledge_add；用户要求“根据知识库、保存过的资料、我的资料”回答时，优先调用 knowledge_query。
                    """);
        }
        if (hasTool(availableToolNames, "web_read", "web_search")) {
            rules.append("""

                    网页工具规则：
                    - 用户给出 URL 并要求阅读、总结或保存网页时，优先调用 web_read；用户要求查询最新资料、搜索互联网或找公开资料时，优先调用 web_search，必要时再对搜索结果中的 URL 调用 web_read。
                    """);
        }
        if (hasTool(availableToolNames, "meituan_travel")) {
            rules.append("""

                    旅行工具规则：
                    - 用户询问国内酒店、机票、火车票、景点门票、度假推荐或组合旅行规划时，优先调用 meituan_travel；缺少关键日期、城市或人数时先追问。
                    """);
        }
        if (hasTool(availableToolNames, "email_send", "email_text_send")) {
            rules.append("""

                    邮件工具规则：
                    - 邮件发送是具有外部副作用的工具；只有用户明确要求发送或确认发送邮件时才调用 email_send 或 email_text_send，意图不确定时先追问。
                    """);
        }
        if (hasTool(availableToolNames, "care_agent")) {
            rules.append("""

                    照护工具规则：
                    - 用户提到患者、家属、医生、照护、打卡、安全确认、患者状态、绑定患者、联系医生、制定患者方案时，必须优先调用 care_agent。
                    """);
        }
        return rules.toString();
    }

    private boolean hasTool(Set<String> availableToolNames, String... names) {
        if (availableToolNames == null || availableToolNames.isEmpty() || names == null) {
            return false;
        }
        for (String name : names) {
            if (name != null && availableToolNames.contains(name)) {
                return true;
            }
        }
        return false;
    }
}
