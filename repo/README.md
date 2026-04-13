# Exam Scheduling and Notification Management System

A secure, full-stack exam scheduling and notification platform designed for K-12 international schools operating on an intranet-first deployment model. The system manages exam session lifecycles, roster imports, compliance-gated publishing, multi-channel notifications, and comprehensive audit trails.

## Implementation Summary

- **Exam Session Management**: Full lifecycle from DRAFT through compliance review to PUBLISHED, with state machine enforcement
- **Roster Import**: CSV/XLSX upload with preview-then-commit workflow, validation, and rollback support
- **Notification Engine**: Multi-channel delivery (WeChat intranet, in-app inbox) with fallback, DND settings, and subscription controls
- **Compliance Review Queue**: Mandatory approval gating before any content reaches students
- **Version History**: Full entity versioning with diff comparison and restore-to-previous capability
- **Security Layer**: Request signing, AES encryption at rest, BCrypt password hashing, session management, account lockout, rate limiting, RBAC with ABAC scope filtering
- **Audit Trail**: Immutable audit log for all state-changing operations
- **Anti-Cheat Flagging**: Proctor-driven flag and review system for exam integrity

## Prerequisites

- **Docker** >= 20.10 and **Docker Compose** >= 2.0 (for containerized deployment)
- **Java** 17+ (for local backend development)
- **Maven** 3.8+ (or use the included `mvnw` wrapper)
- **Node.js** 18+ and **npm** (for local frontend development)
- **MySQL** 8.0 (if running without Docker)

## Quick Start with Docker

```bash
# Clone the repository
cd repo

# Copy and configure environment variables
cp .env.example .env
# Edit .env to set production secrets (APP_SIGNING_SECRET, APP_ENCRYPTION_KEY, DB_PASSWORD)

# Start all services
docker compose up -d

# Verify services are running
docker compose ps

# View logs
docker compose logs -f backend
```

The application will be available at:
- **Frontend**: http://localhost
- **Backend API**: http://localhost:8080
- **MySQL**: localhost:3306

## Manual Startup Instructions

### 1. MySQL Database

```bash
# Start MySQL (ensure it is running on port 3306)
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS exam_scheduling;"
mysql -u root -p -e "CREATE USER IF NOT EXISTS 'examuser'@'%' IDENTIFIED BY 'exampass';"
mysql -u root -p -e "GRANT ALL PRIVILEGES ON exam_scheduling.* TO 'examuser'@'%';"
```

### 2. Backend (Spring Boot)

```bash
cd backend

# Set environment variables
export DB_HOST=localhost
export DB_PORT=3306
export DB_NAME=exam_scheduling
export DB_USER=examuser
export DB_PASSWORD=exampass
export APP_SIGNING_SECRET=change-this-signing-secret-in-production
# AES key: Base64 encoding of 16, 24, or 32 raw bytes (example below is 32 bytes)
export APP_ENCRYPTION_KEY=Q2hhbmdlVGhpc1RvQVJlYWxQcm9kdWN0aW9uS2V5ISE=

# Build and run
./mvnw spring-boot:run
```

The backend starts on port 8080.

### 3. Frontend (Vue.js)

```bash
cd frontend

# Install dependencies
npm install

# Development mode
npm run dev

# Production build
npm run build
```

The frontend dev server starts on port 5173. The production build outputs to `dist/` and is served by Nginx in Docker.

## Running Tests

Use the included test runner script:

```bash
# Make executable (first time only)
chmod +x run_tests.sh

# Run all tests (backend unit + frontend unit + API integration)
./run_tests.sh all

# Run only backend unit tests
./run_tests.sh backend

# Run only frontend unit tests
./run_tests.sh frontend

# Run only API integration tests
./run_tests.sh api
```

### Test Profiles

- **Backend unit tests**: Run against an in-memory H2 database using the `test` profile
- **Frontend unit tests**: Run with Vitest
- **API integration tests**: Spring Boot tests with `test` + `integration` profiles (signing relaxed for MockMvc); `SecurityIntegrationTest` uses `test` only and exercises request signing. Integration tests set a valid Base64 AES key so local `AES_ENCRYPTION_KEY` env values cannot break the context.

## Default Credentials

