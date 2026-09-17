package com.example.ingestion.transactions.model;

import java.util.List;

public record TransactionPage(List<Transaction> data, String nextCursor, boolean hasMore) {
}
