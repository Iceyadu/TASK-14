package com.eaglepoint.exam.imports.service;

import com.eaglepoint.exam.audit.service.AuditService;
import com.eaglepoint.exam.imports.dto.ImportPreviewResponse;
import com.eaglepoint.exam.imports.dto.ImportPreviewResponse.ImportRowError;
import com.eaglepoint.exam.imports.dto.ImportPreviewResponse.PreviewRow;
import com.eaglepoint.exam.imports.dto.ImportPreviewResponse.RowFieldError;
import com.eaglepoint.exam.imports.model.ImportJob;
import com.eaglepoint.exam.imports.model.ImportJobRow;
import com.eaglepoint.exam.imports.model.ImportJobStatus;
import com.eaglepoint.exam.imports.repository.ImportJobRepository;
import com.eaglepoint.exam.imports.repository.ImportJobRowRepository;
import com.eaglepoint.exam.roster.model.RosterEntry;
import com.eaglepoint.exam.roster.repository.RosterEntryRepository;
import com.eaglepoint.exam.rooms.repository.ClassRepository;
import com.eaglepoint.exam.scheduling.model.Term;
import com.eaglepoint.exam.scheduling.repository.TermRepository;
import com.eaglepoint.exam.security.model.User;
import com.eaglepoint.exam.security.repository.UserRepository;
import com.eaglepoint.exam.security.service.IdempotencyService;
import com.eaglepoint.exam.shared.context.RequestContext;
import com.eaglepoint.exam.shared.enums.Role;
import com.eaglepoint.exam.shared.exception.AccessDeniedException;
import com.eaglepoint.exam.shared.exception.EntityNotFoundException;
import com.eaglepoint.exam.shared.exception.StateTransitionException;
import com.eaglepoint.exam.versioning.service.VersionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service handling the full import lifecycle: file upload, parsing, validation,
 * preview, commit, error reporting, and rollback.
 */
@Service
public class ImportService {

    private static final Logger log = LoggerFactory.getLogger(ImportService.class);

    private static final String[] REQUIRED_HEADERS = {
            "student_username", "class_name", "term_name",
            "student_id_number", "guardian_contact", "accommodation_notes"
    };

