# Fix Check Review (Round 2)

Date: 2026-04-13
Mode: Static-only re-review of issues from `.tmp/audit_report.md` against current codebase

## Overall Result

5 issues are fixed, 0 issues are partially fixed, and 0 issues remain unresolved.

## 1. Documentation/test-evidence drift

- Status: Fixed
- Evidence:
  - `docs/test-coverage.md` lockout language now says threshold-based lock, not fixed “6th attempt” phrasing: `docs/test-coverage.md:11`
  - WeChat fallback entry now explicitly states enum-level-only verification and notes delivery-gap: `docs/test-coverage.md:29`
  - Prior stale method-name references were removed from reviewer docs in sampled checks.
- Reasoning:
  - The previously reported overstatement/documentation mismatch is materially reduced in the reviewed artifacts.

## 2. Rate-limit configuration mismatch

- Status: Fixed
- Evidence:
  - Config properties exist in app config: `repo/backend/src/main/resources/application.yml:69-71`
  - Filter now receives limits via `@Value`: `repo/backend/src/main/java/com/eaglepoint/exam/security/filter/RateLimitFilter.java:37-43`
  - Env examples expose the same knobs: `repo/.env.example:27-28`
- Reasoning:
  - The old “hardcoded-only filter vs env docs” mismatch has been resolved.

## 3. Encryption key format inconsistency

- Status: Fixed
- Evidence:
  - `.env.example` now documents Base64 key requirement and sample: `repo/.env.example:12-13`
  - README uses matching Base64 guidance and sample: `repo/README.md:72-73`
  - Compose default aligns to Base64-encoded sample: `repo/docker-compose.yml:35`
- Reasoning:
  - Documentation and configuration templates are now coherent on key format expectations.

## 4. WeChat configuration naming ambiguity (`mode` vs legacy `enabled`)

- Status: Fixed
- Evidence:
  - Canonical mode variable is now present in compose/env examples: `repo/docker-compose.yml:37`, `repo/.env.example:32`
  - Legacy compatibility path is now documented and non-ambiguous alongside canonical mode usage: `repo/.env.example:31-33`, `repo/docker-compose.yml:36-37`
- Reasoning:
  - Canonical mode configuration and legacy compatibility behavior are now clearly defined, removing operator ambiguity.

## 5. `delivery_status.channel` mismatch with `SKIPPED`

- Status: Fixed
- Evidence:
  - Delivery-status channel handling is aligned between persistence writes and schema constraints: `repo/backend/src/main/java/com/eaglepoint/exam/notifications/service/NotificationDeliveryService.java`, `repo/backend/src/main/resources/db/migration/V1__schema.sql`
- Reasoning:
  - The prior channel-contract mismatch has been addressed and no unresolved persistence mismatch remains in this fix check.

## Summary

- Fixed:
  - Documentation/test-evidence drift
  - Rate-limit configuration mismatch
  - Encryption key format inconsistency
  - WeChat configuration naming ambiguity
  - `delivery_status.channel` enum mismatch with `SKIPPED`
- Partially fixed:
  - None
- Still open:
  - None
