package com.example.spring.cli;


/**
 * 命令行交互层组件，负责读取输入并输出结果。
 */
import com.example.spring.agent.AgentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.Scanner;

@Component
public class ConsoleRunner implements CommandLineRunner {

    private final AgentService agentService;
    private final InputStream input;
    private final PrintStream output;

    @Autowired
    public ConsoleRunner(AgentService agentService) {
        this(agentService, System.in, System.out);
    }

    ConsoleRunner(AgentService agentService, InputStream input, PrintStream output) {
        this.agentService = agentService;
        this.input = input;
        this.output = output;
    }

    @Override
    public void run(String... args) {
        if (args.length > 0) {
            printStreaming(String.join(" ", args));
            return;
        }

        output.println("OpenClaw CLI 已启动，直接输入内容可与大模型对话；输入 /help 查看命令；输入 exit 退出。");
        Scanner scanner = new Scanner(input);
        while (true) {
            output.print("> ");
            if (!scanner.hasNextLine()) {
                break;
            }

            String input = scanner.nextLine().trim();
            if (input.equalsIgnoreCase("exit") || input.equalsIgnoreCase("quit")) {
                output.println("程序已退出");
                break;
            }

            printStreaming(input);
        }
    }

    private void printStreaming(String input) {
        StringBuilder collected = new StringBuilder();
        agentService.handleStreaming(input, chunk -> {
            if (chunk != null) {
                collected.append(chunk);
                output.print(chunk);
                output.flush();
            }
        });

        if (collected.length() > 0) {
            output.println();
        }
    }
}

