package com.example.ingestion.imports;

public class RowValidationException extends RuntimeException {

    public RowValidationException(String message) {
        super(message);
    }
}
