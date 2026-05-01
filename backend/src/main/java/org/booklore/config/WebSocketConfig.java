package org.booklore.config;

import org.booklore.config.security.interceptor.WebSocketAuthInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.WebSocketMessageBrokerStats;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

import java.util.Arrays;
import java.util.regex.Pattern;

@Slf4j
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final Pattern COMMA_SPLIT_PATTERN = Pattern.compile("\\s*,\\s*");
    private final WebSocketAuthInterceptor webSocketAuthInterceptor;
    private final Environment env;

    public WebSocketConfig(WebSocketAuthInterceptor webSocketAuthInterceptor, Environment env) {
        this.webSocketAuthInterceptor = webSocketAuthInterceptor;
        this.env = env;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void suppressStatsLogging(ApplicationReadyEvent event) {
        var stats = event.getApplicationContext().getBean(WebSocketMessageBrokerStats.class);
        stats.setLoggingPeriod(30 * 24 * 60 * 60 * 1000L); // 30 days
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue", "/topic")
                .setHeartbeatValue(new long[]{10_000, 10_000})
                .setTaskScheduler(wsHeartbeatScheduler());
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Bean
    public TaskScheduler wsHeartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        scheduler.setDaemon(true);
        return scheduler;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String allowedOrigins = env.getProperty("app.cors.allowed-origins", "*").trim();
        var endpoint = registry.addEndpoint("/ws");
        if ("*".equals(allowedOrigins)) {
            endpoint.setAllowedOriginPatterns("*");
            log.warn("WebSocket endpoint is configured to allow all origins (*). " +
                    "This is the default for backward compatibility, but it's recommended to set 'app.cors.allowed-origins' to an explicit list.");
        } else if (allowedOrigins.isEmpty()) {
            log.info("WebSocket endpoint registered at /ws (same-origin only)");
        } else {
            String[] origins = Arrays.stream(COMMA_SPLIT_PATTERN.split(allowedOrigins))
                    .filter(s -> !s.isEmpty())
                    .toArray(String[]::new);
            endpoint.setAllowedOriginPatterns(origins);
            log.info("WebSocket endpoint registered at /ws with allowed origins: {}", Arrays.toString(origins));
        }
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setSendTimeLimit(15_000)
                .setSendBufferSizeLimit(512 * 1024)
                .setMessageSizeLimit(128 * 1024)
                .setTimeToFirstMessage(30_000);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(webSocketAuthInterceptor);
    }
}
