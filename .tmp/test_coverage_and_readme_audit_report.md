# Test Coverage Audit

## Scope

- Audit mode: static inspection only. No code, tests, scripts, containers, servers, or package managers were run.
- Repo root inspected: `repo/`
- Primary evidence sources:
  - Controllers: `repo/backend/src/main/java/com/eaglepoint/exam/*/controller/*.java`
  - API tests: `repo/API_tests/src/test/java/com/eaglepoint/exam/integration/*.java`
  - Backend unit tests: `repo/unit_tests/backend/src/test/java/...`
  - Frontend unit tests: `repo/unit_tests/frontend/*.spec.js`
  - Browser E2E tests: `repo/E2E_tests/specs/*.e2e.spec.js`
  - README / orchestration: `repo/README.md`, `repo/run_tests.sh`, `repo/docker-compose.yml`

## Backend Endpoint Inventory

- Auth
  - `POST /api/auth/login` (`repo/backend/src/main/java/com/eaglepoint/exam/auth/controller/AuthController.java`, `login`)
  - `POST /api/auth/logout` (`AuthController.java`, `logout`)
  - `GET /api/auth/session` (`AuthController.java`, `getSession`)
  - `POST /api/auth/devices` (`AuthController.java`, `registerDevice`)
  - `GET /api/auth/devices` (`AuthController.java`, `listDevices`)
  - `DELETE /api/auth/devices/{id}` (`AuthController.java`, `removeDevice`)
  - `POST /api/auth/sessions/{userId}/terminate` (`AuthController.java`, `terminateSession`)
  - `POST /api/auth/users/{userId}/unlock` (`AuthController.java`, `unlockAccount`)
- Compliance
  - `GET /api/compliance/reviews` (`repo/backend/src/main/java/com/eaglepoint/exam/compliance/controller/ComplianceController.java`, `listPendingReviews`)
  - `GET /api/compliance/reviews/{id}` (`ComplianceController.java`, `getReview`)
  - `POST /api/compliance/reviews/{id}/approve` (`ComplianceController.java`, `approve`)
  - `POST /api/compliance/reviews/{id}/reject` (`ComplianceController.java`, `reject`)
- Imports
  - `POST /api/rosters/import/upload` (`repo/backend/src/main/java/com/eaglepoint/exam/imports/controller/ImportController.java`, `uploadAndPreview`)
  - `POST /api/rosters/import/{jobId}/commit` (`ImportController.java`, `commitImport`)
  - `GET /api/rosters/import/{jobId}/errors` (`ImportController.java`, `getImportErrors`)
  - `POST /api/rosters/import/{jobId}/rollback` (`ImportController.java`, `rollbackImport`)
- Jobs
  - `GET /api/jobs` (`repo/backend/src/main/java/com/eaglepoint/exam/jobs/controller/JobController.java`, `listJobs`)
  - `GET /api/jobs/{id}` (`JobController.java`, `getJob`)
  - `POST /api/jobs/{id}/rerun` (`JobController.java`, `rerunJob`)
  - `POST /api/jobs/{id}/cancel` (`JobController.java`, `cancelJob`)
- Notifications
  - `GET /api/notifications` (`repo/backend/src/main/java/com/eaglepoint/exam/notifications/controller/NotificationController.java`, `listNotifications`)
  - `POST /api/notifications` (`NotificationController.java`, `createNotification`)
  - `POST /api/notifications/{id}/submit-review` (`NotificationController.java`, `submitForReview`)
  - `POST /api/notifications/{id}/publish` (`NotificationController.java`, `publishNotification`)
  - `POST /api/notifications/{id}/cancel` (`NotificationController.java`, `cancelNotification`)
  - `GET /api/notifications/inbox` (`NotificationController.java`, `getInbox`)
  - `POST /api/notifications/inbox/{id}/read` (`NotificationController.java`, `markInboxRead`)
  - `GET /api/notifications/delivery-status` (`NotificationController.java`, `getDeliveryStatus`)
  - `GET /api/notifications/subscriptions` (`NotificationController.java`, `getSubscriptions`)
  - `PUT /api/notifications/subscriptions` (`NotificationController.java`, `updateSubscriptions`)
- Proctors
  - `GET /api/proctor-assignments` (`repo/backend/src/main/java/com/eaglepoint/exam/proctors/controller/ProctorController.java`, `listAssignments`)
  - `POST /api/proctor-assignments` (`ProctorController.java`, `createAssignment`)
  - `DELETE /api/proctor-assignments/{id}` (`ProctorController.java`, `deleteAssignment`)
- Reference data
  - `GET /api/terms` (`repo/backend/src/main/java/com/eaglepoint/exam/rooms/controller/ReferenceDataController.java`, `listTerms`)
  - `GET /api/grades` (`ReferenceDataController.java`, `listGrades`)
  - `GET /api/classes` (`ReferenceDataController.java`, `listClasses`)
  - `GET /api/courses` (`ReferenceDataController.java`, `listCourses`)
- Rooms / campuses
  - `GET /api/campuses` (`repo/backend/src/main/java/com/eaglepoint/exam/rooms/controller/RoomController.java`, `listCampuses`)
  - `GET /api/campuses/{id}` (`RoomController.java`, `getCampus`)
  - `POST /api/campuses` (`RoomController.java`, `createCampus`)
  - `PUT /api/campuses/{id}` (`RoomController.java`, `updateCampus`)
  - `DELETE /api/campuses/{id}` (`RoomController.java`, `deleteCampus`)
  - `GET /api/rooms` (`RoomController.java`, `listRooms`)
  - `GET /api/rooms/{id}` (`RoomController.java`, `getRoom`)
  - `POST /api/rooms` (`RoomController.java`, `createRoom`)
  - `PUT /api/rooms/{id}` (`RoomController.java`, `updateRoom`)
  - `DELETE /api/rooms/{id}` (`RoomController.java`, `deleteRoom`)
