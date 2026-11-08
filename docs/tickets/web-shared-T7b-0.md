# T7b-0: Extract the shared web component (`web-shared`) from `organization`

- **Status:** drafted by the BA (2026-11-08), checked against branch `claude/gallant-ritchie-u9aa1v` (T7a, T7c and T9g merged). Awaiting Architect validation. Slice of T7b, decided in `platform-admin-T9.md` A7 and in `cross-module-conventions-T6.md` decision 2.
- **Workflow:** BA → Architect → Developer → Lead Developer (see `CLAUDE.md`).
- **Type:** pure refactoring. Any change of observable HTTP behaviour is a failure of this ticket.
- **Sources:** `cross-module-conventions-T6.md` (decision 2, "Order of steps and error contract"), `endpoints-T5.md` (error table), `validation-message-locale.md` (pinned texts), `identity-T7.md` (decisions 9 and 11), `platform-admin-T9.md` (A7).

## Context / Why

The error contract (`ApiErrorResponse {code, message, timestamp}`, the pinned validation texts, the generic handlers) lives today in `organization/presentation/rest/exception`. `identity` is now the second module. In T7b it gets a login controller, and `bootstrap` gets an `AuthenticationEntryPoint` and an `AccessDeniedHandler`. All three must write `ApiErrorResponse`.

Without this slice, `identity` would depend on `organization` (forbidden by T7 decision 7) or copy the contract (forbidden by T6: copies drift). T6 and the first T9 Architect validation (A7) said the extraction is triggered by the second module with a REST surface. That is now.

## Actor(s)

A developer building a module (here: the T7b developer). No end user sees any difference.

## User Story

As a developer of a business module, I want the error contract and the generic exception mapping to exist once, outside any business module, so that every module and the security chain answer clients with exactly the same codes, messages and shape, without depending on each other.

## What is extracted (to `web-shared`)

| Today (`organization`) | Goes to `web-shared` | Note |
|---|---|---|
| `ApiErrorResponse` (record, fields in this order: `code`, `message`, `timestamp`) | yes | JSON shape and order unchanged. |
| `FieldErrorMessages` (pinned texts built from the constraint type) | yes, stays public | Includes the `@Email` branch already present. |
| In `ApiExceptionHandler`: `MethodArgumentNotValidException` (400 `VALIDATION_FAILED`, `"<field>: <text>"` parts, distinct, sorted) | yes | |
| `MethodArgumentTypeMismatchException` (400, `"<param>: must be a valid UUID"`) | yes, text unchanged | Generalisation by type is T9 A6, not here. |
| `HttpMessageNotReadableException` (400, `"Request body is missing or malformed"`) | yes | |
| `TenantNotResolvedException` (401, code and message from the exception, header `WWW-Authenticate: Bearer realm="quine-erp"`) | yes | The header value becomes one constant, reusable by the T7b entry point. |
| `BusinessRuleViolationException` (409, code from the exception) | yes | **Not in the T6 list** but a shared-kernel type: otherwise identity (`EMAIL_ALREADY_EXISTS`) must copy it. |
| `DomainException` (422, code from the exception) | yes | Same reason. |
| `OptimisticLockingFailureException` (409 `CONCURRENT_MODIFICATION`) | yes | See "Message generalisation". |
| `DataIntegrityViolationException` (409 `DATA_INTEGRITY_VIOLATION`, `"The request conflicts with existing data"`) | yes | |
| The private `error(status, code, message)` helper | yes | |

The generic advice is **unscoped** (T6). Package and module name: `web-shared`, `com.zim.shared.web` (T6 decision 2; the Architect confirms the exact names). Dependencies: `shared`, spring-web/webmvc, jakarta validation and `spring-tx` (for the `org.springframework.dao` exception types). **No JPA, no Spring Security, no servlet container.** `shared` stays framework-free.

## What stays in `organization`

- `ApiExceptionHandler`, shrunk to the handlers for exceptions the module owns:
  - `OrganizationNotFoundException` (404 `ORGANIZATION_NOT_FOUND`)
  - `OrganizationAlreadyExistsException` (409 `ORGANIZATION_ALREADY_EXISTS`)
- It keeps `@RestControllerAdvice(basePackageClasses = OrganizationController.class)`.
- `RequestDtoConstraintGuardTest` stays (it scans the organization request package).

## Double-mapping risk (central point of this ticket)

Spring tries the advice beans in `@Order` order. For each applicable advice, it looks for a handler method for the exception. **The first advice that has a matching method wins, even if a later advice has a more specific one.** With one unscoped advice plus one scoped advice per module:

