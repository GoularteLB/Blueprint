package com.example.ingestion.imports;

import com.example.ingestion.imports.model.ImportJob;
import com.example.ingestion.imports.model.ImportStatusResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/imports")
public class ImportController {

    private final ImportService service;
    private final ImportJobRepository repository;

    public ImportController(ImportService service, ImportJobRepository repository) {
        this.service = service;
        this.repository = repository;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty() || !hasCsvName(file.getOriginalFilename())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file must be a non-empty .csv");
        }
        ImportJob job = service.accept(file);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "jobId", job.id(),
                "status", job.status(),
                "fileName", job.fileName(),
                "fileSizeBytes", job.fileSizeBytes()
        ));
    }

    @GetMapping("/{id}")
    public ImportStatusResponse get(@PathVariable UUID id) {
        ImportJob job = repository.find(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return ImportStatusResponse.from(job);
    }

    @GetMapping
    public List<ImportStatusResponse> list() {
        return repository.findAll().stream().map(ImportStatusResponse::from).toList();
    }

    private boolean hasCsvName(String fileName) {
        return fileName != null && fileName.toLowerCase().endsWith(".csv");
    }
}