- Roster
  - `GET /api/rosters` (`repo/backend/src/main/java/com/eaglepoint/exam/roster/controller/RosterController.java`, `listRosterEntries`)
  - `GET /api/rosters/export` (`RosterController.java`, `exportRoster`)
  - `POST /api/rosters` (`RosterController.java`, `createRosterEntry`)
  - `GET /api/rosters/{id}` (`RosterController.java`, `getRosterEntry`)
  - `PUT /api/rosters/{id}` (`RosterController.java`, `updateRosterEntry`)
  - `DELETE /api/rosters/{id}` (`RosterController.java`, `deleteRosterEntry`)
- Exam sessions
  - `GET /api/exam-sessions` (`repo/backend/src/main/java/com/eaglepoint/exam/scheduling/controller/ExamSessionController.java`, `listSessions`)
  - `POST /api/exam-sessions` (`ExamSessionController.java`, `createSession`)
  - `GET /api/exam-sessions/{id}` (`ExamSessionController.java`, `getSession`)
  - `PUT /api/exam-sessions/{id}` (`ExamSessionController.java`, `updateSession`)
  - `POST /api/exam-sessions/{id}/submit-review` (`ExamSessionController.java`, `submitForReview`)
  - `POST /api/exam-sessions/{id}/publish` (`ExamSessionController.java`, `publishSession`)
  - `POST /api/exam-sessions/{id}/unpublish` (`ExamSessionController.java`, `unpublishSession`)
  - `POST /api/exam-sessions/{id}/archive` (`ExamSessionController.java`, `archiveSession`)
  - `GET /api/exam-sessions/student/schedule` (`ExamSessionController.java`, `getStudentSchedule`)
- Users
  - `GET /api/users` (`repo/backend/src/main/java/com/eaglepoint/exam/users/controller/UserController.java`, `listUsers`)
  - `POST /api/users` (`UserController.java`, `createUser`)
  - `GET /api/users/{id}` (`UserController.java`, `getUser`)
  - `PUT /api/users/{id}` (`UserController.java`, `updateUser`)
  - `PUT /api/users/{id}/scope` (`UserController.java`, `updateScope`)
  - `PUT /api/users/{id}/concurrent-sessions` (`UserController.java`, `toggleConcurrentSessions`)
- Versioning
  - `GET /api/versions/{entityType}/{entityId}` (`repo/backend/src/main/java/com/eaglepoint/exam/versioning/controller/VersionController.java`, `getVersions`)
  - `GET /api/versions/{entityType}/{entityId}/{versionNumber}` (`VersionController.java`, `getVersion`)
  - `GET /api/versions/{entityType}/{entityId}/compare` (`VersionController.java`, `compareVersions`)
  - `POST /api/versions/{entityType}/{entityId}/restore` (`VersionController.java`, `restoreVersion`)
- Audit
  - `GET /api/audit` (`repo/backend/src/main/java/com/eaglepoint/exam/audit/controller/AuditController.java`, `queryAuditLog`)
- Anti-cheat
  - `GET /api/anticheat/flags` (`repo/backend/src/main/java/com/eaglepoint/exam/anticheat/controller/AntiCheatController.java`, `listFlags`)
  - `POST /api/anticheat/flags/{id}/review` (`AntiCheatController.java`, `reviewFlag`)

Total backend endpoints: **75**

## API Test Mapping Table

