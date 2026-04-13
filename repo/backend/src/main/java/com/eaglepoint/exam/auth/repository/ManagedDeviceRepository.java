package com.eaglepoint.exam.auth.repository;

import com.eaglepoint.exam.auth.model.ManagedDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for managed device lookups.
 */
@Repository
public interface ManagedDeviceRepository extends JpaRepository<ManagedDevice, Long> {

    Optional<ManagedDevice> findByDeviceFingerprint(String deviceFingerprint);

    boolean existsByDeviceFingerprint(String deviceFingerprint);
}
