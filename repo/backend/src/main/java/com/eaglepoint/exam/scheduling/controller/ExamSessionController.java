package com.eaglepoint.exam.scheduling.controller;

import com.eaglepoint.exam.scheduling.dto.CreateExamSessionRequest;
import com.eaglepoint.exam.scheduling.dto.ExamSessionResponse;
import com.eaglepoint.exam.scheduling.model.ExamSessionStatus;
import com.eaglepoint.exam.scheduling.service.ExamSessionService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for exam session management and lifecycle operations.
 */
@RestController
@RequestMapping("/api/exam-sessions")
public class ExamSessionController {

    private final ExamSessionService examSessionService;

    public ExamSessionController(ExamSessionService examSessionService) {
        this.examSessionService = examSessionService;
    }

    /**
     * Lists exam sessions with optional filters, paginated.
     */
    @GetMapping
    @RequirePermission(Permission.SESSION_VIEW)
    public ResponseEntity<ApiResponse<List<ExamSessionResponse>>> listSessions(
            @RequestParam(required = false) Long termId,
            @RequestParam(required = false) ExamSessionStatus status,
            @RequestParam(required = false) Long campusId,
            Pageable pageable) {

        Page<ExamSessionResponse> page = examSessionService.listSessions(termId, status, campusId, pageable);

        PaginationInfo pagination = new PaginationInfo(
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );

        return ResponseEntity.ok(ApiResponse.success(page.getContent(), pagination));
    }

    /**
     * Creates a new exam session.
     */
    @PostMapping
    @RequirePermission(Permission.SESSION_CREATE)
    public ResponseEntity<ApiResponse<ExamSessionResponse>> createSession(
            @Valid @RequestBody CreateExamSessionRequest request) {
        ExamSessionResponse response = examSessionService.createSession(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Gets a single exam session by ID.
     */
    @GetMapping("/{id}")
    @RequirePermission(Permission.SESSION_VIEW)
    public ResponseEntity<ApiResponse<ExamSessionResponse>> getSession(@PathVariable Long id) {
        ExamSessionResponse response = examSessionService.getSession(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Updates an exam session (only in DRAFT or REJECTED status).
     */
    @PutMapping("/{id}")
    @RequirePermission(Permission.SESSION_EDIT)
    public ResponseEntity<ApiResponse<ExamSessionResponse>> updateSession(
            @PathVariable Long id,
            @Valid @RequestBody CreateExamSessionRequest request) {
        ExamSessionResponse response = examSessionService.updateSession(id, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Submits an exam session for compliance review.
     */
    @PostMapping("/{id}/submit-review")
    @RequirePermission(Permission.SESSION_SUBMIT_REVIEW)
    public ResponseEntity<ApiResponse<ExamSessionResponse>> submitForReview(@PathVariable Long id) {
        ExamSessionResponse response = examSessionService.submitForReview(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Publishes an approved exam session with idempotency support.
     */
    @PostMapping("/{id}/publish")
    @RequirePermission(Permission.SESSION_PUBLISH)
    public ResponseEntity<ApiResponse<ExamSessionResponse>> publishSession(
            @PathVariable Long id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        ExamSessionResponse response = examSessionService.publishSession(id, idempotencyKey);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Unpublishes a published exam session.
     */
    @PostMapping("/{id}/unpublish")
    @RequirePermission(Permission.SESSION_PUBLISH)
    public ResponseEntity<ApiResponse<ExamSessionResponse>> unpublishSession(@PathVariable Long id) {
        ExamSessionResponse response = examSessionService.unpublishSession(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Archives an unpublished exam session. ADMIN only.
     */
    @PostMapping("/{id}/archive")
    @RequirePermission(Permission.SESSION_ARCHIVE)
    public ResponseEntity<ApiResponse<ExamSessionResponse>> archiveSession(@PathVariable Long id) {
        ExamSessionResponse response = examSessionService.archiveSession(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Returns the exam schedule for the authenticated student.
     */
    @GetMapping("/student/schedule")
    @RequirePermission(Permission.SESSION_VIEW)
    public ResponseEntity<ApiResponse<List<ExamSessionResponse>>> getStudentSchedule() {
        Long studentUserId = RequestContext.getUserId();
        List<ExamSessionResponse> schedule = examSessionService.getStudentSchedule(studentUserId);
        return ResponseEntity.ok(ApiResponse.success(schedule));
    }
}
