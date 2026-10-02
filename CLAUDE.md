# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

There is no Maven wrapper (`mvnw`) — use the system `mvn` (≥ 3.9.0, enforced by `maven-enforcer-plugin`) with **Java 21** (enforced as `[21,22)`, i.e. Java 22+ will fail the build).

```bash
# Build everything
mvn clean install

# Build/test a single module (and the modules it depends on)
mvn -pl organization -am clean install

# Run only unit tests (default surefire include patterns: *Test.java, *Tests.java, *TestCase.java)
mvn -pl organization test

# Run a single test class
mvn -pl organization test -Dtest=OrganizationTest

# Run a single test method
mvn -pl organization test -Dtest=OrganizationTest#shouldActivateWhenPending
```

**Integration tests are not run by `mvn test`.** Classes suffixed `*IT.java` (e.g.
`OrganizationRepositoryAdapterIT`, `OrganizationDatabaseConstraintsIT`) don't match surefire's
default include patterns, and no `maven-failsafe-plugin` is configured in the parent POM. They
must be run explicitly:

```bash
mvn -pl organization test -Dtest=OrganizationRepositoryAdapterIT
```

These `*IT` tests use **Testcontainers** (`testcontainers-postgresql`) and use the
`OrganizationConfigurationIT` / `PostgresIntegrationTest` support classes — **Docker must be
running** to execute them.

## Architecture

**Modular monolith.** The root `pom.xml` (`packaging=pom`) aggregates one Maven module per
bounded context: `shared`, `organization`, `identity`, `customer`, `supplier`, `catalog`,
`inventory`, `purchasing`, `sales`, `cash`, `notification`, `reporting`, `bootstrap`.

- **`shared`** is the shared kernel: `AggregateRoot`, `DomainEvent`, `BusinessRule` (+
  `BusinessRuleViolationException`), `DomainException`, `TenantId`. Every other module depends on it and
  builds its own aggregates/rules on top of these base types.
- **`bootstrap`** is the composition root: a Spring Boot app (`QuineApplication`) that wires the
  business modules (today `organization` and `identity`). Until login (T7b) exists there is no real tenant
  source: without a profile every tenant-scoped endpoint answers 401 (`FailClosedTenantProvider`).
  The `smoke` profile reads the tenant from an `X-Smoke-Tenant` header and adds a
  `/smoke/organizations/{id}/activate` shortcut, for local runs only (it refuses to start together with any
  profile other than `smoke`, `local`, `dev`, `test` or `docker`):

  ```bash
  docker compose up -d --build   # Postgres + app with the smoke profile on :18080
  # or, with Postgres on localhost:15432:
  java -jar bootstrap/target/bootstrap-1.0-SNAPSHOT.jar --spring.profiles.active=smoke
  ```

  The Postman collection in `docs/postman/` runs against it.
- **`organization`** is the only fully implemented business module and is the reference to
  follow when building out the others (same package layout, same CQRS-ish handler pattern,
  same test layering).
- **`identity`** is implemented as far as T7a: the `User` aggregate (e-mail, BCrypt password hash, one owner
  per tenant), `CreateOwnerHandler` and its own `identity` schema. Login, tokens and the security filter
  are T7b (deferred); registration creating the owner is T7c. See `docs/tickets/identity-T7.md`.
- The other business modules (`customer`, `supplier`, `catalog`, `inventory`,
  `purchasing`, `sales`, `cash`, `notification`, `reporting`) are scaffolded (POM only, single
  placeholder `Main.java`) and not yet implemented.

### Per-module layering (hexagonal / DDD), e.g. `organization`

```
com.zim.organization
├── domain
│   ├── model        # aggregates (Organization, Store) extending shared AggregateRoot
│   ├── valueobject   # OrganizationId, StoreId, CurrencyCode, ... (records)
│   ├── event         # domain events (OrganizationRegistered, StoreAdded, ...)
│   ├── rule          # one class per BusinessRule (e.g. StoreCodeMustBeUniqueRule)
│   ├── repository     # domain-facing repository interface (port, driven side)
│   └── exception
├── application
│   ├── command       # one record/class per use case input (RegisterOrganizationCommand, ...)
│   ├── handler       # one handler per command, orchestrates the aggregate + ports
│   ├── port          # driven ports the handler depends on (generators, ClockProvider,
│   │                  # DomainEventPublisher) — plain functional interfaces, no Spring here
│   ├── result         # use-case output DTOs
│   └── exception
├── infrastructure
│   ├── persistence
│   │   ├── entity      # JPA entities
│   │   ├── mapper       # entity <-> domain model mapping
│   │   ├── adapteur     # repository port implementations (adapts JPA to domain repository)
│   │   └── repository   # Spring Data JPA repositories
│   ├── event          # DomainEventPublisher implementation
│   └── configuration   # Spring @Configuration / bean wiring for the module
└── presentation
    └── rest
        ├── request / response   # REST DTOs
        └── exception            # ApiExceptionHandler, ApiErrorResponse
```

