package com.example.spring.wechat.report;

import com.example.spring.wechat.bot.WechatReply;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WechatReplyPresentationService {

    private final WechatReportService reportService;

    public WechatReplyPresentationService(WechatReportService reportService) {
        this.reportService = reportService;
    }

    public WechatReply enhance(WechatReply reply) {
        return enhance(reply, "");
    }

    public WechatReply enhance(WechatReply reply, String userText) {
        if (reply == null) {
            return WechatReply.text("");
        }
        if (reply.parts() != null && !reply.parts().isEmpty()) {
            return reply;
        }
        if (reply.hasImage()) {
            return reply;
        }
        String text = reply.text() == null ? "" : reply.text().strip();
        if (!reportService.shouldCreateReport(text)) {
            return reply;
        }

        WechatReport report = reportService.create(reportService.inferTitle(text), text);
        String summary = reportService.summaryForWechat(text);
        if (!reportService.shouldSendSummaryOnly(userText, text)) {
            return WechatReply.ordered(List.of(
                    WechatReply.Part.text(text),
                    WechatReply.Part.text(reportTitleText("📄 查看美化版报告", report)),
                    WechatReply.Part.text(report.url())));
        }
        return WechatReply.ordered(List.of(
                WechatReply.Part.text("""
                📌 摘要
                %s""".formatted(summary).strip()),
                WechatReply.Part.text(reportTitleText("📄 查看完整报告", report)),
                WechatReply.Part.text(report.url())));
    }

    private String reportTitleText(String label, WechatReport report) {
        return "%s：%s".formatted(label, report.title()).strip();
    }
}
