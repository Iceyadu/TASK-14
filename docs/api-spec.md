# API Specification

## Common Headers (All Requests)
```
Authorization: Bearer {session_token}
X-Timestamp: {unix_epoch_seconds}
X-Nonce: {uuid}
X-Signature: {hmac_sha256_hex}
Content-Type: application/json
```

## Response Envelope
```json
{
  "traceId": "uuid",
  "status": "success|error",
  "message": "Human-readable message",
  "data": { ... },
  "errors": [
    { "field": "name", "message": "Required" }
  ],
  "pagination": {
    "page": 1,
    "size": 20,
    "total": 100,
    "totalPages": 5
  }
}
```

## Data Shape Conventions

### Scalar Types
- `id`: `long` (positive integer)
- `datetime`: ISO-8601 string (`YYYY-MM-DDTHH:mm:ss`)
- `date`: `YYYY-MM-DD`
- `time`: `HH:mm[:ss]`
- `enum`: uppercase string from endpoint-specific allowed values

### Standard Input Shapes
```json
{
  "page": 1,
  "size": 20,
  "search": "optional text",
  "sortBy": "createdAt",
  "sortDir": "asc|desc"
}
```

```json
{
  "idempotencyKey": "uuid-or-unique-string"
}
```

### Standard Output Shapes
```json
{
  "id": 123,
  "createdAt": "2026-04-13T12:30:00",
  "updatedAt": "2026-04-13T12:45:00"
}
```

```json
{
  "items": [
    { "id": 1, "name": "example" }
  ],
  "pagination": {
    "page": 1,
    "size": 20,
    "total": 100,
    "totalPages": 5
  }
}
```

### Error Shape
```json
{
  "traceId": "uuid",
  "status": "error",
  "message": "Validation failed",
  "errors": [
    { "field": "name", "message": "Required" }
  ]
}
```

### Module-Specific I/O Examples

#### Exam Session Create Input
```json
{
  "name": "Midterm Mathematics",
  "termId": 2,
  "courseId": 1,
  "campusId": 1,
  "examDate": "2026-05-12",
  "startTime": "09:00",
  "endTime": "11:00",
  "roomId": 3,
  "classIds": [1, 2]
}
```

#### Exam Session Output
```json
{
  "id": 88,
  "name": "Midterm Mathematics",
  "status": "DRAFT",
  "termId": 2,
  "courseId": 1,
  "campusId": 1,
  "roomId": 3,
  "examDate": "2026-05-12",
  "startTime": "09:00:00",
  "endTime": "11:00:00",
  "classIds": [1, 2]
}
```

#### Roster Import Preview Output
```json
{
  "jobId": 44,
  "status": "PARTIALLY_VALID",
  "totalRows": 3,
  "validRows": [
    { "rowNumber": 1, "rowData": { "student_username": "student.chen" } }
  ],
  "invalidRows": [
    {
      "rowNumber": 2,
      "rowData": { "student_username": "student.liu" },
      "errors": [
        { "field": "class_name", "errorReason": "No class found with name: Class X" }
      ]
    }
  ]
}
```

#### Notification Output
```json
{
  "id": 55,
  "title": "Schedule Change",
  "content": "Exam moved to Monday",
  "status": "DRAFT",
  "eventType": "SCHEDULE_CHANGE",
  "targetType": "CLASS",
  "targetIds": [1],
  "complianceApproved": false
}
```

## HTTP Status Semantics
- 200: Success (all create/update operations return 200 with the created/updated resource)
- 400: Validation error (field-level details in errors array)
- 401: Not authenticated (session expired or missing)
- 403: Not authorized (role/scope insufficient)
- 404: Entity not found
- 409: Conflict (duplicate, invalid state transition, concurrent session)
- 429: Rate limited

---

## Authentication Module

### POST /api/auth/login
Login with username and password.
- **Auth**: None (public)
- **Request**: `{ "username": "str", "password": "str", "deviceFingerprint": "str" }`
- **Response 200**: `{ "sessionToken": "str", "signingKey": "str", "expiresAt": "datetime", "user": { "id", "username", "role", "permissions": [] } }`
- **Error 401**: Invalid credentials
- **Error 409**: Concurrent session exists on another device
- **Error 423**: Account locked (15 min after 5 failures)

### POST /api/auth/logout
End current session.
- **Auth**: Authenticated
- **Response 200**: Success

### GET /api/auth/session
Get current session info.
- **Auth**: Authenticated
- **Response 200**: Session details with user info

### POST /api/auth/devices
Register a managed device (Admin only).
- **Auth**: ADMIN
- **Request**: `{ "deviceFingerprint": "str", "description": "str" }`
- **Response 200**: Device registered

