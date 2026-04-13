package com.eaglepoint.exam.shared.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.UUID;

/**
 * Standard JSON response envelope used by every API endpoint.
 *
 * @param <T> the type of the {@code data} payload
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final String traceId;
    private final String status;
    private final String message;
    private final T data;
    private final List<FieldError> errors;
    private final PaginationInfo pagination;

    private ApiResponse(String status, String message, T data,
                        List<FieldError> errors, PaginationInfo pagination) {
        this.traceId = UUID.randomUUID().toString();
        this.status = status;
        this.message = message;
        this.data = data;
        this.errors = errors;
        this.pagination = pagination;
    }

    // ---- Factory methods ----

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("success", null, data, null, null);
    }

    public static <T> ApiResponse<T> success(T data, PaginationInfo pagination) {
        return new ApiResponse<>("success", null, data, null, pagination);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>("error", message, null, null, null);
    }

    public static <T> ApiResponse<T> error(String message, List<FieldError> fieldErrors) {
        return new ApiResponse<>("error", message, null, fieldErrors, null);
    }

    // ---- Getters ----

    public String getTraceId() {
        return traceId;
    }

    public String getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }

    public List<FieldError> getErrors() {
        return errors;
    }

    public PaginationInfo getPagination() {
        return pagination;
    }
}
