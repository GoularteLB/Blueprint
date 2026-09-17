package com.example.ingestion.imports;

import com.example.ingestion.analytics.model.SummaryRow;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SummaryAccumulator {

    private record Key(UUID jobId, LocalDate month, String category) {
    }

    private static final class Aggregate {
        private BigDecimal totalAmount = BigDecimal.ZERO;
        private long txCount = 0;
    }

    private final Map<Key, Aggregate> data = new HashMap<>();

    public void add(UUID jobId, Instant occurredAt, String category, BigDecimal amount) {
        LocalDate month = occurredAt.atZone(ZoneOffset.UTC).toLocalDate().withDayOfMonth(1);
        Aggregate aggregate = data.computeIfAbsent(new Key(jobId, month, category), k -> new Aggregate());
        aggregate.totalAmount = aggregate.totalAmount.add(amount);
        aggregate.txCount++;
    }

    public List<SummaryRow> snapshot() {
        List<SummaryRow> rows = new ArrayList<>(data.size());
        for (Map.Entry<Key, Aggregate> entry : data.entrySet()) {
            Key key = entry.getKey();
            Aggregate agg = entry.getValue();
            rows.add(new SummaryRow(key.jobId(), key.month(), key.category(), agg.totalAmount, agg.txCount));
        }
        return rows;
    }

    public void clear() {
        data.clear();
    }
}
