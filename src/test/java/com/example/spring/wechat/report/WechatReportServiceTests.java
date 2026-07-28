package com.example.spring.wechat.report;

import com.example.spring.wechat.bot.WechatReply;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class WechatReportServiceTests {

    @TempDir
    Path tempDir;

    @Test
    void keepsShortReplyAsPlainText() {
        WechatReportService reportService = new WechatReportService(properties());
        WechatReplyPresentationService presentationService = new WechatReplyPresentationService(reportService);

        WechatReply reply = presentationService.enhance(WechatReply.text("今天天气不错。"));

        assertThat(reply.text()).isEqualTo("今天天气不错。");
    }

    @Test
    void convertsLongStructuredReplyToSummaryAndReportLink() throws Exception {
        WechatReportService reportService = new WechatReportService(properties());
        WechatReplyPresentationService presentationService = new WechatReplyPresentationService(reportService);

        WechatReply reply = presentationService.enhance(WechatReply.text("""
                杭州出行助手报告：
                1. 上午天气晴朗，适合出行
                2. 中午紫外线偏强，建议防晒
                3. 下午可能拥堵，建议提前出发
                4. 晚上气温下降，建议带外套
                5. 推荐餐厅 A
                6. 推荐餐厅 B
                """));

        assertThat(reply.parts()).hasSize(2);
        assertThat(reply.parts().get(0).text()).contains("杭州出行助手报告");
        assertThat(reply.parts().get(1).text()).contains("📄 查看美化版报告", "https://example.com/r/");
        assertThat(reply.parts().get(1).text()).doesNotContain("](https://example.com/r/");
        try (var paths = Files.list(tempDir)) {
            assertThat(paths.map(path -> path.getFileName().toString()).toList())
                    .anyMatch(fileName -> fileName.endsWith(".html"));
        }
    }

    @Test
    void explicitReportRequestUsesSummaryAndReportLinkOnly() {
        WechatReportService reportService = new WechatReportService(properties());
        WechatReplyPresentationService presentationService = new WechatReplyPresentationService(reportService);

        WechatReply reply = presentationService.enhance(WechatReply.text("""
                出行完整报告：
                1. 上午天气晴朗，适合出行
                2. 中午紫外线偏强，建议防晒
                3. 下午可能拥堵，建议提前出发
                4. 晚上气温下降，建议带外套
                5. 推荐餐厅 A
                6. 推荐餐厅 B
                """), "帮我生成完整报告");

        assertThat(reply.parts()).hasSize(2);
        assertThat(reply.parts().get(0).text()).contains("📌 摘要");
        assertThat(reply.parts().get(1).text()).contains("📄 查看完整报告", "https://example.com/r/");
        assertThat(reply.parts().get(1).text()).doesNotContain("](https://example.com/r/");
    }

    @Test
    void storesAndFindsGeneratedReport() {
        WechatReportService reportService = new WechatReportService(properties());

        WechatReport report = reportService.create("测试报告", "完整内容");

        assertThat(reportService.find(report.id())).isPresent();
        assertThat(Files.exists(report.path())).isTrue();
    }

    private WechatReportProperties properties() {
        WechatReportProperties properties = new WechatReportProperties();
        properties.setStorageDir(tempDir);
        properties.setPublicBaseUrl("https://example.com");
        properties.setItemCountThreshold(5);
        properties.setTextLengthThreshold(900);
        return properties;
    }
}