1. A module handler for an exception whose supertype is mapped in the generic advice is **shadowed** if the generic advice is ordered first. Today no clash exists (the two organization exceptions extend `RuntimeException`), but the next module exception that extends `DomainException` would silently get a 422.
2. A handler duplicated in two advices means two sources of truth, which is exactly the drift T6 forbids.
3. The generic advice imported twice (module configuration plus `bootstrap`) produces duplicate beans. In `@WebMvcTest` slices that import only the module advice, the generic mapping would be silently missing and every 400/401/409 would become a framework default.

Rules this ticket must establish and test:

- The generic advice is `@Order(Ordered.LOWEST_PRECEDENCE)`. Module advices are ordered **before** it and contain no catch-all.
- A module advice declares handlers only for exceptions that module owns.
- Each exception type appears in exactly one advice.
- The generic advice is wired by one explicit configuration class, imported once by `bootstrap` and by every slice or IT that imports a controller. A slice that forgets it must fail loudly (its error assertions fail), never degrade silently.

## Acceptance Criteria

**T7b-0-AC1: no observable change (the criterion that matters)**
- Given the existing organization web tests (`OrganizationControllerTest`, `TenantOrganizationControllerTest`) and the five `*EndToEndIT` classes,
- When they run after the extraction,
- Then they pass **with no change to any assertion**. Only imports and `@Import` lists may change. The reviewer checks the diff of these tests: a changed `jsonPath`, status or header expectation is a rejection.

**T7b-0-AC2: no code and no message change**
- Given every error the API can produce today,
- When it is produced after the extraction,
- Then status, `code`, `message`, headers, JSON body (exactly three keys) and the ISO `timestamp` format are identical.
- Evidence: a **characterisation test written before the move** (green on `organization` as it is) asserting the table below, then moved and kept green after the move:

| Trigger | Status | `code` | `message` | Header |
|---|---|---|---|---|
| invalid DTO | 400 | `VALIDATION_FAILED` | `"<field>: <text>; ..."` distinct, sorted | none |
| malformed path id | 400 | `VALIDATION_FAILED` | `"<param>: must be a valid UUID"` | none |
| missing or malformed body | 400 | `VALIDATION_FAILED` | `Request body is missing or malformed` | none |
| `TenantNotResolvedException` | 401 | `TENANT_NOT_RESOLVED` | `The tenant of the current request could not be resolved` | `WWW-Authenticate: Bearer realm="quine-erp"` |
| `BusinessRuleViolationException` | 409 | the rule's code | the rule's message | none |
| `DomainException` | 422 | the exception's code | its message | none |
| `OptimisticLockingFailureException` | 409 | `CONCURRENT_MODIFICATION` | see AC8 | none |
| `DataIntegrityViolationException` | 409 | `DATA_INTEGRITY_VIOLATION` | `The request conflicts with existing data` | none |
| `OrganizationNotFoundException` | 404 | `ORGANIZATION_NOT_FOUND` | `Organization '<id>' was not found` | none |
| `OrganizationAlreadyExistsException` | 409 | `ORGANIZATION_ALREADY_EXISTS` | `An organization with legal name '<name>' already exists` | none |

- The characterisation test also asserts that none of the bodies echoes raw input, the tenant, a SQL fragment or a stack trace (as `TenantNotResolvedMappingTest` does today).

**T7b-0-AC3: usable without `organization`**
- Given a probe controller in a package that is not under `com.zim.organization`, in `web-shared`'s own tests (or `identity`'s),
- When it throws each generic exception,
- Then the mapping of AC2 applies. This proves a second module gets the contract without referencing `organization`.

**T7b-0-AC4: dependency direction**
- `web-shared` depends on `shared` (and Spring web/validation/tx) only. `organization` depends on `web-shared`. `bootstrap` depends on both. `identity` and `organization` do not reference each other. `web-shared` does not reference any business module. A violation fails the build or a test (Maven dependency structure at minimum).

**T7b-0-AC5: one mapping per exception**
- Given the full context (`QuineApplication`),
- When a test lists every `@ControllerAdvice` bean and their `@ExceptionHandler` exception types,
- Then no type is handled by two advices, and no module advice handles a type declared outside its own module's packages. The generic advice is exactly one bean.

**T7b-0-AC6: scoping**
- The generic advice has no `basePackages`, `basePackageClasses`, `assignableTypes` or `annotations`. The organization advice is still scoped to `OrganizationController.class` (the current `TenantNotResolvedMappingTest` assertion moves to this advice).

