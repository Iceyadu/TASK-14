# Delivery Acceptance & Project Architecture Audit (Static-Only)

## 1. Verdict
- **Overall conclusion: Partial Pass**
- **Why:** The repository is a coherent full-stack deliverable with broad module coverage and security foundations. Major earlier blockers in this report are no longer the dominant risk profile; the remaining material risks are mainly **contract mismatches and documentation/test-evidence drift** rather than end-to-end absence of core capabilities.

## 2. Scope and Static Verification Boundary
- **Reviewed:**
  - Documentation/config: `repo/README.md`, `docs/*.md`, backend `application*.yml`, `pom.xml`, `run_tests.sh`
  - Backend architecture/security/business modules under `repo/backend/src/main/java/com/eaglepoint/exam/**`
  - DB schema/migrations: `repo/backend/src/main/resources/db/migration/*.sql`
  - Frontend role/navigation/notification/import/version views under `repo/frontend/src/**`
  - Backend/frontend tests under `repo/backend/src/test/java/**`, `repo/unit_tests/backend/**`, `repo/frontend/tests/**`
- **Not reviewed/executed:**
  - No runtime startup, no API execution, no browser interaction, no Docker, no test execution.
- **Intentionally not executed:** project run, Docker compose, all tests.
- **Claims requiring manual verification:**
  - End-to-end runtime behavior, performance, cluster behavior under multi-node contention, real WeChat connectivity, and actual UX rendering fidelity.

## 3. Repository / Requirement Mapping Summary
- **Prompt core goal:** Offline-capable secure exam scheduling + roster import + compliance-gated publishing + notifications (WeChat when available, otherwise inbox), with strict RBAC/ABAC, audit/versioning, and job processing.
- **Mapped implementation areas:**
  - Auth/session/signing/rate-limit: `security/filter/*`, `auth/*`, `security/interceptor/*`
  - RBAC/ABAC and data scope: `shared/enums/RolePermissions.java`, `security/service/ScopeService.java`, domain services
  - Core workflows: scheduling, roster/import, notifications, compliance, versioning, jobs, anti-cheat
  - Frontend role-based navigation + workflow screens: router, sidebar, roster import, subscription settings, version history
  - Static test suites + test configs.

## 4. Section-by-section Review

### 4.1 Hard Gates

#### 4.1.1 Documentation and static verifiability
- **Conclusion: Partial Pass**
- **Rationale:** README/docs are substantial, but reproducibility has contradictions and inaccurate reviewer notes.
- **Evidence:**
  - Startup/testing instructions present: `repo/README.md:49-123`
  - README instructs `./mvnw spring-boot:run` in backend: `repo/README.md:75`
  - Test runner also uses `./mvnw`: `repo/run_tests.sh:12`
  - Backend directory does not include `mvnw`: `repo/backend` listing
  - Reviewer notes contain non-existent method references and inaccurate assertions: `docs/reviewer-notes.md:17,60,69,76-83,102-105`
- **Manual verification note:** Runtime reproducibility cannot be confirmed statically.

#### 4.1.2 Material deviation from Prompt
- **Conclusion: Partial Pass**
- **Rationale:** Most core prompt constraints are represented in code, but some requirement semantics remain under-evidenced (notably prompt-adjacent leaderboard/ranking surfaces) and some integration behaviors are not fully provable statically.
- **Evidence:**
  - Exam publish includes compliance approval check: `repo/backend/src/main/java/com/eaglepoint/exam/scheduling/service/ExamSessionService.java` (`publishSession`, `isApproved` usage).
  - Notification publish also enforces approval: `repo/backend/src/main/java/com/eaglepoint/exam/notifications/service/NotificationService.java:262-268`
  - Prompt-adjacent leaderboard/ranking view remains not explicitly evidenced in reviewed scope.

### 4.2 Delivery Completeness

#### 4.2.1 Core explicit requirements coverage
- **Conclusion: Partial Pass**
- **Rationale:** Many required modules exist and core flows are implemented; remaining gaps are concentrated in consistency and contract-level issues.
- **Evidence:**
  - Role-based routing/navigation implemented: `repo/frontend/src/router/index.js:22-128`, `repo/frontend/src/components/shared/NavigationSidebar.vue:9-63`
  - Roster import preview/commit with duplicate/invalid handling exists: frontend `repo/frontend/src/views/roster/RosterImport.vue:46-71,173-210`, `repo/frontend/src/components/import/ImportPreview.vue:27-45`; backend `repo/backend/src/main/java/com/eaglepoint/exam/imports/service/ImportService.java:145-231,467-525`
  - Version APIs and restore endpoint exist: `repo/backend/src/main/java/com/eaglepoint/exam/versioning/controller/VersionController.java:35-90`
  - Student subscriptions + DND UI exists: `repo/frontend/src/views/notifications/SubscriptionSettings.vue:11-118`
  - **Gap:** delivery-status channel contract mismatch (`SKIPPED` vs schema enum): `repo/backend/src/main/java/com/eaglepoint/exam/notifications/service/NotificationDeliveryService.java:104-105`, `repo/backend/src/main/resources/db/migration/V1__schema.sql:243`
  - **Gap:** documentation/test evidence drift reduces static verifiability: `docs/reviewer-notes.md`, `docs/test-coverage.md`

