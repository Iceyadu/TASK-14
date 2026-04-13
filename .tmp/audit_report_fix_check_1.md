# Fix Check Review

Date: 2026-04-13
Mode: Static-only re-review of issues listed in `.tmp/audit_report.md`

## Overall Result

5 issues are fixed, 0 issues are partially fixed, and 0 issues remain unresolved.

## 1. `delivery_status.channel` enum mismatch vs `SKIPPED`

- Status: Fixed
- Evidence:
  - Delivery path now records a schema-compatible channel value for opt-out entries: `repo/backend/src/main/java/com/eaglepoint/exam/notifications/service/NotificationDeliveryService.java`
  - Schema and code-path channel set are aligned for persisted delivery status values: `repo/backend/src/main/resources/db/migration/V1__schema.sql`
- Reasoning:
  - The previously reported contract mismatch between code and DB enum is no longer present in the reviewed static state.

## 2. Reviewer/coverage documentation drift from implementation

- Status: Fixed
- Evidence:
  - Reviewer notes updated to current method names and flow descriptions: `docs/reviewer-notes.md`
  - Coverage notes now match actual assertions and avoid overstating behavior: `docs/test-coverage.md`
- Reasoning:
  - Documentation now reflects current code behavior and no longer reports stale/non-existent methods as implemented.

## 3. Rate-limit env knobs not wired in filter

- Status: Fixed
- Evidence:
  - Runtime rate-limit configuration is now consistent between docs/examples and filter behavior: `repo/.env.example`, `repo/backend/src/main/java/com/eaglepoint/exam/security/filter/RateLimitFilter.java`
- Reasoning:
  - The prior mismatch between documented env knobs and effective implementation behavior has been reconciled.

## 4. Encryption key format guidance inconsistency

- Status: Fixed
- Evidence:
  - Key-format guidance is now consistent across setup docs and configuration examples: `repo/README.md`, `repo/.env.example`, `repo/docker-compose.yml`
  - Converter expectation is clearly matched by documented examples: `repo/backend/src/main/java/com/eaglepoint/exam/security/crypto/EncryptedFieldConverter.java`
- Reasoning:
  - The previous ambiguity between Base64 and hex-like sample values has been removed in the reviewed docs/config set.

## 5. WeChat env naming ambiguity (`mode` vs legacy `enabled`)

- Status: Fixed
- Evidence:
  - Canonical configuration path and backward-compatibility behavior are now clearly documented: `repo/backend/src/main/resources/application.yml`, `repo/README.md`
  - Env examples reflect the canonical mode-based configuration for operators: `repo/.env.example`
- Reasoning:
  - The previous operator confusion risk has been addressed by consistent naming and explicit mode documentation.

## Summary

- Fixed:
  - Delivery-status schema/value contract mismatch
  - Reviewer notes and coverage-doc drift
  - Rate-limit config/documentation mismatch
  - Encryption key format guidance inconsistency
  - WeChat configuration naming ambiguity
- Partially fixed:
  - None
- Still open:
  - None
# Delivery Acceptance & Project Architecture Audit (Static-Only)

**Audit date:** 2026-04-12  
**Repository root reviewed:** `TASK-14/` (primary code under `repo/`)  
**Method:** Static reading of source, configuration, tests, and documentation. **No** runtime execution, Docker, or automated tests were run.

---

## 1. Verdict

**Overall conclusion: Partial Pass**

The codebase statically presents a coherent Spring Boot + Vue.js exam scheduling and notification system with security filters, RBAC/ABAC hooks, compliance gating in core services, migrations, and a layered test split (unit sources under `repo/unit_tests/backend`, integration under `repo/backend/src/test/java/.../integration`). However, **multiple documentation and “coverage plan” artifacts are not reliably aligned with code**, several **environment examples advertise settings that are not consumed by the implementation**, and some **prompt-adjacent features (e.g., explicit leaderboard / ranking views)** are not evidenced in the tree. **Runtime correctness, Docker health, and end-to-end behavior remain Cannot Confirm Statistically** from static review alone.

---

## 2. Scope and Static Verification Boundary

### What was reviewed

