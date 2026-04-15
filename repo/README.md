# Exam Scheduling and Notification Management System

Project Type: `fullstack`

Secure intranet-first platform for K-12 exam scheduling, roster operations, compliance-gated publishing, notification delivery, and auditability.

## Docker-Only Startup

Prerequisites (host):
- Docker
- Docker Compose

From `repo/`:

```bash
cp .env.example .env
# set real secrets in .env for production use

docker-compose up
```

Optional detached mode:

```bash
docker-compose up -d
```

Service URLs after startup:
- Frontend: `http://localhost`
- Backend API: `http://localhost:8080`

## Test Execution

All test commands run inside Docker containers through `run_tests.sh`.

```bash
./run_tests.sh all
./run_tests.sh backend
./run_tests.sh frontend
./run_tests.sh api
./run_tests.sh e2e
```

## Credentials and Role Setup

Seeded demo accounts after `docker-compose up`:

| Username | Password | Role |
|---|---|---|
| `admin` | `Admin@12345678` | `ADMIN` |
| `coord.wang` | `Admin@12345678` | `ACADEMIC_COORDINATOR` |
| `teacher.li` | `Admin@12345678` | `HOMEROOM_TEACHER` |
| `teacher.zhang` | `Admin@12345678` | `SUBJECT_TEACHER` |
| `student.chen` | `Admin@12345678` | `STUDENT` |

## Deterministic Verification Workflow

1. **Auth/Session**
   - `POST /api/auth/login` with admin credentials -> expect `200` and `sessionToken`.
   - `GET /api/auth/session` with Bearer token -> expect current user payload.
2. **Roster import preview/commit**
   - In UI go to `Rosters -> Import`.
   - Upload invalid fixture and verify invalid-row preview + disabled commit path.
   - Upload valid file and commit; verify roster list grows.
3. **Compliance-gated publish**
   - Create notification in UI (`Notifications -> Create`).
   - Submit for review, approve in `Compliance Review Queue`, publish.
   - Confirm job appears in `Job Monitor`.
4. **Inbox/subscription**
   - Login as `student.chen`.
   - `GET /api/notifications/subscriptions` should succeed.
   - Verify inbox item appears after publication; mark as read.
5. **Versioning**
   - Open an exam session version page and run restore.
   - Verify a new version row appears after restore.
6. **Audit**
   - Open `Audit Log`.
   - Filter by `ENTITY_TYPE=CAMPUS` and confirm create/update events exist.

## Architecture Summary

`Vue SPA` -> `Spring Boot REST` -> `MySQL`

Core modules: `auth`, `security`, `scheduling`, `roster`, `imports`, `compliance`, `notifications`, `versioning`, `audit`, `anticheat`, `rooms`, `users`, `jobs`.

## Security and Compliance Controls

- Local username/password authentication with lockout policy.
- RBAC + ABAC scope enforcement.
- Request signing + nonce/timestamp replay protection.
- Rate limiting (user + IP).
- AES encryption at rest for sensitive fields.
- Compliance review required before publish.
- Immutable audit log for state changes.

## API Spec

Detailed endpoint and payload reference: `docs/api-spec.md`