| Endpoint | Covered | Test type | Test files | Evidence |
|---|---|---|---|---|
| `POST /api/auth/login` | yes | true no-mock HTTP | `RealHttpAuthApiTest.java` | `repo/API_tests/src/test/java/com/eaglepoint/exam/integration/RealHttpAuthApiTest.java :: testRealHttpLoginSessionAndLogout` |
| `POST /api/auth/logout` | yes | true no-mock HTTP | `RealHttpAuthApiTest.java`, `RealHttpMissingEndpointsTest.java` | `RealHttpAuthApiTest.java :: testRealHttpLoginSessionAndLogout` |
| `GET /api/auth/session` | yes | true no-mock HTTP | `RealHttpAuthApiTest.java`, `RealHttpMissingEndpointsTest.java` | `RealHttpAuthApiTest.java :: testRealHttpLoginSessionAndLogout` |
| `POST /api/auth/devices` | yes | true no-mock HTTP | `RealHttpMockMvcGapCoverageTest.java` | `repo/API_tests/src/test/java/com/eaglepoint/exam/integration/RealHttpMockMvcGapCoverageTest.java :: testRealHttpCoverageForPreviouslyMockMvcOnlyEndpoints` |
| `GET /api/auth/devices` | yes | true no-mock HTTP | `RealHttpMockMvcGapCoverageTest.java` | `RealHttpMockMvcGapCoverageTest.java :: testRealHttpCoverageForPreviouslyMockMvcOnlyEndpoints` |
| `DELETE /api/auth/devices/{id}` | yes | true no-mock HTTP | `RealHttpMockMvcGapCoverageTest.java` | `RealHttpMockMvcGapCoverageTest.java :: testRealHttpCoverageForPreviouslyMockMvcOnlyEndpoints` |
| `POST /api/auth/sessions/{userId}/terminate` | yes | true no-mock HTTP | `RealHttpMockMvcGapCoverageTest.java` | `RealHttpMockMvcGapCoverageTest.java :: testRealHttpCoverageForPreviouslyMockMvcOnlyEndpoints` |
| `POST /api/auth/users/{userId}/unlock` | yes | true no-mock HTTP | `RealHttpMockMvcGapCoverageTest.java` | `RealHttpMockMvcGapCoverageTest.java :: testRealHttpCoverageForPreviouslyMockMvcOnlyEndpoints` |
| `GET /api/compliance/reviews` | yes | true no-mock HTTP | `RealHttpWorkflowCoverageTest.java` | `repo/API_tests/src/test/java/com/eaglepoint/exam/integration/RealHttpWorkflowCoverageTest.java :: testRealHttpHighValueWorkflowsHaveDeepAssertions` |
| `GET /api/compliance/reviews/{id}` | yes | true no-mock HTTP | `RealHttpMissingEndpointsTest.java` | `repo/API_tests/src/test/java/com/eaglepoint/exam/integration/RealHttpMissingEndpointsTest.java :: testPreviouslyMissingPathsOverRealHttp` |
| `POST /api/compliance/reviews/{id}/approve` | yes | true no-mock HTTP | `RealHttpWorkflowCoverageTest.java` | `RealHttpWorkflowCoverageTest.java :: testRealHttpHighValueWorkflowsHaveDeepAssertions` |
| `POST /api/compliance/reviews/{id}/reject` | yes | true no-mock HTTP | `RealHttpMissingEndpointsTest.java` | `RealHttpMissingEndpointsTest.java :: testPreviouslyMissingPathsOverRealHttp` |
| `POST /api/rosters/import/upload` | yes | true no-mock HTTP | `RealHttpWorkflowCoverageTest.java`, `RealHttpMockMvcGapCoverageTest.java` | `RealHttpWorkflowCoverageTest.java :: testRealHttpHighValueWorkflowsHaveDeepAssertions` |
| `POST /api/rosters/import/{jobId}/commit` | yes | true no-mock HTTP | `RealHttpWorkflowCoverageTest.java`, `RealHttpMockMvcGapCoverageTest.java` | `RealHttpWorkflowCoverageTest.java :: testRealHttpHighValueWorkflowsHaveDeepAssertions` |
| `GET /api/rosters/import/{jobId}/errors` | yes | true no-mock HTTP | `RealHttpWorkflowCoverageTest.java`, `RealHttpMissingEndpointsTest.java` | `RealHttpWorkflowCoverageTest.java :: testRealHttpHighValueWorkflowsHaveDeepAssertions` |
| `POST /api/rosters/import/{jobId}/rollback` | yes | true no-mock HTTP | `RealHttpMockMvcGapCoverageTest.java` | `RealHttpMockMvcGapCoverageTest.java :: testRealHttpCoverageForPreviouslyMockMvcOnlyEndpoints` |
| `GET /api/jobs` | yes | true no-mock HTTP | `RealHttpWorkflowCoverageTest.java` | `RealHttpWorkflowCoverageTest.java :: testRealHttpListEndpointsAssertPayloadShape` |
| `GET /api/jobs/{id}` | yes | true no-mock HTTP | `RealHttpMissingEndpointsTest.java` | `RealHttpMissingEndpointsTest.java :: testPreviouslyMissingPathsOverRealHttp` |
| `POST /api/jobs/{id}/rerun` | yes | true no-mock HTTP | `RealHttpMockMvcGapCoverageTest.java` | `RealHttpMockMvcGapCoverageTest.java :: testRealHttpCoverageForPreviouslyMockMvcOnlyEndpoints` |
| `POST /api/jobs/{id}/cancel` | yes | true no-mock HTTP | `RealHttpMissingEndpointsTest.java` | `RealHttpMissingEndpointsTest.java :: testPreviouslyMissingPathsOverRealHttp` |
| `GET /api/notifications` | yes | true no-mock HTTP | `RealHttpWorkflowCoverageTest.java` | `RealHttpWorkflowCoverageTest.java :: testRealHttpListEndpointsAssertPayloadShape` |
| `POST /api/notifications` | yes | true no-mock HTTP | `RealHttpWorkflowCoverageTest.java` | `RealHttpWorkflowCoverageTest.java :: testRealHttpHighValueWorkflowsHaveDeepAssertions` |
| `POST /api/notifications/{id}/submit-review` | yes | true no-mock HTTP | `RealHttpWorkflowCoverageTest.java` | `RealHttpWorkflowCoverageTest.java :: testRealHttpHighValueWorkflowsHaveDeepAssertions` |
| `POST /api/notifications/{id}/publish` | yes | true no-mock HTTP | `RealHttpWorkflowCoverageTest.java` | `RealHttpWorkflowCoverageTest.java :: testRealHttpHighValueWorkflowsHaveDeepAssertions` |
| `POST /api/notifications/{id}/cancel` | yes | true no-mock HTTP | `RealHttpMissingEndpointsTest.java` | `RealHttpMissingEndpointsTest.java :: testPreviouslyMissingPathsOverRealHttp` |
| `GET /api/notifications/inbox` | yes | true no-mock HTTP | `RealHttpWorkflowCoverageTest.java` | `RealHttpWorkflowCoverageTest.java :: testRealHttpHighValueWorkflowsHaveDeepAssertions` |
| `POST /api/notifications/inbox/{id}/read` | yes | true no-mock HTTP | `RealHttpWorkflowCoverageTest.java`, `RealHttpMissingEndpointsTest.java` | `RealHttpWorkflowCoverageTest.java :: testRealHttpHighValueWorkflowsHaveDeepAssertions` |
| `GET /api/notifications/delivery-status` | yes | true no-mock HTTP | `RealHttpWorkflowCoverageTest.java` | `RealHttpWorkflowCoverageTest.java :: testRealHttpHighValueWorkflowsHaveDeepAssertions` |
| `GET /api/notifications/subscriptions` | yes | true no-mock HTTP | `RealHttpMissingEndpointsTest.java` | `RealHttpMissingEndpointsTest.java :: testPreviouslyMissingPathsOverRealHttp` |
| `PUT /api/notifications/subscriptions` | yes | true no-mock HTTP | `RealHttpMockMvcGapCoverageTest.java` | `RealHttpMockMvcGapCoverageTest.java :: testRealHttpCoverageForPreviouslyMockMvcOnlyEndpoints` |
| `GET /api/proctor-assignments` | yes | true no-mock HTTP | `RealHttpMissingEndpointsTest.java` | `RealHttpMissingEndpointsTest.java :: testPreviouslyMissingPathsOverRealHttp` |
| `POST /api/proctor-assignments` | yes | true no-mock HTTP | `RealHttpMockMvcGapCoverageTest.java` | `RealHttpMockMvcGapCoverageTest.java :: testRealHttpCoverageForPreviouslyMockMvcOnlyEndpoints` |
| `DELETE /api/proctor-assignments/{id}` | yes | true no-mock HTTP | `RealHttpMissingEndpointsTest.java` | `RealHttpMissingEndpointsTest.java :: testPreviouslyMissingPathsOverRealHttp` |
| `GET /api/terms` | yes | true no-mock HTTP | `RealHttpMockMvcGapCoverageTest.java` | `RealHttpMockMvcGapCoverageTest.java :: testRealHttpCoverageForPreviouslyMockMvcOnlyEndpoints` |
| `GET /api/grades` | yes | true no-mock HTTP | `RealHttpMockMvcGapCoverageTest.java` | `RealHttpMockMvcGapCoverageTest.java :: testRealHttpCoverageForPreviouslyMockMvcOnlyEndpoints` |
| `GET /api/classes` | yes | true no-mock HTTP | `RealHttpMockMvcGapCoverageTest.java` | `RealHttpMockMvcGapCoverageTest.java :: testRealHttpCoverageForPreviouslyMockMvcOnlyEndpoints` |
| `GET /api/courses` | yes | true no-mock HTTP | `RealHttpMockMvcGapCoverageTest.java` | `RealHttpMockMvcGapCoverageTest.java :: testRealHttpCoverageForPreviouslyMockMvcOnlyEndpoints` |
| `GET /api/campuses` | yes | true no-mock HTTP | `RealHttpMockMvcGapCoverageTest.java` | `RealHttpMockMvcGapCoverageTest.java :: testRealHttpCoverageForPreviouslyMockMvcOnlyEndpoints` |
| `GET /api/campuses/{id}` | yes | true no-mock HTTP | `RealHttpMissingEndpointsTest.java` | `RealHttpMissingEndpointsTest.java :: testPreviouslyMissingPathsOverRealHttp` |
| `POST /api/campuses` | yes | true no-mock HTTP | `RealHttpMockMvcGapCoverageTest.java` | `RealHttpMockMvcGapCoverageTest.java :: testRealHttpCoverageForPreviouslyMockMvcOnlyEndpoints` |
| `PUT /api/campuses/{id}` | yes | true no-mock HTTP | `RealHttpMockMvcGapCoverageTest.java` | `RealHttpMockMvcGapCoverageTest.java :: testRealHttpCoverageForPreviouslyMockMvcOnlyEndpoints` |
| `DELETE /api/campuses/{id}` | yes | true no-mock HTTP | `RealHttpMissingEndpointsTest.java` | `RealHttpMissingEndpointsTest.java :: testPreviouslyMissingPathsOverRealHttp` |
| `GET /api/rooms` | yes | true no-mock HTTP | `RealHttpMockMvcGapCoverageTest.java` | `RealHttpMockMvcGapCoverageTest.java :: testRealHttpCoverageForPreviouslyMockMvcOnlyEndpoints` |
| `GET /api/rooms/{id}` | yes | true no-mock HTTP | `RealHttpMissingEndpointsTest.java` | `RealHttpMissingEndpointsTest.java :: testPreviouslyMissingPathsOverRealHttp` |
| `POST /api/rooms` | yes | true no-mock HTTP | `RealHttpMockMvcGapCoverageTest.java` | `RealHttpMockMvcGapCoverageTest.java :: testRealHttpCoverageForPreviouslyMockMvcOnlyEndpoints` |
| `PUT /api/rooms/{id}` | yes | true no-mock HTTP | `RealHttpMissingEndpointsTest.java` | `RealHttpMissingEndpointsTest.java :: testPreviouslyMissingPathsOverRealHttp` |
| `DELETE /api/rooms/{id}` | yes | true no-mock HTTP | `RealHttpMissingEndpointsTest.java` | `RealHttpMissingEndpointsTest.java :: testPreviouslyMissingPathsOverRealHttp` |
| `GET /api/rosters` | yes | true no-mock HTTP | `RealHttpWorkflowCoverageTest.java` | `RealHttpWorkflowCoverageTest.java :: testRealHttpListEndpointsAssertPayloadShape` |
| `GET /api/rosters/export` | yes | true no-mock HTTP | `RealHttpMockMvcGapCoverageTest.java` | `RealHttpMockMvcGapCoverageTest.java :: testRealHttpCoverageForPreviouslyMockMvcOnlyEndpoints` |
| `POST /api/rosters` | yes | true no-mock HTTP | `RealHttpWorkflowCoverageTest.java`, `RealHttpMockMvcGapCoverageTest.java` | `RealHttpWorkflowCoverageTest.java :: testRealHttpHighValueWorkflowsHaveDeepAssertions` |
| `GET /api/rosters/{id}` | yes | true no-mock HTTP | `RealHttpMockMvcGapCoverageTest.java`, `RealHttpMissingEndpointsTest.java` | `RealHttpMockMvcGapCoverageTest.java :: testRealHttpCoverageForPreviouslyMockMvcOnlyEndpoints` |
| `PUT /api/rosters/{id}` | yes | true no-mock HTTP | `RealHttpMockMvcGapCoverageTest.java`, `RealHttpMissingEndpointsTest.java` | `RealHttpMockMvcGapCoverageTest.java :: testRealHttpCoverageForPreviouslyMockMvcOnlyEndpoints` |
| `DELETE /api/rosters/{id}` | yes | true no-mock HTTP | `RealHttpMissingEndpointsTest.java` | `RealHttpMissingEndpointsTest.java :: testPreviouslyMissingPathsOverRealHttp` |
| `GET /api/exam-sessions` | yes | true no-mock HTTP | `RealHttpWorkflowCoverageTest.java` | `RealHttpWorkflowCoverageTest.java :: testRealHttpListEndpointsAssertPayloadShape` |
| `POST /api/exam-sessions` | yes | true no-mock HTTP | `RealHttpWorkflowCoverageTest.java`, `RealHttpMockMvcGapCoverageTest.java` | `RealHttpWorkflowCoverageTest.java :: testRealHttpHighValueWorkflowsHaveDeepAssertions` |
| `GET /api/exam-sessions/{id}` | yes | true no-mock HTTP | `RealHttpMockMvcGapCoverageTest.java` | `RealHttpMockMvcGapCoverageTest.java :: testRealHttpCoverageForPreviouslyMockMvcOnlyEndpoints` |
| `PUT /api/exam-sessions/{id}` | yes | true no-mock HTTP | `RealHttpWorkflowCoverageTest.java` | `RealHttpWorkflowCoverageTest.java :: testRealHttpHighValueWorkflowsHaveDeepAssertions` |
| `POST /api/exam-sessions/{id}/submit-review` | yes | true no-mock HTTP | `RealHttpWorkflowCoverageTest.java` | `RealHttpWorkflowCoverageTest.java :: testRealHttpHighValueWorkflowsHaveDeepAssertions` |
| `POST /api/exam-sessions/{id}/publish` | yes | true no-mock HTTP | `RealHttpWorkflowCoverageTest.java` | `RealHttpWorkflowCoverageTest.java :: testRealHttpHighValueWorkflowsHaveDeepAssertions` |
| `POST /api/exam-sessions/{id}/unpublish` | yes | true no-mock HTTP | `RealHttpWorkflowCoverageTest.java`, `RealHttpMissingEndpointsTest.java` | `RealHttpWorkflowCoverageTest.java :: testRealHttpHighValueWorkflowsHaveDeepAssertions` |
| `POST /api/exam-sessions/{id}/archive` | yes | true no-mock HTTP | `RealHttpWorkflowCoverageTest.java`, `RealHttpMissingEndpointsTest.java` | `RealHttpWorkflowCoverageTest.java :: testRealHttpHighValueWorkflowsHaveDeepAssertions` |
| `GET /api/exam-sessions/student/schedule` | yes | true no-mock HTTP | `RealHttpWorkflowCoverageTest.java` | `RealHttpWorkflowCoverageTest.java :: testRealHttpHighValueWorkflowsHaveDeepAssertions` |
| `GET /api/users` | yes | true no-mock HTTP | `RealHttpWorkflowCoverageTest.java` | `RealHttpWorkflowCoverageTest.java :: testRealHttpListEndpointsAssertPayloadShape` |
| `POST /api/users` | yes | true no-mock HTTP | `RealHttpMockMvcGapCoverageTest.java` | `RealHttpMockMvcGapCoverageTest.java :: testRealHttpCoverageForPreviouslyMockMvcOnlyEndpoints` |
| `GET /api/users/{id}` | yes | true no-mock HTTP | `RealHttpMissingEndpointsTest.java` | `RealHttpMissingEndpointsTest.java :: testPreviouslyMissingPathsOverRealHttp` |
| `PUT /api/users/{id}` | yes | true no-mock HTTP | `RealHttpMissingEndpointsTest.java` | `RealHttpMissingEndpointsTest.java :: testPreviouslyMissingPathsOverRealHttp` |
| `PUT /api/users/{id}/scope` | yes | true no-mock HTTP | `RealHttpMissingEndpointsTest.java` | `RealHttpMissingEndpointsTest.java :: testPreviouslyMissingPathsOverRealHttp` |
| `PUT /api/users/{id}/concurrent-sessions` | yes | true no-mock HTTP | `RealHttpMockMvcGapCoverageTest.java` | `RealHttpMockMvcGapCoverageTest.java :: testRealHttpCoverageForPreviouslyMockMvcOnlyEndpoints` |
| `GET /api/versions/{entityType}/{entityId}` | yes | true no-mock HTTP | `RealHttpWorkflowCoverageTest.java` | `RealHttpWorkflowCoverageTest.java :: testRealHttpHighValueWorkflowsHaveDeepAssertions` |
| `GET /api/versions/{entityType}/{entityId}/{versionNumber}` | yes | true no-mock HTTP | `RealHttpMockMvcGapCoverageTest.java` | `RealHttpMockMvcGapCoverageTest.java :: testRealHttpCoverageForPreviouslyMockMvcOnlyEndpoints` |
| `GET /api/versions/{entityType}/{entityId}/compare` | yes | true no-mock HTTP | `RealHttpWorkflowCoverageTest.java`, `RealHttpMissingEndpointsTest.java` | `RealHttpWorkflowCoverageTest.java :: testRealHttpHighValueWorkflowsHaveDeepAssertions` |
| `POST /api/versions/{entityType}/{entityId}/restore` | yes | true no-mock HTTP | `RealHttpWorkflowCoverageTest.java` | `RealHttpWorkflowCoverageTest.java :: testRealHttpHighValueWorkflowsHaveDeepAssertions` |
| `GET /api/audit` | yes | true no-mock HTTP | `RealHttpWorkflowCoverageTest.java` | `RealHttpWorkflowCoverageTest.java :: testRealHttpListEndpointsAssertPayloadShape` |
| `GET /api/anticheat/flags` | yes | true no-mock HTTP | `RealHttpWorkflowCoverageTest.java` | `RealHttpWorkflowCoverageTest.java :: testRealHttpListEndpointsAssertPayloadShape` |
| `POST /api/anticheat/flags/{id}/review` | yes | true no-mock HTTP | `RealHttpMockMvcGapCoverageTest.java` | `RealHttpMockMvcGapCoverageTest.java :: testRealHttpCoverageForPreviouslyMockMvcOnlyEndpoints` |

