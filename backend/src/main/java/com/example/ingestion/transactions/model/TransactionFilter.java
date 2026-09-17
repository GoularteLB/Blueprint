package com.example.ingestion.transactions.model;

import java.time.Instant;
import java.util.UUID;

public record TransactionFilter(String category, UUID jobId, Instant from, Instant to) {
}
