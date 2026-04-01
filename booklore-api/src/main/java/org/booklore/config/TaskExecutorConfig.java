package org.booklore.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.SimpleAsyncTaskScheduler;
import org.springframework.security.concurrent.DelegatingSecurityContextExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
@EnableAsync
public class TaskExecutorConfig {

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        return new DelegatingSecurityContextExecutor(
                Executors.newVirtualThreadPerTaskExecutor()
        );
    }

    @Bean
    public TaskScheduler taskScheduler() {
        var scheduler = new SimpleAsyncTaskScheduler();
        scheduler.setVirtualThreads(true);
        scheduler.setThreadNamePrefix("scheduler-");
        return scheduler;
    }
}
