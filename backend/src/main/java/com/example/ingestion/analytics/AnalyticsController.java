package com.example.ingestion.analytics;

import com.example.ingestion.analytics.model.CategoryPoint;
import com.example.ingestion.analytics.model.MonthlyPoint;
import com.example.ingestion.analytics.model.OverviewResult;
import com.example.ingestion.analytics.model.Source;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AnalyticsController {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final AnalyticsRepository repository;

    public AnalyticsController(AnalyticsRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/api/analytics/overview")
    public Map<String, Object> overview(@RequestParam(required = false) String source,
                                         @RequestParam(required = false) UUID jobId) {
        Source resolved = source == null ? Source.SUMMARY : parseSource(source);
        OverviewResult result = resolved == Source.SUMMARY
                ? repository.overviewSummary(jobId)
                : repository.overviewDirect(jobId);
        BigDecimal average = result.totalRecords() == 0
                ? BigDecimal.ZERO
                : result.totalAmount().divide(BigDecimal.valueOf(result.totalRecords()), 2, RoundingMode.HALF_UP);
        return Map.of(
                "totalRecords", result.totalRecords(),
                "totalAmount", result.totalAmount(),
                "averageAmount", average,
                "categories", result.categories(),
                "source", resolved.name().toLowerCase()
        );
    }

    @GetMapping("/api/analytics/monthly")
    public List<Map<String, Object>> monthly(@RequestParam(required = false) String source,
                                              @RequestParam String from,
                                              @RequestParam String to,
                                              @RequestParam(required = false) UUID jobId) {
        Instant fromInstant = Instant.parse(from);
        Instant toInstant = Instant.parse(to);
        Source resolved = resolveSource(source, fromInstant, toInstant);
        List<MonthlyPoint> points = resolved == Source.SUMMARY
                ? repository.monthlySummary(toUtcDate(fromInstant), toUtcDate(toInstant), jobId)
                : repository.monthlyDirect(fromInstant, toInstant, jobId);
        return points.stream()
                .map(p -> Map.<String, Object>of(
                        "month", MONTH_FORMAT.format(p.month()),
                        "totalAmount", p.totalAmount(),
                        "txCount", p.txCount()))
                .toList();
    }

    @GetMapping("/api/analytics/categories")
    public List<Map<String, Object>> categories(@RequestParam(required = false) String source,
                                                 @RequestParam String from,
                                                 @RequestParam String to,
                                                 @RequestParam(required = false) UUID jobId) {
        Instant fromInstant = Instant.parse(from);
        Instant toInstant = Instant.parse(to);
        Source resolved = resolveSource(source, fromInstant, toInstant);
        List<CategoryPoint> points = resolved == Source.SUMMARY
                ? repository.categoriesSummary(toUtcDate(fromInstant), toUtcDate(toInstant), jobId)
                : repository.categoriesDirect(fromInstant, toInstant, jobId);
        return points.stream()
                .map(p -> Map.<String, Object>of(
                        "category", p.category(),
                        "totalAmount", p.totalAmount(),
                        "txCount", p.txCount()))
                .toList();
    }

    private Source resolveSource(String raw, Instant from, Instant to) {
        Source explicit = raw == null ? null : parseSource(raw);
        boolean aligned = isAlignedToMonth(from) && isAlignedToMonth(to);
        if (explicit == null) {
            return aligned ? Source.SUMMARY : Source.DIRECT;
        }
        if (explicit == Source.SUMMARY && !aligned) {
            throw new UnalignedIntervalException("source=summary requires from/to aligned to full months");
        }
        return explicit;
    }

    private Source parseSource(String raw) {
        try {
            return Source.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("source must be 'direct' or 'summary'");
        }
    }

    private boolean isAlignedToMonth(Instant instant) {
        var dateTime = instant.atZone(ZoneOffset.UTC);
        return dateTime.getDayOfMonth() == 1 && dateTime.toLocalTime().equals(LocalTime.MIDNIGHT);
    }

    private LocalDate toUtcDate(Instant instant) {
        return instant.atZone(ZoneOffset.UTC).toLocalDate();
    }
}
