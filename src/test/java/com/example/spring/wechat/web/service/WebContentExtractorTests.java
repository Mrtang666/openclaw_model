package com.example.spring.wechat.web.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebContentExtractorTests {

    @Test
    void extractsReadableTitleAndTextFromHtml() {
        WebContentExtractor extractor = new WebContentExtractor();

        var page = extractor.extract("https://example.com", """
                <html>
                <head><title>测试文章</title><script>ignore()</script></head>
                <body><nav>导航</nav><main><h1>标题</h1><p>这是正文内容。</p></main></body>
                </html>
                """);

        assertThat(page.title()).isEqualTo("测试文章");
        assertThat(page.content()).contains("标题", "这是正文内容").doesNotContain("ignore", "导航");
    }
}
