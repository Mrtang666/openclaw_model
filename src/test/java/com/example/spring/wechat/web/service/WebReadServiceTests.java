package com.example.spring.wechat.web.service;

import com.example.spring.wechat.web.config.WebToolProperties;
import com.example.spring.wechat.web.model.WebPageContent;
import com.example.spring.wechat.web.repository.WebPageCacheRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebReadServiceTests {

    @Test
    void returnsFreshCachedPageBeforeDownloadingUrl() {
        WebPageCacheRepository cacheRepository = mock(WebPageCacheRepository.class);
        WebPageContent cached = new WebPageContent(
                "https://example.com/a",
                "缓存网页",
                "缓存正文",
                Instant.parse("2026-07-23T00:00:00Z"));
        when(cacheRepository.findFresh(anyString(), any(Instant.class))).thenReturn(Optional.of(cached));
        WebReadService service = new WebReadService(
                new WebContentExtractor(),
                new WebToolProperties(
                        new WebToolProperties.Read(1000, 1024),
                        new WebToolProperties.Search("none", "", "", 5),
                        new WebToolProperties.Cache(24)),
                cacheRepository);

        WebPageContent result = service.read("https://example.com/a");

        assertThat(result).isSameAs(cached);
    }
}