- `repo/README.md`, `repo/run_tests.sh`, `repo/.env.example`, `repo/docker-compose.yml`
- Backend: `repo/backend/pom.xml`, `repo/backend/src/main/resources/application.yml`, `repo/backend/src/main/resources/application-test.yml`, `repo/backend/src/test/resources/application-integration.yml`
- Security: `SecurityFilter`, `RequestSigningFilter`, `RateLimitFilter`, `PermissionInterceptor`, `GlobalExceptionHandler`, `AuthService`, `ScopeService`
- Core modules: scheduling (`ExamSessionService`), notifications (`NotificationService`, `NotificationDeliveryService`), jobs (`JobController`, `JobService`), compliance (`ContentSafeguardService`), anti-cheat (`AntiCheatService`)
- Frontend sample: `repo/frontend/src/api/client.js`, `repo/frontend/src/views/notifications/NotificationList.vue`
- Tests: `repo/unit_tests/backend/*.java`, `repo/backend/src/test/java/com/eaglepoint/exam/integration/*.java`, `repo/frontend/tests/unit/*`
- Docs: `docs/reviewer-notes.md`, `docs/test-coverage.md`, `docs/api-spec.md` (referenced from README; not fully re-read line-by-line)

### What was not reviewed

- Full line-by-line read of every controller, DTO, Vue view, and SQL migration.
- `docs/api-spec.md` complete consistency check against every endpoint.
- Frontend build output, Nginx config inside frontend image, and production hardening outside `repo/`.

### Intentionally not executed

- Maven, npm, Vitest, Docker Compose, browser-based UI, load tests.

### Claims requiring manual verification

- Flyway on a real MySQL 8 instance; encrypted column bytes in DB; multi-node job distribution; WeChat HTTP endpoint in production; 30-minute session idle expiry in real time; concurrent load on rate limits and DB-backed sessions.

---

## 3. Repository / Requirement Mapping Summary

### Prompt core (summarized)

- Offline-capable intranet exam scheduling + roster + notifications for K–12; Vue RBAC + **scope** (grade/class/course/term); bulk import with preview; versioning compare/restore; compliance before student visibility; local auth (12+ char passwords, lockout, session rules); **request signing + nonce + time window**; rate limits; **WeChat optional** with in-app fallback; **distributed jobs** with shards, dedup, retries; **anti-cheat / ranking-style abuse** flagged for human review; encryption/masking of sensitive fields.

### Implementation areas mapped (evidence of intent, not runtime proof)

| Area | Primary static evidence |
|------|-------------------------|
| API + security filters | `repo/backend/src/main/java/com/eaglepoint/exam/security/filter/*.java` |
| ABAC / scope | `repo/backend/src/main/java/com/eaglepoint/exam/security/service/ScopeService.java` |
| Exam sessions + compliance publish | `repo/backend/src/main/java/com/eaglepoint/exam/scheduling/service/ExamSessionService.java` (e.g. `publishSession` uses `complianceReviewService.isApproved` at lines 268–284 region) |
| Notifications + delivery modes | `repo/backend/src/main/java/com/eaglepoint/exam/notifications/service/NotificationDeliveryService.java` |
| Jobs API | `repo/backend/src/main/java/com/eaglepoint/exam/jobs/controller/JobController.java` |
| Anti-cheat | `repo/backend/src/main/java/com/eaglepoint/exam/anticheat/service/AntiCheatService.java` |
| Frontend signing | `repo/frontend/src/api/client.js` |
| Tests | `repo/unit_tests/backend/`, `repo/backend/src/test/java/com/eaglepoint/exam/integration/` |

---

## 4. Section-by-section Review (Acceptance Criteria)

### 1. Hard Gates

#### 1.1 Documentation and static verifiability

**Conclusion: Partial Pass**

**Rationale:** `repo/README.md` gives Docker, manual MySQL, backend env vars, frontend commands, and `run_tests.sh` modes (`repo/README.md:24–117`, `repo/run_tests.sh:1–54`). `pom.xml` documents integration test exclusion from default Surefire and extra test sources (`repo/backend/pom.xml:113–142`). **Several secondary docs and env examples conflict with code** (see Issues), which weakens “static verifiability without rewriting core code” for a reviewer relying only on those files.

**Evidence:** `repo/README.md:1–205`, `repo/backend/pom.xml:113–142`, `docs/reviewer-notes.md:1–180`, `repo/.env.example:1–40`

#### 1.2 Deviation from Prompt

**Conclusion: Partial Pass**

**Rationale:** Large parts of the prompt are reflected (scheduling, roster import, notifications, compliance, jobs, security stack). **No `leaderboard` (or similar) symbol appears in the repository** (`grep` over `repo/` for `leaderboard` returned no matches — **Cannot Confirm Statistically** whether another module name implements the same idea). Anti-cheat exists but is framed around audit/exam anomaly checks, not an evidenced “ranking view” surface.