The `domain` layer is framework-free; `application` handlers depend only on domain types and
`port` interfaces (also framework-free); `infrastructure` is where Spring/JPA/Flyway live and
where ports get concrete adapter implementations; `presentation` is the REST boundary and maps
requests to `application` commands.

### Multi-tenancy

Aggregates carry a `TenantId` value object (`com.zim.shared.domain.TenantId` in the shared
kernel, a `UUID` wrapper). Keep this in mind when adding new aggregates/tables in any module — tenant
scoping is a cross-cutting concern of the domain model, not bolted on at the persistence layer.

### Database

- **PostgreSQL** + **Flyway**, one migration path per module:
  `<module>/src/main/resources/db/migration/<module>/V*__*.sql` (e.g.
  `organization/src/main/resources/db/migration/organization/V1__create_organization_schema.sql`).
- Each module's Flyway config scopes itself to its own Postgres **schema** (see
  `organization/src/test/resources/application-test.yaml`: `flyway.schemas` /
  `default-schema: organization`) — modules do not share a schema, and entities name their schema
  explicitly (`@Table(schema = "...")`).
- In `bootstrap` each module has its own `Flyway` bean (`FlywayConfiguration`), so each module keeps its
  own `flyway_schema_history` and every module can start at `V1`. A new module adds its pair of beans there.

## Conventions

Followed by `organization`; every new module follows them. Each rule points to the ticket that
explains why. Full list and rationale: `docs/tickets/cross-module-conventions-T6.md`.

**Tenant** (`docs/tickets/tenant-scoping-T1-T4.md`)
- A controller calls `CurrentTenantProvider.currentTenant()` once per request and passes it in the
  command. The tenant never comes from the path, query, body or a header; no request DTO has a
  `tenantId`; tenant-scoped responses don't echo it (registration generates it and returns it).
- Another tenant's aggregate looks exactly like a missing one: the same 404, never 409 or 403.
- Providers fail closed (`TenantNotResolvedException`, 401). The only production default is
  `FailClosedTenantProvider` in `bootstrap` (`!smoke` profile; `smoke` swaps in a local-only
  `SmokeTenantProvider`), removed when `identity` (T7) lands. Modules ship no default of their own.
- Tenant-scoped endpoints get their own controller; the public one (register) never injects the provider.

**Errors** (`docs/tickets/endpoints-T5.md`, shared contract)
- Step order: request validation (400), tenant (401), then in the handler: ownership (404), value
  objects (422), business rules (409). Body: `ApiErrorResponse {code, message, timestamp}`.
- `message` is developer-facing English. Clients branch on `code` only; `Accept-Language` is ignored.
- A repeat of a state-changing action returns `<ENTITY>_ALREADY_<STATE>` (e.g.
  `ORGANIZATION_ALREADY_CLOSED`, `STORE_ALREADY_INACTIVE`, `STORE_IS_ALREADY_HEADQUARTERS`): target state
  reached. `..._ALREADY_EXISTS` is a real duplicate conflict. `..._CANNOT_BE_...` means refused from the
  current status. No endpoint is a silent no-op.
- Scope each module's `@RestControllerAdvice` with `basePackageClasses`. `ApiErrorResponse` and the
  generic handlers stay in `organization` until a second module exists, then are extracted once into a
  shared web component (`web-shared`), never copied. The shared generic advice will be unscoped.

**Validation messages** (`docs/tickets/validation-message-locale.md`)
- Build each message from the constraint type with the pinned texts, never `getDefaultMessage()` or
  default-locale formatting. No `ValidationMessages.properties`.
- DTOs may use only constraints with a pinned text, no class-level ones, enforced by a guard test per
  module (model: `RequestDtoConstraintGuardTest`).

**Wiring and migrations**
- `bootstrap` wires modules by hand in `QuineApplication` (`@Import`, `@EntityScan`,
  `@EnableJpaRepositories`); a module not added there silently doesn't load.
- A V3-style migration (column, backfill and constraint in one transaction) is acceptable only while no
  deployed database exists; after that follow `docs/conventions-migrations.md`.

**Deferred until a second module exists:** ArchUnit rules, the shared validation-message helper and the
shared web component for the error contract (its `CONCURRENT_MODIFICATION` message hardcodes
"organization" and must be generalized when extracted).

## Stack

- Java 21, Spring Boot 4.1.0 (via `spring-boot-dependencies` BOM in the parent POM)
- PostgreSQL, Flyway (`flyway-database-postgresql`)
- JUnit 6, AssertJ, Testcontainers (`testcontainers-postgresql`) for tests

## Workflow development

1. The BA writes/refines the tickets (user stories, acceptance criteria)
2. The Architect validates the ticket's technical design before implementation
3. The Developer implements the ticket
4. The Lead Developer reviews the code before merging

## Code Style

- Use 2 space for indentation
- All API routest start /api