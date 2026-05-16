package org.booklore.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.SimpleAsyncTaskScheduler;
import org.springframework.security.concurrent.DelegatingSecurityContextRunnable;

import org.springframework.core.task.SimpleAsyncTaskExecutor;

@Configuration
@EnableAsync
public class TaskExecutorConfig {

    @Bean(name = "taskExecutor")
    public AsyncTaskExecutor taskExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor();
        executor.setVirtualThreads(true);
        executor.setThreadNamePrefix("async-");
        executor.setTaskDecorator(DelegatingSecurityContextRunnable::new);
        executor.setTaskTerminationTimeout(60_000L);
        return executor;
    }

    @Bean
    public TaskScheduler taskScheduler() {
        var scheduler = new SimpleAsyncTaskScheduler();
        scheduler.setVirtualThreads(true);
        scheduler.setThreadNamePrefix("scheduler-");
        return scheduler;
    }
}
