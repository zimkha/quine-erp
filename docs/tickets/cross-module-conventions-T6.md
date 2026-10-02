# T6: Write the cross-module conventions down (documentation)

- **Status:** written by the BA against `main` at `f20bc52` (2026-10-02). **Architect: validated with changes (2026-10-02, checked against `f20bc52`).** The changes are already applied below.
- **Workflow:** BA → Architect → Developer → Lead Developer (see `CLAUDE.md`).
- **Sources:**
  - `docs/tickets/tenant-scoping-T1-T4.md` ("Rules to add to T6")
  - `docs/tickets/endpoints-T5.md` ("Contract shared by all T5 tickets")
  - `docs/tickets/validation-message-locale.md` (pinned validation texts)

## User story

As a developer building the next module (`customer`, `catalog`, `inventory`…), I want the rules the
`organization` module already follows written in one place, so that I copy them instead of
rediscovering them from five tickets and the code.

## Architect decisions

| # | Question | Decision |
|---|---|---|
| 1 | `CLAUDE.md` or an ADR | **A "Conventions" section in `CLAUDE.md`, no ADR.** `docs/adr/` doesn't exist, and the T1–T5f tickets already hold the rationale, so the section only needs pointers to them. |
| 2 | ArchUnit and the shared helper | **Stay deferred** until a second module exists. `FieldErrorMessages` and `ApiErrorResponse` live in `organization` today. |
| 3 | Migration staging rule | **Out of `CLAUDE.md`.** It matters only once production has large tables. `CLAUDE.md` keeps one line (with the V3 caveat) pointing to a new `docs/conventions-migrations.md`. |
| 4 | Checked against the code | Every rule below was checked. One was wrong and is corrected (a response never echoes the tenant, except registration). "Handler order" is reworded as "step order": Spring picks the most specific exception handler, and `BusinessRuleViolationException` extends `DomainException`. |

## Context

`organization` is the reference module, but its conventions only exist as decisions scattered over the
T1–T5f tickets. A second module would have to read all of them, and a rule that isn't written down gets
broken. The T1–T4 and T5f tickets both say these rules "go to T6". This ticket is only the writing:
**no production code changes**.

## Scope: the conventions to write

Each convention is one short rule plus a pointer to the ticket that explains why. The result is a
"Conventions" section in `CLAUDE.md` (the Architect decides whether any part needs an ADR instead).

### 1. Tenant

- The controller calls `CurrentTenantProvider.currentTenant()` once per request and passes the result as the command's `tenantId`.
- The tenant is never read from the path, the query string, the body or a header. No request DTO declares a `tenantId`, and no tenant-scoped response echoes it. Registration is the one exception: it generates the tenant (`TenantIdGenerator`), takes no `tenantId` in its command, and returns it in `RegisterOrganizationResponse`.
- An aggregate the caller's tenant doesn't own is indistinguishable from a missing one: the same 404, never a 409 or a 403.
- Providers fail closed: if the tenant can't be established they throw `TenantNotResolvedException` (401, never `null`).
- There is one default fail-closed provider, in `bootstrap` (`FailClosedTenantProvider`, `@Profile("!smoke")`), removed when `identity` (T7) supplies the real one. The `smoke` profile swaps in `SmokeTenantProvider` (tenant from `X-Smoke-Tenant`), for local runs only. Modules don't ship their own default (`@ConditionalOnMissingBean` defaults are rejected: registration order and duplicate beans).
- Tenant-scoped endpoints live in their own controller. The controller that serves public endpoints (register) never injects `CurrentTenantProvider`.

### 2. Order of steps and error contract

- Step order: request-format validation (400), tenant resolution (401), then inside the handler: ownership (404), value objects (422), business rules (409).
- Error body is `ApiErrorResponse {code, message, timestamp}`. The status-and-code table is the one in `endpoints-T5.md`.
- `ApiErrorResponse` and `ApiExceptionHandler` live in each module's `presentation/rest/exception`. A new module declares its own copy with the same shape, until a shared one exists.
- Every module's advice also maps `OptimisticLockingFailureException` to 409 `CONCURRENT_MODIFICATION`, and `DataIntegrityViolationException` to 409 `DATA_INTEGRITY_VIOLATION` with a generic message and no SQL detail.
- The 401 carries `WWW-Authenticate: Bearer realm="quine-erp"`.
- `message` is developer-facing English. Clients branch only on `code`, and the server never localizes errors or reads `Accept-Language` for them. Localized end-user text is the client's job.
- Repeated actions: a repeat returns its 409 "already" code (`…_ALREADY_…`), which clients treat as "target state already reached". `…_CANNOT_BE_…` means the action is refused from the current status and is never success. No endpoint is a silent no-op.
- Each module scopes its `@RestControllerAdvice` with `basePackageClasses`, so modules can't take each other's exceptions.

