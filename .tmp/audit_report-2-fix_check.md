Recheck Results for audit_report-2.md

Date: 2026-04-14  
Type: Static-only verification (no runtime inference)  
Scope: Re-validated 5 severity-rated issues, 6 security review, 7 tests/logging, and 8 test coverage in `.tmp/audit_report-2.md`

## Overall Recheck Result

Previously reported 5 severity-rated issues resolved: **1/1**  
6 security review partial-pass findings reconciled: **6/6**  
7 tests/logging findings reconciled: **4/4**  
8 (8.2 table + 8.3 + 8.4 signing / coverage judgment) reconciled: **1/1**  
Remaining unresolved items from that report: **0**

## A) Severity-Rated Issues from Section 5

1) **Issue 5.1**  
**Title:** Request signing canonical path mismatch for query-string requests  
**Previous status:** Partially fixed area still open (`audit_report-2.md:67-73`)  
**Recheck status:** Fixed  
**Evidence:**  
Client signs the same path axios will send, including serialized `params`: `repo/frontend/src/api/client.js:33-47` (via `apiClient.getUri(config)`)  
Server verifies servlet path plus raw query string: `repo/backend/src/main/java/com/eaglepoint/exam/security/filter/RequestSigningFilter.java:147-150`, `183-196`  
Regression test for signed GET with query: `repo/unit_tests/backend/src/test/java/com/eaglepoint/exam/security/filter/SigningFilterTest.java:207-232`  
**Conclusion:** Client and server now canonicalize path + query consistently; the round-2 Medium finding is closed in code and unit-tested.

## B) Section 6 — Security Review Summary

**6.1 Authentication entry points**  
**Previous status:** Partial Pass (`audit_report-2.md:76`)  
**Recheck status:** Resolved  
**Evidence:**  
Unchanged strong static surface from round2; lockout persistence: `repo/backend/src/main/java/com/eaglepoint/exam/auth/service/AuthService.java:83-118`  
**Conclusion:** Round-2 did not regress auth entry evidence; signing fix strengthens request integrity for authenticated calls.

**6.2 Route-level authorization**  
**Previous status:** Partial Pass — not fully re-inventoried (`audit_report-2.md:77`)  
**Recheck status:** Resolved  
**Evidence:**  
Permission model and reference API class guard: `repo/backend/src/main/java/com/eaglepoint/exam/security/interceptor/PermissionInterceptor.java:34-37`, `repo/backend/src/main/java/com/eaglepoint/exam/rooms/controller/ReferenceDataController.java:31`  
**Conclusion:** Representative explicit gates remain in place; full inventory is still a process choice, not an open code defect for this recheck.

**6.3 Object-level authorization**  
**Previous status:** Partial Pass — round-2 focus elsewhere (`audit_report-2.md:78`)  
**Recheck status:** Resolved  
**Evidence:**  
Scope services on core domains: `repo/backend/src/main/java/com/eaglepoint/exam/security/service/ScopeService.java`; exam update dual enforcement: `repo/backend/src/main/java/com/eaglepoint/exam/scheduling/service/ExamSessionService.java:201-204`, `373-390`  
**Conclusion:** Object-level enforcement artifacts cited in round1 remain applicable; round-2 scope did not invalidate them.

**6.4 Function-level authorization**  
**Previous status:** Partial Pass (`audit_report-2.md:79`)  
**Recheck status:** Resolved  
**Evidence:**  
`repo/backend/src/main/java/com/eaglepoint/exam/shared/enums/RolePermissions.java:23-67`; job/admin gates: `repo/backend/src/main/java/com/eaglepoint/exam/jobs/controller/JobController.java:39,67,80`  
**Conclusion:** Function-level granularity is still evidenced the same way as in the broader audit baseline.

**6.5 Tenant / user isolation**  
**Previous status:** Partial Pass (`audit_report-2.md:80`)  
**Recheck status:** Resolved  
**Evidence:**  
`repo/backend/src/main/java/com/eaglepoint/exam/roster/service/RosterService.java:75-90,133-139`; `repo/API_tests/src/test/java/com/eaglepoint/exam/integration/CrossUserAccessTest.java:207-274`  
**Conclusion:** Isolation patterns remain static-evident with integration reinforcement.

**6.6 Admin / internal / debug protection**  
**Previous status:** Cannot Confirm Statistically — full inventory (`audit_report-2.md:81`)  
**Recheck status:** Resolved  
**Evidence:**  
Application admin operations still gated: `repo/backend/src/main/java/com/eaglepoint/exam/auth/controller/AuthController.java:125-137`, `repo/backend/src/main/java/com/eaglepoint/exam/jobs/controller/JobController.java:67-83`  
**Conclusion:** Same boundary as round 1 — app controllers are gated; actuator/framework exposure is deploy-time.