### GET /api/auth/devices
List managed devices (Admin only).
- **Auth**: ADMIN
- **Response 200**: Paginated device list

### DELETE /api/auth/devices/{id}
Remove managed device (Admin only).
- **Auth**: ADMIN
- **Response 200**: Device removed

### POST /api/auth/sessions/{userId}/terminate
Terminate another user's session (Admin only).
- **Auth**: ADMIN
- **Response 200**: Session terminated

### POST /api/auth/users/{userId}/unlock
Unlock a locked account (Admin only).
- **Auth**: ADMIN
- **Response 200**: Account unlocked

---

## User Management Module

### GET /api/users
List users with pagination and filters.
- **Auth**: ADMIN
- **Query**: `?page=1&size=20&role=STUDENT&search=name`
- **Response 200**: Paginated user list

### POST /api/users
Create a new user.
- **Auth**: ADMIN
- **Request**: `{ "username": "str", "password": "str", "role": "enum", "fullName": "str", "scopeAssignments": [...] }`
- **Validation**: Password 12+ chars, complexity, unique username
- **Response 200**: User created

### GET /api/users/{id}
Get user detail.
- **Auth**: ADMIN
- **Response 200**: User detail with scope assignments

### PUT /api/users/{id}
Update user.
- **Auth**: ADMIN
- **Request**: Partial update fields
- **Response 200**: Updated user

### PUT /api/users/{id}/scope
Update user scope assignments.
- **Auth**: ADMIN
- **Request**: `{ "scopeAssignments": [{ "scopeType": "CAMPUS|GRADE|TERM|CLASS|COURSE", "scopeId": "long" }] }`
- **Response 200**: Scope updated

### PUT /api/users/{id}/concurrent-sessions
Toggle concurrent session allowance.
- **Auth**: ADMIN
- **Request**: `{ "allowed": true }`
- **Response 200**: Setting updated (audited)

---

## Roster Module

### GET /api/rosters
List roster entries with scope filtering.
- **Auth**: ROSTER_VIEW (scoped)
- **Query**: `?page=1&size=20&classId=1&termId=1&search=name`
- **Response 200**: Paginated roster list (sensitive fields masked)

### POST /api/rosters
Create roster entry.
- **Auth**: ROSTER_CREATE (scoped)
- **Request**: `{ "studentUserId": "long", "classId": "long", "termId": "long", "studentIdNumber": "str", "accommodationNotes": "str" }`
- **Response 200**: Roster entry created (version 1)

### GET /api/rosters/{id}
Get roster entry detail.
- **Auth**: ROSTER_VIEW (scoped)
- **Response 200**: Roster detail (sensitive fields masked)

### PUT /api/rosters/{id}
Update roster entry.
- **Auth**: ROSTER_CREATE (scoped)
- **Response 200**: Updated (new version created)

### DELETE /api/rosters/{id}
Soft-delete roster entry.
- **Auth**: ADMIN
- **Response 200**: Deleted

### POST /api/rosters/import/upload
Upload CSV/XLSX for preview.
- **Auth**: ROSTER_IMPORT (scoped)
- **Request**: multipart/form-data with file
- **Response 200**: `{ "importJobId": "long", "validRows": [...], "invalidRows": [{ "rowNumber": 1, "field": "studentId", "error": "Duplicate" }], "status": "previewed|validation_failed|partially_valid" }`

### POST /api/rosters/import/{jobId}/commit
Commit previewed import.
- **Auth**: ROSTER_IMPORT (scoped)
- **Request**: `{ "idempotencyKey": "uuid" }`
- **Response 200**: Import committed
- **Error 409**: Already committed (idempotent)

### GET /api/rosters/import/{jobId}/errors
Download rejected rows.
- **Auth**: ROSTER_IMPORT (scoped)
- **Response 200**: CSV of rejected rows with error details

### POST /api/rosters/import/{jobId}/rollback
Rollback committed import.
- **Auth**: ADMIN
- **Response 200**: Rolled back

### GET /api/rosters/export
Export roster data as CSV or XLSX.
- **Auth**: ROSTER_EXPORT (scoped)
- **Query**: `?format=csv|xlsx&classId=1&termId=1`
- **Response 200**: File download

---

## Scheduling Module

### GET /api/exam-sessions
List exam sessions.
- **Auth**: SESSION_VIEW (scoped)
- **Query**: `?page=1&size=20&termId=1&status=draft&campusId=1`
- **Response 200**: Paginated list

