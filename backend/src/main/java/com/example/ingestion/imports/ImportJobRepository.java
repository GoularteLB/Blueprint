package com.example.ingestion.imports;

import com.example.ingestion.imports.model.ImportJob;
import com.example.ingestion.imports.model.ImportStatus;
import com.example.ingestion.imports.model.RowError;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.postgresql.util.PGobject;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ImportJobRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public ImportJobRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public void insert(ImportJob job) {
        jdbc.update("""
                INSERT INTO import_job (id, file_name, file_path, file_size_bytes, status, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                job.id(), job.fileName(), job.filePath().toString(), job.fileSizeBytes(),
                job.status().name(), Timestamp.from(job.createdAt()));
    }

    public Optional<ImportJob> find(UUID id) {
        return jdbc.query("SELECT * FROM import_job WHERE id = ?", this::mapRow, id)
                .stream().findFirst();
    }

    public List<ImportJob> findAll() {
        return jdbc.query("SELECT * FROM import_job ORDER BY created_at DESC", this::mapRow);
    }

    public void markRunning(UUID id) {
        jdbc.update("UPDATE import_job SET status = 'RUNNING', started_at = now() WHERE id = ?", id);
    }

    public void updateProgress(UUID id, long bytesRead, long processedLines, long successLines, long errorLines) {
        jdbc.update("""
                UPDATE import_job
                   SET bytes_read = ?, processed_lines = ?, success_lines = ?, error_lines = ?
                 WHERE id = ?
                """, bytesRead, processedLines, successLines, errorLines, id);
    }

    public void markCompleted(UUID id, List<RowError> errorSample) {
        jdbc.update("""
                UPDATE import_job
                   SET status = 'COMPLETED', finished_at = now(), error_sample = ?
                 WHERE id = ?
                """, toJson(errorSample), id);
    }

    public void markFailed(UUID id, String errorMessage, List<RowError> errorSample) {
        jdbc.update("""
                UPDATE import_job
                   SET status = 'FAILED', finished_at = now(), error_message = ?, error_sample = ?
                 WHERE id = ? AND status = 'RUNNING'
                """, errorMessage, toJson(errorSample), id);
    }

    private PGobject toJson(List<RowError> errorSample) {
        try {
            PGobject value = new PGobject();
            value.setType("jsonb");
            value.setValue(objectMapper.writeValueAsString(errorSample));
            return value;
        } catch (JsonProcessingException | SQLException e) {
            throw new IllegalStateException("failed to serialize error sample", e);
        }
    }

    private ImportJob mapRow(ResultSet rs, int rowNum) throws SQLException {
        List<RowError> errorSample;
        String json = rs.getString("error_sample");
        try {
            errorSample = json == null
                    ? List.of()
                    : objectMapper.readValue(json, objectMapper.getTypeFactory().constructCollectionType(List.class, RowError.class));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to deserialize error sample", e);
        }
        Timestamp startedAt = rs.getTimestamp("started_at");
        Timestamp finishedAt = rs.getTimestamp("finished_at");
        return new ImportJob(
                (UUID) rs.getObject("id"),
                rs.getString("file_name"),
                Path.of(rs.getString("file_path")),
                rs.getLong("file_size_bytes"),
                rs.getLong("bytes_read"),
                ImportStatus.valueOf(rs.getString("status")),
                rs.getLong("processed_lines"),
                rs.getLong("success_lines"),
                rs.getLong("error_lines"),
                errorSample,
                rs.getString("error_message"),
                rs.getTimestamp("created_at").toInstant(),
                startedAt == null ? null : startedAt.toInstant(),
                finishedAt == null ? null : finishedAt.toInstant()
        );
    }
}
