package com.example.spring;

import com.example.spring.config.EnvFileLoader;
import com.example.spring.wechat.knowledge.config.KnowledgeProperties;
import com.example.spring.wechat.netdisk.config.BaiduNetdiskProperties;
import com.example.spring.wechat.knowledge.config.QdrantProperties;
import com.example.spring.wechat.memory.config.WechatMemoryProperties;
import com.example.spring.wechat.web.config.WebToolProperties;
import com.example.spring.wechat.taxi.client.DidiMcpProperties;
import com.example.spring.wechat.payment.config.WechatPayProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.example.spring.wechat.netdisk.config.BaiduNetdiskProperties;

/**
 * Spring Boot 启动类，负责启动整个 OpenClaw 应用。
 */
@SpringBootApplication
@EnableConfigurationProperties({
        WechatMemoryProperties.class,
        KnowledgeProperties.class,
        QdrantProperties.class,
        WebToolProperties.class,
        DidiMcpProperties.class,
        WechatPayProperties.class,
        BaiduNetdiskProperties.class
})
@EnableScheduling
public class AgentClawApplication {

    public static void main(String[] args) {
        EnvFileLoader.loadDefault();
        SpringApplication.run(AgentClawApplication.class, args);
    }
}
