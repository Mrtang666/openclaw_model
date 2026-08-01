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

        assertThat(reply.parts()).hasSize(3);
        assertThat(reply.parts().get(0).text()).contains("杭州出行助手报告");
        assertThat(reply.parts().get(1).text()).contains("📄 查看美化版报告");
        assertThat(reply.parts().get(2).text()).startsWith("https://example.com/r/");
        assertThat(reply.parts().get(2).text()).doesNotContain("]");
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

        assertThat(reply.parts()).hasSize(3);
        assertThat(reply.parts().get(0).text()).contains("📌 摘要");
        assertThat(reply.parts().get(1).text()).contains("📄 查看完整报告");
        assertThat(reply.parts().get(2).text()).startsWith("https://example.com/r/");
        assertThat(reply.parts().get(2).text()).doesNotContain("]");
    }

    @Test
    void storesAndFindsGeneratedReport() {
        WechatReportService reportService = new WechatReportService(properties());

        WechatReport report = reportService.create("测试报告", "完整内容");

        assertThat(reportService.find(report.id())).isPresent();
        assertThat(Files.exists(report.path())).isTrue();
    }

    @Test
    void doesNotTurnMarkdownSectionHeadingIntoDetailCard() throws Exception {
        WechatReportService reportService = new WechatReportService(properties());

        WechatReport report = reportService.create("杭州菜推荐", """
                推荐菜品：
                1. **盐水鸭**
                2. **南京烤鸭**
                3. **清炖狮子头**
                4. **芦蒿炒香干**
                5. **美人肝**
                6. **炖生敲**

                **寻味建议**：
                建议优先选择老字号。
                """);

        String html = Files.readString(report.path());
        assertThat(html).contains("盐水鸭", "南京烤鸭");
        assertThat(html).doesNotContain("<div class=\"item\">寻味建议：</div>");
        assertThat(html).doesNotContain("**盐水鸭**");
    }

    @Test
    void keepsDishDescriptionButFiltersCategoryHeadings() throws Exception {
        WechatReportService reportService = new WechatReportService(properties());

        WechatReport report = reportService.create("无锡菜推荐", """
                **经典大菜与热炒**
                1. **无锡酱排骨**：无锡菜的名片，肉质酥烂脱骨，咸中带甜。
                2. **梁溪脆鳝**：鳝丝油炸后裹浓甜卤汁，口感酥脆。

                **必吃特色点心**
                1. **无锡小笼包**：皮薄馅多，汤汁偏甜。

                * 💡 寻味小贴士**：
                建议选择老字号。
                """);

        String html = Files.readString(report.path());
        assertThat(html).contains("<div class=\"item-title\">经典大菜与热炒：无锡酱排骨</div>");
        assertThat(html).contains("<div class=\"item-desc\">无锡菜的名片");
        assertThat(html).contains("<div class=\"item-title\">必吃特色点心：无锡小笼包</div>");
        assertThat(html).contains("<div class=\"item-desc\">皮薄馅多");
        assertThat(html).doesNotContain("<div class=\"item\">经典大菜与热炒</div>");
        assertThat(html).doesNotContain("<div class=\"item\">寻味小贴士");
    }

    @Test
    void appendsFollowingDescriptionToShortListItem() throws Exception {
        WechatReportService reportService = new WechatReportService(properties());

        WechatReport report = reportService.create("太湖菜推荐", """
                推荐菜品：
                1. **盐水太湖白虾**
                白虾壳薄肉嫩，用盐水简单白灼，吃起来鲜嫩微甜。
                2. **银鱼炒蛋**
                银鱼无骨透明，和鸡蛋同炒，口感软嫩。
                3. **清蒸太湖白鱼**
                白鱼肉质细嫩，清蒸能保留原汁原味。
                4. **无锡小笼包**
                皮薄馅多，汤汁偏甜。
                5. **镜箱豆腐**
                豆腐酿肉馅，汤汁醇厚。
                6. **响油鳝糊**
                热油浇在鳝糊上，蒜香和鲜味融合。
                """);

        String html = Files.readString(report.path());
        assertThat(html).contains("<div class=\"item-title\">盐水太湖白虾</div>");
        assertThat(html).contains("<div class=\"item-desc\">白虾壳薄肉嫩");
        assertThat(html).doesNotContain("<div class=\"item\"><div class=\"item-title\">盐水太湖白虾</div></div>");
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
