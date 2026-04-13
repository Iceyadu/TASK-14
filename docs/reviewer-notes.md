# Static Reviewer Notes

## How to Review This Deliverable

This document provides precise file pointers for a static-only reviewer to inspect critical evidence without running the application.

---

## 1. Authentication & Session Security

### Password Complexity
- **Enforcement**: `repo/backend/src/main/java/com/eaglepoint/exam/auth/service/AuthService.java` — `validatePasswordComplexity()` method
- **Rules**: Min 12 chars, uppercase, lowercase, digit, special char
- **Hashing**: BCrypt with cost factor 12 — `AuthService` constructs `BCryptPasswordEncoder(12)` and uses it in `createUser()` and `login()` password checks

### Account Lockout
- **Logic**: `AuthService.java` — `login()` increments `failedLoginAttempts` on bad password and sets `lockedUntil` when the threshold is reached (see the block after password validation failure)
- **Config**: 5 attempts, 15-minute window
- **Audit**: Every lockout event written to audit_log

### Session Management
- **Expiry**: `SecurityFilter.java` - checks `last_active_at + 30min`
- **Concurrent block**: `AuthService.java` - inline in `login()` method (checks `existingSessions` and invalidates prior sessions around lines 127–138)
- **Managed devices**: `ManagedDevice` entity, only admin can register

---

## 2. Request Signing

### Client Signing
- **Implementation**: `repo/frontend/src/api/client.js` - `computeSignature()` function in request interceptor
- **Computes**: HMAC-SHA256 over method + path + timestamp + nonce + body hash

### Server Verification
- **Filter**: `repo/backend/src/main/java/com/eaglepoint/exam/security/filter/RequestSigningFilter.java`
- **Nonce store**: `nonce_replay` table, cleanup in `JobScheduler.cleanupNonces()`
- **Timestamp tolerance**: 120 seconds enforced in filter

---

## 3. RBAC/ABAC and Row-Level Scope

### Permission Checks
- **Annotation**: `@RequirePermission` on controller methods
- **Interceptor**: `repo/backend/src/main/java/com/eaglepoint/exam/security/interceptor/PermissionInterceptor.java`
- **Scope service**: `repo/backend/src/main/java/com/eaglepoint/exam/security/service/ScopeService.java`

### Row-Level Scope Evidence
- **Roster queries**: `RosterService.java` - all list/get methods call `scopeService.filterByUserScope()`
- **Session queries**: `ExamSessionService.java` - same pattern
- **Student isolation**: Student role queries always include `WHERE student_user_id = :currentUserId`

---

## 4. Workflow State Machines

### Exam Session
- **States enum**: `repo/backend/src/main/java/com/eaglepoint/exam/scheduling/model/ExamSessionStatus.java`
- **Transitions**: `ExamSessionStateMachine.java` - `VALID_TRANSITIONS` map
- **Validation**: `ExamSessionService.java` - `transitionState()` method checks legality
- **Tests**: `repo/unit_tests/backend/ExamSessionServiceTest.java`

### Roster Import
- **States**: `ImportJobStatus.java`
- **Two-step**: `ImportService.java` - `uploadAndPreview()` does NO writes, `commitImport()` is atomic

### Notification
- **States**: `NotificationStatus.java`
- **Fallback**: `NotificationDeliveryService.java` - `deliverNotification()` (attempts WeChat via `attemptWeChatDelivery()`, falls back to `createInboxMessage()`)

---

## 5. Compliance Gating

### Review Required Before Student Visibility
- **Check**: `ExamSessionService.publishSession()` - verifies APPROVED compliance status
- **Check**: `NotificationService.publishNotification()` - same pattern
- **Queue**: `ComplianceReviewService.java` - full approve/reject/resubmit flow

### Re-review Triggers
- **Restore**: `VersionService.restoreVersion()` - sets status to SUBMITTED_FOR_COMPLIANCE_REVIEW
- **Edit published**: `ExamSessionService.updateSession()` - checks if content was published, triggers re-review

---

## 6. Version History

### Immutable Storage
- **Table**: `entity_versions` - see `V1__schema.sql` migration
- **Append-only**: `VersionService.createVersion()` - always INSERT, never UPDATE
- **Restore**: `VersionService.restoreVersion()` - creates NEW version record

### Compare
- **Endpoint**: `GET /api/versions/{type}/{id}/compare?from=1&to=3`
- **Logic**: `VersionService.compareVersions()` - JSON diff

---

## 7. Bulk Import

### Two-Step Evidence
- **Preview**: `ImportService.uploadAndPreview()` - returns errors without DB writes
- **Commit**: `ImportService.commitImport()` - atomic transaction
- **No premature writes**: Search for `@Transactional` in ImportService - only on commit method

