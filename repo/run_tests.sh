#!/bin/bash
# Exam Scheduling System - Docker-based Test Runner
# Usage: ./run_tests.sh [backend|frontend|api|e2e|all]
# Hard-fails on test failures.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$SCRIPT_DIR"
COVERAGE_TARGET="${COVERAGE_TARGET:-95}"

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
    echo "=== Running Backend Unit Tests (Docker) ==="
    docker run --rm \
        -v "$ROOT_DIR:/repo" \
        -w /repo/backend \
        maven:3.9.6-eclipse-temurin-17 \
        mvn clean test --no-transfer-progress 2>&1
    echo ""
}

run_frontend_tests() {
    echo "=== Running Frontend Unit Tests + Coverage (Docker, target ${COVERAGE_TARGET}%) ==="
    docker run --rm \
        -e VITEST_COVERAGE_THRESHOLD="$COVERAGE_TARGET" \
        -v "$ROOT_DIR:/repo" \
        -w /repo/frontend \
        node:20-alpine \
        sh -lc "npm ci && npx vitest run --coverage --reporter=verbose" 2>&1
    echo ""
}

run_api_tests() {
    echo "=== Running API Integration Tests (Docker, API_tests -> com.eaglepoint.exam.integration) ==="
    docker run --rm \
        -v "$ROOT_DIR:/repo" \
        -w /repo/backend \
        maven:3.9.6-eclipse-temurin-17 \
        mvn clean test -Dtest='com.eaglepoint.exam.integration.**' --no-transfer-progress 2>&1
    echo ""
}

run_e2e_tests() {
    echo "=== Running Browser E2E Tests (Docker Compose + Playwright) ==="
    trap 'docker compose down >/dev/null 2>&1 || true' RETURN
    docker compose up -d mysql backend frontend
    docker compose run --rm e2e
    docker compose down
    trap - RETURN
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
    e2e)
        run_e2e_tests
        ;;
    all)
        run_backend_tests
        run_frontend_tests
        run_api_tests
        run_e2e_tests
        echo "=== All Tests Complete ==="
        ;;
    *)
        echo "Usage: $0 [backend|frontend|api|e2e|all]"
        exit 1
        ;;
esac