**T7b-0-AC7: precedence proven**
- Given a test-only module advice (higher precedence) handling a subclass of `DomainException` with its own status,
- When the exception is thrown,
- Then the module's mapping wins. This documents the ordering convention.

**T7b-0-AC8: `CONCURRENT_MODIFICATION` message**
- The code stays `CONCURRENT_MODIFICATION`, status 409. The message currently says "The organization was modified by another request; reload it and retry".
- Step 1 (pure move): the message is moved byte-identical, pinned by the characterisation test.
- Step 2 (separate commit, the only intended diff of this ticket): the message becomes module-neutral. The Architect pins the exact neutral wording (BA proposal: `The resource was modified by another request; reload it and retry`).
- No existing test or Postman assertion checks this text today, and `message` is developer-facing (clients branch on `code`). If the Architect prefers zero diff, the alternative is to keep the organization wording through a handler in the organization advice, at the cost of a second mapping of the same exception. The BA recommends the neutral wording.

**T7b-0-AC9: validation texts and locale independence**
- `FieldErrorMessagesTest` moves with `FieldErrorMessages` and stays green, including the non-default-locale scenarios.
- The existing 400 tests with `Accept-Language` stay green unchanged.
- `RequestDtoConstraintGuardTest` still fails for an unpinned constraint, and its fix hint points to the new location of `FieldErrorMessages`.

**T7b-0-AC10: scope of the change**
- No new endpoint, no migration, no schema change, no Spring Security dependency, no change in `shared`, no change in any `code`.
- `mvn clean install` passes, and the `*IT` classes pass with Docker (`mvn -pl organization test -Dtest=...IT`, and the `bootstrap` ITs).
- The Postman collection, run end to end against the `smoke` profile before and after, gives the same results (same assertions green).

## Business Rules

- The error shape `{code, message, timestamp}` and the code table of `endpoints-T5.md` are one contract with clients. It is defined in one place.
- A module cannot change the mapping of another module's or of the shared exceptions.
- Clients branch on `code` only. `message` stays developer-facing English. No `Accept-Language` handling.
- Pinned validation texts are built from the constraint type, never from `getDefaultMessage()`.

## Tests to move or make generic

| Test | Action |
|---|---|
| `FieldErrorMessagesTest` | Moves to `web-shared`. |
| `organization/.../testing/DefaultLocaleExtension`, `LocaleScenario` (used by `FieldErrorMessagesTest`, `OrganizationControllerTest`, `TenantOrganizationControllerTest`) | Shared test support: a `web-shared` test-jar consumed by `organization` in test scope (Architect decides; no duplication). |
| `TenantNotResolvedMappingTest` | Splits: the 401 mapping part goes to `web-shared` (probe controller outside `organization`); the "advice is scoped to `OrganizationController`" part stays and targets the organization advice. |
| `RequestDtoConstraintGuardTest` | Stays; the reusable logic (scan a package, assert pinned constraints, no class-level constraints) moves to the shared test support so `identity`'s login DTO (T7b) reuses it. Same failure conditions as today. |
| New | Characterisation test (AC2), no-duplicate-mapping test (AC5), unscoped test (AC6), precedence test (AC7). |
| `OrganizationControllerTest`, `TenantOrganizationControllerTest`, five `*EndToEndIT`, `SmokeTenantProviderTest`, `FailClosedTenantProviderTest` | Only `@Import` / import lines change (add the generic advice configuration). Assertions untouched. The last two are deleted in T7b anyway. |

## Files touched (checked in the code)

- New module: `web-shared/pom.xml`, `web-shared/src/main/java/com/zim/shared/web/...` (`ApiErrorResponse`, `FieldErrorMessages`, the generic advice, its configuration class), tests.
- `pom.xml` (root, `<modules>`), `organization/pom.xml` (dependency), `bootstrap/pom.xml` (explicit dependency).
- `organization/src/main/java/com/zim/organization/presentation/rest/exception/ApiExceptionHandler.java` (shrunk), `ApiErrorResponse.java` and `FieldErrorMessages.java` (moved out).
- `bootstrap/src/main/java/com/zim/quine/QuineApplication.java` (`@Import` of the generic advice configuration).
- Organization tests: `OrganizationControllerTest`, `TenantOrganizationControllerTest`, `ActivateOrganizationEndToEndIT`, `AddStoreEndToEndIT`, `ChangeHeadquartersEndToEndIT`, `DeactivateStoreEndToEndIT`, `CloseOrganizationEndToEndIT` (imports only), `TenantNotResolvedMappingTest`, `FieldErrorMessagesTest`, `RequestDtoConstraintGuardTest`, `testing/DefaultLocaleExtension`, `testing/LocaleScenario`.
- Bootstrap tests (imports only; deleted in T7b): `SmokeTenantProviderTest`, `FailClosedTenantProviderTest`.
- Docs: `CLAUDE.md` (Conventions "Errors" bullet: the generic handlers now live in `web-shared`; "Deferred" line); `cross-module-conventions-T6.md` (pointer: done by T7b-0); `tenant-scoping-T1-T4.md` line 34 (says each module keeps a scoped advice that delegates; superseded by T6, mark it); `validation-message-locale.md` (location of the helper).
- `identity/pom.xml` does **not** change here. It takes the dependency in T7b.

