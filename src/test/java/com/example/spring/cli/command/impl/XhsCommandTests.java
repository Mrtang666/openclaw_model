package com.example.spring.cli.command.impl;

import com.example.spring.xhs.config.XhsConsoleProperties;
import com.example.spring.xhs.console.XhsConsoleHealth;
import com.example.spring.xhs.console.XhsConsoleHealthService;
import com.example.spring.xhs.console.XhsConsoleLauncher;
import com.example.spring.xhs.console.XhsConsoleUrlService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class XhsCommandTests {

    @Test
    void opensConsoleAndPrintsHealth() {
        XhsConsoleUrlService urlService = mock(XhsConsoleUrlService.class);
        XhsConsoleLauncher launcher = mock(XhsConsoleLauncher.class);
        XhsConsoleHealthService healthService = mock(XhsConsoleHealthService.class);
        when(urlService.pageUrl()).thenReturn("http://127.0.0.1:8080/xhs-console/index.html");
        when(launcher.open("http://127.0.0.1:8080/xhs-console/index.html")).thenReturn(true);
        when(healthService.health()).thenReturn(new XhsConsoleHealth(
                "UP", true, true, true, "采集服务正常", 2, Instant.now()));
        XhsCommand command = new XhsCommand(
                urlService, launcher, healthService, new XhsConsoleProperties(true, "", true));

        assertThat(command.execute(List.of("start")))
                .contains("小红书舆情管理台已就绪")
                .contains("http://127.0.0.1:8080/xhs-console/index.html")
                .contains("浏览器已自动打开")
                .contains("运行中任务：2");
        verify(launcher).open("http://127.0.0.1:8080/xhs-console/index.html");
    }

    @Test
    void printsManualUrlWhenAutoOpenIsDisabled() {
        XhsConsoleUrlService urlService = mock(XhsConsoleUrlService.class);
        XhsConsoleLauncher launcher = mock(XhsConsoleLauncher.class);
        XhsConsoleHealthService healthService = mock(XhsConsoleHealthService.class);
        when(urlService.pageUrl()).thenReturn("http://127.0.0.1:8080/xhs-console/index.html");
        when(healthService.health()).thenReturn(new XhsConsoleHealth(
                "DEGRADED", true, true, false, "无法连接采集服务", 0, Instant.now()));
        XhsCommand command = new XhsCommand(
                urlService, launcher, healthService, new XhsConsoleProperties(true, "", false));

        assertThat(command.execute(List.of("start")))
                .contains("自动打开浏览器已关闭")
                .contains("服务状态：DEGRADED")
                .contains("采集 Sidecar：异常");
    }
}
