#!/bin/bash
# Exam Scheduling System - Test Runner
# Usage: ./run_tests.sh [backend|frontend|api|all]

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

run_maven_tests() {
    local args="$1"
    if [ -x "./mvnw" ]; then
        ./mvnw test ${args} --no-transfer-progress 2>&1
    elif command -v mvn >/dev/null 2>&1; then
        mvn test ${args} --no-transfer-progress 2>&1
    else
        echo "Neither ./mvnw nor mvn is available in $(pwd)"
        return 127
    fi
}

run_backend_tests() {
    echo "=== Running Backend Unit Tests ==="
    cd "$SCRIPT_DIR/backend"
    run_maven_tests "-Dtest=com.eaglepoint.exam.** -pl ." || echo "Backend tests completed with failures"
    echo ""
}

run_frontend_tests() {
    echo "=== Running Frontend Unit Tests ==="
    cd "$SCRIPT_DIR/frontend"
    if ! command -v npm >/dev/null 2>&1; then
        echo "npm not found; install Node.js/npm to run frontend tests"
        echo ""
        return 127
    fi

    if [ ! -d "node_modules" ] || [ ! -x "node_modules/.bin/vitest" ]; then
        echo "Installing frontend dependencies (npm ci)..."
        npm ci 2>&1 || echo "npm ci completed with failures"
    fi

    npx vitest run --reporter=verbose 2>&1 || echo "Frontend tests completed with failures"
    echo ""
}

run_api_tests() {
    echo "=== Running API Integration Tests (package com.eaglepoint.exam.integration) ==="
    cd "$SCRIPT_DIR/backend"
    run_maven_tests "-Dtest=com.eaglepoint.exam.integration.** -pl ." || echo "API tests completed with failures"
    echo ""
}

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