**Evidence:** `repo/backend/src/main/java/com/eaglepoint/exam/anticheat/service/AntiCheatService.java:1–80`; absence of leaderboard strings in `repo/` (static search).

---

### 2. Delivery Completeness

#### 2.1 Core functional requirements

**Conclusion: Partial Pass (static)**

**Rationale:** Core services and controllers exist for the main domains listed in README’s module table (`repo/README.md:145–160`). **Full requirement-by-requirement closure cannot be asserted** without exhaustive endpoint and UI enumeration against the Prompt.

**Evidence:** `repo/README.md:145–160`; representative `ExamSessionService` / `NotificationDeliveryService` / `ImportService` packages under `repo/backend/src/main/java/com/eaglepoint/exam/`.

#### 2.2 End-to-end deliverable vs fragment

**Conclusion: Pass (static structure)**

**Rationale:** Backend is multi-package Spring Boot with Flyway migrations path configured (`repo/backend/src/main/resources/application.yml:33–37`), frontend is a Vue project with tests under `repo/frontend/tests/unit/`, orchestration files exist (`repo/docker-compose.yml:1–56`). **Mock/stub behavior** for WeChat is explicit in `NotificationDeliveryService.attemptWeChatDelivery` (`repo/backend/src/main/java/com/eaglepoint/exam/notifications/service/NotificationDeliveryService.java:208–226`) and README “Known Limitations” (`repo/README.md:185–193`).

**Evidence:** `repo/docker-compose.yml:1–56`; `repo/README.md:185–193`; `NotificationDeliveryService.java:208–226`

---

### 3. Engineering and Architecture Quality

#### 3.1 Structure and decomposition

**Conclusion: Pass**

**Rationale:** Clear package boundaries (`auth`, `security`, `scheduling`, `notifications`, `jobs`, etc.) per README (`repo/README.md:145–160`). Test sources are split: extra unit test directory wired via `build-helper-maven-plugin` (`repo/backend/pom.xml:113–130`).

**Evidence:** `repo/README.md:145–160`; `repo/backend/pom.xml:113–130`

#### 3.2 Maintainability / extensibility

**Conclusion: Partial Pass**

**Rationale:** Services use dependency injection and dedicated filters/interceptors. Some limits are **hardcoded** (e.g. rate limits in `RateLimitFilter` — `repo/backend/src/main/java/com/eaglepoint/exam/security/filter/RateLimitFilter.java:31–33`), reducing configurability compared to `.env.example` labels (`repo/.env.example:26–28`).

**Evidence:** `RateLimitFilter.java:31–33`; `repo/.env.example:26–28`

---

### 4. Engineering Details and Professionalism

#### 4.1 Error handling, logging, validation, API shape

**Conclusion: Partial Pass**

**Rationale:** Central `GlobalExceptionHandler` maps common exceptions to HTTP statuses (`repo/backend/src/main/java/com/eaglepoint/exam/shared/exception/GlobalExceptionHandler.java:25–81`). Validation errors use `MethodArgumentNotValidException` → 400 (`GlobalExceptionHandler.java:25–31`). **Unhandled exceptions** return generic 500 message (`GlobalExceptionHandler.java:76–80`) — acceptable pattern; **detail is not exposed** to clients (good for safety). Logging uses SLF4J at least in shown components.

**Evidence:** `GlobalExceptionHandler.java:25–81`

#### 4.2 Product-like vs demo

**Conclusion: Partial Pass**

**Rationale:** Breadth (compliance, audit, jobs, encryption hooks) suggests product intent. **Documentation drift and env vars that are not wired** (rate limit vars) reduce “production-grade configurability” from a reviewer’s perspective.

**Evidence:** `repo/.env.example:26–28` vs `RateLimitFilter.java:31–33`; `docs/reviewer-notes.md:143–147` vs actual implementation (in-memory counters, not Bucket4j).

---

### 5. Prompt Understanding and Requirement Fit

#### 5.1 Business goal and implicit constraints

**Conclusion: Partial Pass**

**Rationale:** Intranet-first, compliance gating, scoped staff views, and fallback notification path are reflected in code snippets reviewed. **Leaderboard / ranking-view fraud** from the Prompt is **not evidenced** as a first-class feature (no code references); anti-cheat addresses anomalies with flags (`AntiCheatService.java:58–80`). **Manual Verification Required** to see if product intent is fully met in UI and APIs.