## API Test Classification

### 1. True No-Mock HTTP

- `repo/API_tests/src/test/java/com/eaglepoint/exam/integration/RealHttpAuthApiTest.java`
- `repo/API_tests/src/test/java/com/eaglepoint/exam/integration/RealHttpMissingEndpointsTest.java`
- `repo/API_tests/src/test/java/com/eaglepoint/exam/integration/RealHttpMockMvcGapCoverageTest.java`
- `repo/API_tests/src/test/java/com/eaglepoint/exam/integration/RealHttpWorkflowCoverageTest.java`

Evidence:

- Each class uses `@SpringBootTest(... webEnvironment = RANDOM_PORT)` plus `TestRestTemplate`, which indicates bootstrapped server + actual HTTP requests.
- No `@MockBean`, `jest.mock`, `vi.mock`, or `sinon.stub` was found under `repo/API_tests/`.

### 2. HTTP With Mocking

- `AdministrativeModulesIntegrationTest.java`
- `ApiSurfaceSmokeIntegrationTest.java`
- `AuthManagedDeviceIntegrationTest.java`
- `CrossUserAccessTest.java`
- `ExamSessionIntegrationTest.java`
- `MissingEndpointsIntegrationTest.java`
- `NotificationIntegrationTest.java`
- `RosterExportAuthorizationIntegrationTest.java`
- `RosterImportIntegrationTest.java`
- `SecurityIntegrationTest.java`
- `SignedMutationIntegrationTest.java`
- `VersionRestoreIntegrationTest.java`

