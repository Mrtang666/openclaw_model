package com.example.spring.wechat.web.service;

import com.example.spring.wechat.web.model.WebPageContent;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * 从 HTML 中提取可读正文。
 */
@Service
public class WebContentExtractor {

    public WebPageContent extract(String url, String html) {
        Document document = Jsoup.parse(html == null ? "" : html, url);
        document.select("script,style,noscript,nav,header,footer,aside").remove();
        String title = document.title() == null || document.title().isBlank()
                ? "未命名网页"
                : document.title().strip();
        String content = document.body() == null ? "" : document.body().text().strip();
        return new WebPageContent(url == null ? "" : url.strip(), title, content, Instant.now());
    }
}
