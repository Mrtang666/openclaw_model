package com.example.spring;

import com.example.spring.config.EnvFileLoader;
import com.example.spring.wechat.context.WechatContextProperties;
import com.example.spring.wechat.knowledge.config.KnowledgeProperties;
import com.example.spring.wechat.conversation.rag.RagProperties;
import com.example.spring.wechat.netdisk.config.BaiduNetdiskProperties;
import com.example.spring.wechat.knowledge.config.QdrantProperties;
import com.example.spring.wechat.memory.config.WechatMemoryProperties;
import com.example.spring.wechat.web.config.WebToolProperties;
import com.example.spring.wechat.taxi.client.DidiMcpProperties;
import com.example.spring.wechat.payment.config.WechatPayProperties;
import com.example.spring.wechat.reminder.config.ReminderProperties;
import com.example.spring.wechat.food.config.FoodDeliveryProperties;
import com.example.spring.wechat.travel.config.MeituanTravelProperties;
import com.example.spring.xhs.config.XhsCollectorProperties;
import com.example.spring.xhs.config.XhsAnalysisProperties;
import com.example.spring.xhs.config.XhsAlertProperties;
import com.example.spring.xhs.config.XhsConsoleProperties;
import com.example.spring.xhs.config.XhsScheduledReportProperties;
import com.example.spring.xhs.config.XhsImageAnalysisProperties;
import com.example.spring.xhs.config.XhsCommentAnalysisProperties;
import com.example.spring.wechat.care.config.CareProperties;
import com.example.spring.wechat.care.config.CareTaskProperties;
import com.example.spring.wechat.email.config.EmailProperties;
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
        RagProperties.class,
        KnowledgeProperties.class,
        QdrantProperties.class,
        WebToolProperties.class,
        DidiMcpProperties.class, 
        WechatPayProperties.class,
        BaiduNetdiskProperties.class,
        ReminderProperties.class,
        FoodDeliveryProperties.class,
        MeituanTravelProperties.class,
        XhsCollectorProperties.class,
        XhsAnalysisProperties.class,
        XhsAlertProperties.class,
        XhsConsoleProperties.class,
        XhsScheduledReportProperties.class,
        XhsImageAnalysisProperties.class,
        XhsCommentAnalysisProperties.class,
        CareProperties.class,
        CareTaskProperties.class,
        EmailProperties.class,
        WechatContextProperties.class
})
@EnableScheduling
public class AgentClawApplication {

    public static void main(String[] args) {
        EnvFileLoader.loadDefault();
        SpringApplication.run(AgentClawApplication.class, args);
    }
}
