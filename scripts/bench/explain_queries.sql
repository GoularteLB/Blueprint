EXPLAIN (ANALYZE, BUFFERS)
SELECT id, external_id, occurred_at, category, description, amount, source
  FROM transactions
 ORDER BY occurred_at DESC, id DESC
 LIMIT 51;

EXPLAIN (ANALYZE, BUFFERS)
SELECT id, external_id, occurred_at, category, description, amount, source
  FROM transactions
 WHERE (occurred_at, id) < ('2026-03-12T10:20:00Z', 99822)
 ORDER BY occurred_at DESC, id DESC
 LIMIT 51;

EXPLAIN (ANALYZE, BUFFERS)
SELECT id, external_id, occurred_at, category, description, amount, source
  FROM transactions
 ORDER BY occurred_at DESC, id DESC
 LIMIT 51 OFFSET 950000;

EXPLAIN (ANALYZE, BUFFERS)
SELECT date_trunc('month', occurred_at) AS month, SUM(amount), COUNT(*)
  FROM transactions
 WHERE occurred_at >= '2025-09-01T00:00:00Z' AND occurred_at < '2026-09-01T00:00:00Z'
 GROUP BY 1 ORDER BY 1;

EXPLAIN (ANALYZE, BUFFERS)
SELECT category, SUM(amount), COUNT(*)
  FROM transactions
 WHERE occurred_at >= '2025-09-01T00:00:00Z' AND occurred_at < '2026-09-01T00:00:00Z'
 GROUP BY category ORDER BY 2 DESC;

EXPLAIN (ANALYZE, BUFFERS)
SELECT month, SUM(total_amount), SUM(tx_count)
  FROM category_month_summary
 WHERE month >= '2025-09-01' AND month < '2026-09-01'
 GROUP BY month ORDER BY month;