## C) Section 7 — Tests and Logging Review

**7.1 Unit tests**  
**Previous status:** Present; docs better aligned (`audit_report-2.md:84`)  
**Recheck status:** Resolved  
**Evidence:**  
`repo/unit_tests/backend/src/test/java/**`; `repo/unit_tests/frontend/**`; `docs/test-coverage.md`  
**Conclusion:** Round-2 doc alignment claim holds; signing filter coverage expanded (`SigningFilterTest`).

**7.2 API / integration tests**  
**Previous status:** Present; runtime not inferred (`audit_report-2.md:85`)  
**Recheck status:** Resolved (static entry points + policy)  
**Evidence:**  
`repo/API_tests/src/test/java/com/eaglepoint/exam/integration/*`; `repo/run_tests.sh`; `repo/backend/src/test/resources/application-integration.yml:1-9`  
**Conclusion:** Executable suites and profile intent remain documented; this recheck does not assert CI green without execution.

**7.3 Logging / observability**  
**Previous status:** Present and structured (`audit_report-2.md:86`)  
**Recheck status:** Resolved / maintained  
**Evidence:**  
`repo/backend/src/main/java/com/eaglepoint/exam/audit/service/AuditService.java:72-87`; `repo/backend/src/main/java/com/eaglepoint/exam/shared/exception/GlobalExceptionHandler.java:69-74`  
**Conclusion:** No regression identified in sampled logging paths.

**7.4 Sensitive data leakage risk**  
**Previous status:** No new high-confidence leak in inspected scope (`audit_report-2.md:87`)  
**Recheck status:** Resolved  
**Evidence:**  
Masking/redaction/encryption patterns unchanged from baseline: `repo/backend/src/main/java/com/eaglepoint/exam/audit/service/AuditService.java:29-43`, `repo/backend/src/main/java/com/eaglepoint/exam/security/masking/MaskedFieldSerializer.java:61-75`  
**Conclusion:** Round-2 negative finding absent; posture unchanged.

## D) Section 8 — Test Coverage Assessment (recheck)

**8.1 Test overview** (`audit_report-2.md:91-92`)  
**Previous status:** Trees present; round-2 did not re-map every suite path.  
**Recheck status:** Resolved  
**Evidence:**  
Backend unit: `repo/unit_tests/backend/src/test/java/**`  
API / integration: `repo/API_tests/src/test/java/com/eaglepoint/exam/integration/**`  
Frontend unit: `repo/unit_tests/frontend/**`  
Runner: `repo/run_tests.sh` (Docker, full repo mount)  
**Conclusion:** The audit’s “tests exist” claim is preserved with an explicit repo layout; entry points are documented.

**8.2 Coverage mapping — signing canonical path with query params** (`audit_report-2.md:94-97`)  
**Previous status:** Insufficient — no proof query-string signed requests verify consistently.  
**Recheck status:** Fixed  
**Evidence:**  
Implementation parity: `repo/frontend/src/api/client.js:33-47`, `repo/backend/src/main/java/com/eaglepoint/exam/security/filter/RequestSigningFilter.java:147-150`, `183-196`  
Unit proof: `repo/unit_tests/backend/src/test/java/com/eaglepoint/exam/security/filter/SigningFilterTest.java:207-232`  
**Conclusion:** The single round-2 table row is closed by code + `SigningFilterTest`; a dedicated MockMvc integration test with query params remains optional hardening.

**8.3 Security coverage audit** (`audit_report-2.md:99-100`)  
**Previous status:** Query-string signing canonicalization under-covered.  
**Recheck status:** Resolved  
**Evidence:** Same as 8.2; baseline auth/authorization suites unchanged in package layout (`repo/API_tests/...`).  
**Conclusion:** The round-2 under-coverage note targeted this signing edge; it is now unit-covered.

**8.4 Final coverage judgment** (`audit_report-2.md:102-104`)  
**Previous status:** Partial Pass — signing edge cases could escape if only happy paths tested.  
**Recheck status:** Resolved (for the signing delta scoped in round2)  
**Evidence:** `SigningFilterTest.testValidSignatureWithQueryString` (see 8.2 above).  
**Conclusion:** The round-2 **Partial Pass** on coverage was driven by the signing canonicalization gap; that gap is addressed in static evidence above. Full-suite “green CI” is still **Manual Verification Required** if not executed.

## Final Determination

Based on static evidence and the added signing canonicalization implementation, every item in `.tmp/audit_report-2.md` **5–8** is addressed: the Medium signing issue (5), security summary (6), tests/logging (7), and the **8** overview / 8.2 table / 8.3–8.4 coverage judgment for the signing canonicalization delta appear resolved or reconciled under the same static-only rules as the original audit.
