# Test Coverage Plan (Risk-First)

## Coverage Strategy
Tests are organized by risk priority. High-risk areas get integration tests exercising real service methods with seeded database fixtures. Medium-risk areas get unit tests with focused assertions. Low-risk areas are documented for manual verification.

**Repository layout:** Java unit tests live under `repo/unit_tests/backend/src/test/java/`. API / integration tests live under `repo/API_tests/src/test/java/` (`com.eaglepoint.exam.integration`). Frontend unit specs live under `repo/unit_tests/frontend/` (Vitest includes them from `repo/frontend/vite.config.js`). Browser E2E tests live under `repo/E2E_tests/specs/` (Playwright).

## Risk-First Coverage Mapping

| # | Requirement / Risk Point | Mapped Test Case(s) | Key Assertion / Fixture / Mock | Coverage | Gap | Min Test Addition |
|---|-------------------------|---------------------|-------------------------------|----------|-----|-------------------|
| 1 | Authentication happy path | `AuthServiceTest.testLoginSuccess` | Seeded user, valid credentials -> session token returned | Unit+Integration | None | - |
| 2 | Failed login lockout (5 attempts) | `AuthServiceTest.testAccountLockout` | Repeated failed logins -> account locked when threshold reached, lockout record created | Unit+Integration | None | - |
| 3 | Concurrent session block | `AuthServiceTest.testConcurrentSessionBlocked` | Existing session + new device -> 409 | Unit | None | - |
| 4 | Request signing success | `SigningFilterTest.testValidSignature` | Valid HMAC, timestamp, nonce -> request passes | Unit | None | - |
| 5 | Request signing failure | `SigningFilterTest.testInvalidSignature` | Tampered signature -> 401 | Unit | None | - |
| 6 | Request signing replay | `SigningFilterTest.testReplayNonce` | Same nonce twice -> second rejected | Unit | None | - |
| 7 | Stale timestamp rejection | `SigningFilterTest.testStaleTimestamp` | Timestamp >120s old -> rejected | Unit | None | - |
| 8 | 401 unauthenticated | `SecurityIntegrationTest.testUnauthenticatedAccess` | No session token -> 401 on protected endpoint | Integration | None | - |
| 9 | 403 unauthorized | `CrossUserAccessTest` | Student token -> 403 on admin endpoint | Integration | None | - |
| 10 | Row-scope denial | `CrossUserAccessTest` | Teacher A cannot view Teacher B's class roster | Integration | None | - |
| 11 | Import preview validation | `ImportServiceTest.testPreviewValidation` | CSV with errors -> detailed error report returned | Unit+Integration | None | - |
| 12 | Import commit atomicity | `ImportServiceTest.testCommitAtomicity` | All valid -> committed; any invalid at commit -> rollback | Integration | None | - |
| 13 | Import idempotency | `ImportServiceTest.testCommitIdempotency` | Same idempotency key -> second call returns same result | Unit | None | - |
| 14 | Version restore semantics | `VersionServiceTest.testRestoreCreatesNewVersion` | Restore v2 -> creates v4 (if current is v3) | Unit+Integration | None | - |
| 15 | Version immutability | `VersionServiceTest.testHistoricalVersionsImmutable` | After restore, v1/v2/v3 unchanged | Unit | None | - |
| 16 | Publish blocked without compliance | `ExamSessionServiceTest.testPublishBlockedWithoutApproval` | Session in draft -> publish returns 409 | Unit | None | - |
| 17 | Compliance gating flow | `ComplianceServiceTest.testFullReviewWorkflow` | Submit -> approve -> publish allowed | Integration | None | - |
| 18 | Invalid state transitions | `ExamSessionServiceTest.testInvalidTransition` | Published -> draft returns 409 | Unit | None | - |
| 19 | Notification DND suppression | `NotificationIntegrationTest.testNotificationDndHeldDeliveryStatus` | Student DND window active -> delivery entry `delivered_dnd_held` on `IN_APP` channel | Integration | None | - |
| 20 | WeChat fallback to inbox | `NotificationIntegrationTest.testNotificationToInboxFallback` | Publish with WeChat disabled -> delivery status `fallback_delivered`, inbox entry created | Integration | None | - |
| 21 | Notification blocked for unapproved | `NotificationIntegrationTest.testNotificationBlockedWithoutApproval` | Publish attempt before compliance approval -> 409 Conflict | Integration | None | - |
| 22 | Idempotent job retry | `JobServiceTest.testIdempotentRetry` | Failed job -> retry succeeds -> no duplicate processing | Unit | None | - |
| 23 | Job dedup | `JobServiceTest.testDedupKey` | Same dedup key -> second enqueue rejected | Unit | None | - |
| 24 | Job manual rerun | `JobServiceTest.testManualRerun` | Failed job -> rerun creates new queued job | Unit | None | - |
| 25 | Audit trail creation | `AuditServiceTest.testAuditCreated` | Each operation type creates audit record | Unit | None | - |
| 26 | Encryption persistence | `EncryptionTest.testEncryptedAtRest` | Student ID stored as ciphertext in DB | Integration | None | - |
| 27 | Masked response | `MaskingTest.testMaskedResponse` | API response shows masked student ID | Unit | None | - |
| 28 | Password complexity | `AuthServiceTest.testPasswordComplexity` | Password under 12 chars or missing complexity -> rejected | Unit | None | - |
| 29 | Rate limiting | `RateLimitTest.testUserRateLimit` | 61st request in 1 minute -> 429 | Unit | None | - |
| 30 | Session expiry | `AuthServiceTest.testSessionExpiry` | Session older than 30min inactive -> 401 | Unit | None | - |
| 31 | Anti-cheat flag creation | `AntiCheatServiceTest.testScoreDeltaFlag` | Abnormal score delta -> flag created | Unit | None | - |
| 32 | Export scope + role enforcement | `RosterExportAuthorizationIntegrationTest` | Scoped teacher export includes visible rows; unscoped teacher gets header-only; student export is forbidden (403) | Integration | None | - |
| 33 | Restore triggers re-review | `VersionServiceTest.testRestoreTriggersReReview` | Restore published content -> state becomes submitted_for_review | Integration | None | - |
| 34 | Remember-device managed-device boundary | `AuthManagedDeviceIntegrationTest` | `rememberDevice=true` extends session only after device registration; unmanaged device remains short-session | Integration | None | - |
| 35 | State-machine transition hardening | `ExamSessionStateMachineTest` | All 9 legal transitions validated; 8 illegal transitions assert `StateTransitionException`; `getValidTransitions` asserted for all states | Unit | None | - |
| 36 | Content safeguard screening | `ContentSafeguardServiceTest` | Blocked term/PII/phone-PII flagged; clean content passes; health-without-disclaimer blocked; multiple violations in one call reported | Unit | None | - |
| 37 | Exam-session ABAC predicate | `ScopeServiceTest` | All scope types (campus/term/course/class) checked; ADMIN bypasses; STUDENT always denied; `enforceScope` grant/deny; `filterByUserScope` admin empty / teacher populated; `listScopeIds` deduplication | Unit | None | - |
| 38 | Notification delivery behavior depth | `NotificationDeliveryServiceTest` | DND hold, opt-out skip, WeChat-disabled fallback, multi-target all-processed, all-opted-out marks FAILED | Unit | None | - |
| 39 | Scheduler operational tasks | `JobSchedulerTest` | Job processing, nonce/idempotency cleanup, DND-active / DND-expired / null-window edge cases all handled without error | Unit | None | - |
| 40 | User-service mutation semantics | `UserServiceTest` | Weak password rejected; create+password-history+audit; scope assignments persisted; `getUser` not-found; `updateUser` field change + audit; `updateScope` replaces assignments; `toggleConcurrentSessions` enable/disable + audit | Unit | None | - |
| 41 | Room/proctor/roster service behavior | `RoomServiceTest`, `ProctorServiceTest`, `RosterServiceTest` | Full CRUD on campus/room with audit; scope-filtered campus list (admin all / scoped / empty); proctor create+delete scope checks; roster student-owns-only, admin-only delete, export with real data rows | Unit | None | - |
| 42 | Import exclusion + rollback-path semantics | `RosterImportIntegrationTest.testMixedImportCommitsOnlyValidRows`, `RosterImportIntegrationTest.testAdminRollbackTransitionsJobToRolledBack` | Mixed valid/invalid preview commits only valid subset; admin rollback endpoint transitions import job to `ROLLED_BACK` | Integration | Partial: deep row-level rollback effects still need direct assertions | Add rollback-effect assertion against soft-delete state by import lineage |
| 43 | Notification retry/expiry transition nuance | `NotificationStatusTest`, `NotificationServiceTest.testCancelQueuedNotificationAllowed`, `NotificationServiceTest.testCancelDeliveredNotificationBlocked` | Retry/expiry transition legality and cancel-state guard are asserted explicitly | Unit | None | - |
| 44 | Health-disclaimer safeguard rule | `ContentSafeguardServiceTest.testHealthContentRequiresDisclaimer` | Health text without required disclaimer is flagged | Unit | None | - |
| 45 | Non-admin admin-surface denial permutations | `AdministrativeModulesIntegrationTest.testNonAdminPermissionBoundariesOnAdminModules` | Homeroom teacher gets 403 for jobs, audit, and campus create | Integration | None | - |

