package com.example.ingestion.imports.model;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ImportJob(
        UUID id,
        String fileName,
        Path filePath,
        long fileSizeBytes,
        long bytesRead,
        ImportStatus status,
        long processedLines,
        long successLines,
        long errorLines,
        List<RowError> errorSample,
        String errorMessage,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt
) {

    public static ImportJob pending(UUID id, String fileName, Path filePath, long fileSizeBytes) {
        return new ImportJob(id, fileName, filePath, fileSizeBytes, 0L, ImportStatus.PENDING,
                0L, 0L, 0L, List.of(), null, Instant.now(), null, null);
    }
}
