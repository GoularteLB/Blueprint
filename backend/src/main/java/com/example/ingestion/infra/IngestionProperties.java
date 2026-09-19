package com.example.ingestion.infra;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ingestion")
public record IngestionProperties(
        int batchSize,
        int commitEveryNBatches,
        boolean synchronousCommit,
        String uploadDir,
        int maxErrorSamples,
        int maxErrorRawLength,
        int maxInFlight,
        Executor executor
) {
    public record Executor(int coreSize, int maxSize, int queueCapacity) {
    }
}