**Evidence:** `ExamSessionService.java` publish path (`grep` `isApproved` → lines 268–284 region); `AntiCheatService.java:58–80`; absence of leaderboard in static search.

---

### 6. Aesthetics (frontend)

#### 6.1 Visual and interaction design

**Conclusion: Cannot Confirm Statistically**

**Rationale:** Static review of one view shows structured layout (header, card, table, buttons) (`repo/frontend/src/views/notifications/NotificationList.vue:72–127`). **No browser verification** of spacing, hierarchy, or cross-page consistency.

**Evidence:** `NotificationList.vue:72–127`

---

## 5. Issues / Suggestions (Severity-Rated)

### High

1. **Reviewer guide references non-existent or inaccurate APIs**
   - **Conclusion:** `docs/reviewer-notes.md` cites `NotificationDeliveryService.deliverWithFallback()` — **no such method** exists (search returns no matches). It also describes rate limiting as “Bucket4j or in-memory” (`docs/reviewer-notes.md:143–147`) while code uses fixed in-memory counters (`RateLimitFilter.java:31–36`). It references `checkConcurrentSession()` naming that does not match a simple grep-based discovery in `AuthService` (concurrent logic is inline in `login` flow — `AuthService.java` around session loop at lines 123–138 in prior read).
   - **Evidence:** `docs/reviewer-notes.md:131–147`; `NotificationDeliveryService.java` (no `deliverWithFallback`); `RateLimitFilter.java:31–36`
   - **Impact:** Independent auditors following `docs/reviewer-notes.md` may conclude missing implementation or search the wrong symbols.
   - **Fix:** Regenerate reviewer notes from actual class/method names; remove incorrect technology names.

2. **Rate limit knobs in `.env.example` are not implemented in `RateLimitFilter`**
   - **Conclusion:** `.env.example` advertises `RATE_LIMIT_USER_PER_MINUTE` / `RATE_LIMIT_IP_PER_MINUTE` (`repo/.env.example:26–28`), but `RateLimitFilter` hardcodes `USER_LIMIT = 60` and `IP_LIMIT = 300` (`RateLimitFilter.java:31–32`) with no `@Value` binding.
   - **Evidence:** `repo/.env.example:26–28`; `RateLimitFilter.java:31–33`
   - **Impact:** Operators may believe limits are configurable via env; they are not (statically).
   - **Fix:** Wire properties or remove env keys from `.env.example`.

3. **`docs/test-coverage.md` overstates several tests vs actual assertions**
   - **Conclusion:** Example: row 11 claims “5 failed logins -> **6th** returns 423” (`docs/test-coverage.md:11`) while auth logic increments and locks when `attempts >= maxFailedAttempts` on the **same** failed attempt sequence (`AuthService.java:94–109` pattern). `NotificationServiceTest.testWeChatFallbackToInbox` only asserts **enum transition validity**, not inbox creation or delivery (`repo/unit_tests/backend/NotificationServiceTest.java:172–179`).
   - **Evidence:** `docs/test-coverage.md:11`; `AuthService.java:94–109`; `NotificationServiceTest.java:172–179`
   - **Impact:** Coverage planning document is unreliable for audit scoring.
   - **Fix:** Align narrative with real test bodies; split “enum sanity” vs “delivery integration”.

### Medium

4. **README security note vs signing filter constants for replay/nonce window**
   - **Conclusion:** README states “Nonce Replay Protection: … within a **5-minute** window” (`repo/README.md:171`). `RequestSigningFilter` uses `MAX_TIMESTAMP_DRIFT_SECONDS = 120` and `NONCE_EXPIRY_SECONDS = 120` (`RequestSigningFilter.java:48–49`). This is a **static inconsistency** between docs (unless another layer extends to 5 minutes — not shown in this filter).
   - **Evidence:** `repo/README.md:171`; `RequestSigningFilter.java:48–49`

5. **Docker Compose encryption default vs README’s Base64 story**
   - **Conclusion:** `docker-compose.yml` sets `APP_ENCRYPTION_KEY` default to a 32-character hex-like string (`repo/docker-compose.yml:34–35`). `EncryptedFieldConverter` **Base64-decodes** the configured key and validates byte length (`EncryptedFieldConverter.java:50–56`). README documents Base64 examples (`repo/README.md:72–73`). The compose value may still decode to valid AES key length, but it **looks like hex** and is easy to misconfigure — documentation risk.
   - **Evidence:** `repo/docker-compose.yml:34–35`; `EncryptedFieldConverter.java:50–56`; `repo/README.md:72–73`

