# System Design Document

## 1. Architecture Overview

The system is a monolithic-modular full-stack application designed for offline K-12 intranet deployment.

```
┌─────────────────────────────────────────────┐
│  Vue.js SPA (Frontend)                      │
│  Role-based navigation, forms, inbox        │
│  Central API client with request signing    │
├─────────────────────────────────────────────┤
│  Nginx (Reverse Proxy / Static Serve)       │
├─────────────────────────────────────────────┤
│  Spring Boot (Backend)                      │
│  ┌─────────┬──────────┬──────────────────┐  │
│  │ Auth    │ Security │ Rate Limiter     │  │
│  │ Filter  │ Filter   │ Filter           │  │
│  ├─────────┴──────────┴──────────────────┤  │
│  │ Request Signing Verification Filter   │  │
│  ├───────────────────────────────────────┤  │
│  │ Controllers (Request DTO validation)  │  │
│  ├───────────────────────────────────────┤  │
│  │ Service Layer (Business rules, RBAC,  │  │
│  │ ABAC, scope, workflow, compliance)    │  │
│  ├───────────────────────────────────────┤  │
│  │ Repository Layer (JPA + Encryption)   │  │
│  ├───────────────────────────────────────┤  │
│  │ Job Scheduler (DB-backed queues)      │  │
│  └───────────────────────────────────────┘  │
├─────────────────────────────────────────────┤
│  MySQL 8.0                                  │
│  Master data, audit, outbox, versions       │
└─────────────────────────────────────────────┘
```

### Deployment Model
- **Docker Compose** orchestrates MySQL, backend, and frontend/nginx containers.
- **Single-node** is the default and mandatory deployment mode.
- **Multi-node** is optional: multiple backend instances share the same MySQL and use database-backed job queues with row-level locking for distributed execution. No external broker required.
- **Fully offline**: zero public internet dependency.

## 2. Module Decomposition

### Backend Modules (under `com.eaglepoint.exam`)

| Module | Package | Responsibility |
|--------|---------|---------------|
| Auth | `auth` | Login, session, password rules, lockout, device management |
| Users | `users` | User CRUD, role assignment, scope assignment |
| Roster | `roster` | Candidate roster management, bulk import/export |
| Scheduling | `scheduling` | Exam sessions, terms, subject sessions |
| Rooms | `rooms` | Campus, building, room management |
| Proctors | `proctors` | Proctor assignment to sessions/rooms |
| Notifications | `notifications` | Outbox, channels, delivery, inbox, subscriptions |
| Compliance | `compliance` | Review queue, approval/rejection, gating |
| Audit | `audit` | Immutable audit trail for all operations |
| Jobs | `jobs` | DB-backed job queue, scheduling, retry, monitor |
| Imports | `imports` | CSV/XLSX parsing, preview, commit workflow |
| Versioning | `versioning` | Version history, snapshots, compare, restore |
| AntiCheat | `anticheat` | Score anomaly detection, flag queue |
| Security | `security` | Filters, signing, encryption, masking, rate limiting |
| Shared | `shared` | DTOs, enums, response envelope, exceptions, utils |

### Frontend Modules (under `src/`)

| Module | Path | Responsibility |
|--------|------|---------------|
| Auth | `views/auth/` | Login, session management |
| Dashboard | `views/dashboard/` | Role-specific landing pages |
| Roster | `views/roster/` | Roster CRUD, import/export |
| Scheduling | `views/scheduling/` | Exam session management |
| Rooms | `views/rooms/` | Campus/room management |
| Proctors | `views/proctors/` | Proctor assignments |
| Notifications | `views/notifications/` | Publish, inbox, subscriptions |
| Compliance | `views/compliance/` | Review queue UI |
| Jobs | `views/jobs/` | Job monitor dashboard |
| Versioning | `components/versioning/` | Version history, compare, restore |
| Import | `components/import/` | Upload, preview, commit UI |
| Shared | `components/shared/` | Tables, forms, guards, API client |