    private final ImportJobRepository importJobRepository;
    private final ImportJobRowRepository importJobRowRepository;
    private final RosterEntryRepository rosterEntryRepository;
    private final UserRepository userRepository;
    private final ClassRepository classRepository;
    private final TermRepository termRepository;
    private final IdempotencyService idempotencyService;
    private final VersionService versionService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public ImportService(ImportJobRepository importJobRepository,
                         ImportJobRowRepository importJobRowRepository,
                         RosterEntryRepository rosterEntryRepository,
                         UserRepository userRepository,
                         ClassRepository classRepository,
                         TermRepository termRepository,
                         IdempotencyService idempotencyService,
                         VersionService versionService,
                         AuditService auditService,
                         ObjectMapper objectMapper) {
        this.importJobRepository = importJobRepository;
        this.importJobRowRepository = importJobRowRepository;
        this.rosterEntryRepository = rosterEntryRepository;
        this.userRepository = userRepository;
        this.classRepository = classRepository;
        this.termRepository = termRepository;
        this.idempotencyService = idempotencyService;
        this.versionService = versionService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    /**
     * Uploads and previews an import file. Parses CSV or XLSX, validates each row,
     * and saves ImportJobRow entries without writing to entity tables.
     */
    @Transactional
    public ImportPreviewResponse uploadAndPreview(MultipartFile file, String entityType) {
        Long userId = RequestContext.getUserId();
        String originalFileName = file.getOriginalFilename();
        String fileType = detectFileType(originalFileName);

        // Create import job
        ImportJob job = new ImportJob();
        job.setFileName(originalFileName != null ? originalFileName : "unknown");
        job.setFileType(fileType);
        job.setEntityType(entityType);
        job.setStatus(ImportJobStatus.UPLOADED);
        job.setUploadedBy(userId);
        ImportJob savedJob = importJobRepository.save(job);

        // Parse file into raw rows
        List<Map<String, String>> rawRows;
        try {
            if ("CSV".equals(fileType)) {
                rawRows = parseCsv(file);
            } else {
                rawRows = parseXlsx(file);
            }
        } catch (Exception e) {
            log.error("Failed to parse import file: {}", e.getMessage(), e);
            savedJob.setStatus(ImportJobStatus.VALIDATION_FAILED);
            importJobRepository.save(savedJob);
            ImportPreviewResponse response = new ImportPreviewResponse();
            response.setJobId(savedJob.getId());
            response.setStatus(ImportJobStatus.VALIDATION_FAILED);
            response.setTotalRows(0);
            response.setColumns(List.of(REQUIRED_HEADERS));
            response.setValidRows(List.of());
            response.setInvalidRows(List.of());
            return response;
        }

        // Validate and save row entries
        List<PreviewRow> validPreviewRows = new ArrayList<>();
        List<PreviewRow> invalidPreviewRows = new ArrayList<>();
        Set<String> seenUsernames = new HashSet<>();

        for (int i = 0; i < rawRows.size(); i++) {
            Map<String, String> rowData = rawRows.get(i);
            int rowNumber = i + 1;
            List<ImportRowError> rowErrors = validateRow(rowData, rowNumber, seenUsernames);

            String rowDataJson;
            try {
                rowDataJson = objectMapper.writeValueAsString(rowData);
            } catch (JsonProcessingException e) {
                rowDataJson = "{}";
            }

            ImportJobRow jobRow = new ImportJobRow();
            jobRow.setImportJobId(savedJob.getId());
            jobRow.setRowNumber(rowNumber);
            jobRow.setRowDataJson(rowDataJson);

            if (rowErrors.isEmpty()) {
                jobRow.setIsValid(true);
                jobRow.setErrorDetailsJson(null);
                validPreviewRows.add(new PreviewRow(rowNumber, rowData, null));
            } else {
                jobRow.setIsValid(false);
                try {
                    jobRow.setErrorDetailsJson(objectMapper.writeValueAsString(rowErrors));
                } catch (JsonProcessingException e) {
                    jobRow.setErrorDetailsJson("[]");
                }
                List<RowFieldError> fieldErrors = rowErrors.stream()
                        .map(e -> new RowFieldError(e.getField(), e.getErrorReason()))
                        .collect(Collectors.toList());
                invalidPreviewRows.add(new PreviewRow(rowNumber, rowData, fieldErrors));
            }

            // Track username for duplicate detection
            String username = rowData.get("student_username");
            if (username != null && !username.isBlank()) {
                seenUsernames.add(username.toLowerCase().trim());
            }

            importJobRowRepository.save(jobRow);
        }

        int validCount = validPreviewRows.size();
        int invalidCount = invalidPreviewRows.size();

        // Determine final status
        ImportJobStatus finalStatus;
        if (invalidCount == rawRows.size()) {
            finalStatus = ImportJobStatus.VALIDATION_FAILED;
        } else if (invalidCount > 0) {
            finalStatus = ImportJobStatus.PARTIALLY_VALID;
        } else {
            finalStatus = ImportJobStatus.PREVIEWED;
        }

        savedJob.setStatus(finalStatus);
        importJobRepository.save(savedJob);

        // Build preview response matching frontend expected shape
        ImportPreviewResponse response = new ImportPreviewResponse();
        response.setJobId(savedJob.getId());
        response.setStatus(finalStatus);
        response.setTotalRows(rawRows.size());
        response.setColumns(List.of(REQUIRED_HEADERS));
        response.setValidRows(validPreviewRows);
        response.setInvalidRows(invalidPreviewRows);

        try {
            savedJob.setPreviewResultJson(objectMapper.writeValueAsString(response));
            importJobRepository.save(savedJob);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize preview result JSON", e);
        }

        auditService.logAction("UPLOAD_IMPORT", "ImportJob", savedJob.getId(),
                null, null,
                "Uploaded import file: " + originalFileName
                        + " (" + rawRows.size() + " rows, " + validCount + " valid, " + invalidCount + " invalid)");

        return response;
    }

    /**
     * Commits valid rows from an import job, creating roster entries atomically.
     */
    @Transactional
    public void commitImport(Long jobId, String idempotencyKey) {
        Long userId = RequestContext.getUserId();

        // Check idempotency
        if (idempotencyKey != null) {
            Object existing = idempotencyService.checkAndStore(idempotencyKey, userId, "COMMIT_IMPORT");
            if (existing != null) {
                log.info("Idempotent duplicate detected for COMMIT_IMPORT key={}", idempotencyKey);
                return;
            }
        }

        ImportJob job = importJobRepository.findById(jobId)
                .orElseThrow(() -> new EntityNotFoundException("ImportJob", jobId));

        if (job.getStatus() != ImportJobStatus.PREVIEWED
                && job.getStatus() != ImportJobStatus.PARTIALLY_VALID) {
            throw new StateTransitionException(
                    job.getStatus().name(), ImportJobStatus.COMMITTED.name(),
                    "Import job must be in PREVIEWED or PARTIALLY_VALID status to commit");
        }

        // Get valid rows
        List<ImportJobRow> validRows = importJobRowRepository.findByImportJobIdAndIsValidTrue(jobId);

        if (validRows.isEmpty()) {
            throw new StateTransitionException(
                    job.getStatus().name(), ImportJobStatus.COMMITTED.name(),
                    "No valid rows to commit");
        }

        // Create roster entries for each valid row
        for (ImportJobRow row : validRows) {
            Map<String, String> rowData = deserializeRowData(row.getRowDataJson());

            // Look up student user by username
            String studentUsername = rowData.get("student_username");
            Optional<User> studentUser = userRepository.findByUsername(studentUsername);

            RosterEntry entry = new RosterEntry();
            entry.setStudentUserId(studentUser.map(User::getId).orElse(0L));
            entry.setClassId(resolveClassId(rowData.get("class_name")));
            entry.setTermId(resolveTermId(rowData.get("term_name")));
            entry.setStudentIdNumberEnc(rowData.get("student_id_number"));
            entry.setGuardianContactEnc(rowData.get("guardian_contact"));
            entry.setAccommodationNotesEnc(rowData.get("accommodation_notes"));
            entry.setIsDeleted(false);

            RosterEntry saved = rosterEntryRepository.save(entry);

            // Create version for the new roster entry
            versionService.createVersion("RosterEntry", saved.getId(), saved);
        }

        job.setStatus(ImportJobStatus.COMMITTED);
        job.setCommittedAt(LocalDateTime.now());
        importJobRepository.save(job);

        auditService.logAction("COMMIT_IMPORT", "ImportJob", jobId,
                null, null,
                "Committed import job with " + validRows.size() + " roster entries");

        if (idempotencyKey != null) {
            idempotencyService.storeResponse(idempotencyKey, userId, "COMMIT_IMPORT", Map.of("committed", true));
        }
    }

    /**
     * Returns invalid rows from an import job formatted as downloadable CSV content.
     */
    @Transactional(readOnly = true)
    public String getImportErrors(Long jobId) {
        ImportJob job = importJobRepository.findById(jobId)
                .orElseThrow(() -> new EntityNotFoundException("ImportJob", jobId));

        List<ImportJobRow> invalidRows = importJobRowRepository.findByImportJobIdAndIsValidFalse(jobId);

        StringBuilder csv = new StringBuilder();
        csv.append("row_number,field,error_reason,row_data\n");

        for (ImportJobRow row : invalidRows) {
            String errorDetails = row.getErrorDetailsJson();
            if (errorDetails != null) {
                try {
                    List<Map<String, Object>> errors = objectMapper.readValue(
                            errorDetails,
                            objectMapper.getTypeFactory().constructCollectionType(
                                    List.class, Map.class));
                    for (Map<String, Object> error : errors) {
                        csv.append(row.getRowNumber()).append(",");
                        csv.append(escapeCsvField(String.valueOf(error.get("field")))).append(",");
                        csv.append(escapeCsvField(String.valueOf(error.get("errorReason")))).append(",");
                        csv.append(escapeCsvField(row.getRowDataJson())).append("\n");
                    }
                } catch (JsonProcessingException e) {
                    csv.append(row.getRowNumber()).append(",unknown,parse_error,")
                            .append(escapeCsvField(row.getRowDataJson())).append("\n");
                }
            }
        }

        return csv.toString();
    }

    /**
     * Rolls back a committed import job by soft-deleting the created roster entries.
     * ADMIN only.
     */
    @Transactional
    public void rollbackImport(Long jobId) {
        Role role = RequestContext.getRole();
        if (role != Role.ADMIN) {
            throw new AccessDeniedException("Only ADMIN users can rollback imports");
        }

        ImportJob job = importJobRepository.findById(jobId)
                .orElseThrow(() -> new EntityNotFoundException("ImportJob", jobId));

        if (job.getStatus() != ImportJobStatus.COMMITTED) {
            throw new StateTransitionException(
                    job.getStatus().name(), ImportJobStatus.ROLLED_BACK.name(),
                    "Only COMMITTED imports can be rolled back");
        }

        // Soft-delete committed roster entries created by this import
        List<ImportJobRow> validRows = importJobRowRepository.findByImportJobIdAndIsValidTrue(jobId);
        for (ImportJobRow row : validRows) {
            Map<String, String> rowData = deserializeRowData(row.getRowDataJson());
            String studentUsername = rowData.get("student_username");
            Optional<User> studentUser = userRepository.findByUsername(studentUsername);

            if (studentUser.isPresent()) {
                Long termId = resolveTermId(rowData.get("term_name"));
                List<RosterEntry> entries = rosterEntryRepository
                        .findByStudentUserIdAndTermId(studentUser.get().getId(), termId);
                for (RosterEntry entry : entries) {
                    if (!entry.getIsDeleted()) {
                        entry.setIsDeleted(true);
                        rosterEntryRepository.save(entry);
                    }
                }
            }
        }

        job.setStatus(ImportJobStatus.ROLLED_BACK);
        importJobRepository.save(job);

        auditService.logAction("ROLLBACK_IMPORT", "ImportJob", jobId,
                null, null, "Rolled back import job");
    }

    // ---- CSV / XLSX parsing ----

    private List<Map<String, String>> parseCsv(MultipartFile file) throws Exception {
        List<Map<String, String>> rows = new ArrayList<>();
        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT
                     .builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {

            for (CSVRecord record : parser) {
                Map<String, String> row = new HashMap<>();
                for (String header : REQUIRED_HEADERS) {
                    row.put(header, record.isMapped(header) ? record.get(header) : "");
                }
                rows.add(row);
            }
        }
        return rows;
    }

    private List<Map<String, String>> parseXlsx(MultipartFile file) throws Exception {
        List<Map<String, String>> rows = new ArrayList<>();
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);

            // Read header row
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new IllegalArgumentException("XLSX file has no header row");
            }

