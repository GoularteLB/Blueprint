package com.example.ingestion.imports.model;

import java.math.BigDecimal;
import java.time.Instant;

public record ParsedRow(
        String externalId,
        Instant occurredAt,
        String category,
        String description,
        BigDecimal amount,
        String source
) {
}
