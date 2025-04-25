package com.adityachandel.booklore.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.WebSocketMessageBrokerStats;

@Configuration
public class BeanConfig {

    @Autowired
    private WebSocketMessageBrokerStats webSocketMessageBrokerStats;

    @PostConstruct
    public void init() {
        webSocketMessageBrokerStats.setLoggingPeriod(30 * 24 * 60 * 60 * 1000L); // 30 days
    }
}