Classification reason:

- These classes use `MockMvc` (`@AutoConfigureMockMvc`, `MockMvc`) rather than real socket-level HTTP transport. Under the strict definition in this audit, that is HTTP-layer route exercise with mocked/bypassed transport, not true no-mock HTTP.

### 3. Non-HTTP (unit / integration without HTTP)

- Backend unit tests in `repo/unit_tests/backend/src/test/java/...`
- Frontend unit tests in `repo/unit_tests/frontend/*.spec.js`
- Browser E2E tests exist separately in `repo/E2E_tests/specs/*.e2e.spec.js`; they are HTTP/UI-facing but are not part of the backend API test suite classification above.

## Mock Detection Rules

### Backend API test suite

- No dependency override mock markers found in `repo/API_tests/`: no `@MockBean`, no `jest.mock`, no `vi.mock`, no `sinon.stub`.
- Transport bypass detected in 12 `MockMvc`-based API test files.
  - Evidence: `repo/API_tests/src/test/java/com/eaglepoint/exam/integration/ApiSurfaceSmokeIntegrationTest.java`, class field `private MockMvc mockMvc`
  - Evidence: `repo/API_tests/src/test/java/com/eaglepoint/exam/integration/AdministrativeModulesIntegrationTest.java`, class field `private MockMvc mockMvc`
  - Evidence: same pattern across `AuthManagedDeviceIntegrationTest.java`, `CrossUserAccessTest.java`, `ExamSessionIntegrationTest.java`, `MissingEndpointsIntegrationTest.java`, `NotificationIntegrationTest.java`, `RosterExportAuthorizationIntegrationTest.java`, `RosterImportIntegrationTest.java`, `SecurityIntegrationTest.java`, `SignedMutationIntegrationTest.java`, `VersionRestoreIntegrationTest.java`

