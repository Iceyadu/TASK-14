package com.eaglepoint.exam.notifications.repository;

import com.eaglepoint.exam.notifications.model.DndSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * JPA repository for {@link DndSetting}.
 */
@Repository
public interface DndSettingRepository extends JpaRepository<DndSetting, Long> {

    Optional<DndSetting> findByStudentUserId(Long studentUserId);
}
