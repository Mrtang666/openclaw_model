package com.example.spring;

import com.example.spring.wechat.knowledge.config.KnowledgeProperties;
import com.example.spring.wechat.knowledge.config.QdrantProperties;
import com.example.spring.wechat.memory.config.WechatMemoryProperties;
import com.example.spring.wechat.web.config.WebToolProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Boot 启动类，负责启动整个 OpenClaw 应用。
 */
@SpringBootApplication
@EnableConfigurationProperties({
        WechatMemoryProperties.class,
        KnowledgeProperties.class,
        QdrantProperties.class,
        WebToolProperties.class
})
@EnableScheduling
public class AgentClawApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentClawApplication.class, args);
    }
}