### Backend unit test suite

- Extensive Mockito mocking is present and expected for non-HTTP unit tests.
  - `repo/unit_tests/backend/src/test/java/com/eaglepoint/exam/auth/controller/AuthControllerUnitTest.java`: mocks `AuthService`, `ManagedDeviceRepository`, `UserRepository`
  - `repo/unit_tests/backend/src/test/java/com/eaglepoint/exam/notifications/service/NotificationDeliveryServiceTest.java`: mocks notification, target, delivery, inbox, subscription, and DND repositories
  - `repo/unit_tests/backend/src/test/java/com/eaglepoint/exam/proctors/service/ProctorServiceTest.java`: mocks assignment/session/class repositories plus `ScopeService` and `AuditService`
  - `repo/unit_tests/backend/src/test/java/com/eaglepoint/exam/users/service/UserServiceTest.java`: mocks `UserRepository`, `UserScopeAssignmentRepository`, `PasswordHistoryRepository`, `AuthService`, `AuditService`
  - `repo/unit_tests/backend/src/test/java/com/eaglepoint/exam/roster/service/RosterServiceTest.java`: mocks repository + scope/audit/user/class/term collaborators

### Frontend unit test suite

- `vi.mock` is used heavily to isolate router/store/API behavior.
  - `repo/unit_tests/frontend/SubscriptionSettings.spec.js`: mocks `frontend/src/api/client.js`
  - `repo/unit_tests/frontend/FrontendCoreFlows.spec.js`: mocks `vue-router`, auth store, and API client
  - `repo/unit_tests/frontend/api.client.spec.js`: mocks `axios`

## Coverage Summary

- Total endpoints: **75**
- Endpoints with HTTP tests of any kind: **75**
- Endpoints with true no-mock HTTP tests: **75**
- HTTP coverage: **100.0%**
- True API coverage: **100.0%**

Important qualification:

- Coverage breadth is complete.
- Sufficiency is not complete. Several “missing path” or “gap coverage” tests only prove that the route is reachable over real HTTP and does not throw a 5xx; they do not deeply validate business behavior.

## Unit Test Summary

### Test files

- Backend unit tests found: **32**
- Frontend unit test/spec files found: **16**

### Modules covered

- Controllers
  - Backend: `AuthControllerUnitTest`, `NotificationControllerUnitTest`
  - Frontend view/controller-equivalent behavior: `RoomProctorCrudUI.spec.js`, `AdminQueuesFlow.spec.js`, `AdminCrudFailurePaths.spec.js`, `ExamSessionDetailPublishUI.spec.js`, `NotificationListView.spec.js`, `VersionCompare.spec.js`, `SubscriptionSettings.spec.js`
- Services
  - Backend: `AntiCheatServiceTest`, `AuditServiceTest`, `AuthServiceTest`, `ComplianceServiceTest`, `ContentSafeguardServiceTest`, `ImportServiceTest`, `JobServiceTest`, `JobSchedulerTest`, `NotificationServiceTest`, `NotificationDeliveryServiceTest`, `ProctorServiceTest`, `RoomServiceTest`, `RosterServiceTest`, `ExamSessionServiceTest`, `ExamSessionStateMachineTest`, `IdempotencyServiceTest`, `ScopeServiceTest`, `UserServiceTest`, `VersionServiceTest`
- Repositories
  - Direct backend repository tests: `EntityVersionRepositoryTest`, `SessionRepositoryTest`
- Auth / guards / middleware
  - Backend: `SecurityFilterUnitTest`, `SigningFilterTest`, `RateLimitTest`, `PermissionInterceptorTest`, `WebMvcConfigTest`, `FilterConfigTest`
  - Frontend: `router.guard.spec.js`, `api.client.spec.js`

