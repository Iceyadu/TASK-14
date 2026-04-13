package com.eaglepoint.exam.imports.controller;

import com.eaglepoint.exam.imports.dto.ImportPreviewResponse;
import com.eaglepoint.exam.imports.service.ImportService;
import com.eaglepoint.exam.security.annotation.RequirePermission;
import com.eaglepoint.exam.shared.context.RequestContext;
import com.eaglepoint.exam.shared.dto.ApiResponse;
import com.eaglepoint.exam.shared.enums.Permission;
import com.eaglepoint.exam.shared.enums.Role;
import com.eaglepoint.exam.shared.exception.AccessDeniedException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST controller for roster import operations.
 */
@RestController
@RequestMapping("/api/rosters/import")
public class ImportController {

    private final ImportService importService;

    public ImportController(ImportService importService) {
        this.importService = importService;
    }

    /**
     * Uploads an import file and returns a validation preview.
     */
    @PostMapping("/upload")
    @RequirePermission(Permission.ROSTER_IMPORT)
    public ResponseEntity<ApiResponse<ImportPreviewResponse>> uploadAndPreview(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "entityType", defaultValue = "RosterEntry") String entityType) {

        ImportPreviewResponse response = importService.uploadAndPreview(file, entityType);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Commits valid rows from an import job, creating actual roster entries.
     */
    @PostMapping("/{jobId}/commit")
    @RequirePermission(Permission.ROSTER_IMPORT)
    public ResponseEntity<ApiResponse<Void>> commitImport(
            @PathVariable Long jobId,
            @RequestParam(required = false) String idempotencyKey) {

        importService.commitImport(jobId, idempotencyKey);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * Returns invalid rows from an import job as downloadable CSV.
     */
    @GetMapping("/{jobId}/errors")
    @RequirePermission(Permission.ROSTER_IMPORT)
    public ResponseEntity<byte[]> getImportErrors(@PathVariable Long jobId) {
        String csvContent = importService.getImportErrors(jobId);
        byte[] bytes = csvContent.getBytes();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.setContentDispositionFormData("attachment", "import_errors_" + jobId + ".csv");

        return ResponseEntity.ok()
                .headers(headers)
                .body(bytes);
    }

    /**
     * Rolls back a committed import job. ADMIN only.
     */
    @PostMapping("/{jobId}/rollback")
    @RequirePermission(Permission.ROSTER_DELETE)
    public ResponseEntity<ApiResponse<Void>> rollbackImport(@PathVariable Long jobId) {
        importService.rollbackImport(jobId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
