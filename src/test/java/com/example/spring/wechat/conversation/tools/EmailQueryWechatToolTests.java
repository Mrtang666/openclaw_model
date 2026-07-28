package com.example.spring.wechat.conversation.tools;

import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.wechat.email.config.EmailProperties;
import com.example.spring.wechat.email.model.DownloadedEmailAttachment;
import com.example.spring.wechat.email.model.EmailMessageDetail;
import com.example.spring.wechat.email.model.EmailMessageSummary;
import com.example.spring.wechat.email.service.EmailReceiveService;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EmailQueryWechatToolTests {

    @Test
    void listsRecentEmailsAndCachesOrdinal() {
        StubEmailReceiveService service = new StubEmailReceiveService();
        EmailQueryWechatTool tool = new EmailQueryWechatTool(service);

        WechatReply reply = tool.execute(request("查最近邮件", Map.of("action", "list")));
        assertThat(reply.text()).contains("最近邮件", "测试邮件", "编号：1001", "读取第一封");

        WechatReply detail = tool.execute(request("读取第一封", Map.of("action", "read", "mail_index", "1")));
        assertThat(detail.text()).contains("邮件内容", "这是一封测试邮件正文");
    }

    @Test
    void downloadsAttachmentsAsWechatFileParts() {
        StubEmailReceiveService service = new StubEmailReceiveService();
        EmailQueryWechatTool tool = new EmailQueryWechatTool(service);

        tool.execute(request("查最近邮件", Map.of("action", "list")));
        WechatReply reply = tool.execute(request("下载第一封附件", Map.of("action", "download_attachments", "mail_index", "1")));

        assertThat(reply.parts()).hasSize(2);
        assertThat(reply.parts().get(0).text()).contains("已下载邮件附件");
        assertThat(reply.parts().get(1).hasFile()).isTrue();
        assertThat(reply.parts().get(1).file().fileName()).isEqualTo("report.pdf");
    }

    private WechatToolRequest request(String text, Map<String, String> arguments) {
        return new WechatToolRequest(
                "user-1",
                text,
                arguments,
                "",
                List.of(),
                null,
                null);
    }

    private static final class StubEmailReceiveService extends EmailReceiveService {

        private StubEmailReceiveService() {
            super(new EmailProperties());
        }

        @Override
        public int defaultLimit() {
            return 10;
        }

        @Override
        public List<EmailMessageSummary> listRecent(int limit) {
            return List.of(summary());
        }

        @Override
        public EmailMessageDetail read(String uid) {
            return new EmailMessageDetail(
                    uid,
                    "sender@qq.com",
                    List.of("user@qq.com"),
                    "测试邮件",
                    Instant.parse("2026-07-27T08:00:00Z"),
                    true,
                    1,
                    "这是一封测试邮件正文。");
        }

        @Override
        public List<DownloadedEmailAttachment> downloadAttachments(String uid, Integer attachmentIndex) {
            return List.of(new DownloadedEmailAttachment(
                    uid,
                    "report.pdf",
                    "application/pdf",
                    Path.of("data", "downloads", "email", "attachments", "report.pdf"),
                    "pdf-content".getBytes(StandardCharsets.UTF_8),
                    128));
        }

        private EmailMessageSummary summary() {
            return new EmailMessageSummary(
                    "1001",
                    "sender@qq.com",
                    "测试邮件",
                    Instant.parse("2026-07-27T08:00:00Z"),
                    true,
                    true,
                    1,
                    "这是一封测试邮件正文。");
        }
    }
}
