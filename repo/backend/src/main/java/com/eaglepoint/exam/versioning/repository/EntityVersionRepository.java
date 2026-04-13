package com.eaglepoint.exam.versioning.repository;

import com.eaglepoint.exam.versioning.model.EntityVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * JPA repository for {@link EntityVersion}.
 */
@Repository
public interface EntityVersionRepository extends JpaRepository<EntityVersion, Long> {

    List<EntityVersion> findByEntityTypeAndEntityIdOrderByVersionNumberDesc(
            String entityType, Long entityId);

    Optional<EntityVersion> findByEntityTypeAndEntityIdAndVersionNumber(
            String entityType, Long entityId, int versionNumber);

    long countByEntityTypeAndEntityId(String entityType, Long entityId);
}
