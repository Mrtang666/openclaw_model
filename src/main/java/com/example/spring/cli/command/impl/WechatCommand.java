package com.example.spring.cli.command.impl;


/**
 * CLI 命令系统组件，负责解析和执行本地命令。
 */
import com.example.spring.exception.CommandException;
import com.example.spring.wechat.bot.WechatBotService;
import com.example.spring.wechat.bot.WechatBotStatus;
import com.example.spring.wechat.bot.WechatStartResult;
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
        return "微信接入：wechat start/status/stop";
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
            case "stop" -> wechatBotService.stop();
            default -> throw new CommandException("未知微信命令：" + action + "，用法：wechat start/status/stop");
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
        return output.toString();
    }

    private String usage() {
        return """
                用法：
                wechat start - 启动微信扫码登录
                wechat status - 查看微信 Bot 状态
                wechat stop - 停止微信 Bot
                """.stripTrailing();
    }
}

