package com.eaglepoint.exam.versioning.controller;

import com.eaglepoint.exam.security.annotation.RequirePermission;
import com.eaglepoint.exam.shared.dto.ApiResponse;
import com.eaglepoint.exam.shared.enums.Permission;
import com.eaglepoint.exam.versioning.model.EntityVersion;
import com.eaglepoint.exam.versioning.service.VersionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST controller for entity version management.
 */
@RestController
@RequestMapping("/api/versions")
public class VersionController {

    private final VersionService versionService;

    public VersionController(VersionService versionService) {
        this.versionService = versionService;
    }

    /**
     * Lists all versions for an entity, ordered by version number descending.
     */
    @GetMapping("/{entityType}/{entityId}")
    @RequirePermission(Permission.VERSION_VIEW)
    public ResponseEntity<ApiResponse<List<EntityVersion>>> getVersions(
            @PathVariable String entityType,
            @PathVariable Long entityId) {

        List<EntityVersion> versions = versionService.getVersions(entityType, entityId);
        return ResponseEntity.ok(ApiResponse.success(versions));
    }

    /**
     * Returns a specific version of an entity.
     */
    @GetMapping("/{entityType}/{entityId}/{versionNumber}")
    @RequirePermission(Permission.VERSION_VIEW)
    public ResponseEntity<ApiResponse<EntityVersion>> getVersion(
            @PathVariable String entityType,
            @PathVariable Long entityId,
            @PathVariable int versionNumber) {

        EntityVersion version = versionService.getVersion(entityType, entityId, versionNumber);
        return ResponseEntity.ok(ApiResponse.success(version));
    }

    /**
     * Returns two version snapshots for client-side diff comparison.
     */
    @GetMapping("/{entityType}/{entityId}/compare")
    @RequirePermission(Permission.VERSION_VIEW)
    public ResponseEntity<ApiResponse<Map<String, EntityVersion>>> compareVersions(
            @PathVariable String entityType,
            @PathVariable Long entityId,
            @RequestParam int from,
            @RequestParam int to) {

        Map<String, EntityVersion> comparison = versionService.compareVersions(
                entityType, entityId, from, to);
        return ResponseEntity.ok(ApiResponse.success(comparison));
    }

    /**
     * Restores an entity to a previous version by creating a new version with
     * the target version's data.
     */
    @PostMapping("/{entityType}/{entityId}/restore")
    @RequirePermission(Permission.VERSION_RESTORE)
    public ResponseEntity<ApiResponse<EntityVersion>> restoreVersion(
            @PathVariable String entityType,
            @PathVariable Long entityId,
            @RequestParam int targetVersion,
            @RequestParam(required = false) String idempotencyKey) {

        EntityVersion restored = versionService.restoreVersion(
                entityType, entityId, targetVersion, idempotencyKey);
        return ResponseEntity.ok(ApiResponse.success(restored));
    }
}
