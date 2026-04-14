#!/bin/bash
# Exam Scheduling System - Docker-based Test Runner
# Usage: ./run_tests.sh [backend|frontend|api|all]
# Mounts the full repo so unit_tests/, API_tests/, and backend/ share one tree.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$SCRIPT_DIR"
BACKEND_DIR="$ROOT_DIR/backend"
FRONTEND_DIR="$ROOT_DIR/frontend"

require_docker() {
    if ! command -v docker >/dev/null 2>&1; then
        echo "docker not found; install Docker Desktop / Engine first"
        exit 127
    fi
    if ! docker compose version >/dev/null 2>&1; then
        echo "docker compose not available; install Docker Compose v2 plugin"
        exit 127
    fi
}

run_backend_tests() {
    echo "=== Running Backend Unit Tests (Docker, unit_tests/backend + default excludes) ==="
    docker run --rm \
        -v "$ROOT_DIR:/repo" \
        -w /repo/backend \
        maven:3.9.6-eclipse-temurin-17 \
        mvn test --no-transfer-progress 2>&1 \
        || echo "Backend unit tests completed with failures"
    echo ""
}

run_frontend_tests() {
    echo "=== Running Frontend Unit Tests (Docker, unit_tests/frontend) ==="
    docker run --rm \
        -v "$ROOT_DIR:/repo" \
        -w /repo/frontend \
        node:20-alpine \
        sh -lc "npm ci && npx vitest run --reporter=verbose" 2>&1 \
        || echo "Frontend tests completed with failures"
    echo ""
}

run_api_tests() {
    echo "=== Running API Integration Tests (Docker, API_tests -> com.eaglepoint.exam.integration) ==="
    docker run --rm \
        -v "$ROOT_DIR:/repo" \
        -w /repo/backend \
        maven:3.9.6-eclipse-temurin-17 \
        mvn test -Dtest='com.eaglepoint.exam.integration.**' --no-transfer-progress 2>&1 \
        || echo "API tests completed with failures"
    echo ""
}

require_docker

case "${1:-all}" in
    backend)
        run_backend_tests
        ;;
    frontend)
        run_frontend_tests
        ;;
    api)
        run_api_tests
        ;;
    all)
        run_backend_tests
        run_frontend_tests
        run_api_tests
        echo "=== All Tests Complete ==="
        ;;
    *)
        echo "Usage: $0 [backend|frontend|api|all]"
        exit 1
        ;;
esac
