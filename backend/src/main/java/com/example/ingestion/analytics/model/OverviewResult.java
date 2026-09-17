package com.example.ingestion.analytics.model;

import java.math.BigDecimal;

public record OverviewResult(long totalRecords, BigDecimal totalAmount, int categories) {
}
