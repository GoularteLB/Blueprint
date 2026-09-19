package com.example.ingestion.infra;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

public final class DateParams {

    private static final int DATE_ONLY_LENGTH = 10;

    private DateParams() {
    }

    public static Instant parse(String value) {
        if (value.length() == DATE_ONLY_LENGTH) {
            return LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant();
        }
        return Instant.parse(value);
    }
}
