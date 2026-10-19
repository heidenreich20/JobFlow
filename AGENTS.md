# AGENTS.md — JobFlow

Instructions for AI coding agents working in this repository. Read this fully before changing code.

## What this project is

JobFlow is a REST API for project and task management, built as a portfolio project. It is a backend only: Java 21, Spring Boot 4.1.1, Spring Data JPA (Hibernate), Spring Security with JWT (jjwt 0.12.6), PostgreSQL 17, Maven (wrapper included). Base package: `com.pablo.jobflow`.

Quality matters more than speed here. The goal is clean architecture, a consistent permission model, and thorough tests. Do not add features, dependencies, or abstractions that were not asked for.

## Commands

The developer is on Windows. Use the wrapper, never a global Maven.

**Running the app requires `JOBFLOW_JWT_SECRET`** (Base64, at least 32 bytes), because `application.properties` reads `jobflow.jwt.secret=${JOBFLOW_JWT_SECRET}` with no default. If the app fails at startup with an unresolved-placeholder error and the variable is missing, that is expected: stop and tell the developer. Do **not** invent a secret, add a default to the main properties file, or commit a real one anywhere. **Tests do not need the variable**, because `src/test/resources/config/application.properties` supplies a test-only secret (see Testing conventions). `JOBFLOW_DB_PASSWORD` is optional and defaults to the local dev password.

```powershell
docker compose up -d            # PostgreSQL must be running, including for tests
.\mvnw.cmd clean test           # full suite
.\mvnw.cmd test -Dtest=TaskServiceTest            # one class
.\mvnw.cmd test -Dtest=TaskServiceTest#methodName # one method
.\mvnw.cmd spring-boot:run      # run the API on :8080
```

- The project targets **Java 21** (`java.version` in `pom.xml`). The suite also passes on newer JDKs (it has run on 25), but do not use language features newer than 21.
- `@SpringBootTest` tests connect to the **real local PostgreSQL** configured in `src/main/resources/application.properties`, so they fail if the container is down. Check that first before debugging a failure.
- **Never create `src/test/resources/application.properties`.** A same-named file on the test classpath *replaces* the main one instead of adding to it, so the datasource, `jobflow.jwt.expiration-ms` and every other setting vanish and all `@SpringBootTest` classes fail to start (this already happened once). Test-only overrides go in `src/test/resources/config/application.properties`, which Spring Boot loads in addition to the main file and which wins on conflicts. It currently holds only the test-only JWT secret. After moving or deleting test resources, run `clean`, since stale copies in `target/test-classes` keep shadowing.
- `pom.xml` sets `-Duser.timezone=UTC` and wires the Mockito agent through surefire. Do not remove either.
- Run the full suite before declaring any task done. The last known count is 210 tests, all passing. A change that lowers the count needs an explanation.

## Architecture

Layered: `Controller → Service → Repository → PostgreSQL`.

Package layout is by feature, with controllers kept separate:

```
JobflowApplication.java   entry point (package root)
auth/          JWT, filter, AuthService, CurrentUserProvider, login/register DTOs
config/        SecurityConfig, SpringDataConfig
controller/    all @RestController classes (thin), incl. small Hello/About/Version info endpoints
organization/  Organization, OrganizationMembership, roles, services, exceptions, DTOs
project/       Project, ProjectService, repository, DTOs, exceptions
task/          Task, TaskService, TaskSpecifications, enums, DTOs, exceptions
user/          User, Role, UserRepository, UserResponse
web/           GlobalExceptionHandler (central error responses)
```

Hello, About and Version are trivial info endpoints. They are protected like everything else except `/api/auth/**`, and should stay that way unless told otherwise.

Rules:
- Controllers only handle HTTP: validation (`@Valid`), status codes, `Location` headers. No business logic.
- Business rules and authorization live in services.
- Never expose JPA entities in responses. Use request and response DTOs (Java records). Note: `ProjectController` currently maps entities to `ProjectResponse` itself while `TaskService` returns `TaskResponse`. Follow the pattern of the file you are editing, and do not refactor it unless asked. (Open decision for the developer: which direction to standardize on.)
- Filtering uses Spring Data `Specification`s (`TaskSpecifications`). Extend those rather than adding many repository methods.
- Use constructor injection. No field `@Autowired` in main code.

## Authorization model (the most important thing to get right)

Authentication is stateless JWT (`Authorization: Bearer <token>`). Only `/api/auth/**` is public. The current user comes from `CurrentUserProvider.requireCurrentUser()`. The returned `User` is **detached**: read it and use it as an association reference, but mutating it persists nothing.

