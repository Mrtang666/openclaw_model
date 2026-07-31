package com.example.spring.wechat.conversation.tools;

import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.wechat.email.model.EmailMessage;
import com.example.spring.wechat.email.model.EmailSendResult;
import com.example.spring.wechat.email.service.EmailService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailWechatToolTests {

    @Test
    void exposesFunctionCallingDefinition() {
        EmailWechatTool tool = new EmailWechatTool(mock(EmailService.class));

        assertThat(tool.name()).isEqualTo("email_send");
        assertThat(tool.arguments()).containsExactly("to", "subject", "body", "cc", "bcc", "confirm_token");
        assertThat(tool.parameters())
                .filteredOn(parameter -> parameter.name().equals("to"))
                .singleElement()
                .satisfies(parameter -> assertThat(parameter.required()).isTrue());
        assertThat(tool.capability().summary()).isNotBlank();
        assertThat(tool.capability().boundaries()).isEmpty();
        assertThat(tool.capability().requiredInformation()).isEmpty();
        assertThat(tool.capability().outputs()).isEmpty();
    }

    @Test
    void forwardsEmailArgumentsToService() {
        EmailService service = mock(EmailService.class);
        when(service.sendOrStage(eq("session-1"), any(EmailMessage.class), eq("")))
                .thenReturn(EmailSendResult.sent("邮件已发送"));
        EmailWechatTool tool = new EmailWechatTool(service);

        WechatReply reply = tool.execute(request(Map.of(
                "to", "to@example.com, second@example.com",
                "subject", "Subject",
                "body", "Body",
                "cc", "cc@example.com",
                "bcc", "bcc@example.com")));

        ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(service).sendOrStage(eq("session-1"), captor.capture(), eq(""));
        EmailMessage message = captor.getValue();
        assertThat(message.to()).containsExactly("to@example.com", "second@example.com");
        assertThat(message.subject()).isEqualTo("Subject");
        assertThat(message.body()).isEqualTo("Body");
        assertThat(message.cc()).containsExactly("cc@example.com");
        assertThat(message.bcc()).containsExactly("bcc@example.com");
        assertThat(reply.text()).isEqualTo("邮件已发送");
    }

    @Test
    void forwardsConfirmationTokenToService() {
        EmailService service = mock(EmailService.class);
        when(service.sendOrStage(eq("session-1"), any(EmailMessage.class), eq("token-1")))
                .thenReturn(EmailSendResult.sent("邮件已发送"));
        EmailWechatTool tool = new EmailWechatTool(service);

        WechatReply reply = tool.execute(request(Map.of("confirm_token", " token-1 ")));

        verify(service).sendOrStage(eq("session-1"), any(EmailMessage.class), eq("token-1"));
        assertThat(reply.text()).isEqualTo("邮件已发送");
    }

    private static WechatToolRequest request(Map<String, String> arguments) {
        return new WechatToolRequest("session-1", "", arguments, "", List.of(), null, null);
    }
}
