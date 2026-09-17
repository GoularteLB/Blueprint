package com.example.ingestion.imports;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.input.CountingInputStream;

public final class BatchReader implements AutoCloseable {

    private final CountingInputStream countingInputStream;
    private final CSVParser csvParser;
    private final Iterator<CSVRecord> records;
    private final CsvRowParser rowParser;
    private final ErrorSampler errorSampler;
    private final int batchSize;

    private long lines = 0;
    private long ok = 0;
    private long errors = 0;

    private BatchReader(CountingInputStream countingInputStream, CSVParser csvParser,
                         CsvRowParser rowParser, ErrorSampler errorSampler, int batchSize) {
        this.countingInputStream = countingInputStream;
        this.csvParser = csvParser;
        this.records = csvParser.iterator();
        this.rowParser = rowParser;
        this.errorSampler = errorSampler;
        this.batchSize = batchSize;
    }

    public static BatchReader open(Path filePath, CsvRowParser rowParser, ErrorSampler errorSampler,
                                    int batchSize) throws IOException {
        CountingInputStream countingInputStream = new CountingInputStream(Files.newInputStream(filePath));
        Reader reader = new InputStreamReader(countingInputStream, StandardCharsets.UTF_8);
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setTrim(true)
                .build();
        CSVParser csvParser = CSVParser.parse(reader, format);
        return new BatchReader(countingInputStream, csvParser, rowParser, errorSampler, batchSize);
    }

    public boolean hasMore() {
        return records.hasNext();
    }

    public List<Object[]> nextBatch(UUID jobId, SummaryAccumulator summary) {
        List<Object[]> batch = new ArrayList<>(batchSize);
        int taken = 0;
        while (taken < batchSize && records.hasNext()) {
            CSVRecord record = records.next();
            lines++;
            taken++;
            try {
                var row = rowParser.parse(record);
                batch.add(new Object[]{jobId, row.externalId(), Timestamp.from(row.occurredAt()),
                        row.category(), row.description(), row.amount(), row.source()});
                summary.add(jobId, row.occurredAt(), row.category(), row.amount());
                ok++;
            } catch (RowValidationException e) {
                errors++;
                errorSampler.add(record.getRecordNumber(), e.getMessage(), String.join(",", record.values()));
            }
        }
        return batch;
    }

    public long bytesRead() {
        return countingInputStream.getByteCount();
    }

    public long lines() {
        return lines;
    }

    public long ok() {
        return ok;
    }

    public long errors() {
        return errors;
    }

    @Override
    public void close() throws IOException {
        csvParser.close();
    }
}