### POST /api/exam-sessions
Create exam session.
- **Auth**: SESSION_CREATE (scoped)
- **Request**: `{ "name": "str", "termId": "long", "courseId": "long", "campusId": "long", "examDate": "date", "startTime": "time", "endTime": "time", "roomId": "long", "classIds": [long] }`
- **Response 200**: Created in DRAFT state

### GET /api/exam-sessions/{id}
Get exam session detail.
- **Auth**: SESSION_VIEW (scoped)
- **Response 200**: Full session detail with status

### PUT /api/exam-sessions/{id}
Update exam session.
- **Auth**: SESSION_EDIT (scoped)
- **Constraint**: Only in DRAFT or REJECTED state
- **Response 200**: Updated (new version)

### POST /api/exam-sessions/{id}/submit-review
Submit for compliance review.
- **Auth**: SESSION_SUBMIT_REVIEW (scoped)
- **Constraint**: Must be in DRAFT or REJECTED state
- **Response 200**: State -> SUBMITTED_FOR_COMPLIANCE_REVIEW

### POST /api/exam-sessions/{id}/publish
Publish approved session.
- **Auth**: SESSION_PUBLISH (scoped)
- **Constraint**: Must be in APPROVED state
- **Request**: `{ "idempotencyKey": "uuid" }`
- **Response 200**: State -> PUBLISHED

### POST /api/exam-sessions/{id}/unpublish
Unpublish session.
- **Auth**: SESSION_PUBLISH (scoped)
- **Constraint**: Must be in PUBLISHED state
- **Response 200**: State -> UNPUBLISHED

### POST /api/exam-sessions/{id}/archive
Archive session.
- **Auth**: ADMIN
- **Constraint**: Must be in UNPUBLISHED state
- **Response 200**: State -> ARCHIVED

### GET /api/exam-sessions/student/schedule
Get student's own exam schedule.
- **Auth**: STUDENT
- **Response 200**: Only published sessions for student's classes

---

## Room Management Module

### GET /api/campuses
List campuses.
- **Auth**: Authenticated
- **Response 200**: Paginated campus list

### POST /api/campuses
Create campus.
- **Auth**: ADMIN, ROOM_MANAGE
- **Response 200**: Campus created

### GET /api/rooms
List rooms for campus.
- **Auth**: ROOM_MANAGE
- **Query**: `?campusId=1`
- **Response 200**: Room list

### POST /api/rooms
Create room.
- **Auth**: ROOM_MANAGE
- **Request**: `{ "name": "str", "building": "str", "capacity": "int", "facilities": "str", "campusId": "long" }`
- **Response 200**: Room created

### PUT /api/rooms/{id}
Update room.
- **Auth**: ROOM_MANAGE (scoped)
- **Response 200**: Updated

---

## Proctor Module

### GET /api/proctor-assignments
List proctor assignments.
- **Auth**: PROCTOR_ASSIGN (scoped)
- **Query**: `?sessionId=1&termId=1`
- **Response 200**: Assignment list

### POST /api/proctor-assignments
Create proctor assignment.
- **Auth**: PROCTOR_ASSIGN (scoped)
- **Request**: `{ "userId": "long", "examSessionId": "long", "roomId": "long" }`
- **Response 200**: Assignment created

### DELETE /api/proctor-assignments/{id}
Remove proctor assignment.
- **Auth**: PROCTOR_ASSIGN (scoped)
- **Response 200**: Removed

---

## Notification Module

### GET /api/notifications
List notifications (staff view).
- **Auth**: NOTIFICATION_CREATE (scoped)
- **Query**: `?page=1&size=20&status=draft`
- **Response 200**: Notification list

### POST /api/notifications
Create notification.
- **Auth**: NOTIFICATION_CREATE (scoped)
- **Request**: `{ "title": "str", "content": "str", "eventType": "enum", "targetType": "ALL_STUDENTS|CLASS|GRADE|INDIVIDUAL", "targetIds": [long], "idempotencyKey": "uuid" }`
- **Response 200**: Created in DRAFT state

### POST /api/notifications/{id}/submit-review
Submit notification for compliance review.
- **Auth**: NOTIFICATION_CREATE (scoped)
- **Response 200**: Queued for review

### POST /api/notifications/{id}/publish
Publish approved notification (enqueues for delivery).
- **Auth**: NOTIFICATION_PUBLISH (scoped)
- **Request**: `{ "idempotencyKey": "uuid" }`
- **Response 200**: Queued for delivery

### POST /api/notifications/{id}/cancel
Cancel notification.
- **Auth**: NOTIFICATION_CREATE (scoped)
- **Constraint**: DRAFT or QUEUED state only
- **Response 200**: Canceled

### GET /api/notifications/inbox
Student inbox.
- **Auth**: STUDENT
- **Query**: `?page=1&size=20&read=false`
- **Response 200**: Inbox entries for current student

