# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

bk-repo (BlueKing Artifact Repository) is a microservice-based artifact management platform built by Tencent BlueKing. It provides storage and management for various artifact types (Docker, Maven, npm, Helm, PyPI, RPM, NuGet, Generic, OCI, Git, etc.) with features including artifact proxying, distribution, scanning, and package management.

## Repository Structure

```
src/
├── backend/       # Kotlin/Spring Boot microservices (Gradle build)
├── frontend/      # Vue.js web UI (Yarn/Lerna monorepo)
├── gateway/       # OpenResty/Nginx gateway with Lua scripts
└── proxy/         # Separate Gradle project for proxy service
```

## Build Commands

### Backend (from `src/backend/`)

```bash
# Build without tests (fastest for development)
./gradlew clean build -x test

# Build a specific service module
./gradlew :repository:boot-repository:build -x test
./gradlew :core:generic:boot-generic:build -x test

# Run tests for a specific module
./gradlew :repository:biz-repository:test

# Run a single test class
./gradlew :repository:biz-repository:test --tests "com.tencent.bkrepo.repository.SomeTest"

# Static analysis
./gradlew detekt
```

Build output JARs go to `src/backend/release/`.

### Frontend (from `src/frontend/`)

```bash
yarn install
yarn public           # Production build (via gulp)
yarn build:dev        # Dev build (via lerna)
```

Frontend is a Yarn/Lerna monorepo with workspaces: `core/devops-repository` and `core/devops-op`.

## Backend Architecture

The backend is a Kotlin Gradle project using Spring Boot + Spring Cloud. All source lives under `src/backend/`.

**Technical stack:** Kotlin on JDK 17, Gradle with `com.tencent.devops.boot` plugin, Spring Boot + Spring Cloud, MongoDB, Redis, Spring Cloud Stream (file/memory/Pulsar binders), OpenResty gateway.

**Testing:** JUnit with Mockito-Kotlin and MockK; embedded MongoDB (`de.flapdoodle.embed.mongo`) for integration tests.

### Three-Layer Module Pattern

Every service follows this structure — understanding this is critical for navigating the codebase:

- `api-{name}/` — API interfaces, DTOs, Feign clients. Package: `com.tencent.bkrepo.{name}`. Can only depend on other api modules and common-api. Uses `api` dependency scope (transitive).
- `biz-{name}/` — Business logic, service implementations, controllers. Depends on its api module and `common-service`. Uses `implementation` scope (non-transitive).
- `boot-{name}/` — Spring Boot application entry point. Depends only on its biz module. Produces the runnable JAR.

### Key Modules

- `repository` — Core domain: projects, repositories, nodes, metadata, packages
- `auth` — Authentication/authorization (integrates with bk-user, bk-iam)
- `core/{protocol}` — Protocol-specific services (generic, helm, oci, maven, npm, pypi, rpm, nuget, composer, cargo, conan, s3, huggingface, lfs, git, svn, ddc)
- `common/` — ~27 shared libraries (common-api, common-mongo, common-storage, common-artifact, common-service, common-security, common-redis, common-stream, etc.)
- `job` — Scheduled tasks (boot-job-schedule, boot-job-worker)
- `media` — Media processing (boot-media, boot-media-job, boot-media-live)
- `boot-assembly` — All-in-one monolith assembly (bundles all services into one JAR)

**Storage:** Multi-tier strategy — object storage for artifact files, MongoDB for node info/metadata. Configured via `storage.type` property.

**Version constants** are centralized in `src/backend/buildSrc/src/main/kotlin/Versions.kt`.

## Naming Conventions

These naming patterns are enforced across the codebase:

### Classes
- **Controllers:** `User{Resource}Controller` (user-facing) or `{Resource}Controller` (service-to-service)
- **Services:** `{Resource}Service` (interface) / `{Resource}ServiceImpl` (implementation)
- **MongoDB entities:** `T{Entity}` (e.g., `TProject`, `TNode`) — always `data class` with `@Document`
- **Request DTOs:** `{Resource}{Action}Request` (e.g., `ProjectCreateRequest`)
- **Response DTOs:** `{Resource}Info` or just `{Resource}` — immutable `data class` with `val` fields

### Packages
- Base: `com.tencent.bkrepo.{module}.{layer}.{subpackage}`
- Controllers in `controller/`, services in `service/` with impls in `service/impl/`

### Conversions
- Entity → DTO: extension function `fun TProject.toInfo(): ProjectInfo`
- Request → Entity: extension function `fun ProjectCreateRequest.toEntity(): TProject`

## Code Conventions

### Detekt Rules (enforced, zero tolerance)
- Max line length: 120 characters
- Max method length: 120 lines, max class size: 2400 lines
- Max function parameters: 6 (constructors: 15), max return count: 3, max nesting depth: 4
- No wildcard imports, files must end with newline
- No `println`/`printStackTrace` in production code

### Kotlin Idioms (required)
- Prefer `val` over `var`; use immutable collections
- Use safe calls `?.` and Elvis `?:` — avoid `!!`
- Use `when` expressions instead of if-else chains
- Use string interpolation, extension functions, collection operations
- No `Thread.sleep`, no hardcoded values/magic numbers

### Controller Patterns
- All endpoints return `Response<T>` via `ResponseBuilder`
- User-facing endpoints require `@Principal` and `@Permission` annotations
- Get userId via `@RequestAttribute` — never from request body
- Annotate with `@Tag`, `@Operation` for Swagger docs, `@AuditEntry` for audit logging
- No business logic in controllers — delegate to services

### Service Patterns
- Method flow: parameter validation → business rule checks → DAO operations → related data creation → logging
- Throw `ErrorCodeException` for business errors (never generic exceptions)
- Use `@Transactional(rollbackFor = [Exception::class])` when needed
- Log all important operations (create/update/delete)
- Max 100 lines per method; no circular dependencies

### MongoDB/Repository Patterns
- Entities require: `id`, `createdBy`, `createdDate`, `lastModifiedBy`, `lastModifiedDate`
- Use `@CompoundIndex` for common queries; unique indexes for business keys
- Type-safe queries with `where()` — no string-based queries
- Use `BulkOps` for batch operations
- Soft delete: filter with `where(TProject::deleted).isEqualTo(null)`
- Check empty lists before `in` queries to avoid full scans

### Test Patterns
- Class naming: `{Class}Test`; method naming: backtick descriptive style `` `should create project successfully`() ``
- Use `@DisplayName` annotations
- AAA pattern: Arrange → Act → Assert
- MockK for mocking (`every`/`returns` for setup, `verify` for assertions)
- Integration tests: `@SpringBootTest` with `@TestPropertySource`
- Controller tests: `@AutoConfigureMockMvc` with `MockMvc`

## Git Commit Convention

Format: `tag: description #issue`

Tags: `feat`/`feature` (new feature), `fix`/`bug`/`bugfix` (bug fix), `refactor`/`perf` (refactoring/optimization), `test`, `docs`, `format`, `chore`, `del` (breaking removal), `depend` (dependency changes)