            Map<Integer, String> headerMap = new HashMap<>();
            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                Cell cell = headerRow.getCell(i);
                if (cell != null) {
                    headerMap.put(i, getCellStringValue(cell).trim().toLowerCase());
                }
            }

            // Read data rows
            for (int rowIdx = 1; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
                Row row = sheet.getRow(rowIdx);
                if (row == null) {
                    continue;
                }

                Map<String, String> rowData = new HashMap<>();
                for (Map.Entry<Integer, String> header : headerMap.entrySet()) {
                    Cell cell = row.getCell(header.getKey());
                    String value = cell != null ? getCellStringValue(cell) : "";
                    rowData.put(header.getValue(), value.trim());
                }
                rows.add(rowData);
            }
        }
        return rows;
    }

    private String getCellStringValue(Cell cell) {
        if (cell.getCellType() == CellType.STRING) {
            return cell.getStringCellValue();
        } else if (cell.getCellType() == CellType.NUMERIC) {
            double val = cell.getNumericCellValue();
            if (val == Math.floor(val)) {
                return String.valueOf((long) val);
            }
            return String.valueOf(val);
        } else if (cell.getCellType() == CellType.BOOLEAN) {
            return String.valueOf(cell.getBooleanCellValue());
        }
        return "";
    }

    // ---- Validation ----

    private List<ImportRowError> validateRow(Map<String, String> rowData, int rowNumber,
                                             Set<String> seenUsernames) {
        List<ImportRowError> errors = new ArrayList<>();

        // Required field checks
        String studentUsername = rowData.get("student_username");
        if (studentUsername == null || studentUsername.isBlank()) {
            errors.add(new ImportRowError(rowNumber, "student_username", "Required field is missing"));
        }

        String className = rowData.get("class_name");
        if (className == null || className.isBlank()) {
            errors.add(new ImportRowError(rowNumber, "class_name", "Required field is missing"));
        }

        String termName = rowData.get("term_name");
        if (termName == null || termName.isBlank()) {
            errors.add(new ImportRowError(rowNumber, "term_name", "Required field is missing"));
        }

        String studentIdNumber = rowData.get("student_id_number");
        if (studentIdNumber == null || studentIdNumber.isBlank()) {
            errors.add(new ImportRowError(rowNumber, "student_id_number", "Required field is missing"));
        }

        // Duplicate detection (case-insensitive)
        if (studentUsername != null && !studentUsername.isBlank()) {
            String normalized = studentUsername.toLowerCase().trim();
            if (seenUsernames.contains(normalized)) {
                errors.add(new ImportRowError(rowNumber, "student_username",
                        "Duplicate entry: " + studentUsername));
            }
        }

        // Foreign key checks: verify student user exists
        if (studentUsername != null && !studentUsername.isBlank() && errors.isEmpty()) {
            Optional<User> user = userRepository.findByUsername(studentUsername.trim());
            if (user.isEmpty()) {
                errors.add(new ImportRowError(rowNumber, "student_username",
                        "No user found with username: " + studentUsername));
            }
        }

        if (className != null && !className.isBlank()) {
            if (classRepository.findFirstByNameIgnoreCase(className.trim()).isEmpty()) {
                errors.add(new ImportRowError(rowNumber, "class_name",
                        "No class found with name: " + className));
            }
        }

        if (termName != null && !termName.isBlank()) {
            if (termRepository.findFirstByNameIgnoreCase(termName.trim()).isEmpty()) {
                errors.add(new ImportRowError(rowNumber, "term_name",
                        "No term found with name: " + termName));
            }
        }

        return errors;
    }

    // ---- Helpers ----

    private String detectFileType(String fileName) {
        if (fileName != null && fileName.toLowerCase().endsWith(".xlsx")) {
            return "XLSX";
        }
        return "CSV";
    }

    private Map<String, String> deserializeRowData(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            // H2 JSON columns (and some JDBC JSON bindings) may round-trip row objects as a JSON
            // *string* value; unwrap one or more textual layers before reading fields.
            while (root != null && root.isTextual()) {
                String inner = root.asText();
                if (inner == null || inner.isBlank()) {
                    return Map.of();
                }
                root = objectMapper.readTree(inner);
            }
            if (root == null || !root.isObject()) {
                log.warn("Row data JSON is not a JSON object: {}", json);
                return Map.of();
            }
            Map<String, String> result = new HashMap<>();
            root.fields().forEachRemaining(entry -> {
                JsonNode v = entry.getValue();
                if (v == null || v.isNull()) {
                    result.put(entry.getKey(), "");
                } else if (v.isValueNode()) {
                    result.put(entry.getKey(), v.asText());
                } else {
                    result.put(entry.getKey(), v.toString());
                }
            });
            return result;
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize row data JSON", e);
            return Map.of();
        }
    }

    private Long resolveClassId(String className) {
        if (className == null || className.isBlank()) {
            return 0L;
        }
        return classRepository.findFirstByNameIgnoreCase(className.trim())
                .map(c -> c.getId())
                .orElseThrow(() -> new IllegalStateException("Unresolved class name: " + className));
    }

    private Long resolveTermId(String termName) {
        if (termName == null || termName.isBlank()) {
            return 0L;
        }
        return termRepository.findFirstByNameIgnoreCase(termName.trim())
                .map(Term::getId)
                .orElseThrow(() -> new IllegalStateException("Unresolved term name: " + termName));
    }

    private String escapeCsvField(String field) {
        if (field == null) {
            return "";
        }
        if (field.contains(",") || field.contains("\"") || field.contains("\n")) {
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }
        return field;
    }
}