## 3. Role Model

### Roles
1. **ADMIN** - System administrator, full access, manages users and devices
2. **ACADEMIC_COORDINATOR** - Academic affairs coordinator, campus/grade/term scoped
3. **HOMEROOM_TEACHER** - Class-level scope for assigned classes and terms
4. **SUBJECT_TEACHER** - Course/session/class/term scope for assigned subjects
5. **STUDENT** - Personal scope only (own schedule, inbox, subscriptions)

### Permission Matrix

| Permission | ADMIN | ACADEMIC_COORDINATOR | HOMEROOM_TEACHER | SUBJECT_TEACHER | STUDENT |
|-----------|-------|---------------------|-----------------|----------------|---------|
| USER_MANAGE | Yes | No | No | No | No |
| ROSTER_VIEW | Yes | Scoped(campus/grade/term) | Scoped(class/term) | Scoped(course/class/term) | Own only |
| ROSTER_CREATE | Yes | Scoped | No | No | No |
| ROSTER_IMPORT | Yes | Scoped | No | No | No |
| ROSTER_EXPORT | Yes | Scoped | Scoped | Scoped | No |
| SESSION_VIEW | Yes | Scoped | Scoped | Scoped | Own schedule |
| SESSION_CREATE | Yes | Scoped | No | No | No |
| SESSION_EDIT | Yes | Scoped | No | No | No |
| SESSION_SUBMIT_REVIEW | Yes | Scoped | No | No | No |
| SESSION_PUBLISH | Yes | Scoped | No | No | No |
| ROOM_MANAGE | Yes | Scoped(campus) | No | No | No |
| PROCTOR_ASSIGN | Yes | Scoped | No | No | No |
| COMPLIANCE_REVIEW | Yes | No | No | No | No |
| COMPLIANCE_SUBMIT | Yes | Scoped | No | No | No |
| NOTIFICATION_CREATE | Yes | Scoped | Scoped | Scoped | No |
| NOTIFICATION_PUBLISH | Yes | Scoped | No | No | No |
| SUBSCRIPTION_MANAGE | No | No | No | No | Own only |
| INBOX_VIEW | No | No | No | No | Own only |
| VERSION_VIEW | Yes | Scoped | Scoped | Scoped | No |
| VERSION_RESTORE | Yes | Scoped | No | No | No |
| JOB_MONITOR | Yes | Yes(read-only) | No | No | No |
| JOB_RERUN | Yes | No | No | No | No |
| ANTICHEAT_REVIEW | Yes | Yes | No | No | No |
| AUDIT_VIEW | Yes | No | No | No | No |
| VIEW_HEALTH_DATA | Yes | Designated only | No | No | No |
| DEVICE_MANAGE | Yes | No | No | No | No |

### Row-Level Scope Enforcement

Every service method that returns data applies scope filtering:

```
Admin: no scope restriction (all data visible), all access audited
Academic Coordinator: campus_id IN assigned_campuses AND grade_id IN assigned_grades AND term_id IN assigned_terms
Homeroom Teacher: class_id IN assigned_classes AND term_id IN assigned_terms
Subject Teacher: course_id IN assigned_courses AND class_id IN assigned_classes AND term_id IN assigned_terms
Student: student_id = current_user.student_id
```

## 4. Authentication and Session Model

### Login Flow
1. User submits username + password
2. Check account lockout status (15 min after 5 failures)
3. Validate password against BCrypt hash (12+ chars, complexity enforced at registration)
4. Check concurrent session: if active session exists on different device, block unless Admin-allowed
5. Create session record in `sessions` table with: session_id, user_id, device_fingerprint, created_at, last_active_at, expires_at, signing_key
6. Issue session token (UUID) and session signing key (HMAC-SHA256 derived)
7. If remember-device requested: validate device is in managed_devices table, set extended expiry (7 days)
8. Record login in audit log

