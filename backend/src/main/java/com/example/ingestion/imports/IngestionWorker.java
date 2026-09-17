package com.example.ingestion.imports;

import com.example.ingestion.analytics.AnalyticsRepository;
import com.example.ingestion.imports.model.ImportJob;
import com.example.ingestion.infra.IngestionProperties;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class IngestionWorker {

    private static final String INSERT_SQL = """
            INSERT INTO transactions (job_id, external_id, occurred_at, category, description, amount, source)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

    private final ImportJobRepository jobs;
    private final CsvRowParser rowParser;
    private final AnalyticsRepository summaryRepo;
    private final TransactionTemplate txTemplate;
    private final JdbcTemplate jdbc;
    private final IngestionProperties props;
    private final Semaphore admission;

    public IngestionWorker(ImportJobRepository jobs, CsvRowParser rowParser, AnalyticsRepository summaryRepo,
                            TransactionTemplate txTemplate, JdbcTemplate jdbc, IngestionProperties props,
                            Semaphore admissionSemaphore) {
        this.jobs = jobs;
        this.rowParser = rowParser;
        this.summaryRepo = summaryRepo;
        this.txTemplate = txTemplate;
        this.jdbc = jdbc;
        this.props = props;
        this.admission = admissionSemaphore;
    }

    @Async("ingestionExecutor")
    public void process(UUID jobId) {
        ErrorSampler errors = new ErrorSampler(props.maxErrorSamples(), props.maxErrorRawLength());
        SummaryAccumulator summary = new SummaryAccumulator();
        try {
            ImportJob job = jobs.find(jobId).orElseThrow();
            jobs.markRunning(jobId);

            try (BatchReader reader = BatchReader.open(job.filePath(), rowParser, errors, props.batchSize())) {
                while (reader.hasMore()) {
                    txTemplate.executeWithoutResult(status -> {
                        if (!props.synchronousCommit()) {
                            jdbc.execute("SET LOCAL synchronous_commit = off");
                        }

                        for (int n = 0; n < props.commitEveryNBatches() && reader.hasMore(); n++) {
                            List<Object[]> batch = reader.nextBatch(jobId, summary);
                            if (!batch.isEmpty()) {
                                jdbc.batchUpdate(INSERT_SQL, batch);
                            }
                        }
                        summaryRepo.upsertAll(summary.snapshot());
                        jobs.updateProgress(jobId, reader.bytesRead(), reader.lines(), reader.ok(), reader.errors());

                        if (!reader.hasMore()) {
                            jobs.markCompleted(jobId, errors.sample());
                        }
                    });
                    summary.clear();
                }
            }
        } catch (Exception e) {
            jobs.markFailed(jobId, e.getMessage(), errors.sample());
        } finally {
            admission.release();
        }
    }
}