## Frontend Test Coverage

| # | Requirement / Risk Point | Mapped Test Case(s) | Key Assertion | Coverage |
|---|-------------------------|---------------------|---------------|----------|
| F1 | Route guard by role | `router.guard.spec.js` | Student cannot access /admin routes | Unit |
| F2 | API client path contracts | `api.client.spec.js` | All API paths match backend routes | Unit |
| F3 | Subscription settings | `SubscriptionSettings.spec.js` | Toggle event types, DND validation | Unit |
| F4 | DND UI validation | `DndSettings.spec.js` | Start < End enforced, time format | Unit |
| F5 | Import preview rendering | `ImportPreview.spec.js` | Error rows displayed with row/field/reason | Unit |
| F6 | Version compare UI | `VersionCompare.spec.js` | Two versions diffed, changes highlighted | Unit |
| F7 | Version restore UI | `VersionCompare.spec.js` | Restore confirmation, new version displayed | Unit |
| F8 | Delivery status rendering | `DeliveryStatus.spec.js` | Per-channel status displayed correctly | Unit |
| F9 | Notification publish action gating in real view | `NotificationListView.spec.js` | Publish button appears only for `DRAFT && complianceApproved` rows | Unit |
| F10 | Exam-session publish gating in real view | `ExamSessionDetailPublishUI.spec.js` | `DRAFT` shows submit-only; `APPROVED` shows publish action | Unit |
| F11 | Core user-path form flows | `FrontendCoreFlows.spec.js` | Login submit, notification create payload, user-create flow, roster export flow | Unit |
| F12 | Admin queue behavior | `AdminQueuesFlow.spec.js` | Compliance reject-comment validation, job rerun/cancel actions, anti-cheat review submission | Unit |
| F13 | Admin CRUD failure-path validation | `AdminCrudFailurePaths.spec.js` | Invalid/partial form submissions block API calls and render specific validation errors | Unit |
| F14 | Empty/loading/error states | `TableStates.spec.js` | Each state renders correctly | Unit |
| F15 | Major view smoke pass (explicitly low-depth) | `MajorViewsCoverage.spec.js` | Real imports mount and trigger initial fetch hooks | Smoke |
| F16 | Session expiry router guard (unit-level) | `SessionExpiryUI.spec.js` | Unauthenticated navigation redirects to `/login` with redirect param; session cleared post-auth forces re-login; re-login follows redirect back to requested URL | Unit |
| F17 | Room/proctor CRUD UI validation | `RoomProctorCrudUI.spec.js` | Room edit dialog pre-fills, empty-name blocks save, valid rename calls `updateRoom`; proctor assign without selection shows validation error | Unit |