### Important modules not directly unit-tested

- Controllers without direct backend unit tests:
  - `AntiCheatController`, `AuditController`, `ComplianceController`, `ImportController`, `JobController`, `ProctorController`, `ReferenceDataController`, `RoomController`, `RosterController`, `ExamSessionController`, `UserController`, `VersionController`
- Repositories without direct backend repository tests:
  - `ManagedDeviceRepository`, `PasswordHistoryRepository`, `ComplianceReviewRepository`, `ImportJobRepository`, `ImportJobRowRepository`, `JobRunRepository`, `NotificationRepository`, `NotificationTargetRepository`, `DeliveryStatusRepository`, `InboxMessageRepository`, `SubscriptionSettingRepository`, `DndSettingRepository`, `ProctorAssignmentRepository`, `CampusRepository`, `ClassRepository`, `CourseRepository`, `GradeRepository`, `RoomRepository`, `RosterEntryRepository`, `ExamSessionRepository`, `ExamSessionClassRepository`, `TermRepository`, `UserRepository`, `UserScopeAssignmentRepository`, `IdempotencyKeyRepository`, `NonceReplayRepository`

## API Observability Check

### Strong

- `repo/API_tests/src/test/java/com/eaglepoint/exam/integration/RealHttpWorkflowCoverageTest.java :: testRealHttpHighValueWorkflowsHaveDeepAssertions`
  - Shows concrete request bodies for roster, exam session, notification, and import workflows.
  - Asserts specific response payload fields such as status transitions, IDs, version list size, compare keys, inbox contents, and delivery list shape.
- `repo/API_tests/src/test/java/com/eaglepoint/exam/integration/VersionRestoreIntegrationTest.java :: testRestoreCreatesNewVersion`
  - Shows create, update, restore, and version-number assertions across multiple version fetches.
- `repo/API_tests/src/test/java/com/eaglepoint/exam/integration/RosterImportIntegrationTest.java :: testFullImportWorkflow`
  - Shows multipart upload, commit, and import outcome checks.

### Weak

- `repo/API_tests/src/test/java/com/eaglepoint/exam/integration/ApiSurfaceSmokeIntegrationTest.java :: testMajorApiSurfaceAsAdmin`
  - Mostly status + existence checks; request/response semantics are shallow.
- `repo/API_tests/src/test/java/com/eaglepoint/exam/integration/RealHttpMissingEndpointsTest.java :: testPreviouslyMissingPathsOverRealHttp`
  - Only asserts “not server error”; endpoint, method, and route existence are visible, but expected business outcomes are intentionally weak.
- `repo/API_tests/src/test/java/com/eaglepoint/exam/integration/RealHttpWorkflowCoverageTest.java :: testRealHttpListEndpointsAssertPayloadShape`
  - Asserts array + pagination shape only; does not validate filter correctness or data semantics.

Overall observability verdict: **mixed**

## Tests Check

- Success paths: strong for auth/session, exam lifecycle, notification publication, version restore, roster import, and admin operations.
  - Evidence: `ExamSessionIntegrationTest.java :: testFullSessionLifecycle`, `NotificationIntegrationTest.java :: testNotificationToInboxFallback`, `VersionRestoreIntegrationTest.java :: testRestoreCreatesNewVersion`, `RosterImportIntegrationTest.java :: testFullImportWorkflow`
- Failure cases: present for unauthorized access, lockout, publish-without-approval, permission denial, scope denial, and missing IDs.
  - Evidence: `SecurityIntegrationTest.java :: testUnauthenticatedAccess`, `SecurityIntegrationTest.java :: testAccountLockoutAfterFailedAttempts`, `ExamSessionIntegrationTest.java :: testPublishBlockedWithoutApproval`, `RosterExportAuthorizationIntegrationTest.java :: testScopedTeacherCanExportButUnscopedTeacherGetsHeaderOnlyAndStudentForbidden`, `RealHttpMissingEndpointsTest.java :: testPreviouslyMissingPathsOverRealHttp`
- Edge cases: present but uneven.
  - Evidence: import idempotency and mixed-valid-row behavior in `RosterImportIntegrationTest.java :: testImportCommitIdempotent`, `testMixedImportCommitsOnlyValidRows`
  - Evidence: DND delivery hold in `NotificationIntegrationTest.java :: testNotificationDndHeldDeliveryStatus`
  - Weak area: many CRUD list/filter endpoints are only smoke-checked, not semantically exercised over multiple filter combinations.
- Auth / permissions: strong.
  - Evidence: `CrossUserAccessTest.java`, `SecurityIntegrationTest.java`, `SignedMutationIntegrationTest.java`, `RosterExportAuthorizationIntegrationTest.java`, browser permission specs in `repo/E2E_tests/specs/permission-denial.e2e.spec.js`
- Integration boundaries: good.
  - Evidence: real HTTP coverage across auth, persistence-backed workflows, job processing, import, and versioning in `RealHttpWorkflowCoverageTest.java` and `RealHttpMockMvcGapCoverageTest.java`
- Real assertions vs superficial:
  - Strong examples: `RealHttpWorkflowCoverageTest.java`, `VersionRestoreIntegrationTest.java`, `NotificationIntegrationTest.java`
  - Superficial examples: `ApiSurfaceSmokeIntegrationTest.java`, `RealHttpMissingEndpointsTest.java`
- `run_tests.sh` check:
  - Docker-based: **OK**
  - Evidence: `repo/run_tests.sh:27-67`, `repo/docker-compose.yml:3-64`
  - Local dependency flag: **not triggered**
  - Qualification: frontend test runner uses `npm ci`, but only inside `node:20-alpine` container (`repo/run_tests.sh:39-47`), not on the host.

## End-to-End Expectations

