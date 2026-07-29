package com.example.spring.xhs.console;

import com.example.spring.xhs.config.XhsConsoleProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.boot.web.server.WebServer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class XhsConsoleUrlServiceTests {

    @Test
    void usesActualEmbeddedServerPort() {
        WebServerApplicationContext context = mock(WebServerApplicationContext.class);
        WebServer webServer = mock(WebServer.class);
        when(context.getWebServer()).thenReturn(webServer);
        when(webServer.getPort()).thenReturn(18080);

        XhsConsoleUrlService service = new XhsConsoleUrlService(
                new XhsConsoleProperties(true, "", true), context);

        assertThat(service.pageUrl()).isEqualTo("http://127.0.0.1:18080/xhs-console/index.html");
    }

    @Test
    void prefersConfiguredBaseUrl() {
        XhsConsoleUrlService service = new XhsConsoleUrlService(
                new XhsConsoleProperties(true, "https://console.example.com/", true),
                mock(WebServerApplicationContext.class));

        assertThat(service.pageUrl()).isEqualTo("https://console.example.com/xhs-console/index.html");
    }

    @Test
    void rejectsDisabledConsole() {
        XhsConsoleUrlService service = new XhsConsoleUrlService(
                new XhsConsoleProperties(false, "", true), mock(WebServerApplicationContext.class));

        assertThatThrownBy(service::pageUrl).hasMessageContaining("未启用");
    }
}
