package com.example.command;

import com.example.service.WeatherService;

public class CliProcessor {

    private final WeatherService weatherService;

    public CliProcessor() {
        this.weatherService = new WeatherService();
    }

    public void execute(String line) {
        String[] parts = line.split("\\s+", 2);
        String cmd = parts[0].toLowerCase();
        String arg = parts.length > 1 ? parts[1] : null;

        switch (cmd) {
            case "help" -> printHelp();
            case "version" -> printVersion();
            case "status" -> printStatus();
            case "weather" -> {
                if (arg == null || arg.isBlank()) {
                    throw new IllegalArgumentException("城市名不能为空，用法: weather <城市名>");
                }
                weatherService.printWeather(arg);
            }
            default -> System.out.println("未知命令: " + cmd + "，输入 help 查看可用命令");
        }
    }

    private void printHelp() {
        System.out.println("可用命令:");
        System.out.println("  help                    显示本帮助信息");
        System.out.println("  version                 显示应用版本");
        System.out.println("  status                  显示程序运行状态");
        System.out.println("  weather <城市名>         查询指定城市的天气");
        System.out.println("  exit / quit             退出程序");
    }

    private void printVersion() {
        System.out.println("Spring Demo CLI");
        System.out.println("版本: 1.0.0");
        System.out.println("JDK: " + System.getProperty("java.version"));
    }

    private void printStatus() {
        Runtime rt = Runtime.getRuntime();
        System.out.println("应用状态: RUNNING");
        System.out.println("Java 版本: " + System.getProperty("java.version"));
        System.out.println("可用处理器: " + rt.availableProcessors());
        System.out.println("空闲内存: " + rt.freeMemory() / 1024 / 1024 + " MB");
        System.out.println("总内存: " + rt.totalMemory() / 1024 / 1024 + " MB");
        System.out.println("最大内存: " + rt.maxMemory() / 1024 / 1024 + " MB");
    }
}