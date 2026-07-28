package com.example.spring.wechat.conversation.tools;

import com.example.spring.tool.protocol.function.FunctionCallingToolCall;
import com.example.spring.tool.protocol.validation.ToolCallValidator;
import com.example.spring.wechat.reminder.model.ReminderRepeatType;
import com.example.spring.wechat.reminder.model.ReminderStatus;
import com.example.spring.wechat.reminder.model.ReminderTask;
import com.example.spring.wechat.reminder.service.ReminderService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReminderCreateWechatToolTests {

    @Test
    void requiresTitleAndExecuteAtInFunctionCallingSchema() {
        ReminderCreateWechatTool tool = new ReminderCreateWechatTool(mock(ReminderService.class));
        WechatToolDefinition definition = new WechatToolDefinition(
                tool.name(), tool.description(), tool.parameters(), tool.capability());

        assertThat(new ToolCallValidator().validate(
                new FunctionCallingToolCall("call-1", "reminder_create", Map.of("title", "取快递")),
                List.of(definition)).valid()).isFalse();
    }

    @Test
    void createsReminderThroughServiceAndReturnsTaskNumber() {
        ReminderService service = mock(ReminderService.class);
        when(service.create(any())).thenReturn(task());
        ReminderCreateWechatTool tool = new ReminderCreateWechatTool(service);

        var reply = tool.execute(new WechatToolRequest(
                "clawbot:connection-1:wechat-user-1",
                "半小时后提醒我取快递",
                Map.of("title", "取快递", "execute_at", "2026-07-27T19:30:00+08:00"),
                "", null, null));

        assertThat(reply.text()).contains("已创建提醒 #7").contains("取快递");
    }

    private ReminderTask task() {
        Instant now = Instant.parse("2026-07-27T11:30:00Z");
        return new ReminderTask(
                7L, null, "clawbot:connection-1:wechat-user-1", "connection-1", "wechat-user-1",
                "取快递", "", ReminderRepeatType.ONCE, "Asia/Shanghai", now,
                ReminderStatus.ACTIVE, 0, 3, null, "", null, now, now);
    }
}