### Session Expiry
- Inactivity timeout: 30 minutes (last_active_at + 30min)
- Remember-device: 7 days from creation (managed devices only)
- Every request updates last_active_at
- Expired sessions are rejected; user must re-authenticate

### Password Rules
- Minimum 12 characters
- At least 1 uppercase, 1 lowercase, 1 digit, 1 special character
- BCrypt with salt (cost factor 12)
- No password reuse (last 5 passwords stored as hashes)

### Lockout
- 5 consecutive failed attempts triggers 15-minute lockout
- Failed attempt counter resets on successful login
- Lockout events are audited
- Admin can manually unlock accounts

### Concurrent Sessions
- Default: one active session per user
- New login from different device is blocked with clear error
- Admin can set `allow_concurrent_sessions = true` per user (audited)
- Admin can terminate any user's active session (audited)

## 5. Request Signing Model

### Signing Process (Client)
1. On login, client receives `session_signing_key` (derived from session)
2. For each request, compute:
   ```
   string_to_sign = HTTP_METHOD + "\n" + PATH + "\n" + TIMESTAMP + "\n" + NONCE + "\n" + SHA256(BODY)
   signature = HMAC-SHA256(session_signing_key, string_to_sign)
   ```
3. Include headers: `X-Timestamp`, `X-Nonce`, `X-Signature`

### Verification (Server)
1. Parse headers: timestamp, nonce, signature
2. Reject if |current_time - timestamp| > 120 seconds
3. Reject if nonce exists in replay store (MySQL table with TTL cleanup)
4. Recompute expected signature using session's signing key
5. Reject if signatures don't match
6. Store nonce with expiry timestamp
7. Nonce cleanup job runs every 5 minutes, deletes entries older than 120 seconds

### Key Management
- Server secret stored in environment variable (`APP_SIGNING_SECRET`)
- Session signing keys derived: `HMAC-SHA256(APP_SIGNING_SECRET, session_id + user_id)`
- Keys rotate with each new session
- No static secrets in client code

## 6. Workflow State Models

### 6.1 Exam Session States
```
draft -> submitted_for_compliance_review -> approved -> published
                                        -> rejected -> draft (edit & resubmit)
published -> unpublished -> archived
                        -> submitted_for_compliance_review (re-review)
archived -> restored (creates new version in draft state, triggers re-review if was published)
```

Legal transitions enforced in `ExamSessionStateMachine`:
| From | To | Allowed Roles |
|------|----|--------------|
| draft | submitted_for_compliance_review | ADMIN, ACADEMIC_COORDINATOR |
| submitted_for_compliance_review | approved | ADMIN (compliance reviewer) |
| submitted_for_compliance_review | rejected | ADMIN (compliance reviewer) |
| rejected | draft | ADMIN, ACADEMIC_COORDINATOR |
| approved | published | ADMIN, ACADEMIC_COORDINATOR |
| published | unpublished | ADMIN, ACADEMIC_COORDINATOR |
| unpublished | submitted_for_compliance_review | ADMIN, ACADEMIC_COORDINATOR |
| unpublished | archived | ADMIN |
| archived | restored | ADMIN |

### 6.2 Roster Import States
```
uploaded -> previewed -> approved_for_commit -> committed
                     -> validation_failed (all rows invalid)
                     -> partially_valid -> approved_for_commit (after user excludes invalid rows)
committed -> rolled_back (Admin only)
```

### 6.3 Notification States
```
draft -> queued -> sending -> delivered
                           -> failed -> retried -> delivered
                                                -> failed (max retries)
                                                -> fallback_to_in_app
queued -> canceled
draft -> canceled
sending -> expired (delivery window passed)
* -> fallback_to_in_app (WeChat unavailable)
```

### 6.4 Compliance Review States
```
pending -> approved
        -> rejected -> requires_changes -> pending (resubmit)
```

### 6.5 Job Run States
```
queued -> running -> succeeded
                  -> failed -> retrying -> running
                                        -> failed (max retries)
queued -> canceled
failed -> manually_rerun -> queued
```

