package com.example.spring.cli;

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
            output.println(agentService.handle(String.join(" ", args)));
            return;
        }

        output.println("OpenClaw CLI 已启动，输入 help 查看命令，输入 exit 退出。");
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

            String output = agentService.handle(input);
            if (!output.isBlank()) {
                this.output.println(output);
            }
        }
    }
}
