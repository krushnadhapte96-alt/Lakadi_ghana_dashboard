package com.ghana.dashboard.common;

import java.util.Map;

public class DomainException extends RuntimeException {

    private final int status;
    private final Map<String, java.util.List<String>> errors;

    public DomainException(int status, String message) {
        super(message);
        this.status = status;
        this.errors = null;
    }

    public DomainException(int status, String message, Map<String, java.util.List<String>> errors) {
        super(message);
        this.status = status;
        this.errors = errors;
    }

    public int getStatus() {
        return status;
    }

    public Map<String, java.util.List<String>> getErrors() {
        return errors;
    }
}