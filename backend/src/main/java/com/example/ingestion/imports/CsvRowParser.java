package com.example.ingestion.imports;

import com.example.ingestion.imports.model.ParsedRow;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

@Component
public class CsvRowParser {

    private static final DateTimeFormatter OCCURRED_AT_FORMAT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    public ParsedRow parse(CSVRecord record) {
        String externalId = required(record, "external_id");
        if (externalId.isBlank()) {
            throw new RowValidationException("external_id is required");
        }

        String occurredAtRaw = required(record, "occurred_at");
        OffsetDateTime occurredAt;
        try {
            occurredAt = OffsetDateTime.parse(occurredAtRaw, OCCURRED_AT_FORMAT);
        } catch (RuntimeException e) {
            throw new RowValidationException("occurred_at must be ISO-8601 with an explicit offset");
        }

        String category = required(record, "category");
        if (category.isBlank()) {
            throw new RowValidationException("category is required");
        }

        String amountRaw = required(record, "amount");
        BigDecimal amount;
        try {
            amount = new BigDecimal(amountRaw.trim());
        } catch (NumberFormatException e) {
            throw new RowValidationException("amount is not a valid decimal");
        }

        String description = optional(record, "description");
        String source = optional(record, "source");

        return new ParsedRow(externalId, occurredAt.toInstant(), category.toUpperCase(Locale.ROOT), description, amount, source);
    }

    private String required(CSVRecord record, String name) {
        try {
            String value = record.get(name);
            if (value == null) {
                throw new RowValidationException("missing column: " + name);
            }
            return value;
        } catch (RowValidationException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new RowValidationException("missing column: " + name);
        }
    }

    private String optional(CSVRecord record, String name) {
        try {
            return record.isMapped(name) && record.isSet(name) ? record.get(name) : null;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
