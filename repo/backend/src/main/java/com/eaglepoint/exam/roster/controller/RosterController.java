package com.eaglepoint.exam.roster.controller;

import com.eaglepoint.exam.roster.dto.CreateRosterRequest;
import com.eaglepoint.exam.roster.dto.RosterResponse;
import com.eaglepoint.exam.roster.service.RosterService;
import com.eaglepoint.exam.security.annotation.RequirePermission;
import com.eaglepoint.exam.shared.context.RequestContext;
import com.eaglepoint.exam.shared.dto.ApiResponse;
import com.eaglepoint.exam.shared.dto.PaginationInfo;
import com.eaglepoint.exam.shared.enums.Permission;
import com.eaglepoint.exam.shared.enums.Role;
import com.eaglepoint.exam.shared.exception.AccessDeniedException;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.List;

/**
 * REST controller for roster management operations.
 */
@RestController
@RequestMapping("/api/rosters")
public class RosterController {

    private final RosterService rosterService;

    public RosterController(RosterService rosterService) {
        this.rosterService = rosterService;
    }

    /**
     * Lists roster entries with optional filters, paginated.
     */
    @GetMapping
    @RequirePermission(Permission.ROSTER_VIEW)
    public ResponseEntity<ApiResponse<List<RosterResponse>>> listRosterEntries(
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) Long termId,
            @RequestParam(required = false) String search,
            Pageable pageable) {

        Page<RosterResponse> page = rosterService.listRosterEntries(classId, termId, search, pageable);

        PaginationInfo pagination = new PaginationInfo(
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );

        return ResponseEntity.ok(ApiResponse.success(page.getContent(), pagination));
    }

    /**
     * Exports roster entries as CSV within the caller's data scope.
     */
    @GetMapping("/export")
    @RequirePermission(Permission.ROSTER_EXPORT)
    public ResponseEntity<byte[]> exportRoster(
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) Long termId) {
        byte[] csv = rosterService.exportRosterCsv(classId, termId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.setContentDispositionFormData("attachment", "roster_export.csv");
        return ResponseEntity.ok().headers(headers).body(csv);
    }

    /**
     * Creates a new roster entry.
     */
    @PostMapping
    @RequirePermission(Permission.ROSTER_CREATE)
    public ResponseEntity<ApiResponse<RosterResponse>> createRosterEntry(
            @Valid @RequestBody CreateRosterRequest request) {
        RosterResponse response = rosterService.createRosterEntry(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Gets a single roster entry by ID.
     */
    @GetMapping("/{id}")
    @RequirePermission(Permission.ROSTER_VIEW)
    public ResponseEntity<ApiResponse<RosterResponse>> getRosterEntry(@PathVariable Long id) {
        RosterResponse response = rosterService.getRosterEntry(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Updates a roster entry.
     */
    @PutMapping("/{id}")
    @RequirePermission(Permission.ROSTER_CREATE)
    public ResponseEntity<ApiResponse<RosterResponse>> updateRosterEntry(
            @PathVariable Long id,
            @Valid @RequestBody CreateRosterRequest request) {
        RosterResponse response = rosterService.updateRosterEntry(id, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Soft-deletes a roster entry. ADMIN only.
     */
    @DeleteMapping("/{id}")
    @RequirePermission(Permission.ROSTER_DELETE)
    public ResponseEntity<ApiResponse<Void>> deleteRosterEntry(@PathVariable Long id) {
        rosterService.deleteRosterEntry(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
