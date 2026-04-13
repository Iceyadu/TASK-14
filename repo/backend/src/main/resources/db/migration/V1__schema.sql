-- ============================================================================
-- V1 - Complete Schema for Secure Exam Scheduling & Notification Management
-- ============================================================================

-- ---------------------------------------------------------------------------
-- Users & Authentication
-- ---------------------------------------------------------------------------

CREATE TABLE users (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    username        VARCHAR(100)    NOT NULL UNIQUE,
    password_hash   VARCHAR(255)    NOT NULL,
    full_name       VARCHAR(200)    NOT NULL,
    role            ENUM('ADMIN','ACADEMIC_COORDINATOR','HOMEROOM_TEACHER','SUBJECT_TEACHER','STUDENT') NOT NULL,
    allow_concurrent_sessions BOOLEAN NOT NULL DEFAULT FALSE,
    locked_until    DATETIME        NULL,
    failed_login_attempts INT       NOT NULL DEFAULT 0,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_users_role (role),
    INDEX idx_users_locked_until (locked_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE password_history (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT          NOT NULL,
    password_hash   VARCHAR(255)    NOT NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_password_history_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_password_history_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE user_scope_assignments (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT          NOT NULL,
    scope_type      ENUM('CAMPUS','GRADE','TERM','CLASS','COURSE') NOT NULL,
    scope_id        BIGINT          NOT NULL,
    CONSTRAINT fk_user_scope_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_scope_user (user_id),
    INDEX idx_scope_type_id (scope_type, scope_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- Sessions & Devices
-- ---------------------------------------------------------------------------

CREATE TABLE sessions (
    id                  BIGINT          AUTO_INCREMENT PRIMARY KEY,
    session_token       VARCHAR(255)    NOT NULL UNIQUE,
    user_id             BIGINT          NOT NULL,
    device_fingerprint  VARCHAR(255)    NULL,
    signing_key         VARCHAR(512)    NULL,
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_active_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at          DATETIME        NOT NULL,
    is_remember_device  BOOLEAN         NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_sessions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_sessions_user (user_id),
    INDEX idx_sessions_expires (expires_at),
    INDEX idx_sessions_device (device_fingerprint)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE managed_devices (
    id                  BIGINT          AUTO_INCREMENT PRIMARY KEY,
    device_fingerprint  VARCHAR(255)    NOT NULL UNIQUE,
    description         VARCHAR(500)    NULL,
    registered_by       BIGINT          NOT NULL,
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_managed_devices_user FOREIGN KEY (registered_by) REFERENCES users(id),
    INDEX idx_managed_devices_registered_by (registered_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- Academic Structure
-- ---------------------------------------------------------------------------

CREATE TABLE terms (
    id          BIGINT          AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100)    NOT NULL,
    start_date  DATE            NOT NULL,
    end_date    DATE            NOT NULL,
    is_active   BOOLEAN         NOT NULL DEFAULT FALSE,
    INDEX idx_terms_active (is_active),
    INDEX idx_terms_dates (start_date, end_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE grades (
    id      BIGINT          AUTO_INCREMENT PRIMARY KEY,
    name    VARCHAR(50)     NOT NULL,
    level   INT             NOT NULL,
    INDEX idx_grades_level (level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE campuses (
    id      BIGINT          AUTO_INCREMENT PRIMARY KEY,
    name    VARCHAR(200)    NOT NULL,
    address VARCHAR(500)    NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE classes (
    id          BIGINT          AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100)    NOT NULL,
    grade_id    BIGINT          NOT NULL,
    campus_id   BIGINT          NOT NULL,
    CONSTRAINT fk_classes_grade FOREIGN KEY (grade_id) REFERENCES grades(id),
    CONSTRAINT fk_classes_campus FOREIGN KEY (campus_id) REFERENCES campuses(id),
    INDEX idx_classes_grade (grade_id),
    INDEX idx_classes_campus (campus_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE courses (
    id          BIGINT          AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(200)    NOT NULL,
    grade_id    BIGINT          NOT NULL,
    CONSTRAINT fk_courses_grade FOREIGN KEY (grade_id) REFERENCES grades(id),
    INDEX idx_courses_grade (grade_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE rooms (
    id          BIGINT          AUTO_INCREMENT PRIMARY KEY,
    campus_id   BIGINT          NOT NULL,
    name        VARCHAR(100)    NOT NULL,
    building    VARCHAR(100)    NULL,
    capacity    INT             NOT NULL DEFAULT 0,
    facilities  TEXT            NULL,
    CONSTRAINT fk_rooms_campus FOREIGN KEY (campus_id) REFERENCES campuses(id),
    INDEX idx_rooms_campus (campus_id),
    INDEX idx_rooms_capacity (capacity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- Roster
-- ---------------------------------------------------------------------------

CREATE TABLE roster_entries (
    id                      BIGINT          AUTO_INCREMENT PRIMARY KEY,
    student_user_id         BIGINT          NOT NULL,
    class_id                BIGINT          NOT NULL,
    term_id                 BIGINT          NOT NULL,
    student_id_number_enc   VARBINARY(512)  NULL,
    guardian_contact_enc    VARBINARY(512)  NULL,
    accommodation_notes_enc VARBINARY(512)  NULL,
    is_deleted              BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_roster_student FOREIGN KEY (student_user_id) REFERENCES users(id),
    CONSTRAINT fk_roster_class FOREIGN KEY (class_id) REFERENCES classes(id),
    CONSTRAINT fk_roster_term FOREIGN KEY (term_id) REFERENCES terms(id),
    INDEX idx_roster_student (student_user_id),
    INDEX idx_roster_class (class_id),
    INDEX idx_roster_term (term_id),
    INDEX idx_roster_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- Exam Scheduling
-- ---------------------------------------------------------------------------

CREATE TABLE exam_sessions (
    id              BIGINT      AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(200) NOT NULL,
    term_id         BIGINT      NOT NULL,
    course_id       BIGINT      NOT NULL,
    campus_id       BIGINT      NOT NULL,
    room_id         BIGINT      NULL,
    scheduled_date  DATE        NOT NULL,
    start_time      TIME        NOT NULL,
    end_time        TIME        NOT NULL,
    status          ENUM('DRAFT','SUBMITTED_FOR_COMPLIANCE_REVIEW','APPROVED','REJECTED','PUBLISHED','UNPUBLISHED','ARCHIVED','RESTORED') NOT NULL DEFAULT 'DRAFT',
    created_by      BIGINT      NOT NULL,
    created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_exam_term FOREIGN KEY (term_id) REFERENCES terms(id),
    CONSTRAINT fk_exam_course FOREIGN KEY (course_id) REFERENCES courses(id),
    CONSTRAINT fk_exam_campus FOREIGN KEY (campus_id) REFERENCES campuses(id),
    CONSTRAINT fk_exam_room FOREIGN KEY (room_id) REFERENCES rooms(id),
    CONSTRAINT fk_exam_creator FOREIGN KEY (created_by) REFERENCES users(id),
    INDEX idx_exam_term (term_id),
    INDEX idx_exam_course (course_id),
    INDEX idx_exam_campus (campus_id),
    INDEX idx_exam_room (room_id),
    INDEX idx_exam_date (scheduled_date),
    INDEX idx_exam_status (status),
    INDEX idx_exam_created_by (created_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE exam_session_classes (
    exam_session_id BIGINT NOT NULL,
    class_id        BIGINT NOT NULL,
    PRIMARY KEY (exam_session_id, class_id),
    CONSTRAINT fk_esc_exam FOREIGN KEY (exam_session_id) REFERENCES exam_sessions(id) ON DELETE CASCADE,
    CONSTRAINT fk_esc_class FOREIGN KEY (class_id) REFERENCES classes(id),
    INDEX idx_esc_class (class_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE proctor_assignments (
    id              BIGINT      AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT      NOT NULL,
    exam_session_id BIGINT      NOT NULL,
    room_id         BIGINT      NOT NULL,
    created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_proctor_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_proctor_exam FOREIGN KEY (exam_session_id) REFERENCES exam_sessions(id) ON DELETE CASCADE,
    CONSTRAINT fk_proctor_room FOREIGN KEY (room_id) REFERENCES rooms(id),
    INDEX idx_proctor_user (user_id),
    INDEX idx_proctor_exam (exam_session_id),
    INDEX idx_proctor_room (room_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- Notifications
-- ---------------------------------------------------------------------------

CREATE TABLE notifications (
    id          BIGINT          AUTO_INCREMENT PRIMARY KEY,
    title       VARCHAR(300)    NOT NULL,
    content     TEXT            NOT NULL,
    event_type  ENUM('SCHEDULE_CHANGE','REVIEW_OUTCOME','CHECK_IN_REMINDER','RESULT_PUBLISHED','GENERAL') NOT NULL,
    target_type ENUM('ALL_STUDENTS','CLASS','GRADE','INDIVIDUAL') NOT NULL,
    status      ENUM('DRAFT','QUEUED','SENDING','DELIVERED','FAILED','RETRIED','CANCELED','EXPIRED','FALLBACK_TO_IN_APP') NOT NULL DEFAULT 'DRAFT',
    created_by  BIGINT          NOT NULL,
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_notifications_creator FOREIGN KEY (created_by) REFERENCES users(id),
    INDEX idx_notifications_event_type (event_type),
    INDEX idx_notifications_status (status),
    INDEX idx_notifications_created_by (created_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE notification_targets (
    id              BIGINT      AUTO_INCREMENT PRIMARY KEY,
    notification_id BIGINT      NOT NULL,
    target_id       BIGINT      NOT NULL,
    CONSTRAINT fk_notif_targets_notif FOREIGN KEY (notification_id) REFERENCES notifications(id) ON DELETE CASCADE,
    INDEX idx_notif_targets_notification (notification_id),
    INDEX idx_notif_targets_target (target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE delivery_status (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    notification_id BIGINT          NOT NULL,
    student_user_id BIGINT          NOT NULL,
    channel         ENUM('WECHAT','IN_APP') NOT NULL,
    status          VARCHAR(50)     NOT NULL,
    attempted_at    DATETIME        NULL,
    delivered_at    DATETIME        NULL,
    failure_reason  TEXT            NULL,
    CONSTRAINT fk_delivery_notification FOREIGN KEY (notification_id) REFERENCES notifications(id) ON DELETE CASCADE,
    CONSTRAINT fk_delivery_student FOREIGN KEY (student_user_id) REFERENCES users(id),
    INDEX idx_delivery_notification (notification_id),
    INDEX idx_delivery_student (student_user_id),
    INDEX idx_delivery_status (status),
    INDEX idx_delivery_channel (channel)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE inbox_messages (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    student_user_id BIGINT          NOT NULL,
    notification_id BIGINT          NULL,
    title           VARCHAR(300)    NOT NULL,
    content         TEXT            NOT NULL,
    is_read         BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_inbox_student FOREIGN KEY (student_user_id) REFERENCES users(id),
    CONSTRAINT fk_inbox_notification FOREIGN KEY (notification_id) REFERENCES notifications(id) ON DELETE SET NULL,
    INDEX idx_inbox_student (student_user_id),
    INDEX idx_inbox_read (is_read),
    INDEX idx_inbox_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE subscription_settings (
    id              BIGINT      AUTO_INCREMENT PRIMARY KEY,
    student_user_id BIGINT      NOT NULL,
    event_type      ENUM('SCHEDULE_CHANGE','REVIEW_OUTCOME','CHECK_IN_REMINDER','RESULT_PUBLISHED','GENERAL') NOT NULL,
    enabled         BOOLEAN     NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_subscription_student FOREIGN KEY (student_user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE KEY uk_subscription (student_user_id, event_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE dnd_settings (
    id              BIGINT      AUTO_INCREMENT PRIMARY KEY,
    student_user_id BIGINT      NOT NULL UNIQUE,
    dnd_start       TIME        NOT NULL,
    dnd_end         TIME        NOT NULL,
    CONSTRAINT fk_dnd_student FOREIGN KEY (student_user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- Compliance & Versioning
-- ---------------------------------------------------------------------------

CREATE TABLE compliance_reviews (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    entity_type     VARCHAR(50)     NOT NULL,
    entity_id       BIGINT          NOT NULL,
    status          ENUM('PENDING','APPROVED','REJECTED','REQUIRES_CHANGES') NOT NULL DEFAULT 'PENDING',
    submitted_by    BIGINT          NOT NULL,
    reviewed_by     BIGINT          NULL,
    comment         TEXT            NULL,
    required_changes TEXT           NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at     DATETIME        NULL,
    CONSTRAINT fk_compliance_submitter FOREIGN KEY (submitted_by) REFERENCES users(id),
    CONSTRAINT fk_compliance_reviewer FOREIGN KEY (reviewed_by) REFERENCES users(id),
    INDEX idx_compliance_entity (entity_type, entity_id),
    INDEX idx_compliance_status (status),
    INDEX idx_compliance_submitted_by (submitted_by),
    INDEX idx_compliance_reviewed_by (reviewed_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE entity_versions (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    entity_type     VARCHAR(50)     NOT NULL,
    entity_id       BIGINT          NOT NULL,
    version_number  INT             NOT NULL,
    snapshot_json   JSON            NOT NULL,
    created_by      BIGINT          NOT NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_entity_version_creator FOREIGN KEY (created_by) REFERENCES users(id),
    INDEX idx_entity_version_entity (entity_type, entity_id),
    INDEX idx_entity_version_number (entity_type, entity_id, version_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- Data Import
-- ---------------------------------------------------------------------------

CREATE TABLE import_jobs (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    file_name       VARCHAR(255)    NOT NULL,
    file_type       ENUM('CSV','XLSX') NOT NULL,
    entity_type     VARCHAR(50)     NOT NULL,
    status          ENUM('UPLOADED','PREVIEWED','VALIDATION_FAILED','PARTIALLY_VALID','APPROVED_FOR_COMMIT','COMMITTED','ROLLED_BACK') NOT NULL DEFAULT 'UPLOADED',
    preview_result  JSON            NULL,
    uploaded_by     BIGINT          NOT NULL,
    idempotency_key VARCHAR(255)    NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    committed_at    DATETIME        NULL,
    CONSTRAINT fk_import_uploader FOREIGN KEY (uploaded_by) REFERENCES users(id),
    INDEX idx_import_status (status),
    INDEX idx_import_uploaded_by (uploaded_by),
    INDEX idx_import_idempotency (idempotency_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE import_job_rows (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    import_job_id   BIGINT          NOT NULL,
    row_number      INT             NOT NULL,
    row_data        JSON            NOT NULL,
    is_valid        BOOLEAN         NOT NULL DEFAULT TRUE,
    error_details   JSON            NULL,
    CONSTRAINT fk_import_row_job FOREIGN KEY (import_job_id) REFERENCES import_jobs(id) ON DELETE CASCADE,
    INDEX idx_import_row_job (import_job_id),
    INDEX idx_import_row_valid (is_valid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- Background Jobs
-- ---------------------------------------------------------------------------

CREATE TABLE job_runs (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    job_type        VARCHAR(50)     NOT NULL,
    entity_id       BIGINT          NULL,
    dedup_key       VARCHAR(255)    NOT NULL UNIQUE,
    shard_key       INT             NOT NULL DEFAULT 0,
    status          ENUM('QUEUED','RUNNING','SUCCEEDED','FAILED','RETRYING','MANUALLY_RERUN','CANCELED') NOT NULL DEFAULT 'QUEUED',
    attempts        INT             NOT NULL DEFAULT 0,
    max_attempts    INT             NOT NULL DEFAULT 3,
    next_retry_at   DATETIME        NULL,
    failure_reason  TEXT            NULL,
    node_id         VARCHAR(100)    NULL,
    created_by      BIGINT          NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at      DATETIME        NULL,
    completed_at    DATETIME        NULL,
    CONSTRAINT fk_job_creator FOREIGN KEY (created_by) REFERENCES users(id),
    INDEX idx_job_status (status),
    INDEX idx_job_type (job_type),
    INDEX idx_job_shard (shard_key),
    INDEX idx_job_next_retry (next_retry_at),
    INDEX idx_job_node (node_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- Audit & Security
-- ---------------------------------------------------------------------------

CREATE TABLE audit_log (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    timestamp       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_id         BIGINT          NULL,
    action          VARCHAR(100)    NOT NULL,
    entity_type     VARCHAR(50)     NULL,
    entity_id       BIGINT          NULL,
    old_state       TEXT            NULL,
    new_state       TEXT            NULL,
    details_json    JSON            NULL,
    ip_address      VARCHAR(45)     NULL,
    session_id      VARCHAR(255)    NULL,
    trace_id        VARCHAR(36)     NULL,
    INDEX idx_audit_timestamp (timestamp),
    INDEX idx_audit_user (user_id),
    INDEX idx_audit_action (action),
    INDEX idx_audit_entity (entity_type, entity_id),
    INDEX idx_audit_trace (trace_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE nonce_replay (
    nonce       VARCHAR(36)     NOT NULL PRIMARY KEY,
    expires_at  DATETIME        NOT NULL,
    INDEX idx_nonce_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE idempotency_keys (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    idempotency_key VARCHAR(255)    NOT NULL,
    user_id         BIGINT          NOT NULL,
    operation_type  VARCHAR(50)     NOT NULL,
    response_json   JSON            NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at      DATETIME        NOT NULL,
    UNIQUE KEY uk_idempotency (idempotency_key, user_id, operation_type),
    INDEX idx_idempotency_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- Anti-Cheat
-- ---------------------------------------------------------------------------

CREATE TABLE anticheat_flags (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    student_user_id BIGINT          NOT NULL,
    rule_type       VARCHAR(50)     NOT NULL,
    details         JSON            NULL,
    status          ENUM('PENDING','DISMISSED','CONFIRMED_FOR_INVESTIGATION') NOT NULL DEFAULT 'PENDING',
    flagged_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_by     BIGINT          NULL,
    review_decision VARCHAR(50)     NULL,
    review_comment  TEXT            NULL,
    reviewed_at     DATETIME        NULL,
    CONSTRAINT fk_anticheat_student FOREIGN KEY (student_user_id) REFERENCES users(id),
    CONSTRAINT fk_anticheat_reviewer FOREIGN KEY (reviewed_by) REFERENCES users(id),
    INDEX idx_anticheat_student (student_user_id),
    INDEX idx_anticheat_status (status),
    INDEX idx_anticheat_rule (rule_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
