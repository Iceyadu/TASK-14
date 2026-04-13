package com.eaglepoint.exam.imports.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Represents a single row parsed from an import file, with validation results.
 */
@Entity
@Table(name = "import_job_rows")
public class ImportJobRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "import_job_id", nullable = false)
    private Long importJobId;

    @Column(name = "row_number", nullable = false)
    private int rowNumber;

    @Column(name = "row_data", nullable = false, columnDefinition = "json")
    private String rowDataJson;

    @Column(name = "is_valid", nullable = false)
    private boolean isValid;

    @Column(name = "error_details", columnDefinition = "json")
    private String errorDetailsJson;

    public ImportJobRow() {
    }

    // ---- Getters / Setters ----

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getImportJobId() {
        return importJobId;
    }

    public void setImportJobId(Long importJobId) {
        this.importJobId = importJobId;
    }

    public int getRowNumber() {
        return rowNumber;
    }

    public void setRowNumber(int rowNumber) {
        this.rowNumber = rowNumber;
    }

    public String getRowDataJson() {
        return rowDataJson;
    }

    public void setRowDataJson(String rowDataJson) {
        this.rowDataJson = rowDataJson;
    }

    public boolean getIsValid() {
        return isValid;
    }

    public void setIsValid(boolean isValid) {
        this.isValid = isValid;
    }

    public String getErrorDetailsJson() {
        return errorDetailsJson;
    }

    public void setErrorDetailsJson(String errorDetailsJson) {
        this.errorDetailsJson = errorDetailsJson;
    }
}
