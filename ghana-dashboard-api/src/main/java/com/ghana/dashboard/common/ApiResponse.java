package com.ghana.dashboard.common;

import java.time.OffsetDateTime;
import java.util.Map;

public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        Map<String, java.util.List<String>> errors,
        int status,
        String timestamp) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "OK", data, null, 200, OffsetDateTime.now().toString());
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, message, data, null, 200, OffsetDateTime.now().toString());
    }

    public static <T> ApiResponse<T> success(int status, String message) {
        return new ApiResponse<>(true, message, null, null, status, OffsetDateTime.now().toString());
    }

    public static ApiResponse<Void> error(int status, String message) {
        return new ApiResponse<>(false, message, null, null, status, OffsetDateTime.now().toString());
    }

    public static ApiResponse<Void> error(int status, String message, Map<String, java.util.List<String>> errors) {
        return new ApiResponse<>(false, message, null, errors, status, OffsetDateTime.now().toString());
    }
}