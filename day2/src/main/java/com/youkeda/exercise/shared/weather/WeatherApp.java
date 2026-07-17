package com.youkeda.exercise.shared.weather;

import java.util.Scanner;

public class WeatherApp {

    private static final String VERSION = "V1.0";

    public static void main(String[] args) {
        WeatherService weatherService = new WeatherService();
        Scanner scanner = new Scanner(System.in);

        System.out.println("==========================================");
        System.out.println("   🌤️  天气查询工具 v" + VERSION);
        System.out.println("   输入城市名查询天气，输入 exit 退出");
        System.out.println("==========================================");
        System.out.println();

        while (true) {
            System.out.print("请输入城市名 > ");
            String inputLine = scanner.nextLine().trim();

            if (inputLine.isEmpty()) {
                continue;
            }

            if (inputLine.equalsIgnoreCase("exit") ||
                    inputLine.equalsIgnoreCase("quit") ||
                    inputLine.equals("退出")) {
                System.out.println("👋 程序已退出，欢迎下次使用");
                scanner.close();
                return;
            }

            if (inputLine.equals("help") || inputLine.equals("帮助")) {
                System.out.println("📖 使用说明：");
                System.out.println("  • 输入城市名查询天气，如：杭州");
                System.out.println("  • 输入 exit 或 退出 退出程序");
                System.out.println("  • 输入 help 或 帮助 查看此说明");
                System.out.println();
                continue;
            }

            // 查询天气
            System.out.println();
            String result = weatherService.queryWeather(inputLine);
            System.out.println(result);
            System.out.println();
        }
    }
}