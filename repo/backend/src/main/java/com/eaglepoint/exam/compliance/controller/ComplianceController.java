package com.eaglepoint.exam.compliance.controller;

import com.eaglepoint.exam.compliance.dto.ReviewDecisionRequest;
import com.eaglepoint.exam.compliance.model.ComplianceReview;
import com.eaglepoint.exam.compliance.service.ComplianceReviewService;
import com.eaglepoint.exam.security.annotation.RequirePermission;
import com.eaglepoint.exam.shared.dto.ApiResponse;
import com.eaglepoint.exam.shared.dto.PaginationInfo;
import com.eaglepoint.exam.shared.enums.Permission;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for compliance review management.
 */
@RestController
@RequestMapping("/api/compliance/reviews")
public class ComplianceController {

    private final ComplianceReviewService complianceReviewService;

    public ComplianceController(ComplianceReviewService complianceReviewService) {
        this.complianceReviewService = complianceReviewService;
    }

    /**
     * Lists pending compliance reviews, paginated.
     */
    @GetMapping
    @RequirePermission(Permission.COMPLIANCE_REVIEW)
    public ResponseEntity<ApiResponse<List<ComplianceReview>>> listPendingReviews(Pageable pageable) {
        Page<ComplianceReview> page = complianceReviewService.listPendingReviews(pageable);

        PaginationInfo pagination = new PaginationInfo(
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());

        return ResponseEntity.ok(ApiResponse.success(page.getContent(), pagination));
    }

    /**
     * Returns a single compliance review by ID.
     */
    @GetMapping("/{id}")
    @RequirePermission(Permission.COMPLIANCE_REVIEW)
    public ResponseEntity<ApiResponse<ComplianceReview>> getReview(@PathVariable Long id) {
        ComplianceReview review = complianceReviewService.getReview(id);
        return ResponseEntity.ok(ApiResponse.success(review));
    }

    /**
     * Approves a pending compliance review.
     */
    @PostMapping("/{id}/approve")
    @RequirePermission(Permission.COMPLIANCE_REVIEW)
    public ResponseEntity<ApiResponse<ComplianceReview>> approve(
            @PathVariable Long id,
            @Valid @RequestBody ReviewDecisionRequest request) {

        ComplianceReview review = complianceReviewService.approve(id, request.getComment());
        return ResponseEntity.ok(ApiResponse.success(review));
    }

    /**
     * Rejects a pending compliance review.
     */
    @PostMapping("/{id}/reject")
    @RequirePermission(Permission.COMPLIANCE_REVIEW)
    public ResponseEntity<ApiResponse<ComplianceReview>> reject(
            @PathVariable Long id,
            @Valid @RequestBody ReviewDecisionRequest request) {

        ComplianceReview review = complianceReviewService.reject(
                id, request.getComment(), request.getRequiredChanges());
        return ResponseEntity.ok(ApiResponse.success(review));
    }
}