## 7. Compliance Gating

### Content Requiring Compliance Review
1. Exam session publication (schedule changes visible to students)
2. Notification content targeted to students
3. Review outcomes and results visible to students
4. Check-in reminders with student-facing content
5. Any modification to already-published student-visible content

### Workflow
1. Staff submits content for review (creates `compliance_review` record with status `pending`)
2. Admin with `COMPLIANCE_REVIEW` permission sees pending items in review queue
3. Reviewer approves or rejects with mandatory comment
4. Rejected items return to submitter with feedback
5. Approved items become eligible for publish action
6. Publish is a separate explicit action (approval != auto-publish)
7. Notifications tied to unapproved/unpublished content are blocked from student delivery

### Re-review Triggers
- Editing approved but unpublished content resets to `pending`
- Modifying published content unpublishes it and creates new review request
- Version restore of published content triggers re-review

## 8. Version History and Restore Model

### Versioned Entities
- Exam sessions
- Roster entries
- Room assignments
- Proctor assignments
- Notifications
- Compliance review decisions

### Storage
- `entity_versions` table stores: entity_type, entity_id, version_number, snapshot_json, created_by, created_at
- Each create/update creates a new version entry
- Snapshots are complete JSON representations of the entity at that point
- Versions are append-only and immutable

### Operations
- **Compare**: any two versions of the same entity can be compared (diff computed server-side)
- **Restore**: creates a NEW version (version_number = max + 1) with the content from the selected historical version
- Restore preserves all prior versions unchanged
- Restore of student-visible content triggers compliance re-review

### Access Control
- View versions: ADMIN, ACADEMIC_COORDINATOR, HOMEROOM_TEACHER (scoped), SUBJECT_TEACHER (scoped)
- Restore: ADMIN, ACADEMIC_COORDINATOR (scoped)
- Students cannot view version history

## 9. Import/Export Model

### Import Workflow (Two-Step)
1. **Upload & Preview**
   - Accept CSV or XLSX file
   - Parse and validate all rows without writing to database
   - Return preview with: valid rows, invalid rows with per-field errors (row number, field name, error reason)
   - Detect duplicates (case-insensitive), invalid formats, foreign key mismatches
   - Create `import_job` record with status `uploaded` -> `previewed`

2. **Commit**
   - User reviews preview and triggers commit
   - Atomic transaction: all valid rows committed or none
   - Import job transitions to `committed`
   - Each imported row creates version history entry and audit record
   - Idempotent via import_job_id as dedup key

### Export
- Same scope filtering as list endpoints
- Formats: CSV, XLSX
- Audit logged
- Sensitive fields encrypted/masked per user permissions

### Sample Files
- `repo/backend/src/main/resources/samples/roster_import_sample.csv`
- `repo/backend/src/main/resources/samples/roster_import_sample.xlsx`

## 10. Notification and Fallback Model

### Channels
1. **In-App Inbox** (always available, primary channel)
2. **WeChat** (optional, intranet-only deployment)

### Delivery Flow
1. Staff creates notification -> `draft`
2. If student-targeted: must pass compliance review
3. After approval + publish: notification enters outbox as `queued`
4. Job processor picks up queued notifications:
   a. Check student subscription settings (opt-in/out by event type)
   b. Check DND window (if active, hold for inbox delivery after DND ends)
   c. Attempt WeChat delivery if configured and available
   d. On WeChat failure (after 3 retries): create in-app inbox entry (`fallback_to_in_app`)
   e. If WeChat not deployed: direct to in-app inbox
5. Track per-channel delivery status

### Student Subscription Settings
- Per event type: SCHEDULE_CHANGE, REVIEW_OUTCOME, CHECK_IN_REMINDER, RESULT_PUBLISHED, GENERAL
- Opt in/out toggle per type
- DND window: start_time, end_time (daily recurring)
- DND suppressed notifications delivered to inbox when window ends

