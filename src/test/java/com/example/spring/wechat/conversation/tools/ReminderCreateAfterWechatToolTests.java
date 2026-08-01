package com.example.spring.wechat.conversation.tools;

import com.example.spring.tool.protocol.function.FunctionCallingToolCall;
import com.example.spring.tool.protocol.validation.ToolCallValidator;
import com.example.spring.wechat.reminder.model.ReminderRepeatType;
import com.example.spring.wechat.reminder.model.ReminderStatus;
import com.example.spring.wechat.reminder.model.ReminderTask;
import com.example.spring.wechat.reminder.service.ReminderService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReminderCreateAfterWechatToolTests {

    @Test
    void schemaRequiresDelayValueAndUnitAndDoesNotExposeExecuteAt() {
        ReminderCreateAfterWechatTool tool = new ReminderCreateAfterWechatTool(mock(ReminderService.class));
        WechatToolDefinition definition = new WechatToolDefinition(
                tool.name(), tool.description(), tool.parameters(), tool.capability());

        assertThat(tool.parameters()).extracting(WechatToolParameter::name)
                .contains("delay_value", "delay_unit")
                .doesNotContain("delay_minutes")
                .doesNotContain("execute_at");
        assertThat(new ToolCallValidator().validate(
                new FunctionCallingToolCall("call-1", "reminder_create_after", Map.of("title", "喝水")),
                List.of(definition)).valid()).isFalse();
    }

    @Test
    void passesRelativeDelayToServiceWithoutAnAbsoluteTime() {
        ReminderService service = mock(ReminderService.class);
        when(service.createAfter(any())).thenReturn(task());
        ReminderCreateAfterWechatTool tool = new ReminderCreateAfterWechatTool(service);

        var reply = tool.execute(new WechatToolRequest(
                "clawbot:connection-1:wechat-user-1",
                "两分钟后提醒我去喝水",
                Map.of(
                        "title", "喝水",
                        "content", "去喝一杯水",
                        "delay_value", "2",
                        "delay_unit", "minutes"),
                "", null, null));

        ArgumentCaptor<ReminderService.CreateAfterCommand> commandCaptor =
                ArgumentCaptor.forClass(ReminderService.CreateAfterCommand.class);
        verify(service).createAfter(commandCaptor.capture());
        assertThat(commandCaptor.getValue().delayValue()).isEqualTo(2L);
        assertThat(commandCaptor.getValue().delayUnit()).isEqualTo("minutes");
        assertThat(reply.text()).contains("已创建提醒 #8").contains("喝水");
    }

    private ReminderTask task() {
        Instant now = Instant.parse("2026-07-27T08:00:00Z");
        return new ReminderTask(
                8L, null, "clawbot:connection-1:wechat-user-1", "connection-1", "wechat-user-1",
                "喝水", "去喝一杯水", ReminderRepeatType.ONCE, "Asia/Shanghai",
                Instant.parse("2026-07-27T08:02:00Z"), ReminderStatus.ACTIVE,
                0, 3, null, "", null, now, now);
    }
}
