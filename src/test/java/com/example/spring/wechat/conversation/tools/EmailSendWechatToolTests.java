package com.example.spring.wechat.conversation.tools;

import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.wechat.email.EmailToolException;
import com.example.spring.wechat.email.config.EmailProperties;
import com.example.spring.wechat.email.model.EmailSendRequest;
import com.example.spring.wechat.email.model.EmailSendResult;
import com.example.spring.wechat.email.model.PreparedEmailAttachment;
import com.example.spring.wechat.email.service.EmailSendService;
import com.example.spring.wechat.model.WechatIncomingFile;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EmailSendWechatToolTests {

    @Test
    void parsesWechatTextAndUsesIncomingFileForConfirmation() {
        StubEmailSendService service = new StubEmailSendService();
        EmailSendWechatTool tool = new EmailSendWechatTool(service, Duration.ofMinutes(10));

        WechatReply reply = tool.execute(new WechatToolRequest(
                "user-1",
                """
                        把这个文件发送到
                        someone@qq.com
                        主题是测试邮件
                        正文就是这个文件
                        """,
                Map.of(),
                "",
                List.of(),
                List.of(file("报告.pdf")),
                null,
                null));

        assertThat(reply.text()).contains("准备发送邮件", "someone@qq.com", "测试邮件", "报告.pdf");
        WechatReply confirmed = tool.execute(request("user-1", "确认发送", Map.of()));

        assertThat(confirmed.text()).contains("邮件已发送", "someone@qq.com", "测试邮件");
        assertThat(service.lastRequest.body()).isEqualTo("附件已随邮件发送，请查收。");
    }

    @Test
    void keepsPendingSendWhenSmtpFailsSoUserCanRetry() {
        StubEmailSendService service = new StubEmailSendService();
        service.failSend = true;
        EmailSendWechatTool tool = new EmailSendWechatTool(service, Duration.ofMinutes(10));

        tool.execute(new WechatToolRequest(
                "user-1",
                "把这个文件发到 someone@qq.com",
                Map.of("to", "someone@qq.com"),
                "",
                List.of(),
                List.of(file("报告.pdf")),
                null,
                null));

        WechatReply failed = tool.execute(request("user-1", "确认发送", Map.of()));
        assertThat(failed.text()).contains("邮件发送失败", "重新发送", "取消发送");

        service.failSend = false;
        WechatReply retried = tool.execute(request("user-1", "重新发送", Map.of()));

        assertThat(retried.text()).contains("邮件已发送");
        assertThat(service.sendCount).isEqualTo(2);
    }

    private WechatToolRequest request(String sessionKey, String text, Map<String, String> arguments) {
        return new WechatToolRequest(
                sessionKey,
                text,
                arguments,
                "",
                List.of(),
                null,
                null);
    }

    private WechatIncomingFile file(String fileName) {
        return new WechatIncomingFile(
                "wechat://file/1",
                fileName,
                "application/pdf",
                "pdf-content".getBytes(StandardCharsets.UTF_8),
                null,
                null,
                null);
    }

    private static final class StubEmailSendService extends EmailSendService {

        private EmailSendRequest lastRequest;
        private boolean failSend;
        private int sendCount;

        private StubEmailSendService() {
            super(new EmailProperties());
        }

        @Override
        public PreparedEmailAttachment prepareIncomingFile(String sessionKey, WechatIncomingFile file) {
            return new PreparedEmailAttachment(
                    Path.of("data", "email", "attachments", file.fileName()),
                    file.fileName(),
                    file.size(),
                    false);
        }

        @Override
        public EmailSendResult send(EmailSendRequest request) {
            sendCount++;
            lastRequest = request;
            if (failSend) {
                throw new EmailToolException("邮件发送失败：SMTP 登录失败");
            }
            return new EmailSendResult(request.to(), request.subject(), request.attachmentPath().getFileName().toString(), 128);
        }
    }
}
