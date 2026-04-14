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
  - Audit notes were consolidated and stale reviewer-note references removed from active report set.
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
