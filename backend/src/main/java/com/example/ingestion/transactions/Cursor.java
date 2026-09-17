package com.example.ingestion.transactions;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

public record Cursor(Instant ts, long id) {

    public String encode() {
        String raw = ts.toString() + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static Cursor decode(String value) {
        String raw = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
        int sep = raw.lastIndexOf('|');
        Instant ts = Instant.parse(raw.substring(0, sep));
        long id = Long.parseLong(raw.substring(sep + 1));
        return new Cursor(ts, id);
    }
}
