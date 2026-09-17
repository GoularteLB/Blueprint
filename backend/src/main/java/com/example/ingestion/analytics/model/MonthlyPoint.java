package com.example.ingestion.analytics.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MonthlyPoint(LocalDate month, BigDecimal totalAmount, long txCount) {
}
