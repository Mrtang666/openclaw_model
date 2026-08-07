package com.example.spring.xhs.console;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class XhsConsoleWebConfigurationTests {

    @Test
    void disablesBrowserCachingForConsoleResources() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = XhsConsoleWebConfiguration.NO_CACHE_INTERCEPTOR.preHandle(
                new MockHttpServletRequest("GET", "/xhs-console/index.html"), response, new Object());

        assertThat(proceed).isTrue();
        assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL))
                .isEqualTo("no-store, no-cache, must-revalidate");
        assertThat(response.getHeader(HttpHeaders.PRAGMA)).isEqualTo("no-cache");
        assertThat(response.getDateHeader(HttpHeaders.EXPIRES)).isZero();
    }

    @Test
    void consoleEntryContainsAuthorizationNavigationAndVersionedAssets() throws IOException {
        try (var input = getClass().getResourceAsStream("/static/xhs-console/index.html")) {
            assertThat(input).isNotNull();
            String html = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(html)
                    .contains("data-view=\"authorization\"")
                    .contains("styles.css?v=20260807-overview-risk-1")
                    .contains("app.js?v=20260807-overview-risk-1");
        }
    }
}
