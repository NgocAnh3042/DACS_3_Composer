package com.example.backend.notification;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class FcmAsyncConfiguration {
    @Bean(name="fcmTaskExecutor") public ThreadPoolTaskExecutor fcmTaskExecutor(){
        ThreadPoolTaskExecutor executor=new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);executor.setMaxPoolSize(3);executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("fcm-");executor.setWaitForTasksToCompleteOnShutdown(false);executor.initialize();
        return executor;
    }
}
