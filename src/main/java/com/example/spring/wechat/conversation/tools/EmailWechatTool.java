package com.example.spring.wechat.conversation.tools;

import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.wechat.email.model.EmailMessage;
import com.example.spring.wechat.email.model.EmailSendResult;
import com.example.spring.wechat.email.service.EmailService;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class EmailWechatTool implements WechatTool {

    private final EmailService emailService;

    public EmailWechatTool(EmailService emailService) {
        this.emailService = emailService;
    }

    @Override
    public String name() {
        return "email_text_send";
    }

    @Override
    public String description() {
        return "通过 QQ 邮箱 SMTP 发送纯文本邮件；白名单收件人可直接发送，非白名单收件人必须先生成草稿并等待用户确认。";
    }

    @Override
    public List<String> arguments() {
        return List.of("to", "subject", "body", "cc", "bcc", "confirm_token");
    }

    @Override
    public List<WechatToolParameter> parameters() {
        return List.of(
                WechatToolParameter.requiredString(
                        "to",
                        "收件人邮箱地址列表，多个地址用逗号分隔；确认已有草稿时可以留空",
                        "friend@example.com"),
                WechatToolParameter.requiredString(
                        "subject",
                        "邮件主题；确认已有草稿时可以留空",
                        "会议纪要"),
                WechatToolParameter.requiredString(
                        "body",
                        "纯文本邮件正文；确认已有草稿时可以留空",
                        "你好，这是今天的会议纪要。"),
                WechatToolParameter.optionalString(
                        "cc",
                        "抄送邮箱地址列表，多个地址用逗号分隔",
                        "copy@example.com"),
                WechatToolParameter.optionalString(
                        "bcc",
                        "密送邮箱地址列表，多个地址用逗号分隔；除非用户明确要求，不要主动使用",
                        "hidden@example.com"),
                WechatToolParameter.optionalString(
                        "confirm_token",
                        "非白名单邮件草稿的确认令牌；只有用户明确确认发送时才传入",
                        "token-123"));
    }

    @Override
    public WechatToolCapability capability() {
        return new WechatToolCapability(
                "发送或准备邮件，只支持纯文本外发邮件。",
                List.of(
                        "只在用户明确要求发送或准备邮件时调用",
                        "不要编造收件人或邮箱地址",
                        "非白名单收件人必须先让用户确认",
                        "不读取收件箱，不发送附件，不发送 HTML 邮件",
                        "不发送凭证、验证码、私钥等敏感信息，除非用户明确提供确切内容和接收方"),
                List.of(
                        "缺少收件人、主题或正文时先追问",
                        "用户确认待发送草稿时传入 confirm_token"),
                List.of("发送结果、待确认草稿摘要或需要补充的信息"));
    }

    @Override
    public WechatReply execute(WechatToolRequest request) {
        EmailMessage message = new EmailMessage(
                parseAddresses(request.argument("to")),
                request.argument("subject"),
                request.argument("body"),
                parseAddresses(request.argument("cc")),
                parseAddresses(request.argument("bcc")));
        EmailSendResult result = emailService.sendOrStage(
                request.sessionKey(),
                message,
                request.argument("confirm_token"));
        return WechatReply.text(result.userMessage());
    }

    private List<String> parseAddresses(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .filter(address -> address != null && !address.isBlank())
                .map(String::strip)
                .toList();
    }
}