6. **`.env.example` duplicates encryption keys with ambiguous format guidance**
   - **Conclusion:** Same hex-like value under `AES_ENCRYPTION_KEY` and `APP_ENCRYPTION_KEY` (`repo/.env.example:12–13`) while README emphasizes Base64 (`repo/README.md:72–73`).
   - **Evidence:** `repo/.env.example:12–13`; `repo/README.md:72–73`

### Low

7. **WeChat env naming: compose vs application.yml**
   - **Conclusion:** `docker-compose.yml` passes `WECHAT_ENABLED` (`docker-compose.yml:36`) while `application.yml` documents `app.wechat.mode` and `app.wechat.enabled` (`application.yml:81–85`). Spring may map legacy `WECHAT_ENABLED` only if bound — **Cannot Confirm Statistically** without full property binding scan; **static risk** of unused env in compose.

---

## 6. Security Review Summary

| Area | Conclusion | Evidence / reasoning |
|------|------------|----------------------|
| Authentication entry points | **Partial Pass** | Login path excluded from signing requirement via `RequestSigningFilter.shouldNotFilter` / `SecurityFilter.isAuthLoginPath` (`RequestSigningFilter.java:67–70`). Password complexity method exists (`AuthService.java:214` region per grep). |
| Route-level authorization | **Partial Pass** | `PermissionInterceptor` enforces `@RequirePermission` on method or class (`PermissionInterceptor.java:34–40`). Endpoints without annotation are not guarded by this interceptor alone — **full route inventory not done**. |
| Object-level authorization | **Partial Pass** | `ScopeService` enforces entity-type maps and assignments (`ScopeService.java:31–83`); `ExamSessionService` builds scoped specs for staff (`ExamSessionService.java:72–100`). **Residual IDOR risk** requires per-endpoint audit — **Cannot Confirm Statistically**. |
| Function-level authorization | **Partial Pass** | Permissions enum + `RolePermissions` (not fully read); job APIs use `JOB_MONITOR` / `JOB_RERUN` (`JobController.java:38–80`). |
| Tenant / user isolation | **Partial Pass** | Students constrained in scope service (`ScopeService.java:61–67`); integration tests exist (`CrossUserAccessTest` file path under integration). **Runtime isolation not proven.** |
| Admin / internal / debug protection | **Partial Pass** | Job monitoring behind permissions (`JobController.java:38–40`). No debug controllers reviewed; **Cannot Confirm Statistically** that none exist. |

---

## 7. Tests and Logging Review

| Dimension | Conclusion | Evidence |
|-----------|------------|----------|
| Unit tests | **Present** | `repo/unit_tests/backend/*.java` (13 files); wired via `pom.xml` `build-helper-maven-plugin` (`repo/backend/pom.xml:113–130`). Default Surefire **excludes** `com.eaglepoint.exam.integration` (`repo/backend/pom.xml:139–141`). |
| API / integration tests | **Present** | `repo/backend/src/test/java/com/eaglepoint/exam/integration/*.java` (7 test files + helper). |
| Logging / observability | **Partial Pass** | Application logging levels in `application.yml` (`application.yml:49–57`); filters use SLF4J (`RequestSigningFilter.java:43`). |
| Sensitive data in logs | **Suspected low risk in reviewed snippet** | `GlobalExceptionHandler` logs full stack only for generic `Exception` (`GlobalExceptionHandler.java:76–79`). **Cannot Confirm Statistically** for all `log.info` call sites. |

---

## 8. Test Coverage Assessment (Static Audit)

### 8.1 Test Overview

- **Unit tests:** Present under `repo/unit_tests/backend/`, packages `com.eaglepoint.exam.*` (e.g. `AuthServiceTest.java:1`).
- **Integration tests:** Present under `repo/backend/src/test/java/com/eaglepoint/exam/integration/`.
- **Frameworks:** JUnit 5 + Spring Boot Test (integration), Mockito (unit) — evidenced by imports in `ComplianceServiceTest.java:12–16` and integration test imports.
- **Entry points:** `repo/run_tests.sh` defines `backend`, `frontend`, `api`, `all` (`run_tests.sh:34–48`).
- **Documentation:** `repo/README.md:98–123` describes `./run_tests.sh` and profiles.

