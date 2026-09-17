package com.example.ingestion.analytics;

import com.example.ingestion.analytics.model.CategoryPoint;
import com.example.ingestion.analytics.model.MonthlyPoint;
import com.example.ingestion.analytics.model.OverviewResult;
import com.example.ingestion.analytics.model.SummaryRow;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AnalyticsRepository {

    private final JdbcTemplate jdbc;

    public AnalyticsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void upsertAll(List<SummaryRow> rows) {
        if (rows.isEmpty()) {
            return;
        }
        List<Object[]> batch = new ArrayList<>(rows.size());
        for (SummaryRow row : rows) {
            batch.add(new Object[]{row.jobId(), Date.valueOf(row.month()), row.category(),
                    row.totalAmount(), row.txCount()});
        }
        jdbc.batchUpdate("""
                INSERT INTO category_month_summary (job_id, month, category, total_amount, tx_count)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (job_id, month, category) DO UPDATE
                SET total_amount = category_month_summary.total_amount + EXCLUDED.total_amount,
                    tx_count     = category_month_summary.tx_count     + EXCLUDED.tx_count
                """, batch);
    }

    public OverviewResult overviewDirect(UUID jobId) {
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) AS total_records, COALESCE(SUM(amount), 0) AS total_amount, " +
                "COUNT(DISTINCT category) AS categories FROM transactions");
        List<Object> args = new ArrayList<>();
        if (jobId != null) {
            sql.append(" WHERE job_id = ?");
            args.add(jobId);
        }
        return jdbc.queryForObject(sql.toString(), (rs, rn) -> new OverviewResult(
                rs.getLong("total_records"), rs.getBigDecimal("total_amount"), rs.getInt("categories")
        ), args.toArray());
    }

    public OverviewResult overviewSummary(UUID jobId) {
        StringBuilder sql = new StringBuilder(
                "SELECT COALESCE(SUM(tx_count), 0) AS total_records, COALESCE(SUM(total_amount), 0) AS total_amount, " +
                "COUNT(DISTINCT category) AS categories FROM category_month_summary");
        List<Object> args = new ArrayList<>();
        if (jobId != null) {
            sql.append(" WHERE job_id = ?");
            args.add(jobId);
        }
        return jdbc.queryForObject(sql.toString(), (rs, rn) -> new OverviewResult(
                rs.getLong("total_records"), rs.getBigDecimal("total_amount"), rs.getInt("categories")
        ), args.toArray());
    }

    public List<MonthlyPoint> monthlyDirect(Instant from, Instant to, UUID jobId) {
        StringBuilder sql = new StringBuilder(
                "SELECT date_trunc('month', occurred_at) AS month, SUM(amount) AS total_amount, COUNT(*) AS tx_count " +
                "FROM transactions WHERE occurred_at >= ? AND occurred_at < ?");
        List<Object> args = new ArrayList<>(List.of(Timestamp.from(from), Timestamp.from(to)));
        if (jobId != null) {
            sql.append(" AND job_id = ?");
            args.add(jobId);
        }
        sql.append(" GROUP BY 1 ORDER BY 1");
        return jdbc.query(sql.toString(), (rs, rn) -> new MonthlyPoint(
                rs.getTimestamp("month").toInstant().atZone(ZoneOffset.UTC).toLocalDate(),
                rs.getBigDecimal("total_amount"), rs.getLong("tx_count")
        ), args.toArray());
    }

    public List<MonthlyPoint> monthlySummary(LocalDate from, LocalDate to, UUID jobId) {
        StringBuilder sql = new StringBuilder(
                "SELECT month, SUM(total_amount) AS total_amount, SUM(tx_count) AS tx_count " +
                "FROM category_month_summary WHERE month >= ? AND month < ?");
        List<Object> args = new ArrayList<>(List.of(Date.valueOf(from), Date.valueOf(to)));
        if (jobId != null) {
            sql.append(" AND job_id = ?");
            args.add(jobId);
        }
        sql.append(" GROUP BY month ORDER BY month");
        return jdbc.query(sql.toString(), (rs, rn) -> new MonthlyPoint(
                rs.getDate("month").toLocalDate(), rs.getBigDecimal("total_amount"), rs.getLong("tx_count")
        ), args.toArray());
    }

    public List<CategoryPoint> categoriesDirect(Instant from, Instant to, UUID jobId) {
        StringBuilder sql = new StringBuilder(
                "SELECT category, SUM(amount) AS total_amount, COUNT(*) AS tx_count " +
                "FROM transactions WHERE occurred_at >= ? AND occurred_at < ?");
        List<Object> args = new ArrayList<>(List.of(Timestamp.from(from), Timestamp.from(to)));
        if (jobId != null) {
            sql.append(" AND job_id = ?");
            args.add(jobId);
        }
        sql.append(" GROUP BY category ORDER BY 2 DESC");
        return jdbc.query(sql.toString(), (rs, rn) -> new CategoryPoint(
                rs.getString("category"), rs.getBigDecimal("total_amount"), rs.getLong("tx_count")
        ), args.toArray());
    }

    public List<CategoryPoint> categoriesSummary(LocalDate from, LocalDate to, UUID jobId) {
        StringBuilder sql = new StringBuilder(
                "SELECT category, SUM(total_amount) AS total_amount, SUM(tx_count) AS tx_count " +
                "FROM category_month_summary WHERE month >= ? AND month < ?");
        List<Object> args = new ArrayList<>(List.of(Date.valueOf(from), Date.valueOf(to)));
        if (jobId != null) {
            sql.append(" AND job_id = ?");
            args.add(jobId);
        }
        sql.append(" GROUP BY category ORDER BY 2 DESC");
        return jdbc.query(sql.toString(), (rs, rn) -> new CategoryPoint(
                rs.getString("category"), rs.getBigDecimal("total_amount"), rs.getLong("tx_count")
        ), args.toArray());
    }
}
