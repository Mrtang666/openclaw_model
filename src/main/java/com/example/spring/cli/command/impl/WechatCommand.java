package com.example.spring.cli.command.impl;


/**
 * CLI 命令系统组件，负责解析和执行本地命令。
 */
import com.example.spring.exception.CommandException;
import com.example.spring.wechat.bot.WechatBotService;
import com.example.spring.wechat.bot.WechatBotStatus;
import com.example.spring.wechat.bot.WechatStartResult;
import com.example.spring.wechat.bot.multiclient.ClawBotConnectionSnapshot;
import com.example.spring.wechat.bot.multiclient.ClawBotManagerSnapshot;
import org.springframework.stereotype.Component;

import java.util.List;
import com.example.spring.cli.command.core.Command;

@Component
public class WechatCommand implements Command {

    private final WechatBotService wechatBotService;

    public WechatCommand(WechatBotService wechatBotService) {
        this.wechatBotService = wechatBotService;
    }

    @Override
    public String name() {
        return "wechat";
    }

    @Override
    public String description() {
        return "微信接入：wechat start/status/connections/stop";
    }

    @Override
    public String execute(List<String> arguments) {
        if (arguments.isEmpty()) {
            return usage();
        }

        String action = arguments.get(0).toLowerCase();
        return switch (action) {
            case "start" -> start();
            case "status" -> status();
            case "connections" -> connections();
            case "reconnect" -> reconnect(arguments);
            case "stop" -> stop(arguments);
            default -> throw new CommandException("未知微信命令：" + action
                    + "，用法：wechat start/status/connections/stop");
        };
    }

    private String start() {
        WechatStartResult result = wechatBotService.start();
        StringBuilder output = new StringBuilder(result.message());
        if (result.loginPageUrl() != null && !result.loginPageUrl().isBlank()) {
            output.append(System.lineSeparator())
                    .append("登录页面：")
                    .append(result.loginPageUrl())
                    .append(System.lineSeparator())
                    .append("打开页面扫码后，可输入 wechat status 查看状态。");
        }
        return output.toString();
    }

    private String status() {
        WechatBotStatus status = wechatBotService.status();
        StringBuilder output = new StringBuilder();
        output.append("状态：").append(status.state());
        if (status.botId() != null && !status.botId().isBlank()) {
            output.append(System.lineSeparator()).append("botId：").append(status.botId());
        }
        if (status.lastError() != null && !status.lastError().isBlank()) {
            output.append(System.lineSeparator()).append("最近错误：").append(status.lastError());
        }
        output.append(System.lineSeparator())
                .append("连接：").append(status.connectedCount()).append(" 在线 / ")
                .append(status.pendingCount()).append(" 待扫码 / ")
                .append(status.totalConnections()).append(" 总计");
        return output.toString();
    }

    private String connections() {
        ClawBotManagerSnapshot snapshot = wechatBotService.managerSnapshot();
        StringBuilder output = new StringBuilder()
                .append("ClawBot 连接：")
                .append(snapshot.connectedCount()).append(" 在线，")
                .append(snapshot.pendingCount()).append(" 待扫码，")
                .append(snapshot.totalConnections()).append('/').append(snapshot.maxConnections()).append(" 总连接")
                .append(System.lineSeparator())
                .append("处理器：").append(snapshot.activeTasks()).append(" 处理中，")
                .append(snapshot.queuedTasks()).append(" 排队，")
                .append(snapshot.workerThreads()).append(" 工作线程，模型并发上限 ")
                .append(snapshot.modelMaxConcurrency());
        for (ClawBotConnectionSnapshot connection : snapshot.connections()) {
            output.append(System.lineSeparator())
                    .append("- ").append(connection.connectionId())
                    .append(" [").append(connection.state()).append('/').append(connection.processingState()).append(']')
                    .append(" queue=").append(connection.queuedMessages())
                    .append(" active=").append(connection.activeMessages());
            if (connection.botId() != null && !connection.botId().isBlank()) {
                output.append(" botId=").append(connection.botId());
            }
            if (connection.lastError() != null && !connection.lastError().isBlank()) {
                output.append(" error=").append(connection.lastError());
            }
        }
        return output.toString();
    }

    private String stop(List<String> arguments) {
        if (arguments.size() == 1) {
            return wechatBotService.stop();
        }
        String connectionId = arguments.get(1);
        return wechatBotService.stopConnection(connectionId)
                ? "微信连接已停止：" + connectionId
                : "未找到微信连接：" + connectionId;
    }

    private String reconnect(List<String> arguments) {
        if (arguments.size() < 2) {
            throw new CommandException("缺少 connectionId，用法：wechat reconnect <connectionId>");
        }
        ClawBotConnectionSnapshot connection = wechatBotService.reconnectConnection(arguments.get(1));
        return "已生成新的登录二维码，会话：" + connection.loginSessionId();
    }

    private String usage() {
        return """
                用法：
                wechat start - 启动微信扫码登录
                wechat status - 查看微信 Bot 状态
                wechat connections - 查看全部连接及处理队列
                wechat reconnect <connectionId> - 为指定连接重新生成登录二维码
                wechat stop [connectionId] - 停止全部或指定连接
                """.stripTrailing();
    }
}

