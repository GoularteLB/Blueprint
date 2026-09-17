package com.example.ingestion.imports.model;

public record RowError(long line, String reason, String raw) {
}
