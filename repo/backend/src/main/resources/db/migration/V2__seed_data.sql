-- ============================================================================
-- V2 - Seed Data
-- ============================================================================
-- BCrypt hash for "Admin@12345678" with strength 12:
-- $2a$12$LJ3m4ys3Gzl0E3hUwV0PXO4XEcGVOPkBpMCDqfGKxredf1FDwl5pO

-- ---------------------------------------------------------------------------
-- Users
-- ---------------------------------------------------------------------------

INSERT INTO users (id, username, password_hash, full_name, role, allow_concurrent_sessions) VALUES
(1, 'admin',           '$2a$12$LJ3m4ys3Gzl0E3hUwV0PXO4XEcGVOPkBpMCDqfGKxredf1FDwl5pO', 'System Administrator',     'ADMIN',                  TRUE),
(2, 'coord.wang',      '$2a$12$LJ3m4ys3Gzl0E3hUwV0PXO4XEcGVOPkBpMCDqfGKxredf1FDwl5pO', 'Wang Wei',                 'ACADEMIC_COORDINATOR',   FALSE),
(3, 'teacher.li',      '$2a$12$LJ3m4ys3Gzl0E3hUwV0PXO4XEcGVOPkBpMCDqfGKxredf1FDwl5pO', 'Li Mei',                   'HOMEROOM_TEACHER',       FALSE),
(4, 'teacher.zhang',   '$2a$12$LJ3m4ys3Gzl0E3hUwV0PXO4XEcGVOPkBpMCDqfGKxredf1FDwl5pO', 'Zhang Hua',                'SUBJECT_TEACHER',        FALSE),
(5, 'student.chen',    '$2a$12$LJ3m4ys3Gzl0E3hUwV0PXO4XEcGVOPkBpMCDqfGKxredf1FDwl5pO', 'Chen Xiao Ming',           'STUDENT',                FALSE),
(6, 'student.liu',     '$2a$12$LJ3m4ys3Gzl0E3hUwV0PXO4XEcGVOPkBpMCDqfGKxredf1FDwl5pO', 'Liu Fang',                 'STUDENT',                FALSE),
(7, 'student.zhao',    '$2a$12$LJ3m4ys3Gzl0E3hUwV0PXO4XEcGVOPkBpMCDqfGKxredf1FDwl5pO', 'Zhao Lin',                 'STUDENT',                FALSE);

-- Store initial password in history for all users
INSERT INTO password_history (user_id, password_hash) VALUES
(1, '$2a$12$LJ3m4ys3Gzl0E3hUwV0PXO4XEcGVOPkBpMCDqfGKxredf1FDwl5pO'),
(2, '$2a$12$LJ3m4ys3Gzl0E3hUwV0PXO4XEcGVOPkBpMCDqfGKxredf1FDwl5pO'),
(3, '$2a$12$LJ3m4ys3Gzl0E3hUwV0PXO4XEcGVOPkBpMCDqfGKxredf1FDwl5pO'),
(4, '$2a$12$LJ3m4ys3Gzl0E3hUwV0PXO4XEcGVOPkBpMCDqfGKxredf1FDwl5pO'),
(5, '$2a$12$LJ3m4ys3Gzl0E3hUwV0PXO4XEcGVOPkBpMCDqfGKxredf1FDwl5pO'),
(6, '$2a$12$LJ3m4ys3Gzl0E3hUwV0PXO4XEcGVOPkBpMCDqfGKxredf1FDwl5pO'),
(7, '$2a$12$LJ3m4ys3Gzl0E3hUwV0PXO4XEcGVOPkBpMCDqfGKxredf1FDwl5pO');

-- ---------------------------------------------------------------------------
-- Terms
-- ---------------------------------------------------------------------------

INSERT INTO terms (id, name, start_date, end_date, is_active) VALUES
(1, '2025-2026 Fall Semester',   '2025-09-01', '2026-01-15', FALSE),
(2, '2025-2026 Spring Semester', '2026-02-16', '2026-06-30', TRUE);

