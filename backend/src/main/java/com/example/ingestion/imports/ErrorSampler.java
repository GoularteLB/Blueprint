package com.example.ingestion.imports;

import com.example.ingestion.imports.model.RowError;
import java.util.ArrayList;
import java.util.List;

public class ErrorSampler {

    private final int maxSamples;
    private final int maxRawLength;
    private final List<RowError> samples = new ArrayList<>();

    public ErrorSampler(int maxSamples, int maxRawLength) {
        this.maxSamples = maxSamples;
        this.maxRawLength = maxRawLength;
    }

    public void add(long line, String reason, String raw) {
        if (samples.size() >= maxSamples) {
            return;
        }
        String truncated = raw.length() > maxRawLength ? raw.substring(0, maxRawLength) : raw;
        samples.add(new RowError(line, reason, truncated));
    }

    public List<RowError> sample() {
        return List.copyOf(samples);
    }
}
