package com.example.spring.wechat.web.service;

import com.example.spring.wechat.web.config.WebToolProperties;
import com.example.spring.wechat.web.exception.WebToolException;
import com.example.spring.wechat.web.model.WebPageContent;
import com.example.spring.wechat.web.repository.WebPageCacheRepository;
import org.springframework.stereotype.Service;

import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;

/**
 * 读取公开网页并提取正文。
 */
@Service
public class WebReadService {

    private final WebContentExtractor extractor;
    private final WebToolProperties properties;
    private final WebPageCacheRepository cacheRepository;

    public WebReadService(
            WebContentExtractor extractor,
            WebToolProperties properties,
            WebPageCacheRepository cacheRepository) {
        this.extractor = extractor;
        this.properties = properties;
        this.cacheRepository = cacheRepository;
    }

    public WebPageContent read(String url) {
        URI uri = validateUrl(url);
        String normalizedUrl = uri.toString();
        String urlHash = sha256(normalizedUrl);
        Instant now = Instant.now();
        var cached = cacheRepository.findFresh(urlHash, now);
        if (cached.isPresent()) {
            return cached.get();
        }
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(normalizedUrl).openConnection();
            connection.setConnectTimeout(properties.read().timeoutMs());
            connection.setReadTimeout(properties.read().timeoutMs());
            connection.setRequestProperty("User-Agent", "OpenClawBot/1.0");
            int status = connection.getResponseCode();
            if (status >= 400) {
                throw new WebToolException("网页读取失败，HTTP 状态码：" + status);
            }
            byte[] bytes = connection.getInputStream().readNBytes(properties.read().maxBytes() + 1);
            if (bytes.length > properties.read().maxBytes()) {
                throw new WebToolException("网页内容过大，已超过最大读取限制");
            }
            String html = new String(bytes, StandardCharsets.UTF_8);
            WebPageContent page = extractor.extract(normalizedUrl, html);
            if (page.content().isBlank()) {
                throw new WebToolException("网页没有提取到可读正文");
            }
            cacheRepository.save(urlHash, page, now.plus(properties.cache().ttlHours(), ChronoUnit.HOURS));
            return page;
        } catch (WebToolException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new WebToolException("网页读取失败：" + exception.getMessage(), exception);
        }
    }

    private URI validateUrl(String value) {
        try {
            URI uri = URI.create(value == null ? "" : value.strip());
            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                throw new WebToolException("只支持 http 和 https 网页地址");
            }
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                throw new WebToolException("网页地址缺少 host");
            }
            if ("localhost".equalsIgnoreCase(host)) {
                throw new WebToolException("出于安全原因，不能读取 localhost 地址");
            }
            InetAddress address = InetAddress.getByName(host);
            if (address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress()) {
                throw new WebToolException("出于安全原因，不能读取本机或内网地址");
            }
            return uri;
        } catch (WebToolException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new WebToolException("网页地址格式不正确");
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new WebToolException("网页缓存 key 计算失败", exception);
        }
    }
}
