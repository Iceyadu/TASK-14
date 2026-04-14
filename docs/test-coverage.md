# Test Coverage Plan (Risk-First)

## Coverage Strategy
Tests are organized by risk priority. High-risk areas get integration tests exercising real service methods with seeded database fixtures. Medium-risk areas get unit tests with focused assertions. Low-risk areas are documented for manual verification.

**Repository layout:** Java unit tests live under `repo/unit_tests/backend/src/test/java/`. API / integration tests live under `repo/API_tests/src/test/java/` (`com.eaglepoint.exam.integration`). Frontend unit specs live under `repo/unit_tests/frontend/` (Vitest includes them from `repo/frontend/vite.config.js`).

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
| 19 | Notification DND suppression | `NotificationServiceTest.testDndSuppression` | Student DND active -> notification held, delivered after | Unit | None | - |
| 20 | WeChat fallback to inbox | `NotificationServiceTest.testWeChatFallbackToInbox` | Validates FALLBACK_TO_IN_APP status transition is legal from FAILED and SENDING states (enum-level only; does not exercise actual delivery or inbox creation) | Unit | Delivery-level fallback not tested in unit suite | Add integration or service test calling `deliverNotification()` with WeChat disabled |
| 21 | Notification blocked for unapproved | `NotificationServiceTest.testBlockedUnapproved` | Notification for unapproved content -> delivery blocked | Unit | None | - |
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
| 32 | Export scope enforcement | `RosterService` scope filtering | Teacher export returns only scoped data | Unit | None | - |
| 33 | Restore triggers re-review | `VersionServiceTest.testRestoreTriggersReReview` | Restore published content -> state becomes submitted_for_review | Integration | None | - |

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
| F9 | Publish blocked message | `PublishBlock.spec.js` | Shows "requires compliance approval" when unapproved | Unit |
| F10 | Empty/loading/error states | `TableStates.spec.js` | Each state renders correctly | Unit |

## Integration Test Coverage

| # | Flow | Test File | Key Assertions |
|---|------|-----------|---------------|
| I1 | Roster import to preview to commit | `RosterImportIntegrationTest` | Upload -> preview errors shown -> commit persists -> version created |
| I2 | Session create to review to publish | `ExamSessionIntegrationTest` | Draft -> submit -> approve -> publish -> student visible |
| I3 | Notification to queue to fallback | `NotificationIntegrationTest` | Create -> approve -> publish -> WeChat fail -> inbox created |
| I4 | Cross-user access denial | `CrossUserAccessTest` | User A cannot read User B's scoped data |
| I5 | Restore creates new version | `VersionRestoreIntegrationTest` | Restore -> new version number -> old versions unchanged |
| I6 | Single-node job execution | `JobServiceTest` (unit) | Queued job -> picked up -> executed -> completed |

## Manual Verification Required

| Item | Reason | How to Verify |
|------|--------|--------------|
| Docker Compose startup | Requires Docker runtime | Run `docker compose up` and verify all services healthy |
| XLSX file parsing | Requires actual XLSX binary | Upload sample XLSX through import endpoint |
| WeChat integration | Requires intranet WeChat deployment | Mock WeChat server or use test endpoint |
| Multi-node job distribution | Requires multiple backend instances | Scale backend to 2+ in Docker and observe job distribution |
| Browser request signing | Requires actual browser interaction | Use the Vue.js frontend to make requests and verify signing headers |
| Session inactivity timeout | Requires 30-minute wait | Login, wait 30 minutes, verify session rejected |
