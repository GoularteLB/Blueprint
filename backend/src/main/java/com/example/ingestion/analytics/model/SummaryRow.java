package com.example.ingestion.analytics.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record SummaryRow(UUID jobId, LocalDate month, String category, BigDecimal totalAmount, long txCount) {
}