#### 4.2.2 End-to-end 0→1 deliverable (vs demo/fragment)
- **Conclusion: Partial Pass**
- **Rationale:** Full-stack structure is present and broadly product-like. Confidence is reduced by unresolved static inconsistencies (especially schema/value contract mismatches and documentation drift), not by missing core project structure.
- **Evidence:**
  - Complete repo structure and modules: `repo/README.md:132-159`
  - Remaining contract risk: `delivery_status.channel` enum vs `SKIPPED` write path (see Issues #1).

### 4.3 Engineering and Architecture Quality

#### 4.3.1 Structure and module decomposition
- **Conclusion: Pass**
- **Rationale:** Modules are separated by domain/security concerns with clear package boundaries.
- **Evidence:**
  - Module breakdown documented and reflected in tree: `repo/README.md:144-159`
  - Controllers/services/repositories separated across domains (e.g., scheduling, notifications, imports, versioning, jobs).

#### 4.3.2 Maintainability/extensibility
- **Conclusion: Partial Pass**
- **Rationale:** Overall structure is maintainable, but key authorization and documentation drift issues reduce long-term safety and operability.
- **Evidence:**
  - Central permission interceptor + annotation model: `repo/backend/src/main/java/com/eaglepoint/exam/security/interceptor/PermissionInterceptor.java:34-53`
  - Scope checks centralized: `repo/backend/src/main/java/com/eaglepoint/exam/security/service/ScopeService.java:49-97`
  - **Maintainability risk:** docs claim behavior not present in code: `docs/reviewer-notes.md:17,60,69,76-83`
  - **Maintainability/security risk:** version service lacks per-entity scope enforcement: `repo/backend/src/main/java/com/eaglepoint/exam/versioning/service/VersionService.java:113-199`

### 4.4 Engineering Details and Professionalism

#### 4.4.1 Error handling/logging/validation/API quality
- **Conclusion: Partial Pass**
- **Rationale:** Good baseline patterns exist, but critical defects remain.
- **Evidence:**
  - Centralized exception handling with HTTP mapping: `repo/backend/src/main/java/com/eaglepoint/exam/shared/exception/GlobalExceptionHandler.java:24-74`
  - Audit logging with redaction logic: `repo/backend/src/main/java/com/eaglepoint/exam/audit/service/AuditService.java:29-43,70-83,133-157`
  - Request signing + replay windows: `repo/backend/src/main/java/com/eaglepoint/exam/security/filter/RequestSigningFilter.java:83-111,166-177`
  - **Critical quality gap:** schema/entity mismatch for jobs: see 4.2.2 evidence.

#### 4.4.2 Product-grade vs demo-grade
- **Conclusion: Partial Pass**
- **Rationale:** Product-like breadth exists, but a required integration is still a stub and major security/business controls are inconsistent.
- **Evidence:**
  - WeChat path explicitly simulated success: `repo/backend/src/main/java/com/eaglepoint/exam/notifications/service/NotificationDeliveryService.java:199-207`
  - Compliance control inconsistency between notifications and exam sessions: `.../NotificationService.java:262-268` vs `.../ExamSessionService.java:224-260`

### 4.5 Prompt Understanding and Requirement Fit

#### 4.5.1 Business/constraint fit
- **Conclusion: Partial Pass**
- **Rationale:** Core workflows align with prompt intent, but some requirement semantics are only partially evidenced in static review and a few material mismatches remain.
- **Evidence:**
  - Compliance checks are present in publish paths (exam + notification services).
  - WeChat has explicit modes and fallback behavior in delivery service; real intranet adapter behavior still requires manual verification.
  - Leaderboard/ranking-view-specific implementation is not explicitly evidenced in reviewed files.

### 4.6 Aesthetics (frontend)

#### 4.6.1 Visual/interaction quality
- **Conclusion: Partial Pass**
- **Rationale:** UI has coherent structure, state feedback, and interaction styling, but static review cannot prove full rendering/accessibility quality across devices.
- **Evidence:**
  - Role-adaptive navigation and route guards: `repo/frontend/src/components/shared/NavigationSidebar.vue:9-63`, `repo/frontend/src/router/index.js:136-169`
  - Import/preview states and feedback: `repo/frontend/src/views/roster/RosterImport.vue:124-210`, `repo/frontend/src/components/import/ImportPreview.vue:21-71`
  - DND/subscription interaction controls: `repo/frontend/src/views/notifications/SubscriptionSettings.vue:83-118`
- **Manual verification note:** responsive behavior and final UX polish are **Manual Verification Required**.

## 5. Issues / Suggestions (Severity-Rated)

### High

1. **Severity:** High  
   **Title:** `delivery_status.channel` schema enum conflicts with code path writing `SKIPPED`  
   **Conclusion:** Fail  
   **Evidence:** `repo/backend/src/main/java/com/eaglepoint/exam/notifications/service/NotificationDeliveryService.java:104-105`; `repo/backend/src/main/resources/db/migration/V1__schema.sql:243`  
   **Impact:** Opt-out status writes can fail at DB layer despite otherwise valid notification flow.  
   **Minimum actionable fix:** Extend enum to include `SKIPPED` or remap skip semantics to an allowed channel + status.

2. **Severity:** High  
   **Title:** Reviewer/coverage documentation materially drifts from implementation  
   **Conclusion:** Partial fail  
   **Evidence:** `docs/reviewer-notes.md`, `docs/test-coverage.md` contain method/behavior claims not aligned with current code assertions/paths.  
   **Impact:** Acceptance reviewers may draw incorrect conclusions about implemented safeguards and test guarantees.  
   **Minimum actionable fix:** Regenerate docs from current code paths and exact test assertions.

### Medium

3. **Severity:** Medium  
   **Title:** Rate-limit env knobs are documented but filter uses hardcoded limits  
   **Conclusion:** Partial fail  
   **Evidence:** `repo/.env.example`; `repo/backend/src/main/java/com/eaglepoint/exam/security/filter/RateLimitFilter.java:31-33`  
   **Impact:** Operational tuning expectations do not match actual runtime behavior.  
   **Minimum actionable fix:** Bind values from config or remove unsupported env keys from docs/examples.

4. **Severity:** Medium  
   **Title:** Encryption key format guidance is inconsistent across README/compose/env examples  
   **Conclusion:** Partial fail  
   **Evidence:** `repo/README.md`; `repo/docker-compose.yml`; `repo/.env.example`; `repo/backend/src/main/java/com/eaglepoint/exam/security/crypto/EncryptedFieldConverter.java`  
   **Impact:** Deployment misconfiguration risk and weaker static verifiability.  
   **Minimum actionable fix:** Standardize one canonical key format and examples in all docs/config templates.

### Low

5. **Severity:** Low  
   **Title:** WeChat env naming can cause operator confusion (`mode` vs legacy `enabled`)  
   **Conclusion:** Cannot confirm statistically  
   **Evidence:** `repo/backend/src/main/resources/application.yml` (wechat mode/enabled), compose env usage.  
   **Impact:** Lower direct risk, but increases configuration ambiguity.  
   **Minimum actionable fix:** Document canonical mode and deprecate legacy path in docs.

## 6. Security Review Summary

- **Authentication entry points: Partial Pass**  
  Evidence: login endpoint and filter bypass limited to `/api/auth/login`: `repo/backend/src/main/java/com/eaglepoint/exam/auth/controller/AuthController.java:53-57`, `repo/backend/src/main/java/com/eaglepoint/exam/security/filter/SecurityFilter.java:58-67`; lockout/concurrent session logic: `repo/backend/src/main/java/com/eaglepoint/exam/auth/service/AuthService.java:88-133`.

- **Route-level authorization: Partial Pass**  
  Evidence: permission interceptor + annotations on major controllers: `repo/backend/src/main/java/com/eaglepoint/exam/security/interceptor/WebMvcConfig.java:20-23`, `repo/backend/src/main/java/com/eaglepoint/exam/security/interceptor/PermissionInterceptor.java:34-53`, `repo/backend/src/main/java/com/eaglepoint/exam/scheduling/controller/ExamSessionController.java:47-147`.  
  Gap: unannotated reference endpoints: `repo/backend/src/main/java/com/eaglepoint/exam/rooms/controller/ReferenceDataController.java:52,61,70,94`.

- **Object-level authorization: Partial Pass**  
  Evidence: scoped checks are implemented in services; full endpoint-by-endpoint closure and edge-case proof remain **Cannot Confirm Statistically**.

- **Function-level authorization: Partial Pass**  
  Evidence: permission granularity exists (`SESSION_PUBLISH`, `NOTIFICATION_PUBLISH`, `JOB_RERUN`, etc.) in controllers and role mapping: `repo/backend/src/main/java/com/eaglepoint/exam/shared/enums/RolePermissions.java:23-67`; example controller gates: `repo/backend/src/main/java/com/eaglepoint/exam/jobs/controller/JobController.java:39,67,80`.  
  Gap: complete route/service inventory not fully re-audited in this pass.

- **Tenant / user data isolation: Partial Pass**  
  Evidence: roster student self-isolation and class-scope filtering exist: `repo/backend/src/main/java/com/eaglepoint/exam/roster/service/RosterService.java:75-90,133-139`; notification inbox uses current student user ID: `repo/backend/src/main/java/com/eaglepoint/exam/notifications/controller/NotificationController.java:118-126`.  
  Gap: full multi-attribute isolation proof across all modules is **Cannot Confirm Statistically** in this static-only pass.

- **Admin / internal / debug protection: Partial Pass**  
  Evidence: admin-like operations (session terminate/unlock, job rerun, audit view) are permission-gated: `repo/backend/src/main/java/com/eaglepoint/exam/auth/controller/AuthController.java:125-137`, `repo/backend/src/main/java/com/eaglepoint/exam/jobs/controller/JobController.java:67-83`, `repo/backend/src/main/java/com/eaglepoint/exam/scheduling/controller/ExamSessionController.java:134-136`.  
  Note: no explicit `/internal` or `/debug` controllers found in reviewed scope. Runtime exposure of framework-managed endpoints is **Cannot Confirm Statistically**.

## 7. Tests and Logging Review

- **Unit tests: Partial Pass**  
  - Existence is strong (backend and frontend unit suites present).  
  - Remaining concern is not broad absence, but evidence drift between docs and precise assertions in some tests.  
  - Evidence: `repo/unit_tests/backend/**`; `repo/frontend/tests/unit/**`; `docs/test-coverage.md`.

- **API / integration tests: Partial Pass**  
  - Integration tests exist under `repo/backend/src/test/java/com/eaglepoint/exam/integration/*`.  
  - End-to-end runtime pass/fail is still **Cannot Confirm Statistically** by rule.

- **Logging categories / observability: Pass (static)**  
  - Structured audit logging and exception logging exist; retry/job logging present.  
  - Evidence: `repo/backend/src/main/java/com/eaglepoint/exam/audit/service/AuditService.java:72-87`; `repo/backend/src/main/java/com/eaglepoint/exam/shared/exception/GlobalExceptionHandler.java:69-74`; `repo/backend/src/main/java/com/eaglepoint/exam/jobs/service/JobService.java:140-144,176-182`.

- **Sensitive-data leakage risk in logs/responses: Partial Pass**  
  - Positive: audit redaction + masking serializer + encrypted fields at rest.  
  - Evidence: `repo/backend/src/main/java/com/eaglepoint/exam/audit/service/AuditService.java:29-43,133-157`; `repo/backend/src/main/java/com/eaglepoint/exam/security/masking/MaskedFieldSerializer.java:61-75`; `repo/backend/src/main/java/com/eaglepoint/exam/roster/model/RosterEntry.java:38-48`.  
  - Risk: encryption converter has default fallback key path, which can be unsafe if env config is mishandled: `repo/backend/src/main/java/com/eaglepoint/exam/security/crypto/EncryptedFieldConverter.java:35,67-73`.

## 8. Test Coverage Assessment (Static Audit)

### 8.1 Test Overview
- **Unit/API tests exist:** Yes.
- **Frameworks:** JUnit 5 + Spring Boot Test + MockMvc (backend), Vitest + Vue Test Utils (frontend).
- **Test entry points:**
  - Backend unit: `repo/unit_tests/backend/**` (added via build-helper)
  - Backend integration: `repo/backend/src/test/java/com/eaglepoint/exam/integration/**`
  - Frontend unit: `repo/frontend/tests/unit/**`
- **Documentation for test commands:** Present (`repo/README.md:97-116`, `repo/run_tests.sh:9-48`).
- **Config nuance:** surefire excludes integration package by default (`repo/backend/pom.xml:139-141`), requiring explicit invocation.

### 8.2 Coverage Mapping Table

| Requirement / Risk Point | Mapped Test Case(s) | Key Assertion / Fixture / Mock | Coverage Assessment | Gap | Minimum Test Addition |
|---|---|---|---|---|---|
| Auth lockout after failed attempts | `repo/backend/src/test/java/com/eaglepoint/exam/integration/SecurityIntegrationTest.java:182-219`; `repo/unit_tests/backend/AuthServiceTest.java` | Expects 423 after repeated bad logins (`SecurityIntegrationTest.java:197-205`) | Basically covered | Signing consistency may affect integration run confidence | Ensure all authenticated requests in integration tests are signed or explicitly disable signing in profile |
| Session timeout/inactivity | `repo/backend/src/test/java/com/eaglepoint/exam/integration/SecurityIntegrationTest.java:155-180` | Forces expired session and expects 401 with signed request (`:175-179`) | Basically covered | Single-path only | Add coverage for remember-device 7-day timeout path |
| Request signing/replay | `repo/unit_tests/backend/SigningFilterTest.java`; helper in `repo/backend/src/test/java/com/eaglepoint/exam/integration/SigningTestHelper.java:45-47` | Header/signature/nonces are asserted in unit tests | Basically covered | Integration coverage sparse vs profile requirements | Add integration tests for missing/invalid timestamp/nonce/signature on key endpoints |
| Route authorization (401/403) | `repo/backend/src/test/java/com/eaglepoint/exam/integration/CrossUserAccessTest.java:203-267`; frontend router guard tests | Forbidden cases on cross-user endpoints, role-route guard behavior | Basically covered | Unannotated reference endpoints not explicitly tested | Add negative tests for `/api/terms,/grades,/classes,/courses` per role policy |
| Object-level scope enforcement on updates | No direct test found for changing `campusId`/`classId` to out-of-scope in update calls | Existing unit tests only verify scope check invocation on current entity (`repo/unit_tests/backend/ExamSessionServiceTest.java:248-256`) | Missing | Current tests would not catch reassignment bypass | Add update tests that attempt in-scope object -> out-of-scope target and expect 403 |
| Compliance gate before publish (exam session) | `repo/unit_tests/backend/ExamSessionServiceTest.java` + integration suite | Approval-gated publish path exists in service; test-assertion completeness needs spot-checking | Partial | doc-to-test narrative drift | Add explicit negative publish assertion in integration case |
| Notification publish flow UI availability | Frontend unit `PublishBlock.spec.js` exists; list view condition mismatch not covered | No test tied to `NotificationList.vue` action visibility by actual status enum | Insufficient | Unreachable publish button escaped tests | Add component test on `NotificationList.vue` validating publish visibility for real lifecycle status |
| Import preview validation (duplicates/invalid rows) | `repo/unit_tests/backend/ImportServiceTest.java`; `repo/frontend/tests/unit/ImportPreview.spec.js` | Invalid rows, error rendering, commit-disabled behavior asserted | Sufficient | Runtime parsing edge cases unproven | Add malformed XLSX + extreme row count tests |
| Version access isolation | No clear test found asserting scope checks on version endpoints | Version tests exist (`VersionServiceTest`, `VersionRestoreIntegrationTest`) but do not prove scope isolation | Insufficient | Cross-scope version access risk may remain undetected | Add integration tests where scoped user requests other-scope entity versions and expects 403 |
| Job sharding/retry/idempotency | `repo/unit_tests/backend/JobServiceTest.java` exists | Unit coverage exists for retry/idempotency behaviors | Basically covered | runtime contention unproven | Add multi-node/manual verification scenario |

### 8.3 Security Coverage Audit
- **Authentication:** **Basically covered** by unit/integration tests (lockout/session/concurrent login).
- **Route authorization:** **Basically covered**, but gaps remain for unannotated reference endpoints.
- **Object-level authorization:** **Partial** (core scope mechanisms present; exhaustive edge-case coverage not proven statically).
- **Tenant/data isolation:** **Partial** (service-level filters present; full matrix requires manual/expanded static verification).
- **Admin/internal protection:** **Basically covered** for explicit admin functions; framework endpoint exposure remains **Cannot Confirm Statistically**.

### 8.4 Final Coverage Judgment
- **Partial Pass**
- **Boundary explanation:**
  - Covered: baseline auth, some authorization, import/notification/version/job behaviors.
  - Not sufficiently covered: critical object-level authorization update paths, exam compliance gate enforcement, version-scope isolation, and signing-consistency in integration requests. Because of these gaps, tests could still pass while severe security/business defects remain.

## 9. Final Notes
- This assessment is static-only and evidence-based; no runtime success is claimed.
- Strongest remaining risks are schema/value contract mismatch in notification delivery status and documentation/evidence drift.
- Manual verification remains required for runtime deployment behavior, real WeChat integration, and UI rendering fidelity.