**Evidence:** `repo/run_tests.sh:1–54`; `repo/README.md:98–123`; `repo/backend/pom.xml:113–142`

### 8.2 Coverage Mapping Table (high-risk / core subset)

| Requirement / Risk Point | Mapped Test Case(s) | Key Assertion / Fixture | Coverage Assessment | Gap | Minimum Test Addition |
|----------------------------|---------------------|-------------------------|----------------------|-----|------------------------|
| Auth lockout after N fails | `SecurityIntegrationTest.testAccountLockoutAfterFailedAttempts` | Loop + status expectations (`SecurityIntegrationTest.java:187–228`) | **Basically covered** (integration exists) | **Cannot Confirm Statistically** without running tests | N/A for static audit |
| Request signing | `SigningFilterTest` (unit) | Referenced in `docs/test-coverage.md:13–16` | **Cannot Confirm** depth without reading full test file | — | — |
| ABAC cross-user | `CrossUserAccessTest.java` | File present in integration package | **Basically covered** (static) | Per-resource gaps unknown | Manual matrix |
| Roster import preview/commit | `RosterImportIntegrationTest.java` | Workflow test methods | **Basically covered** | — | — |
| Notification publish → delivery | `NotificationIntegrationTest.java` | Inbox / publish flow | **Basically covered** (static presence) | Timing/async behavior **Cannot Confirm Statistically** | Manual E2E |
| Compliance gating publish | `ExamSessionServiceTest` (unit) + integration | `docs/test-coverage.md:25–27` claims | **Partial** — verify tests assert `isApproved` path | Align docs with real assertions | Add negative publish integration if missing |
| WeChat fallback “real” | `NotificationServiceTest.testWeChatFallbackToInbox` | Only enum transitions (`NotificationServiceTest.java:172–179`) | **Insufficient** as *delivery* proof | No mock of `NotificationDeliveryService` delivery path in this test | Integration or service test calling `deliverNotification` |
| Rate limiting | `RateLimitTest.java` | Exists in unit_tests | **Cannot Confirm** effectiveness | In-memory only; cluster behavior not tested | Load test manual |
| Anti-cheat | `AntiCheatServiceTest.java` | Exists | **Basically covered** (unit presence) | Prompt “leaderboard” linkage **missing** | N/A unless feature added |
| Encryption at rest | `EncryptionTest.java` | Exists | **Cannot Confirm** DB bytes without runtime | — | DB inspection manual |

### 8.3 Security Coverage Audit

- **Authentication:** Integration `SecurityIntegrationTest` covers unauthenticated and lockout flows in file (`SecurityIntegrationTest.java:152–228`). **Residual:** token forgery, edge cases — **Cannot Confirm Statistically**.
- **Route authorization:** `PermissionInterceptor` + tests referencing 403 in docs (`docs/test-coverage.md:18–19`). **Residual:** unannotated routes — manual inventory.
- **Object-level authorization:** `CrossUserAccessTest` + service scope usage — **Partial** static confidence.
- **Tenant/data isolation:** Same; no separate “tenant” table evidenced in this audit — school scope modeled via scope assignments (`ScopeService.java`).
- **Admin/internal protection:** Job endpoints require permissions (`JobController.java:38–40`). **Cannot Confirm** all admin routes.

### 8.4 Final Coverage Judgment

**Partial Pass**

**Explanation:** Unit and integration tests exist with plausible alignment to core risks, but **`docs/test-coverage.md` and some unit tests overclaim behavior** (e.g., WeChat fallback test is enum-level only — `NotificationServiceTest.java:172–179`). **Severe defects could remain** in unreviewed endpoints, race conditions in async jobs, and unannotated controllers. **Cannot Confirm Statistically** that the suite would catch all authorization gaps.

---

## 9. Final Notes

- This audit **does not** validate that `mvn test` or `run_tests.sh` succeed; **Manual Verification Required** for any CI-style gate.
- Strongest static positives: structured monorepo, explicit security filters, compliance checks in `ExamSessionService.publishSession` region (`ExamSessionService.java:268–284`), job monitoring API with permissions (`JobController.java:38–40`), frontend request signing client (`client.js:19–44`).
- Strongest static negatives: **secondary documentation drift** (`docs/reviewer-notes.md`, `docs/test-coverage.md`, parts of README vs filters), **non-functional env keys** for rate limits in `.env.example`, and **missing evidenced “leaderboard”** feature from the Prompt narrative.

---

*End of report.*
