package com.SHADOW.P2P_SERVICE.Configurations;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Required for the @Async on WebSocketMailboxFlusher. Without @EnableAsync the
 * annotation is ignored silently and the handler runs inline on the inbound
 * channel thread, reintroducing the subscribe race and blocking that thread for
 * the settle delay.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "mailboxFlushExecutor")
    public Executor mailboxFlushExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("mailbox-flush-");
        executor.initialize();
        return executor;
    }
}
