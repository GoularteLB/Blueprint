package com.example.ingestion.transactions;

import com.example.ingestion.transactions.model.Transaction;
import com.example.ingestion.transactions.model.TransactionFilter;
import com.example.ingestion.transactions.model.TransactionPage;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TransactionController {

    private final TransactionRepository repository;

    public TransactionController(TransactionRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/api/transactions")
    public TransactionPage list(@RequestParam(defaultValue = "50") int size,
                                 @RequestParam(required = false) String cursor,
                                 @RequestParam(required = false) String category,
                                 @RequestParam(required = false) String from,
                                 @RequestParam(required = false) String to,
                                 @RequestParam(required = false) UUID jobId) {
        int pageSize = Math.min(Math.max(size, 1), 200);
        Cursor decodedCursor = cursor == null ? null : Cursor.decode(cursor);
        TransactionFilter filter = new TransactionFilter(
                category, jobId,
                from == null ? null : Instant.parse(from),
                to == null ? null : Instant.parse(to)
        );

        List<Transaction> rows = repository.page(filter, decodedCursor, pageSize);
        boolean hasMore = rows.size() > pageSize;
        List<Transaction> data = hasMore ? rows.subList(0, pageSize) : rows;

        String nextCursor = hasMore
                ? new Cursor(data.get(data.size() - 1).occurredAt(), data.get(data.size() - 1).id()).encode()
                : null;

        return new TransactionPage(data, nextCursor, hasMore);
    }
}