| Username | Password         | Role                  |
|----------|------------------|-----------------------|
| admin    | Admin@12345678   | ADMIN                 |

Additional users can be created through the admin API after initial login.

## Architecture

The system follows a three-tier architecture:

```
Vue.js (SPA)  -->  Spring Boot (REST API)  -->  MySQL 8.0
  Frontend            Backend                    Database
  Port 80             Port 8080                  Port 3306
```

### Module Breakdown

| Module            | Description                                                |
|-------------------|------------------------------------------------------------|
| `auth`            | Authentication, session management, device registration    |
| `security`        | Filters (signing, rate limit, session), encryption, RBAC   |
| `scheduling`      | Exam session CRUD, lifecycle state machine, student views  |
| `roster`          | Roster entry management and querying                       |
| `imports`         | CSV/XLSX upload, preview, commit, rollback                 |
| `compliance`      | Compliance review queue, approve/reject workflow           |
| `notifications`   | Multi-channel delivery, inbox, subscriptions, DND          |
| `versioning`      | Entity version history, diff comparison, restore           |
| `audit`           | Immutable audit log for all state changes                  |
| `anticheat`       | Exam integrity flagging and review                         |
| `rooms`           | Room, campus, course, grade, class reference data          |
| `users`           | User management and scope assignments                      |
| `jobs`            | Background job scheduling and execution tracking           |
| `shared`          | DTOs, enums, exceptions, request context                   |

## Security Notes

- **Request Signing**: All authenticated API requests are verified via HMAC-SHA256 signature using a per-session signing key
- **Encryption at Rest**: Sensitive fields (PII) are encrypted using AES-256 via a JPA attribute converter
- **Password Storage**: BCrypt with configurable strength (default 12 rounds in production, 4 in tests)
- **Rate Limiting**: Per-user (60/min) and per-IP (300/min) rate limits enforced at the filter level
- **Session Management**: Server-side sessions with configurable timeout (30 min default), single-device enforcement, and remember-device support
- **Account Lockout**: Accounts lock after 5 failed login attempts for 15 minutes
- **RBAC + ABAC**: Role-based permissions with attribute-based scope filtering (campus, class, grade)
- **Nonce Replay Protection**: Request nonces prevent replay attacks within a 2-minute window (120 seconds)
- **Idempotency Keys**: Supported on all mutating operations to prevent duplicate processing

## Compliance and Governance

- **Mandatory Review Queue**: All exam sessions and notifications must pass compliance review before publication
- **Approval Gating**: Publishing endpoints enforce that the entity has an approved compliance review
- **Audit Trail**: Every state change is recorded with actor, timestamp, action, and entity snapshot
- **Version History**: Full versioning with the ability to view, compare, and restore previous versions

## API Documentation

For detailed endpoint specifications, request/response schemas, and error codes, see [docs/api-spec.md](docs/api-spec.md).

## Known Limitations

- **WeChat Integration**: The WeChat intranet push channel is stubbed out; delivery falls back to in-app inbox when `WECHAT_ENABLED=false`
- **Horizontal Scaling**: The current session store is database-backed; for multi-instance deployments, consider Redis-backed sessions
- **File Storage**: Imported files are processed in memory; very large files (>10MB) may require streaming or chunked upload
- **Timezone Handling**: All timestamps are stored and processed in the server's default timezone; multi-timezone support is not implemented
- **Search**: Roster and session search uses simple LIKE queries; full-text search is not yet implemented
- **Email Channel**: Email notifications are not implemented; only WeChat and in-app inbox are supported
- **Internationalization**: The API returns English error messages only; i18n is not yet implemented

## Manual Verification Required

- [ ] Verify login flow with default admin credentials after first deployment
- [ ] Confirm Flyway migrations apply cleanly on a fresh MySQL 8.0 instance
- [ ] Test CSV import with production-representative data volumes
- [ ] Validate compliance review workflow end-to-end with multiple roles
- [ ] Confirm rate limiting behaves correctly under concurrent load
- [ ] Verify session expiration and lockout timing in production configuration
- [ ] Test Nginx reverse proxy routing in Docker deployment
- [ ] Confirm encrypted fields are stored as ciphertext in the database