Every service operation must resolve the acting user and check permissions. Never add an endpoint or service method without an authorization check.

`OrganizationService` tiers: any member can **view** (get by id; list returns only memberships); **OWNER** or **ADMIN** can update; only **OWNER** can delete. Non-members get `OrganizationNotFoundException` (404) on get/update/delete so existence is hidden; members who lack the tier get `InsufficientOrganizationPermissionException` (403).

A project is either **personal** (has an `owner`) or **organization-owned** (has an `organization`). Permission tiers live in `ProjectService` as public predicates, and `TaskService` reuses them. Do not duplicate role logic:

| Tier | Predicate | Personal project | Organization project |
|---|---|---|---|
| view | `canView` | owner | any member |
| edit | `canEdit` | owner | any member except VIEWER |
| manage | `canManage` | owner | OWNER or ADMIN |

- Tasks inherit permissions from their parent project: view for GET, edit for create, update and status change, manage for delete (a MEMBER can edit tasks but not delete them).
- Check order is **visibility first, then permission**. If the user cannot view the resource, throw the not-found exception (`ProjectNotFoundException` or `TaskNotFoundException`) so existence is hidden (404). If they can view it but lack the tier, throw `InsufficientOrganizationPermissionException` (403).
- Creating a project under an organization uses `requireCanCreateUnder`: any member except VIEWER. A non-member or VIEWER gets **403**, not the view-first 404.
- Membership operations (`OrganizationMembershipService`) return **403** (`InsufficientOrganizationPermissionException`) to non-members. They do **not** hide existence with a 404 the way project, task and `OrganizationService` get/update/delete do. For a new membership-scoped endpoint, follow that service's pattern unless told otherwise.
- Organization roles: `OWNER`, `ADMIN`, `MEMBER`, `VIEWER`. Only an OWNER can add, promote, demote or remove another OWNER, and an organization must always keep at least one OWNER (`LastOwnerException`).
- `TaskService` re-fetches the task's project through `ProjectRepository` because `Task.project` is LAZY and the service is not `@Transactional`. Keep that pattern, and do not touch the lazy proxy's fields directly.

## Domain rules

