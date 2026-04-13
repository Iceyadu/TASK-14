# Business Ambiguities and Resolutions

## Q1: Managed Machine Definition for Remember-Device

**Question:** What constitutes a "managed machine" for the 7-day remember-device feature? The prompt says "managed machines" but does not define enrollment or verification.

**My Understanding:** In a K-12 intranet environment, managed machines are school-owned devices registered by IT. There is no MDM integration specified.

**Solution:** Implement a `managed_devices` table where Administrators register device identifiers (a stable browser fingerprint hash combining User-Agent + screen resolution + timezone, stored as a device token). Only devices explicitly registered by an Admin can use the 7-day remember-device feature. The device registration is auditable. This is the narrowest safe implementation without requiring external MDM.

---

## Q2: Concurrent Session Block vs Terminate Policy

**Question:** When a user attempts login from a second device while an active session exists, should the system block the new login or terminate the existing session?

**My Understanding:** The prompt says "blocks concurrent sessions from different devices unless explicitly allowed by Admin" which implies the new login is blocked.

**Solution:** Block the new login attempt and return a clear error message indicating an active session exists on another device. The user must either wait for the existing session to expire or ask an Admin to terminate the existing session. Admin can also grant a per-user concurrent session allowance flag, which is audited.

---

## Q3: Atomic vs Partial Import Commit

**Question:** Should bulk import commit be fully atomic (all-or-nothing) or allow partial commits of valid rows?

**My Understanding:** The prompt mentions "preview step that highlights duplicates and invalid formats before committing changes" suggesting a clean commit after preview validation.

**Solution:** Import commit is fully atomic. All rows in the commit batch must pass validation. The preview step identifies all errors; only after the user resolves errors (or explicitly excludes invalid rows from the batch) can they commit. This prevents partial data states that are hard to audit or roll back.

---

## Q4: Duplicate Detection Case Sensitivity

**Question:** Should duplicate detection in bulk imports be case-sensitive or case-insensitive?

**My Understanding:** Student IDs and structured identifiers should be case-insensitive to prevent near-duplicates.

**Solution:** Duplicate detection is case-insensitive for all string-based key fields (student ID, name fields used in composite keys). Numeric and date fields use exact matching. This is documented in the import preview error messages.

---

## Q5: Scope of Academic Affairs Coordinator

**Question:** The prompt specifies campus-wide, grade-wide, or term-wide scope for Academic Affairs Coordinator but does not define whether a coordinator can have multiple scope assignments.

**My Understanding:** A coordinator may be assigned to one or more campuses/grades/terms.

**Solution:** Implement a `user_scope_assignments` table that supports multiple scope entries per user. Each entry defines a scope type (campus, grade, term) and scope value. All queries for the coordinator role are filtered by the union of their assigned scopes.

---

## Q6: Ranking-Style Views Scope

**Question:** The prompt requires anti-cheat and leaderboard-fraud detection for "ranking-style views" but does not specify what rankings exist in an exam scheduling system.

**My Understanding:** An exam scheduling system does not inherently have competitive leaderboards. However, exam results or scores could be displayed in ranked order.

**Solution:** Implement a single ranking view: "Exam Results Summary" which can display student scores per session in ranked order (visible to staff only, not students, to avoid competitive pressure in K-12). Anti-cheat detection monitors for: (1) impossible score submission timing, (2) identical answer patterns across students, (3) abnormal score jumps between exam sessions. All flags go to a human review queue. Students never see rankings directly; this is a staff-only analytical view.

---

## Q7: DND Suppressed Notification Behavior

**Question:** When a notification is suppressed by a student's Do Not Disturb window, should it appear later, expire, or remain inbox-only?

**My Understanding:** The prompt says students should receive "clear on-screen reminders" and have DND settings.

**Solution:** DND-suppressed notifications are held and delivered to the in-app inbox when the DND window ends. They do not expire during DND. If the notification has a natural expiry (e.g., a reminder for an exam that has already passed), it is marked as expired in the inbox but still visible for record-keeping. WeChat delivery is skipped entirely during DND and not retried after DND ends (the inbox delivery covers it).

---

## Q8: Version Restore and Compliance Re-review

**Question:** If a staff member restores an earlier version of student-visible content (e.g., an exam schedule), should this trigger a new compliance review?

**My Understanding:** Restoring content that has student-facing impact is semantically equivalent to editing it.

**Solution:** Restoring any student-visible content (exam sessions, published schedules, notifications) that is currently in "published" state triggers automatic transition to "submitted_for_compliance_review" state. The restored content must be re-approved and re-published before becoming student-visible again. This is enforced in the service layer.

---

## Q9: Request Signing in Browser Context

**Question:** HMAC request signing requires a shared secret. In a browser-based SPA, the secret cannot be safely embedded in JavaScript.

**My Understanding:** The prompt requires request signing for replay protection. A pure browser-side HMAC with a static secret would be insecure.

**Solution:** Implement a session-bound signing approach: after successful authentication, the server issues a short-lived signing key (derived from the session token using HMAC-SHA256 with server secret). The browser uses this session signing key to sign requests. The server can recompute the expected signing key from the session. The signing key rotates with each session. Nonce replay protection and 120-second timestamp validation remain fully server-side. This provides real replay protection without embedding a static secret in client code.

---

## Q10: Idempotency Key Expiration Window

**Question:** How long should idempotency keys be retained before expiration?

**My Understanding:** Keys must last long enough to cover retry windows but not indefinitely.

**Solution:** Idempotency keys expire after 24 hours. This covers: (1) user retry scenarios within a work session, (2) job retry with exponential backoff (max ~3 retries over minutes), (3) import commit retries. Expired keys are cleaned up by a scheduled job. The 24-hour window is configurable via application properties.

---

## Q11: Export Authorization Scope

**Question:** Should export endpoints apply the same row-level scope as list endpoints?

**My Understanding:** Yes, exports must not bypass authorization.

**Solution:** All export endpoints apply identical RBAC + row-level scope filtering as their corresponding list endpoints. A Homeroom Teacher exporting roster data receives only students in their assigned class/term. Exports are audited with the same detail as list queries.

---

## Q12: Health-Related Data and Minor Protection

**Question:** The prompt mentions "health-related disclaimers" and "minor-protection safeguards" but does not specify what health data exists in an exam scheduling system.

**My Understanding:** K-12 students are minors. Health data might include exam accommodations (extra time for medical conditions) or health flags that affect scheduling.

**Solution:** Implement an optional `accommodation_notes` field on roster entries that is: (1) encrypted at rest, (2) masked in all API responses by default, (3) visible only to users with explicit `VIEW_HEALTH_DATA` permission (Admin and designated Academic Affairs Coordinator), (4) never included in bulk exports unless the exporter has `VIEW_HEALTH_DATA` permission, (5) excluded from audit log detail (only "accommodation_notes was modified" is logged, not the value). A disclaimer banner is shown on any screen displaying health-related data.