### Error Reporting
- **Per-row errors**: `ImportPreviewResponse.ImportRowError` - contains rowNumber, field, errorReason
- **Duplicate detection**: Inline in `ImportService.validateRow()` - case-insensitive via seenUsernames set

### Sample Files
- `repo/backend/src/main/resources/samples/roster_import_sample.csv`
- `repo/backend/src/main/resources/samples/roster_import_sample.xlsx`

---

## 8. Encryption at Rest

### Implementation
- **Converter**: `repo/backend/src/main/java/com/eaglepoint/exam/security/crypto/EncryptedFieldConverter.java`
- **Algorithm**: AES-256-GCM
- **Key source**: `APP_ENCRYPTION_KEY` environment variable
- **Applied to**: `studentIdNumber`, `guardianContact`, `accommodationNotes` fields

### Masking
- **Serializer**: `repo/backend/src/main/java/com/eaglepoint/exam/security/masking/MaskedFieldSerializer.java`
- **Applied via**: `@MaskedField` annotation on DTO fields

---

## 9. Notification Fallback

### DND Logic
- **Check**: `NotificationDeliveryService.java` - `isDndActive()`
- **Hold behavior**: DND notifications queued for inbox delivery after DND window
- **DND release job**: `JobScheduler.releaseDndNotifications()`

### WeChat Fallback
- **Logic**: `NotificationDeliveryService.deliverNotification()` - tries WeChat via `attemptWeChatDelivery()`, on failure creates inbox entry via `createInboxMessage()`
- **Status tracking**: `delivery_status` table tracks per-channel attempts

---

## 10. Rate Limiting
- **Filter**: `repo/backend/src/main/java/com/eaglepoint/exam/security/filter/RateLimitFilter.java`
- **User limit**: configurable via `RATE_LIMIT_USER_PER_MINUTE` (default 60/min), in-memory sliding window counter
- **IP limit**: configurable via `RATE_LIMIT_IP_PER_MINUTE` (default 300/min)
- **Response**: 429 with retry-after header

---

## 11. Idempotency
- **Store**: `idempotency_keys` table
- **Check**: `IdempotencyService.java` - `checkAndStore()`
- **Scope**: key + user_id + operation_type (no cross-user leakage)
- **Expiry**: 24 hours, cleanup job

---

## 12. Audit Trail
- **Service**: `repo/backend/src/main/java/com/eaglepoint/exam/audit/service/AuditService.java`
- **Called from**: Every service method performing writes, state changes, auth events
- **Immutable**: No update/delete operations on audit_log table

---

## 13. Anti-Cheat
- **Detection**: `repo/backend/src/main/java/com/eaglepoint/exam/anticheat/service/AntiCheatService.java`
- **Rules**: Activity burst, identical submissions, score delta
- **Advisory only**: Flags go to review queue, no automated actions
- **Review**: `AntiCheatController.java` - review endpoint

---

## 14. Docker
- **Compose file**: `repo/docker-compose.yml`
- **Backend Dockerfile**: `repo/backend/Dockerfile`
- **Frontend Dockerfile**: `repo/frontend/Dockerfile`
- **Startup**: `docker compose up` starts MySQL, backend, frontend

---

## 15. Test Locations
- **Backend unit tests**: `repo/unit_tests/backend/`
- **Backend integration tests**: `repo/backend/src/test/java/`
- **API integration tests**: `repo/backend/src/test/java/com/eaglepoint/exam/integration/`
- **Frontend tests**: `repo/frontend/tests/`
- **Run all**: `repo/run_tests.sh`

---

## 16. Key Files Quick Reference

| Concern | Primary File |
|---------|-------------|
| Auth login flow | `auth/service/AuthService.java` |
| Password rules | `auth/service/AuthService.java` |
| Session filter | `security/filter/SecurityFilter.java` |
| Request signing | `security/filter/RequestSigningFilter.java` |
| Permission check | `security/interceptor/PermissionInterceptor.java` |
| Scope enforcement | `security/service/ScopeService.java` |
| Exam state machine | `scheduling/service/ExamSessionStateMachine.java` |
| Import two-step | `imports/service/ImportService.java` |
| Compliance review | `compliance/service/ComplianceReviewService.java` |
| Version history | `versioning/service/VersionService.java` |
| Notification delivery | `notifications/service/NotificationDeliveryService.java` |
| Encryption | `security/crypto/EncryptedFieldConverter.java` |
| Masking | `security/masking/MaskedFieldSerializer.java` |
| Job scheduler | `jobs/service/JobService.java` |
| Audit trail | `audit/service/AuditService.java` |
| Anti-cheat | `anticheat/service/AntiCheatService.java` |
| Rate limiting | `security/filter/RateLimitFilter.java` |
| Idempotency | `security/service/IdempotencyService.java` |
| DB schema | `backend/src/main/resources/db/migration/V1__schema.sql` |
| Seed data | `backend/src/main/resources/db/migration/V2__seed_data.sql` |