-- ---------------------------------------------------------------------------
-- Grades
-- ---------------------------------------------------------------------------

INSERT INTO grades (id, name, level) VALUES
(1, 'Grade 7', 7),
(2, 'Grade 8', 8),
(3, 'Grade 9', 9);

-- ---------------------------------------------------------------------------
-- Campuses
-- ---------------------------------------------------------------------------

INSERT INTO campuses (id, name, address) VALUES
(1, 'Main Campus',  '100 Eagle Point Road, Education District'),
(2, 'South Campus', '200 South Boulevard, Education District');

-- ---------------------------------------------------------------------------
-- Classes
-- ---------------------------------------------------------------------------

INSERT INTO classes (id, name, grade_id, campus_id) VALUES
(1, 'Class 7-A', 1, 1),
(2, 'Class 7-B', 1, 1),
(3, 'Class 8-A', 2, 1),
(4, 'Class 9-A', 3, 2);

-- ---------------------------------------------------------------------------
-- Courses
-- ---------------------------------------------------------------------------

INSERT INTO courses (id, name, grade_id) VALUES
(1, 'Mathematics',  1),
(2, 'English',      2),
(3, 'Science',      3);

-- ---------------------------------------------------------------------------
-- Rooms
-- ---------------------------------------------------------------------------

INSERT INTO rooms (id, campus_id, name, building, capacity, facilities) VALUES
(1, 1, 'Room 101', 'Building A', 40, 'Projector, Whiteboard, CCTV'),
(2, 1, 'Room 201', 'Building A', 35, 'Projector, Whiteboard, CCTV'),
(3, 1, 'Room 301', 'Building B', 50, 'Projector, Whiteboard, CCTV, Air Conditioning'),
(4, 2, 'Room 101', 'Building C', 45, 'Projector, Smartboard, CCTV');

-- ---------------------------------------------------------------------------
-- User Scope Assignments
-- ---------------------------------------------------------------------------

-- Academic Coordinator (coord.wang) - assigned to Main Campus + both terms
INSERT INTO user_scope_assignments (user_id, scope_type, scope_id) VALUES
(2, 'CAMPUS', 1),
(2, 'CAMPUS', 2),
(2, 'TERM',   1),
(2, 'TERM',   2);

-- Homeroom Teacher (teacher.li) - assigned to Class 7-A, Grade 7, Main Campus
INSERT INTO user_scope_assignments (user_id, scope_type, scope_id) VALUES
(3, 'CLASS',  1),
(3, 'GRADE',  1),
(3, 'CAMPUS', 1);

-- Subject Teacher (teacher.zhang) - assigned to Mathematics course, Grade 7 and 8
INSERT INTO user_scope_assignments (user_id, scope_type, scope_id) VALUES
(4, 'COURSE', 1),
(4, 'GRADE',  1),
(4, 'GRADE',  2);

-- ---------------------------------------------------------------------------
-- Roster Entries (encrypted field placeholders use AES-encrypted hex bytes)
-- ---------------------------------------------------------------------------

INSERT INTO roster_entries (id, student_user_id, class_id, term_id, student_id_number_enc, guardian_contact_enc, accommodation_notes_enc, is_deleted) VALUES
(1, 5, 1, 2, X'AABBCCDD0011223344556677AABBCCDD', X'AABBCCDD0011223344556677EEFF0011', X'AABBCCDD00112233445566778899AABB', FALSE),
(2, 6, 1, 2, X'BBCCDDEE0011223344556677AABBCCDD', X'BBCCDDEE0011223344556677EEFF0011', X'BBCCDDEE00112233445566778899AABB', FALSE),
(3, 7, 3, 2, X'CCDDEEFF0011223344556677AABBCCDD', X'CCDDEEFF0011223344556677EEFF0011', X'CCDDEEFF00112233445566778899AABB', FALSE);
