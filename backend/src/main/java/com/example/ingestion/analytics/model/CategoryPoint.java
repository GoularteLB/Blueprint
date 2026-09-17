package com.example.ingestion.analytics.model;

import java.math.BigDecimal;

public record CategoryPoint(String category, BigDecimal totalAmount, long txCount) {
}
