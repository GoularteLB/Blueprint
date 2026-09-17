package com.example.ingestion.imports;

import com.example.ingestion.imports.model.ImportJob;
import com.example.ingestion.infra.IngestionProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ImportService {

    private final Semaphore admission;
    private final ImportJobRepository repository;
    private final IngestionWorker worker;
    private final Path uploadDir;

    public ImportService(Semaphore admissionSemaphore, ImportJobRepository repository,
                          IngestionWorker worker, IngestionProperties props) throws IOException {
        this.admission = admissionSemaphore;
        this.repository = repository;
        this.worker = worker;
        this.uploadDir = Path.of(props.uploadDir());
        Files.createDirectories(this.uploadDir);
    }

    public ImportJob accept(MultipartFile file) throws IOException {
        if (!admission.tryAcquire()) {
            throw new IngestionQueueFullException();
        }
        try {
            UUID id = UUID.randomUUID();
            Path target = uploadDir.resolve(id + ".csv");
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target);
            }
            ImportJob job = ImportJob.pending(id, file.getOriginalFilename(), target, Files.size(target));
            repository.insert(job);
            worker.process(id);
            return job;
        } catch (RuntimeException | IOException e) {
            admission.release();
            throw e;
        }
    }
}
