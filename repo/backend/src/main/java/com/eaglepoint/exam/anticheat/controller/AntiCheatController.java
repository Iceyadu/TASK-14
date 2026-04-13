package com.eaglepoint.exam.anticheat.controller;

import com.eaglepoint.exam.anticheat.dto.ReviewFlagRequest;
import com.eaglepoint.exam.anticheat.model.AntiCheatFlag;
import com.eaglepoint.exam.anticheat.service.AntiCheatService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for anti-cheat flag management.
 */
@RestController
@RequestMapping("/api/anticheat")
public class AntiCheatController {

    private final AntiCheatService antiCheatService;

    public AntiCheatController(AntiCheatService antiCheatService) {
        this.antiCheatService = antiCheatService;
    }

    /**
     * Lists anti-cheat flags with optional status filter, paginated.
     */
    @GetMapping("/flags")
    @RequirePermission(Permission.ANTICHEAT_REVIEW)
    public ResponseEntity<ApiResponse<List<AntiCheatFlag>>> listFlags(
            @RequestParam(required = false) String status,
            Pageable pageable) {

        Page<AntiCheatFlag> page = antiCheatService.listFlags(status, pageable);

        PaginationInfo pagination = new PaginationInfo(
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());

        return ResponseEntity.ok(ApiResponse.success(page.getContent(), pagination));
    }

    /**
     * Reviews an anti-cheat flag with a decision.
     */
    @PostMapping("/flags/{id}/review")
    @RequirePermission(Permission.ANTICHEAT_REVIEW)
    public ResponseEntity<ApiResponse<AntiCheatFlag>> reviewFlag(
            @PathVariable Long id,
            @Valid @RequestBody ReviewFlagRequest request) {

        AntiCheatFlag flag = antiCheatService.reviewFlag(id, request.getDecision(), request.getComment());
        return ResponseEntity.ok(ApiResponse.success(flag));
    }
}