## ArchUnit: trigger now, or stay deferred?

The T6 trigger ("a second module exists") is met in fact. The rules T6 and `tenant-scoping-T1-T4.md` planned are: no `getDefaultMessage()` in `..presentation..`, and framework-free `domain` and `application` packages. BA recommendation: **trigger it, but not inside T7b-0**.

- T7b-0 must stay a reviewable pure move, and a new test dependency whose rules may fail on existing code would blur it.
- Create a small ticket **T7b-0b "ArchUnit baseline"** right after T7b-0. It can run in parallel with T7b-1.
- Rule set limited to: no `getDefaultMessage()` in `..presentation..`; no `org.springframework` in `..domain..` and `..application..` (the Developer first verifies it holds); `web-shared` depends on `shared` only.
- Module separation (`identity` vs `organization`) is already enforced by Maven (neither lists the other), so it needs no ArchUnit rule.
- The Architect decides (question A7 in `identity-T7.md`, "T7b, detail and adjustments").

## Out of Scope

- Generalising the parameter-type message beyond UUID (T9 A6).
- Mapping 405 and 415 into `ApiErrorResponse` (T6 out of scope).
- Per-field structured validation errors.
- The servlet-level writer used by `AuthenticationEntryPoint` and `AccessDeniedHandler` (new code, T7b). T7b-0 only exposes the 401 header constant.
- Any new code, endpoint, migration, or security dependency.
- `AUTHENTICATION_REQUIRED` and the platform zone (T9b).

## Open Questions

Architect: A1 to A7 in `identity-T7.md`, "T7b, detail and adjustments". Product owner: none.

## Dependencies

