package com.example.ingestion.imports;

public class IngestionQueueFullException extends RuntimeException {

    public IngestionQueueFullException() {
        super("Ingestion queue is full");
    }
}
