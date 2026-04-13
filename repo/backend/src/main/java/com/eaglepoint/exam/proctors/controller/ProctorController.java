package com.eaglepoint.exam.proctors.controller;

import com.eaglepoint.exam.proctors.dto.CreateProctorAssignmentRequest;
import com.eaglepoint.exam.proctors.model.ProctorAssignment;
import com.eaglepoint.exam.proctors.service.ProctorService;
import com.eaglepoint.exam.security.annotation.RequirePermission;
import com.eaglepoint.exam.shared.dto.ApiResponse;
import com.eaglepoint.exam.shared.enums.Permission;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for proctor assignment management.
 */
@RestController
@RequestMapping("/api/proctor-assignments")
public class ProctorController {

    private final ProctorService proctorService;

    public ProctorController(ProctorService proctorService) {
        this.proctorService = proctorService;
    }

    /**
     * Lists proctor assignments for a given exam session.
     */
    @GetMapping
    @RequirePermission(Permission.PROCTOR_ASSIGN)
    public ResponseEntity<ApiResponse<List<ProctorAssignment>>> listAssignments(
            @RequestParam Long examSessionId) {
        List<ProctorAssignment> assignments = proctorService.listAssignments(examSessionId);
        return ResponseEntity.ok(ApiResponse.success(assignments));
    }

    /**
     * Creates a new proctor assignment.
     */
    @PostMapping
    @RequirePermission(Permission.PROCTOR_ASSIGN)
    public ResponseEntity<ApiResponse<ProctorAssignment>> createAssignment(
            @Valid @RequestBody CreateProctorAssignmentRequest request) {
        ProctorAssignment assignment = proctorService.createAssignment(request);
        return ResponseEntity.ok(ApiResponse.success(assignment));
    }

    /**
     * Deletes a proctor assignment.
     */
    @DeleteMapping("/{id}")
    @RequirePermission(Permission.PROCTOR_ASSIGN)
    public ResponseEntity<ApiResponse<Void>> deleteAssignment(@PathVariable Long id) {
        proctorService.deleteAssignment(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
