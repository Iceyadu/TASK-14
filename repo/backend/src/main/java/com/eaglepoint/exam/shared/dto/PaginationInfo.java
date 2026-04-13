package com.eaglepoint.exam.shared.dto;

/**
 * Pagination metadata included in list responses.
 */
public record PaginationInfo(int page, int size, long total, int totalPages) {
}