## Integration Test Coverage

| # | Flow | Test File | Key Assertions |
|---|------|-----------|---------------|
| I1 | Roster import to preview to commit | `RosterImportIntegrationTest` | Upload -> preview errors shown -> commit persists -> version created |
| I2 | Session create to review to publish | `ExamSessionIntegrationTest` | Draft -> submit -> approve -> publish -> student visible |
| I3 | Notification to queue to fallback | `NotificationIntegrationTest` | Create -> approve -> publish -> WeChat fail -> inbox created |
| I4 | Cross-user access denial | `CrossUserAccessTest` | User A cannot read User B's scoped data |
| I5 | Restore creates new version | `VersionRestoreIntegrationTest` | Restore -> new version number -> old versions unchanged |
| I6 | Single-node job execution | `JobServiceTest` (unit) | Queued job -> picked up -> executed -> completed |
| I7 | Real browser signed flow (SPA + backend) | `E2E_tests/specs/auth-signing.e2e.spec.js` | Browser login succeeds, signed route works, tampered signing key forces re-auth |
| I8 | Real browser persistence flow (SPA + backend + DB) | `E2E_tests/specs/notification-persistence.e2e.spec.js` | Create notification in UI, verify appears after reload |
| I9 | Roster import browser flow | `E2E_tests/specs/roster-import.e2e.spec.js` | Browser upload transitions to preview and shows parsed validation summary |
| I10 | Exam lifecycle and restore in browser | `E2E_tests/specs/exam-lifecycle-version.e2e.spec.js` | Create session -> compliance approval -> publish -> version restore creates a new version row |
| I11 | Student role UX and inbox/subscription flow | `E2E_tests/specs/student-inbox-subscriptions-role.e2e.spec.js` | Admin creates+publishes message; student sees role-scoped nav, inbox read action, and subscription save feedback |
| I12 | Admin/API module depth and failure paths | `AdministrativeModulesIntegrationTest` | Validation failures (400), not-found behavior (404), and CRUD lifecycle assertions for users, rooms, auth devices, jobs/audit/anti-cheat/proctor/reference endpoints |
| I13 | Signed transport on mutating flows (server-enforced) | `SignedMutationIntegrationTest` | Unsigned authenticated mutations rejected; signed POST/PUT mutations accepted for campus and user-management paths |
| I14 | Admin ops breadth in browser | `E2E_tests/specs/admin-ops-room-proctor-job-audit.e2e.spec.js` | Real admin flow covers campus/room CRUD, proctor assignment validation, job monitor page, audit page, and roster export entrypoint visibility |
| I15 | Lockout UX in browser | `E2E_tests/specs/lockout-ui.e2e.spec.js` | Admin creates user in UI; repeated failed logins show lockout feedback on real login screen |
| I16 | Import invalid-path UX depth | `E2E_tests/specs/roster-import.e2e.spec.js` | Invalid import preview disables commit path and supports downloadable error artifact |
| I17 | Admin ops workflow depth (state-changing) | `E2E_tests/specs/admin-ops-room-proctor-job-audit.e2e.spec.js` | Campus/room create+edit, notification publish producing job rows, audit filtering by entity/action, and real export download assertion |
| I18 | Session expiry and re-auth in browser | `E2E_tests/specs/session-expiry-reauth.e2e.spec.js` | Unauth direct URL → login with redirect param; localStorage-cleared session → login redirect; post-login follows original redirect; logout blocks re-entry | E2E |
| I19 | Role-based permission denial in browser | `E2E_tests/specs/permission-denial.e2e.spec.js` | Student blocked from admin routes to dashboard; student inbox accessible but notification-create blocked; teacher blocked from audit/jobs; all protected routes redirect unauthed visitors | E2E |

`run_tests.sh e2e` starts Docker Compose services (`mysql`, `backend`, `frontend`) and then executes the Playwright suite in a dedicated container, so browser-signing and frontend-backend-persistence paths are covered in an automated Docker flow.

## Manual Verification Required

| Item | Reason | How to Verify |
|------|--------|--------------|
| Production-like XLSX parsing edge cases | Requires large/real-world binary samples | Upload representative XLSX files (size, merged cells, unusual encodings) |
| WeChat HTTP adapter against real intranet endpoint | Requires external intranet service | Configure `app.wechat.mode=http` and validate endpoint behavior and retries |
| Multi-node job shard distribution | Requires 2+ backend instances | Scale backend replicas and validate shard ownership / failover behavior |
| Session inactivity timeout timing under wall-clock delay | Requires real waiting and time controls | Login, wait >30 minutes inactive, verify session rejection |