- Task status is `TODO → IN_PROGRESS → DONE`, strictly forward. Anything else raises `InvalidTaskStatusTransitionException` (400). Status changes go only through `PATCH /api/tasks/{id}/status`, never through `PUT`.
- Deleting a project that has tasks requires `?force=true`, otherwise `ProjectHasTasksException`.
- JWT: the only claims are `sub` (the user's email), `iat` and `exp`. Signed with HMAC from `jobflow.jwt.secret`. Do not add claims or change the signing setup without being asked.
- Emails are normalized (trimmed, lowercased) in `AuthService`. Keep any new email handling consistent with that.
- Password length is validated at 8 to 72 characters (BCrypt limit). Do not loosen it.

## Errors

All error responses go through `web/GlobalExceptionHandler` and share one shape: `status`, `error`, `message`, `timestamp`, and an optional `fieldErrors`. For a new failure case, create a specific exception in the owning feature package and map it in the handler. Never return raw exception messages or stack traces from controllers.

## Code style

- Java 21: use records for DTOs, switch expressions, `instanceof` patterns where they help.
- Match existing formatting (4 spaces, same import ordering as neighboring files). Do not reformat files you are not otherwise changing. Line endings are mixed across the repo (some files CRLF, some LF, never mixed within one file), and `.gitattributes` only covers `/mvnw` and `*.cmd`, so Java, Markdown and XML endings are unmanaged. **Preserve each file's existing line endings**, and never normalize them as a side effect.
- Javadoc or comments should explain *why* (lazy loading, check ordering, detached entity), not restate the code. Existing comments like this are intentional, so do not delete them.
- Time handling is UTC everywhere. Do not introduce local-timezone logic.

## Testing conventions

Every behavior change needs tests in the same change.

- **Service tests** (`*ServiceTest`): JUnit 5 + Mockito, repositories mocked. Assertion style varies by file (some use AssertJ, some JUnit `Assertions`), so match the neighboring test file and don't mix styles within one class. Cover the happy path **and every denial path** (not-a-member, VIEWER, MEMBER vs. OWNER/ADMIN, hidden-existence 404 vs. visible 403).
- **Controller tests** (`*ControllerTest`): `@WebMvcTest` (Boot 4 import: `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`) with services mocked. Check status codes, validation errors and the response body shape. Import `SecurityConfig` and mock `JwtService` and `UserDetailsService` where needed (see `ProtectedEndpointTest`).
- **Service integration tests** (for example `TaskServiceIntegrationTest`): `@SpringBootTest` + `@Transactional` against real PostgreSQL. Create a real persisted user, set it in the `SecurityContext`, give every project a real owner or organization, and clear the context in `@AfterEach`. Without that, the authorization layer rejects the call before the behavior under test runs.
- **Repository-level integration tests** (for example `TaskIntegrationTest`) don't go through services, so they need no `SecurityContext`.
- A class-level `@Transactional` on a test can **hide** missing-transaction bugs, because the test supplies the transaction the code under test lacks. When testing that a service method manages its own transaction, write the test without it.
- Mocks hide persistence and transaction bugs. If a change involves derived deletes, lazy loading, transactions, or queries, add an integration test and do not rely on mocks alone.
- Do not weaken, delete or `@Disabled` an existing test to make a change pass. If a test seems wrong, say so and explain.

## Things you must not do

- Do not commit or print secrets. The real JWT secret comes from `JOBFLOW_JWT_SECRET`, so never hardcode it in main properties, docs or logs, and never add a fallback default for it. The only committed secret-like values allowed are the test-only JWT secret in `src/test/resources/config/application.properties` (it must never equal a real secret) and the local-dev DB password in `docker-compose.yml` and as the `JOBFLOW_DB_PASSWORD` default. Never print the value of `JOBFLOW_JWT_SECRET`.
- Do not change `ddl-auto`, the DB schema, the JWT claims, or any public endpoint path or response shape without being asked.
- Do not add dependencies to `pom.xml` without being asked, and say why when one is needed.
- Do not edit `target/` (generated, gitignored), and do not run `git push` or rewrite history.
- Do not make unrelated changes. If you spot another problem, report it at the end instead of fixing it silently.

## Commits

Conventional-commit style, matching the history:

```
feat(task): add task authorization inherited from parent project
test(organization): cover duplicate-name and missing-user edge cases
```

Use `feat`, `fix`, `test`, `refactor`, `docs`, `chore`, with a scope for the feature area. The body should explain *what changed and why*, and note the test count when it changes (for example `Tests: 210 -> 217`). One logical change per commit. Do not commit unless asked.

## Known issues (backlog, do not fix unless asked)

1. **Secrets in git history (partly fixed)**: the JWT secret is now read from `JOBFLOW_JWT_SECRET`, but the old value remains in earlier commits, so it must stay rotated (never reuse it). Scrubbing history is optional. Still open: the README reprints the DB password under “Running Locally”, and `docker-compose.yml` hardcodes it (acceptable for local dev only). Custom `jobflow.*` properties have no metadata, so VS Code shows cosmetic `PROP_UNKNOWN_PROPERTY` warnings, fixable with `META-INF/additional-spring-configuration-metadata.json`.
2. **Force-delete is broken (confirmed)**: `ProjectService.deleteProject(force=true)` calls the derived delete `TaskRepository.deleteByProjectId` with no `@Transactional`. Callers get Spring's `InvalidDataAccessApiUsageException` wrapping `jakarta.persistence.TransactionRequiredException`. Mocked tests and class-level `@Transactional` tests don't catch it. Fix: `@Transactional` on `deleteProject`, with a regression test that does **not** use a class-level `@Transactional`, asserting the delete succeeds.
3. **`GET /api/projects` is O(n)**: `findAll()` then in-memory filtering with a membership lookup per project, and no pagination despite the README. Needs a filtered query and a `Pageable`.
4. **README is stale**: says 62 tests, lists auth, ownership and project members as future work, doesn't document `/api/auth`, organization or membership endpoints, still claims project list pagination, and shows an outdated package layout (controllers under feature packages).
5. `OrganizationMembershipService.addMember` looks users up by raw `request.email()` without the normalization `AuthService` applies.
6. Schema is managed by `ddl-auto=update` with no migrations, and `pom.xml` has empty `<name/>`, `<license/>`, and similar placeholder elements.

## How to work

1. Read the relevant service, its tests and the neighboring feature code before writing anything.
2. Make the smallest change that fully solves the task, following existing patterns.
3. Write or update tests, then run `.\mvnw.cmd clean test`.
4. Finish with a short report: what changed, which tests were added, the final test count, and any problems you noticed but did not touch.

If a requirement is ambiguous, especially anything touching permissions, ask before guessing. A wrong authorization assumption is worse than a question.