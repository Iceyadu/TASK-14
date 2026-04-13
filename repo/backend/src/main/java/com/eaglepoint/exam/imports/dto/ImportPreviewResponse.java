package com.eaglepoint.exam.imports.dto;

import com.eaglepoint.exam.imports.model.ImportJobStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Response DTO returned after uploading and previewing an import file.
 * Shape matches what the frontend ImportPreview.vue component expects:
 * - jobId: the import job ID
 * - columns: list of column header names
 * - validRows: array of row objects with rowNumber and data map
 * - invalidRows: array of row objects with rowNumber, data map, and errors
 */
public class ImportPreviewResponse {

    private Long jobId;
    private ImportJobStatus status;
    private int totalRows;
    private List<String> columns;
    private List<PreviewRow> validRows;
    private List<PreviewRow> invalidRows;

    public ImportPreviewResponse() {
    }

    // ---- Getters / Setters ----

    public Long getJobId() {
        return jobId;
    }

    public void setJobId(Long jobId) {
        this.jobId = jobId;
    }

    public ImportJobStatus getStatus() {
        return status;
    }

    public void setStatus(ImportJobStatus status) {
        this.status = status;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(int totalRows) {
        this.totalRows = totalRows;
    }

    public List<String> getColumns() {
        return columns;
    }

    public void setColumns(List<String> columns) {
        this.columns = columns;
    }

    public List<PreviewRow> getValidRows() {
        return validRows;
    }

    public void setValidRows(List<PreviewRow> validRows) {
        this.validRows = validRows;
    }

    public List<PreviewRow> getInvalidRows() {
        return invalidRows;
    }

    public void setInvalidRows(List<PreviewRow> invalidRows) {
        this.invalidRows = invalidRows;
    }

    /**
     * Flattened field-level errors from invalid rows (for APIs/tests that expect a single list).
     */
    public List<ImportRowError> getErrors() {
        if (invalidRows == null || invalidRows.isEmpty()) {
            return List.of();
        }
        List<ImportRowError> out = new ArrayList<>();
        for (PreviewRow row : invalidRows) {
            if (row.getErrors() == null) {
                continue;
            }
            for (RowFieldError fe : row.getErrors()) {
                out.add(new ImportRowError(row.getRowNumber(), fe.getField(), fe.getReason()));
            }
        }
        return out;
    }

    /**
     * Represents a single row in the import preview, with its parsed data
     * and any validation errors.
     */
    public static class PreviewRow {

        private int rowNumber;
        private Map<String, String> data;
        private List<RowFieldError> errors;

        public PreviewRow() {
        }

        public PreviewRow(int rowNumber, Map<String, String> data, List<RowFieldError> errors) {
            this.rowNumber = rowNumber;
            this.data = data;
            this.errors = errors;
        }

        public int getRowNumber() {
            return rowNumber;
        }

        public void setRowNumber(int rowNumber) {
            this.rowNumber = rowNumber;
        }

        public Map<String, String> getData() {
            return data;
        }

        public void setData(Map<String, String> data) {
            this.data = data;
        }

        public List<RowFieldError> getErrors() {
            return errors;
        }

        public void setErrors(List<RowFieldError> errors) {
            this.errors = errors;
        }
    }

    /**
     * Details for a single field-level validation error within a row.
     * Uses "reason" to match frontend expectations.
     */
    public static class RowFieldError {

        private String field;
        private String reason;

        public RowFieldError() {
        }

        public RowFieldError(String field, String reason) {
            this.field = field;
            this.reason = reason;
        }

        public String getField() {
            return field;
        }

        public void setField(String field) {
            this.field = field;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }

    /**
     * Internal helper used during validation before building the final response.
     * Not serialized to JSON.
     */
    public static class ImportRowError {

        private int rowNumber;
        private String field;
        private String errorReason;

        public ImportRowError() {
        }

        public ImportRowError(int rowNumber, String field, String errorReason) {
            this.rowNumber = rowNumber;
            this.field = field;
            this.errorReason = errorReason;
        }

        public int getRowNumber() {
            return rowNumber;
        }

        public String getField() {
            return field;
        }

        public String getErrorReason() {
            return errorReason;
        }

        public String getReason() {
            return errorReason;
        }
    }
}