- Project is fullstack and browser E2E coverage is present.
  - Evidence: `repo/E2E_tests/specs/exam-lifecycle-version.e2e.spec.js :: exam session create -> compliance approve -> publish -> version restore`
  - Evidence: `repo/E2E_tests/specs/admin-ops-room-proctor-job-audit.e2e.spec.js :: admin room/proctor/job/audit/export operational UX flow`
  - Evidence: `repo/E2E_tests/specs/notification-persistence.e2e.spec.js :: notification create flow persists through backend and reload`
  - Evidence: `repo/E2E_tests/specs/student-inbox-subscriptions-role.e2e.spec.js :: student role UX + inbox/subscriptions after compliance-approved publish`

Verdict: **E2E expectation met**

## Test Coverage Score (0-100)

**84 / 100**

## Score Rationale

- + Complete endpoint inventory coverage: all 75 endpoints have HTTP tests.
- + Complete true no-mock HTTP route coverage: all 75 endpoints are exercised through `TestRestTemplate` in `RealHttp*` suites.
- + Good workflow depth on core business paths: auth/session, exam lifecycle, notifications, version restore, imports.
- + Strong auth/permission/security coverage across both backend API tests and browser E2E tests.
- - Significant fraction of route coverage is shallow. `RealHttpMissingEndpointsTest.java` proves many routes only by “not 5xx” behavior against nonexistent IDs.
- - Several smoke/list tests assert only status, array shape, or pagination presence, not business correctness.
- - Backend controller unit coverage is sparse outside auth and notifications.
- - Direct repository tests are sparse relative to repository count.

## Key Gaps

- Gap 1: route reachability is stronger than behavioral sufficiency.
  - Evidence: `RealHttpMissingEndpointsTest.java :: testPreviouslyMissingPathsOverRealHttp`
- Gap 2: list/filter endpoints are under-asserted for semantic correctness.
  - Evidence: `ApiSurfaceSmokeIntegrationTest.java :: testMajorApiSurfaceAsAdmin`
  - Evidence: `RealHttpWorkflowCoverageTest.java :: testRealHttpListEndpointsAssertPayloadShape`
- Gap 3: backend controller unit tests are missing for most modules outside auth/notifications.
  - Evidence: controller files under `repo/backend/src/main/java/com/eaglepoint/exam/*/controller/*.java` vs unit controller tests only in `auth/controller/AuthControllerUnitTest.java` and `notifications/controller/NotificationControllerUnitTest.java`
- Gap 4: direct repository tests cover only a narrow subset.
  - Evidence: `EntityVersionRepositoryTest.java`, `SessionRepositoryTest.java`

## Confidence & Assumptions

- Confidence: **high**
- Assumption 1: `MockMvc` is not counted as true no-mock HTTP because transport is bypassed, even though real application handlers execute.
- Assumption 2: endpoint coverage was determined strictly from visible request calls and resolved controller mappings; no runtime route discovery was performed.
- Assumption 3: query parameters are treated as part of request examples, but endpoint identity is method + normalized path only.

## Test Coverage Verdict

**PASS WITH MATERIAL QUALITY RESERVATIONS**

Breadth is complete. Depth is uneven.

# README Audit

## Project Type Detection

- README declares project type explicitly as `fullstack`.
  - Evidence: `repo/README.md:3`

## README Location

- `repo/README.md` exists.
  - Evidence: `repo/README.md`

## Hard Gate Check

### Formatting

- PASS
  - Evidence: clear headings, fenced commands, credentials table, numbered verification flow in `repo/README.md:7-98`

### Startup Instructions

- PASS
  - Includes required `docker-compose up`
  - Evidence: `repo/README.md:15-20`

### Access Method

- PASS
  - Frontend URL provided: `http://localhost`
  - Backend API URL provided: `http://localhost:8080`
  - Evidence: `repo/README.md:28-30`

### Verification Method

- PASS
  - README provides API-level verification (`POST /api/auth/login`, `GET /api/auth/session`, `GET /api/notifications/subscriptions`) and UI flows (roster import, compliance review, audit log, versioning).
  - Evidence: `repo/README.md:56-78`

### Environment Rules

- PASS
  - README does not instruct host-side `npm install`, `pip install`, `apt-get`, manual DB setup, or runtime dependency installation.
  - Evidence: `repo/README.md:7-42`
  - Supporting repo evidence: Dockerized services in `repo/docker-compose.yml:3-64`, Dockerized test execution in `repo/run_tests.sh:27-67`

### Demo Credentials

- PASS
  - Username/password/role provided for all five roles: `ADMIN`, `ACADEMIC_COORDINATOR`, `HOMEROOM_TEACHER`, `SUBJECT_TEACHER`, `STUDENT`
  - Evidence: `repo/README.md:44-54`

## Engineering Quality

- Tech stack clarity: good
  - Evidence: `Vue SPA -> Spring Boot REST -> MySQL` in `repo/README.md:80-84`
- Architecture explanation: adequate but brief
  - Evidence: module list in `repo/README.md:84`
- Testing instructions: good
  - Evidence: `repo/README.md:32-42`
- Security / roles: good
  - Evidence: credentials table + security controls in `repo/README.md:44-54`, `86-94`
- Workflows: good
  - Evidence: deterministic verification workflow in `repo/README.md:56-78`
- Presentation quality: good
  - Evidence: consistent structure and formatting throughout `repo/README.md`

## High Priority Issues

- None found.

## Medium Priority Issues

- The API verification steps are descriptive rather than executable.
  - There are endpoint names and expected outcomes, but no copy-paste `curl` or Postman-ready examples.
  - Evidence: `repo/README.md:58-72`
- The startup section mixes local-demo and production wording.
  - `cp .env.example .env` is local-demo oriented, while “set real secrets in .env for production use” introduces production guidance without separating it from local bring-up.
  - Evidence: `repo/README.md:15-18`

## Low Priority Issues

- README does not call out the default frontend login route or first page to open after startup.
  - Access URL is present, but the first interactive landing step is implicit.
  - Evidence: `repo/README.md:28-30`
- README points to `docs/api-spec.md` but does not summarize where the most important API collections start.
  - Evidence: `repo/README.md:96-98`

## Hard Gate Failures

- None.

## README Verdict

**PASS**

The README satisfies the strict hard gates for a fullstack project at `repo/README.md`. Its remaining weaknesses are clarity/operability improvements, not compliance failures.
