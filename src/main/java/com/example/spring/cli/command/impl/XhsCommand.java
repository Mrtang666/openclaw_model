package com.example.spring.cli.command.impl;

import com.example.spring.cli.command.core.Command;
import com.example.spring.exception.CommandException;
import com.example.spring.xhs.config.XhsConsoleProperties;
import com.example.spring.xhs.console.XhsConsoleHealth;
import com.example.spring.xhs.console.XhsConsoleHealthService;
import com.example.spring.xhs.console.XhsConsoleLauncher;
import com.example.spring.xhs.console.XhsConsoleUrlService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class XhsCommand implements Command {

    private final XhsConsoleUrlService urlService;
    private final XhsConsoleLauncher launcher;
    private final XhsConsoleHealthService healthService;
    private final XhsConsoleProperties properties;

    public XhsCommand(
            XhsConsoleUrlService urlService,
            XhsConsoleLauncher launcher,
            XhsConsoleHealthService healthService,
            XhsConsoleProperties properties) {
        this.urlService = urlService;
        this.launcher = launcher;
        this.healthService = healthService;
        this.properties = properties;
    }

    @Override
    public String name() {
        return "xhs";
    }

    @Override
    public String description() {
        return "小红书舆情管理台：xhs start/status/help";
    }

    @Override
    public String execute(List<String> arguments) {
        if (arguments.isEmpty()) {
            return usage();
        }
        return switch (arguments.get(0).toLowerCase(Locale.ROOT)) {
            case "start", "open" -> start();
            case "status" -> status();
            case "help" -> usage();
            default -> throw new CommandException("未知小红书管理台命令，用法：xhs start/status/help");
        };
    }

    private String start() {
        String url = urlService.pageUrl();
        boolean opened = !properties.autoOpen() || launcher.open(url);
        String openMessage = properties.autoOpen()
                ? (opened ? "浏览器已自动打开" : "无法自动打开浏览器，请手动访问上方地址")
                : "自动打开浏览器已关闭，请手动访问上方地址";
        return "小红书舆情管理台已就绪" + System.lineSeparator()
                + "访问地址：" + url + System.lineSeparator()
                + openMessage + System.lineSeparator() + System.lineSeparator()
                + formatHealth(healthService.health());
    }

    private String status() {
        return "管理台地址：" + urlService.pageUrl() + System.lineSeparator()
                + formatHealth(healthService.health());
    }

    private String formatHealth(XhsConsoleHealth health) {
        return "服务状态：" + health.status() + System.lineSeparator()
                + "- 数据库：" + (health.databaseUp() ? "正常" : "异常") + System.lineSeparator()
                + "- 采集 Sidecar：" + (health.collectorEnabled()
                    ? (health.collectorUp() ? "正常" : "异常")
                    : "未启用") + System.lineSeparator()
                + "- 运行中任务：" + health.runningJobs();
    }

    private String usage() {
        return """
                用法：
                /xhs start  - 打开小红书舆情管理台
                /xhs status - 查看管理台、数据库和采集服务状态
                /xhs help   - 查看命令帮助
                """.stripTrailing();
    }
}
