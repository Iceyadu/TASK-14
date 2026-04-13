package com.eaglepoint.exam.notifications.repository;

import com.eaglepoint.exam.notifications.model.InboxMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * JPA repository for {@link InboxMessage}.
 */
@Repository
public interface InboxMessageRepository extends JpaRepository<InboxMessage, Long> {

    Page<InboxMessage> findByStudentUserIdOrderByCreatedAtDesc(Long studentUserId, Pageable pageable);

    Page<InboxMessage> findByStudentUserIdAndIsReadOrderByCreatedAtDesc(Long studentUserId, boolean isRead, Pageable pageable);

    long countByStudentUserIdAndIsReadFalse(Long studentUserId);
}
