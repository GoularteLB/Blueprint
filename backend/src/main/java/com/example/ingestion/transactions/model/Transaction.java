package com.example.ingestion.transactions.model;

import java.math.BigDecimal;
import java.time.Instant;

public record Transaction(
        long id,
        String externalId,
        Instant occurredAt,
        String category,
        String description,
        BigDecimal amount,
        String source
) {
}
