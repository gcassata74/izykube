package com.izylife.izykube.exceptions;

public class DiagramValidationException extends RuntimeException {
    public DiagramValidationException(String message) {
        super(message);
    }

    public DiagramValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}