### Delivery Status
- Per notification per student: channel, status, attempted_at, delivered_at, failure_reason

## 11. Encryption and Masking Model

### Encrypted Fields (AES-256-GCM)
- student_id_number (national/school ID)
- guardian_contact (phone/address)
- accommodation_notes (health-related)

### Implementation
- Application-layer encryption before persistence
- Encryption key from environment variable (`APP_ENCRYPTION_KEY`)
- Ciphertext stored in MySQL VARBINARY columns
- Decryption only in service layer for authorized users
- Search over encrypted fields: not supported (documented limitation)

### Masking
- API responses mask sensitive fields by default (e.g., `student_id: "****1234"`)
- Full value available only with explicit `VIEW_SENSITIVE_DATA` permission
- Audit logs mask sensitive values (log "field modified" not the value)

## 12. Audit and Lineage Model

### Audited Events
- Authentication (login, logout, failed, lockout)
- CRUD operations on all entities
- State transitions (workflow changes)
- Compliance review decisions
- Publish/unpublish actions
- Import preview and commit
- Export operations
- Version restore
- Job runs and reruns
- Notification delivery attempts
- Permission changes
- Device registration
- Concurrent session blocks

### Audit Record Structure
```
audit_id, timestamp, user_id, action, entity_type, entity_id,
old_state, new_state, details_json, ip_address, session_id, trace_id
```

### Retention
- Audit records are append-only
- No deletion API
- Viewable by ADMIN only via audit log viewer

## 13. Jobs and Deduplication Model

### Job Types
- NOTIFICATION_SEND: process notification outbox
- BULK_IMPORT: process committed import jobs
- DATA_CHECK: periodic data integrity checks
- NONCE_CLEANUP: clean expired nonces from replay store
- IDEMPOTENCY_CLEANUP: clean expired idempotency keys
- DND_RELEASE: release held notifications after DND window ends

### Execution Model
- Database-backed job queue (`job_runs` table)
- Row-level locking (`SELECT ... FOR UPDATE SKIP LOCKED`) for distributed execution
- Single-node: one scheduler thread pool processes all jobs
- Multi-node: multiple instances compete for jobs via row locking, natural distribution
- Dedup key: `job_type + entity_id + operation_hash`
- Retry: up to 3 times with exponential backoff (1min, 4min, 16min)
- Idempotent execution: jobs check completion state before processing
- Visual monitor: API endpoints for job status, failure details, manual rerun

### Job Record Structure
```
job_id, job_type, entity_id, dedup_key, status, attempts,
next_retry_at, created_at, started_at, completed_at,
failure_reason, node_id, created_by
```

## 14. Anti-Cheat Review Model

### Scope
- Staff-only "Exam Results Summary" ranking view
- Detection is advisory only; no automated punitive action

### Detection Rules
1. **Impossible Activity Burst**: student submits results for multiple exams within physically impossible timeframes
2. **Repeated Identical Submissions**: same answers/scores submitted multiple times
3. **Abnormal Score Delta**: score change between terms exceeds 3 standard deviations from class average

### Review Queue
- Flags stored in `anticheat_flags` table
- Reviewers: ADMIN, ACADEMIC_COORDINATOR
- Flag record: student_id, rule_type, details, flagged_at, reviewed_by, review_decision, reviewed_at
- Decisions: DISMISSED, CONFIRMED_FOR_INVESTIGATION
- All decisions audited

## 15. Docker Deployment Model

```yaml
services:
  mysql:
    image: mysql:8.0
    volumes: persistent data
    environment: from .env

  backend:
    build: ./backend
    depends_on: mysql
    environment: from .env

  frontend:
    build: ./frontend (nginx serving built Vue.js)
    depends_on: backend
    ports: 80 -> nginx
```

- All containers on internal Docker network
- No external internet required
- MySQL data persisted via Docker volume
- Backend runs Flyway migrations on startup
- Frontend served as static files by nginx with API proxy to backend
