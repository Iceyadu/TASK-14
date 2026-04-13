package com.eaglepoint.exam.shared.dto;

/**
 * Represents a single field-level validation error.
 */
public record FieldError(String field, String message) {
}
