package com.example.spring.wechat.reminder.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ApplicationClockConfiguration {

    @Bean
    public Clock applicationClock() {
        return Clock.systemUTC();
    }
}