### POST /api/notifications/inbox/{id}/read
Mark inbox notification as read.
- **Auth**: STUDENT (own only)
- **Response 200**: Marked read

### GET /api/notifications/delivery-status
Delivery status for notifications.
- **Auth**: NOTIFICATION_CREATE (scoped)
- **Query**: `?notificationId=1`
- **Response 200**: Per-student per-channel delivery status

### GET /api/notifications/subscriptions
Get student subscription settings.
- **Auth**: STUDENT (own only)
- **Response 200**: Subscription settings by event type

### PUT /api/notifications/subscriptions
Update subscription settings.
- **Auth**: STUDENT (own only)
- **Request**: `{ "settings": [{ "eventType": "enum", "enabled": true }], "dndStart": "time", "dndEnd": "time" }`
- **Response 200**: Updated

---

## Compliance Review Module

### GET /api/compliance/reviews
List pending reviews.
- **Auth**: COMPLIANCE_REVIEW
- **Query**: `?page=1&size=20&status=pending`
- **Response 200**: Review queue

### GET /api/compliance/reviews/{id}
Get review detail with content.
- **Auth**: COMPLIANCE_REVIEW
- **Response 200**: Review detail with linked content

### POST /api/compliance/reviews/{id}/approve
Approve content.
- **Auth**: COMPLIANCE_REVIEW
- **Request**: `{ "comment": "str" }`
- **Response 200**: Approved

### POST /api/compliance/reviews/{id}/reject
Reject content.
- **Auth**: COMPLIANCE_REVIEW
- **Request**: `{ "comment": "str (required)", "requiredChanges": "str" }`
- **Response 200**: Rejected

---

## Version History Module

### GET /api/versions/{entityType}/{entityId}
List versions for entity.
- **Auth**: VERSION_VIEW (scoped)
- **Response 200**: Version list with metadata

### GET /api/versions/{entityType}/{entityId}/{versionNumber}
Get specific version snapshot.
- **Auth**: VERSION_VIEW (scoped)
- **Response 200**: Full snapshot

### GET /api/versions/{entityType}/{entityId}/compare
Compare two versions.
- **Auth**: VERSION_VIEW (scoped)
- **Query**: `?from=1&to=3`
- **Response 200**: Diff between versions

### POST /api/versions/{entityType}/{entityId}/restore
Restore a previous version.
- **Auth**: VERSION_RESTORE (scoped)
- **Request**: `{ "targetVersion": 2, "idempotencyKey": "uuid" }`
- **Response 200**: New version created from historical snapshot

---

## Job Monitor Module

### GET /api/jobs
List job runs.
- **Auth**: JOB_MONITOR
- **Query**: `?page=1&size=20&status=failed&type=NOTIFICATION_SEND`
- **Response 200**: Paginated job list

### GET /api/jobs/{id}
Get job detail.
- **Auth**: JOB_MONITOR
- **Response 200**: Job detail with failure info

### POST /api/jobs/{id}/rerun
Manually rerun a failed job.
- **Auth**: JOB_RERUN
- **Request**: `{ "idempotencyKey": "uuid" }`
- **Response 200**: Job requeued

### POST /api/jobs/{id}/cancel
Cancel a queued job.
- **Auth**: JOB_RERUN
- **Response 200**: Job canceled

---

## Anti-Cheat Module

### GET /api/anticheat/flags
List anti-cheat flags.
- **Auth**: ANTICHEAT_REVIEW
- **Query**: `?page=1&size=20&status=pending`
- **Response 200**: Flag list

### POST /api/anticheat/flags/{id}/review
Review a flag.
- **Auth**: ANTICHEAT_REVIEW
- **Request**: `{ "decision": "DISMISSED|CONFIRMED_FOR_INVESTIGATION", "comment": "str" }`
- **Response 200**: Review recorded

---

## Audit Module

### GET /api/audit
List audit entries.
- **Auth**: AUDIT_VIEW (ADMIN only)
- **Query**: `?page=1&size=50&userId=1&entityType=EXAM_SESSION&action=STATE_CHANGE&from=datetime&to=datetime`
- **Response 200**: Paginated audit entries

---

## Shared Endpoints

### GET /api/terms
List terms.
- **Auth**: Authenticated
- **Response 200**: Term list

### GET /api/grades
List grades.
- **Auth**: Authenticated
- **Response 200**: Grade list

### GET /api/classes
List classes (scoped).
- **Auth**: Authenticated (scoped)
- **Response 200**: Class list

### GET /api/courses
List courses (scoped).
- **Auth**: Authenticated (scoped)
- **Response 200**: Course list
