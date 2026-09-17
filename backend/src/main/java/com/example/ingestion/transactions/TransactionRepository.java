package com.example.ingestion.transactions;

import com.example.ingestion.transactions.model.Transaction;
import com.example.ingestion.transactions.model.TransactionFilter;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TransactionRepository {

    private final JdbcTemplate jdbc;

    public TransactionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Transaction> page(TransactionFilter f, Cursor cursor, int size) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, external_id, occurred_at, category, description, amount, source " +
                "FROM transactions WHERE 1=1");
        List<Object> args = new ArrayList<>();

        if (f.category() != null) {
            sql.append(" AND category = ?");
            args.add(f.category());
        }
        if (f.jobId() != null) {
            sql.append(" AND job_id = ?");
            args.add(f.jobId());
        }
        if (f.from() != null) {
            sql.append(" AND occurred_at >= ?");
            args.add(Timestamp.from(f.from()));
        }
        if (f.to() != null) {
            sql.append(" AND occurred_at < ?");
            args.add(Timestamp.from(f.to()));
        }
        if (cursor != null) {
            sql.append(" AND (occurred_at, id) < (?, ?)");
            args.add(Timestamp.from(cursor.ts()));
            args.add(cursor.id());
        }
        sql.append(" ORDER BY occurred_at DESC, id DESC LIMIT ?");
        args.add(size + 1);

        return jdbc.query(sql.toString(), (rs, rowNum) -> new Transaction(
                rs.getLong("id"),
                rs.getString("external_id"),
                rs.getTimestamp("occurred_at").toInstant(),
                rs.getString("category"),
                rs.getString("description"),
                rs.getBigDecimal("amount"),
                rs.getString("source")
        ), args.toArray());
    }
}
