package com.example.spring.wechat.bot.multiclient;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "clawbot")
public class ClawBotConnectionProperties {

    private int maxConnections = 10;
    private int maxPendingLogins = 3;

    public int getMaxConnections() { return maxConnections; }
    public void setMaxConnections(int value) { maxConnections = value > 0 ? value : 10; }
    public int getMaxPendingLogins() { return maxPendingLogins; }
    public void setMaxPendingLogins(int value) { maxPendingLogins = value > 0 ? value : 3; }
}