### 3. Validation messages

- Build each bean-validation message from the constraint annotation type, using the pinned table (`must not be blank`, `must not be null`, `size must be between {min} and {max}`, `must match the required format`, `is invalid`). Never use `getDefaultMessage()` or default-locale number formatting (`String.format("%d")` gives Arabic digits under `ar-EG`).
- Format is `"<field>: <text>"`, parts deduplicated and sorted with plain `String` order. The raw `FieldError` order isn't stable.
- No module ships a `ValidationMessages.properties`.
- Request DTOs may use only constraints that have a pinned text, and no class-level constraints. A new constraint needs a text, a table row and a guard-test update.
- Each module with request DTOs has its own constraint guard test (model: `RequestDtoConstraintGuardTest`).
- Malformed path UUIDs and malformed or missing JSON bodies get the fixed messages `"<param>: must be a valid UUID"` and `"Request body is missing or malformed"`. Raw input is never echoed.
- When a controller first puts a constraint on a `@PathVariable` or `@RequestParam`, `HandlerMethodValidationException` must be mapped with the same pinned texts. No controller does today.

### 4. Wiring a new module into `bootstrap`

- `bootstrap` wires modules by hand in `QuineApplication`: `@Import` for the module's configuration, controllers and advice, plus `@EntityScan` and `@EnableJpaRepositories` for its packages. `@ComponentScan` covers only `com.zim.quine`. A module that isn't added there silently doesn't load.

### 5. Database migrations

- One Flyway path and one Postgres schema per module (already in `CLAUDE.md`).
- `CLAUDE.md` holds one line: a migration like V3 (new column, backfill and constraint in one transaction) is acceptable only while no deployed database exists, and later ones follow `docs/conventions-migrations.md`.
- New file `docs/conventions-migrations.md` holds the staged approach for large tables:
  1. add the column as nullable
  2. backfill in batches
  3. add the foreign key as `NOT VALID`, then `VALIDATE CONSTRAINT`
  4. set `NOT NULL`, backed by a validated `CHECK (col IS NOT NULL)`

## Out of scope

- **ArchUnit rules and a shared validation-message helper** (the Architect's T5f decision 6): they come once a second module exists, so the rule isn't written for one module. This ticket documents the convention and keeps these deferred.
- Per-field structured validation errors (`field` plus a constraint code): a separate follow-up ticket (product-owner follow-up in `validation-message-locale.md`).
- Mapping 405 and 415 into `ApiErrorResponse`.
- Identity, roles and platform admin (T7, T8, T9), row-level security (T10), and the suspension lifecycle.
- Changing any code, test or migration.

## Acceptance criteria

- `CLAUDE.md` has a "Conventions" section with the groups above (the migration group is one line plus a pointer), each rule one or two lines with a pointer to its source ticket.
- `docs/conventions-migrations.md` exists and holds the staged-migration steps.
- Every rule matches what `organization` does today. Where code and ticket disagree, the Architect decides which is right, and the ticket or the code is fixed in its own change.
- The section doesn't contradict the rest of `CLAUDE.md` (layout, multi-tenancy, database, code style, the `/api` prefix).
- The ticket's deferred items (ArchUnit, shared helper) are named as deferred in the section, with the trigger "a second module exists".
- No file outside `CLAUDE.md` and `docs/` changes (the new file is `docs/conventions-migrations.md`). `mvn clean test` is unaffected.

## Definition of done

- ~~Architect validation recorded here.~~ Done (2026-10-02).
- `CLAUDE.md` updated and reviewed by the Lead Developer for accuracy against the code.
- PR into `main`.

## Dependencies

- T5a–T5f and the fail-closed provider: merged.
- None on identity.

## Open questions

**Architect:** ~~1–3~~ all **resolved**, see the decisions table.

**Product owner:** none.
