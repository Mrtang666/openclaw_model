package com.example;

import com.example.command.CliProcessor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Scanner;

@SpringBootApplication
public class SpringDemoApplication implements CommandLineRunner {

    public static void main(String[] args) {
        SpringApplication.run(SpringDemoApplication.class, args);
    }

    @Override
    public void run(String... args) {
        try (Scanner scanner = new Scanner(System.in)) {
            System.out.println("Spring Demo CLI (JDK 21)");
            System.out.println("输入 help 查看命令列表");
            CliProcessor processor = new CliProcessor();
            while (true) {
                System.out.print("> ");
                String line = "";
                if (scanner.hasNextLine()) {
                    line = scanner.nextLine().trim();
                }
                if (line.isEmpty()) continue;
                if ("exit".equalsIgnoreCase(line) || "quit".equalsIgnoreCase(line)) {
                    System.out.println("再见！");
                    break;
                }
                try {
                    processor.execute(line);
                } catch (Exception e) {
                    System.err.println("[错误] " + e.getMessage());
                }
            }
        }
    }
}