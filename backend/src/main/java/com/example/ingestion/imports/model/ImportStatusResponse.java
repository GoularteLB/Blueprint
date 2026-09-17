package com.example.ingestion.imports.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ImportStatusResponse(
        UUID id,
        String fileName,
        ImportStatus status,
        long fileSizeBytes,
        long bytesRead,
        double progressPercent,
        long processedLines,
        long successLines,
        long errorLines,
        Instant startedAt,
        Instant finishedAt,
        Long elapsedMs,
        Double linesPerSecond,
        List<RowError> errorSample,
        String errorMessage
) {

    public static ImportStatusResponse from(ImportJob job) {
        double progressPercent = job.fileSizeBytes() == 0
                ? 0.0
                : (job.bytesRead() * 100.0) / job.fileSizeBytes();

        Instant end = job.finishedAt() != null ? job.finishedAt() : Instant.now();
        Long elapsedMs = job.startedAt() == null ? null : end.toEpochMilli() - job.startedAt().toEpochMilli();
        Double linesPerSecond = (elapsedMs == null || elapsedMs == 0)
                ? null
                : (job.processedLines() * 1000.0) / elapsedMs;

        return new ImportStatusResponse(
                job.id(), job.fileName(), job.status(), job.fileSizeBytes(), job.bytesRead(),
                progressPercent, job.processedLines(), job.successLines(), job.errorLines(),
                job.startedAt(), job.finishedAt(), elapsedMs, linesPerSecond,
                job.errorSample(), job.errorMessage()
        );
    }
}