- Merged: T1 to T6, T7a, T7c, T9g.
- Blocks: T7b-1 (identity's login controller and advice need `ApiErrorResponse` and the generic advice).
- Independent of T9g and of T9f-1.

## Architect validation (2026-11-08, checked against commit 746f742)

**Verdict: validated with changes.** The changes below prevail over the text above where they differ. Verified facts: `BusinessRuleViolationException` (final, extends `DomainException`) and `DomainException` (not final, `protected` constructors, so a test can subclass it for AC7) are in `com.zim.shared.domain`; both `InvalidValueException` classes (organization and identity) extend `DomainException`, so the generic advice maps them to 422. `FieldErrorMessages` depends on no `organization` class (only `jakarta.validation` and Spring's `FieldError`). `ApiExceptionHandler` has 10 handlers (8 generic, 2 module-owned), is scoped by `basePackageClasses`, and has **no `@Order`**.

### Decisions (answers to A1 to A8)

| # | Decision |
|---|---|
| A1 | `web-shared` and `com.zim.shared.web`. **No split package:** `shared` holds `com.zim.shared` (class `Main` only), `.domain` and `.tenant`, and there is no `module-info` (surefire uses `useModulePath=false`). Condition: put nothing in the root package `com.zim.shared`. |
| A2 | Yes, `BusinessRuleViolationException` (409) and `DomainException` (422) are generic. |
| A3 | Two commits, as proposed. Neutral wording: `The resource was modified by another request; reload it and retry`. The characterisation test pins the old text first, then the second commit changes that pin and nothing else. |
| A4 | **Explicit ordering, with a constants class.** Today the organization advice has no `@Order`, so if the generic advice is also lowest there is a tie and the winner depends on bean registration order (non-deterministic). Add `AdviceOrder` in `web-shared`: `GENERIC = LOWEST_PRECEDENCE`, `MODULE = LOWEST_PRECEDENCE - 100`. Put `@Order` on the advice **class** (not on a `@Bean` method). AC5 (via `ControllerAdviceBean.findAnnotatedBeans`) also asserts that every module advice's order is lower than the generic one. |
| A5 | One explicit `WebSharedConfiguration` with `@Import(GenericExceptionAdvice.class)`. `@Import` de-duplicates a class imported several times; a duplicate only appears if the advice is also declared by `@Bean` or scanned, so do not do both. **No `AutoConfiguration`** (it would not load in a slice, so an omission would be silent) and no `@ConditionalOnMissingBean`. A slice that forgets the import fails loudly (the default Spring 400 has no `code` or `message` keys). Comfort option: one organization presentation configuration that groups controllers, advice and `WebSharedConfiguration`, to shorten the `@Import` lists of the 7 tests. |
| A6 | `web-shared` test-jar (`test-jar` goal of maven-jar-plugin; consumers declare `<type>test-jar</type><scope>test</scope>`). Put `DefaultLocaleExtension` and `LocaleScenario` in `com.zim.shared.web.testing`. Make the set of pinned constraints in `FieldErrorMessages` public so the DTO guard reuses it, with no second set to keep in sync; the guard keeps filtering its own class because its scan sees the package's test classes. Exclude `web-shared`'s own `@SpringBootConfiguration` test class from the test-jar, or put it in a package that is not an ancestor of a consumer's test package. **Consequence:** `mvn -pl organization test` without `-am` needs `web-shared` (and its test-jar) installed; update the commands in `CLAUDE.md` (`-am`, or `install` first). |
| A7 | ArchUnit stays a separate ticket T7b-0b. ArchUnit is not managed by the Boot BOM (pin the version in the parent). Verified: `domain` and `application` of organization and identity have no `org.springframework`, and no `getDefaultMessage()` exists in main. Module-to-module dependency bans (AC4) go through `maven-enforcer-plugin` `bannedDependencies` now (see below). |
| A8 | Servlet-side writer in `web-shared`, with corrections: it uses the Boot bean `tools.jackson.databind.json.JsonMapper` (**Boot 4 ships Jackson 3**, `tools.jackson.core:jackson-databind` 3.1.4, not `com.fasterxml`); it needs `jakarta.servlet-api` in `provided` scope, so "no servlet container" is true only for T7b-0; it has **no Spring Security dependency** (the classes implementing `AuthenticationEntryPoint` and `AccessDeniedHandler` live in `bootstrap`); it writes status and body directly, never `sendError`, with the same JSON content type as the advice; it receives code, message and a flag for the `WWW-Authenticate` header. That is T7b-2 work; T7b-0 only exposes the header constant. |

### Changes to apply to this ticket

1. **Three commits, in this order:** (1) the characterisation test written against the current code, green before any move; (2) the pure extraction, with the `AdviceOrder` constants, AC5 to AC7 and the enforcer rules; (3) the neutral `CONCURRENT_MODIFICATION` wording.
2. **Maven shape:** add `web-shared` next to `shared` in the root `<modules>` (the order is cosmetic, the reactor sorts by dependency). Dependencies: `shared` (`${project.version}`), `spring-web`, `spring-context` (for `FieldError`), `spring-tx`, `jakarta.validation-api`, all managed by the BOM. **No `spring-webmvc` in main and never `spring-boot-starter-web` (it brings Tomcat).** Test scope: `spring-boot-starter-webmvc-test`, `spring-boot-starter-validation`, JUnit.
3. **AC4 enforcement:** Maven's cycle detection catches `web-shared` pointing at a business module, but nothing stops `identity` depending on `organization` or the reverse. Use `maven-enforcer-plugin` (already in the parent) with `bannedDependencies` in the `identity` and `organization` poms. This covers AC4 without ArchUnit.
4. **Docs list additions:** `.claude/agents/dev.md` (lines 36 and 37) and `.claude/agents/code-reviewer.md` (line 43) cite the `ApiExceptionHandler` / `ApiErrorResponse` pattern and must point to `web-shared`. Also `CLAUDE.md` commands (test-jar, `-am`).
5. **Corrections for other tickets:** after this ticket, T9 A6 (parameter-type message) and the round 2 file list (`FieldErrorMessages` for `reason`) target `web-shared`, not `organization`.
6. **Facts confirmed:** all test class names listed in "Tests to move or make generic" are exact; `OrganizationConfigurationIT` does not import the advice. There is no test CI in `.github` (only a publication workflow).

### Risks added

- Advice tie without `@Order` (non-deterministic mapping): fixed by A4.
- Test-jar: commands without `-am` fail until `web-shared` is installed.
- A second `@SpringBootConfiguration` leaking from the test-jar into a consumer's component scan.
