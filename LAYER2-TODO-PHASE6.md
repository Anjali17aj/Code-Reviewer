# Layer 2 TODO: Phase 6 — Docker + CI/CD + Final Integration

**Received from:** @cto
**Risk Level:** Low
**Security Required:** None (infrastructure/deployment configuration only)

## Tasks

- [ ] **Task 1: Update docker-compose.yml — Production Ready**
  - Update the existing docker-compose.yml with production-ready configuration
  - Add Redis service with healthcheck
  - Add environment variable placeholders using ${VAR:-default} syntax
  - Update MySQL credentials to use environment variables
  - Add Redis volume for persistence
  - Update backend environment with all required variables (DATABASE_URL, REDIS_HOST, etc.)
  - Add depends_on conditions for MySQL and Redis healthchecks
  - Acceptance: docker-compose.yml parses correctly, all services defined
  - Security: No hardcoded secrets in the file
  - Dependencies: None

- [ ] **Task 2: Update backend/Dockerfile — Multi-stage Build**
  - Update the existing backend/Dockerfile with improved multi-stage build
  - Change runtime base from eclipse-temurin:17-jre-alpine to eclipse-temurin:17-jre-jammy
  - Keep the build stage identical (maven:3.9-eclipse-temurin-17)
  - Acceptance: Dockerfile syntax is valid
  - Security: Minimal attack surface with JRE-only runtime
  - Dependencies: None

- [ ] **Task 3: Update frontend/Dockerfile — Multi-stage Build**
  - Update the existing frontend/Dockerfile with improved multi-stage build
  - Change node base from node:18-alpine to node:20-alpine
  - Add --configuration=production to npm run build
  - Acceptance: Dockerfile syntax is valid, builds production assets
  - Security: Build-time secrets not baked into image
  - Dependencies: None

- [ ] **Task 4: Update frontend/nginx.conf**
  - Update the existing nginx.conf with production optimizations
  - Add proxy_connect_timeout and proxy_read_timeout directives
  - Ensure Angular SPA routing, API proxy, and static asset caching are correct
  - Acceptance: nginx.conf syntax is valid
  - Security: Proxy headers properly set
  - Dependencies: None

- [ ] **Task 5: Create .gitlab-ci.yml — CI/CD Pipeline**
  - Create a new .gitlab-ci.yml file at project root
  - Define stages: test, build, deploy
  - Add backend-test job (Maven test with JUnit artifacts)
  - Add frontend-test job (npm test with ChromeHeadless)
  - Add backend-build job (Maven package, only main/develop branches)
  - Add frontend-build job (npm build production, only main/develop branches)
  - Add docker-build job (build and push Docker images, only main)
  - Add deploy-production job (SSH deploy, manual trigger, only main)
  - Acceptance: .gitlab-ci.yml syntax is valid
  - Security: Secrets via CI/CD variables, not in file
  - Dependencies: None

- [ ] **Task 6: Update .env with all required variables**
  - Update the existing .env file with all required variables
  - Add MYSQL_ROOT_PASSWORD, MYSQL_USER, MYSQL_PASSWORD
  - Update JWT_SECRET placeholder
  - Keep OPENAI_API_KEY, GITHUB_CLIENT_ID, GITHUB_CLIENT_SECRET, GITHUB_REDIRECT_URI
  - Add REDIS_HOST, REDIS_PORT
  - Add API_URL for frontend
  - Acceptance: .env contains all variables referenced in docker-compose.yml
  - Security: Placeholder values only, no real secrets
  - Dependencies: None

- [ ] **Task 7: Create README.md**
  - Create a new README.md at project root
  - Include project title, features, tech stack table
  - Include Getting Started (Prerequisites, Quick Start, Development Setup)
  - Include API Endpoints table
  - Include Environment Variables table
  - Include License
  - Acceptance: README.md is comprehensive and accurate
  - Security: No secrets or credentials in README
  - Dependencies: None

- [ ] **Task 8: Final Verification**
  - Run backend compilation: cd backend && mvn compile
  - Run frontend build: cd frontend && npx ng build --configuration=production
  - Validate docker-compose config: docker compose config
  - Count Java and TypeScript files
  - Acceptance: All commands succeed, file count matches expectations
  - Security: No verification failures that could hide issues
  - Dependencies: All previous tasks

## Notes
- All tasks are Low risk — no security review required
- Tasks 1-6 are file creation/updates (independent, can be parallelized)
- Task 7 depends on understanding the full project (after Tasks 1-6)
- Task 8 depends on all previous tasks
- The existing files already have good structure; these are refinements for production
