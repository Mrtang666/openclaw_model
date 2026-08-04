package com.example.spring.wechat.conversation.agent.policy;

import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.wechat.image.generation.model.ImageGenerationResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ToolCapabilityPolicyTests {

    private final ToolCapabilityPolicy policy = new ToolCapabilityPolicy();

    @Test
    void identifiesTerminalActionTools() {
        assertThat(policy.endsAgentTurnAfterExecution("taxi_service")).isTrue();
        assertThat(policy.endsAgentTurnAfterExecution("reminder_create_after")).isTrue();
        assertThat(policy.endsAgentTurnAfterExecution("food_delivery")).isTrue();
        assertThat(policy.endsAgentTurnAfterExecution("meituan_travel")).isTrue();
        assertThat(policy.endsAgentTurnAfterExecution("email_text_send")).isTrue();
        assertThat(policy.endsAgentTurnAfterExecution("browser_screenshot")).isTrue();
        assertThat(policy.endsAgentTurnAfterExecution("care_agent")).isTrue();

        assertThat(policy.endsAgentTurnAfterExecution("web_search")).isFalse();
        assertThat(policy.endsAgentTurnAfterExecution("")).isFalse();
        assertThat(policy.endsAgentTurnAfterExecution(null)).isFalse();
    }

    @Test
    void classifiesToolFailureReplies() {
        assertThat(policy.isFailureReply("map_search", "地图查询失败：地点存在歧义")).isTrue();
        assertThat(policy.isFailureReply("reminder_create", "提醒操作未完成：缺少时间")).isTrue();
        assertThat(policy.isFailureReply("reminder_snooze", "提醒操作未完成：找不到最近提醒")).isTrue();

        assertThat(policy.isFailureReply("map_search", "地图查询成功：已找到地点")).isFalse();
        assertThat(policy.isFailureReply("weather", "提醒操作未完成：模拟文本")).isFalse();
        assertThat(policy.isFailureReply(null, null)).isFalse();
    }

    @Test
    void keepsMapImageAndTextVisiblePartsWhenMapReturnsImage() {
        WechatReply.Part text = WechatReply.Part.text("路线说明");
        WechatReply.Part blankText = WechatReply.Part.text(" ");
        WechatReply.Part image = WechatReply.Part.image("地图图片", image("map.png"));
        WechatReply.Part voice = WechatReply.Part.voice(voice());

        List<WechatReply.Part> visible = policy.visibleParts(
                "map_search",
                List.of(text, blankText, image, voice));

        assertThat(visible).containsExactly(text, image);
    }

    @Test
    void keepsOnlyMediaPartsForMediaTools() {
        WechatReply.Part text = WechatReply.Part.text("纯文本说明");
        WechatReply.Part image = WechatReply.Part.image("图片", image("cat.png"));
        WechatReply.Part voice = WechatReply.Part.voice(voice());
        WechatReply.Part file = WechatReply.Part.file(file());

        assertThat(policy.visibleParts("image_generation", List.of(text, image)))
                .containsExactly(image);
        assertThat(policy.visibleParts("voice_synthesis", List.of(text, voice)))
                .containsExactly(voice);
        assertThat(policy.visibleParts("document_generation", List.of(text, file)))
                .containsExactly(file);
        assertThat(policy.visibleParts("browser_screenshot", List.of(text, image)))
                .containsExactly(image);
    }

    @Test
    void hidesVisiblePartsForOrdinaryTools() {
        assertThat(policy.visibleParts("weather", List.of(
                WechatReply.Part.text("天气很好"),
                WechatReply.Part.image("图片", image("weather.png")))))
                .isEmpty();
        assertThat(policy.visibleParts(null, null)).isEmpty();
    }

    @Test
    void rendersRuntimeRulesOnlyForAvailableTools() {
        String mapAndEmailRules = policy.runtimeRules(Set.of("map_search", "email_text_send"));

        assertThat(mapAndEmailRules).contains("地图规则");
        assertThat(mapAndEmailRules).contains("邮件工具规则");
        assertThat(mapAndEmailRules).doesNotContain("旅行工具规则");
        assertThat(mapAndEmailRules).doesNotContain("照护工具规则");

        String reminderRules = policy.runtimeRules(Set.of("reminder_create_after", "reminder_snooze"));
        assertThat(reminderRules).contains("必须调用 reminder_create_after");
        assertThat(reminderRules).contains("调用 reminder_snooze");
    }

    private ImageGenerationResult image(String fileName) {
        return new ImageGenerationResult("prompt", "", new byte[]{1}, fileName, "image/png", 1, 1);
    }

    private WechatReply.Voice voice() {
        return new WechatReply.Voice(new byte[]{1}, "reply.silk", 1000, 16000, 6, 16, "你好");
    }

    private WechatReply.FileAttachment file() {
        return new WechatReply.FileAttachment(new byte[]{1}, "report.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "报告");
    }
}
