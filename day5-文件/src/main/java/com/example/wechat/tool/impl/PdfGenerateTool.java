package com.example.wechat.tool.impl;

import com.example.wechat.tool.Tool;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.TextAlignment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class PdfGenerateTool implements Tool {

    // ===== 预编译字体 =====
    private static PdfFont cachedChineseFont = null;

    @Override
    public String getName() {
        return "generate_pdf";
    }

    @Override
    public String getDescription() {
        return "根据用户需求生成PDF文件，支持文本内容导出为PDF";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> contentProp = new HashMap<>();
        contentProp.put("type", "string");
        contentProp.put("description", "要生成PDF的内容（支持Markdown格式）");
        properties.put("content", contentProp);

        Map<String, Object> titleProp = new HashMap<>();
        titleProp.put("type", "string");
        titleProp.put("description", "PDF标题");
        properties.put("title", titleProp);

        schema.put("properties", properties);
        schema.put("required", new String[]{"content"});
        return schema;
    }

    @Override
    public String execute(Map<String, Object> params) {
        String content = (String) params.get("content");
        String title = (String) params.getOrDefault("title", "文档");

        if (content == null || content.trim().isEmpty()) {
            return "请提供要生成PDF的内容";
        }

        try {
            log.info("开始生成PDF，标题: {}, 内容长度: {}", title, content.length());

            byte[] pdfBytes = generatePdf(content, title);
            log.info("PDF生成成功，大小: {} bytes", pdfBytes.length);

            String base64Pdf = Base64.getEncoder().encodeToString(pdfBytes);
            return "PDF:" + base64Pdf + ":" + title + ".pdf";

        } catch (Exception e) {
            log.error("PDF生成失败", e);
            return "❌ PDF生成失败: " + e.getMessage();
        }
    }

    /**
     * 生成PDF文件
     */
    private byte[] generatePdf(String content, String title) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (PdfWriter writer = new PdfWriter(baos);
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document document = new Document(pdfDoc)) {

            // ===== 加载中文字体 =====
            PdfFont chineseFont = getChineseFont();

            // 标题
            Paragraph titlePara = new Paragraph(title)
                    .setFont(chineseFont)
                    .setFontSize(20)
                    .setBold()
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(20);
            document.add(titlePara);

            document.add(new Paragraph("──────────────────────").setFont(chineseFont).setTextAlignment(TextAlignment.CENTER));

            // 内容按段落分割
            String[] paragraphs = content.split("\n");
            for (String para : paragraphs) {
                if (para.trim().isEmpty()) {
                    document.add(new Paragraph(" "));
                    continue;
                }
                if (para.startsWith("# ")) {
                    Paragraph p = new Paragraph(para.substring(2))
                            .setFont(chineseFont)
                            .setFontSize(16)
                            .setBold()
                            .setMarginTop(10);
                    document.add(p);
                } else if (para.startsWith("## ")) {
                    Paragraph p = new Paragraph(para.substring(3))
                            .setFont(chineseFont)
                            .setFontSize(14)
                            .setBold()
                            .setMarginTop(8);
                    document.add(p);
                } else if (para.startsWith("### ")) {
                    Paragraph p = new Paragraph(para.substring(4))
                            .setFont(chineseFont)
                            .setFontSize(13)
                            .setBold()
                            .setMarginTop(6);
                    document.add(p);
                } else if (para.startsWith("- ") || para.startsWith("* ")) {
                    Paragraph p = new Paragraph("  • " + para.substring(2))
                            .setFont(chineseFont)
                            .setFontSize(12)
                            .setMarginBottom(3);
                    document.add(p);
                } else {
                    Paragraph p = new Paragraph(para)
                            .setFont(chineseFont)
                            .setFontSize(12)
                            .setMarginBottom(5);
                    document.add(p);
                }
            }

            // 页脚
            document.add(new Paragraph(" "));
            document.add(new Paragraph("—— 由AI助手生成 ——")
                    .setFont(chineseFont)
                    .setFontSize(10)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontColor(com.itextpdf.kernel.colors.ColorConstants.GRAY));
        }

        return baos.toByteArray();
    }

    /**
     * 获取中文字体 - Windows 专用
     */
    private PdfFont getChineseFont() throws Exception {
        // ===== 如果已经加载，直接返回缓存 =====
        if (cachedChineseFont != null) {
            return cachedChineseFont;
        }

        // ===== Windows 系统字体路径 =====
        String[] fontPaths = {
                "C:/Windows/Fonts/simsun.ttc",      // 宋体
                "C:/Windows/Fonts/msyh.ttc",         // 微软雅黑
                "C:/Windows/Fonts/msyhbd.ttc",       // 微软雅黑 Bold
                "C:/Windows/Fonts/simhei.ttf",       // 黑体
                "C:/Windows/Fonts/simkai.ttf",       // 楷体
                "C:/Windows/Fonts/fangsong.ttf",     // 仿宋
                "C:/Windows/Fonts/STZHONGS.TTF",     // 华文中宋
                "C:/Windows/Fonts/STKAITI.TTF",      // 华文楷体
                "C:/Windows/Fonts/STSONG.TTF",       // 华文宋体
                "C:/Windows/Fonts/STXIHEI.TTF",      // 华文细黑
                "C:/Windows/Fonts/STLITI.TTF",       // 华文隶书
                "C:/Windows/Fonts/YAHEI.TTF",         // 微软雅黑（备用）
                "C:/Windows/Fonts/YAHEIB.TTF",        // 微软雅黑 Bold（备用）
        };

        for (String path : fontPaths) {
            File f = new File(path);
            if (f.exists()) {
                try {
                    log.info("✅ 找到系统字体: {}", path);
                    // 使用 iText7 的 createFont 方法：路径 + 编码
                    cachedChineseFont = PdfFontFactory.createFont(path, "Identity-H");
                    log.info("✅ 字体加载成功: {}", path);
                    return cachedChineseFont;
                } catch (Exception e) {
                    log.warn("字体加载失败: {} - {}", path, e.getMessage());
                }
            }
        }

        // ===== 如果系统字体都找不到，尝试从项目 resources 加载 =====
        try {
            String[] resources = {"/fonts/simsun.ttf", "/fonts/msyh.ttf", "/fonts/simhei.ttf"};
            for (String resource : resources) {
                java.net.URL url = getClass().getResource(resource);
                if (url != null) {
                    // 对于文件系统中的文件，可以直接用路径
                    String path = url.getPath();
                    if (!path.contains(".jar!")) {
                        File f = new File(path);
                        if (f.exists()) {
                            try {
                                log.info("✅ 从 resources 加载字体: {}", resource);
                                cachedChineseFont = PdfFontFactory.createFont(f.getAbsolutePath(), "Identity-H");
                                log.info("✅ 字体加载成功: {}", resource);
                                return cachedChineseFont;
                            } catch (Exception e) {
                                log.warn("resources 字体加载失败: {}", e.getMessage());
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("resources 字体加载失败: {}", e.getMessage());
        }

        // ===== 最后保底：使用 Arial（不支持中文，但不会报错） =====
        log.warn("⚠️ 所有中文字体加载失败，使用默认字体（中文可能显示为乱码）");
        cachedChineseFont = PdfFontFactory.createFont("Helvetica", "Identity-H");
        return cachedChineseFont;
    }
}