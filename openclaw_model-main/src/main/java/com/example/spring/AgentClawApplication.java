package com.example.spring;


/**
 * Spring Boot 启动类，负责启动整个应用。
 */
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.example.spring.wechat.memory.config.WechatMemoryProperties;

@SpringBootApplication
@EnableConfigurationProperties(WechatMemoryProperties.class)
@EnableScheduling
public class AgentClawApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentClawApplication.class, args);
    }
}

