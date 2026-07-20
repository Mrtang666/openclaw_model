package com.example.spring;


/**
 * Spring Boot 启动类，负责启动整个应用。
 */
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AgentClawApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentClawApplication.class, args);
    }
}

