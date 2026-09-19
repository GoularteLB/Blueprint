package com.example.ingestion.infra;

import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
public class AsyncConfig {

    @Bean("ingestionExecutor")
    public ThreadPoolTaskExecutor ingestionExecutor(IngestionProperties props) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(props.executor().coreSize());
        executor.setMaxPoolSize(props.executor().maxSize());
        executor.setQueueCapacity(props.executor().queueCapacity());
        executor.setThreadNamePrefix("ingest-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        return executor;
    }

    @Bean
    public Semaphore admissionSemaphore(IngestionProperties props) {
        return new Semaphore(props.maxInFlight());
    }

    @Bean
    public TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }
}
