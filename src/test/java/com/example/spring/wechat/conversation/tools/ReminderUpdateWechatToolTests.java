package com.example.spring.wechat.conversation.tools;

import com.example.spring.wechat.reminder.model.ReminderRepeatType;
import com.example.spring.wechat.reminder.model.ReminderStatus;
import com.example.spring.wechat.reminder.model.ReminderTask;
import com.example.spring.wechat.reminder.service.ReminderService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReminderUpdateWechatToolTests {

    @Test
    void passesTitleSelectorAndStrongRelativeDelayToService() {
        ReminderService service = mock(ReminderService.class);
        when(service.update(any())).thenReturn(task());
        ReminderUpdateWechatTool tool = new ReminderUpdateWechatTool(service);

        var reply = tool.execute(new WechatToolRequest(
                "clawbot:connection-1:wechat-user-1",
                "把交水费提醒改到两小时后",
                Map.of(
                        "current_title", "交水费",
                        "delay_value", "2",
                        "delay_unit", "hours"),
                "", null, null));

        ArgumentCaptor<ReminderService.UpdateCommand> command =
                ArgumentCaptor.forClass(ReminderService.UpdateCommand.class);
        verify(service).update(command.capture());
        assertThat(command.getValue().currentTitle()).isEqualTo("交水费");
        assertThat(command.getValue().delayValue()).isEqualTo(2L);
        assertThat(command.getValue().delayUnit()).isEqualTo("hours");
        assertThat(reply.text()).contains("已修改提醒 #12");
    }

    private ReminderTask task() {
        Instant now = Instant.parse("2026-07-28T02:00:00Z");
        return new ReminderTask(
                12L, null, "clawbot:connection-1:wechat-user-1", "connection-1", "wechat-user-1",
                "交水费", "", ReminderRepeatType.ONCE, "Asia/Shanghai",
                now.plusSeconds(7_200), ReminderStatus.ACTIVE, 0, 3,
                null, "", null, now, now);
    }
}
