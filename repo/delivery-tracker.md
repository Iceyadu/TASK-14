# Delivery Tracker

## Current Stage
Phase 4 - Final Hardening, Tests, Docker, and Package Cleanup

## Current Focus
Complete system implementation with all security boundaries, workflow state machines, and test coverage.

## Hard-Fail Risks
1. Request signing without HMAC secret distribution model for browser clients
2. Encryption at rest requires AES key management in offline environment
3. WeChat integration depends on intranet deployment - fallback is primary path
4. XLSX parsing requires Apache POI dependency bundled in Docker image
5. Multi-node job scheduling degrades to single-node via database-backed queues

## Required Deliverables
- [x] Package structure (TASK-14/ with docs/, repo/, sessions/)
- [x] metadata.json
- [x] prompt.md
- [x] docs/design.md
- [x] docs/api-spec.md
- [x] docs/questions.md
- [x] docs/test-coverage.md
- [x] docs/reviewer-notes.md
- [x] repo/backend/ (Spring Boot)
- [x] repo/frontend/ (Vue.js)
- [x] repo/docker-compose.yml
- [x] repo/run_tests.sh
- [x] repo/README.md
- [x] repo/.env.example
- [x] repo/unit_tests/
- [x] repo/API_tests/
- [x] sessions/ placeholder files
- [x] Sample CSV/XLSX import files

## Open Business Ambiguities
See docs/questions.md for full list with resolutions.

## Packaging Exclusions
- node_modules/
- .venv/
- .pytest_cache/
- __pycache__/
- target/ (Maven build output)
- .idea/ .vscode/
- *.class files
- build/ dist/ (generated artifacts)
- .env (only .env.example included)
- Runtime cache folders
- Upload artifacts
