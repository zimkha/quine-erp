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
mvn -pl organization -am test

# Run a single test class (-am builds the upstream modules, whose tests are skipped by the filter)
mvn -pl organization -am test -Dtest=OrganizationTest -Dsurefire.failIfNoSpecifiedTests=false

# Run a single test method
mvn -pl organization -am test -Dtest=OrganizationTest#shouldActivateWhenPending -Dsurefire.failIfNoSpecifiedTests=false
```

**`-am` is required** (or an earlier `mvn install`): `organization` needs `shared` and `web-shared`, and its
tests use the `web-shared` **test-jar** (locale and DTO-guard test support). Without `-am` and without those
artifacts in `~/.m2`, `mvn -pl organization test` fails at dependency resolution.

**Integration tests are not run by `mvn test`.** Classes suffixed `*IT.java` (e.g.
`OrganizationRepositoryAdapterIT`, `OrganizationDatabaseConstraintsIT`) don't match surefire's
default include patterns, and no `maven-failsafe-plugin` is configured in the parent POM. They
must be run explicitly:

```bash
mvn -pl organization -am test -Dtest=OrganizationRepositoryAdapterIT -Dsurefire.failIfNoSpecifiedTests=false
```

These `*IT` tests use **Testcontainers** (`testcontainers-postgresql`) and use the
`OrganizationConfigurationIT` / `PostgresIntegrationTest` support classes — **Docker must be
running** to execute them.

## Architecture

**Modular monolith.** The root `pom.xml` (`packaging=pom`) aggregates one Maven module per
bounded context: `shared`, `web-shared`, `organization`, `identity`, `customer`, `supplier`, `catalog`,
`inventory`, `purchasing`, `sales`, `cash`, `notification`, `reporting`, `bootstrap`.

- **`shared`** is the shared kernel: `AggregateRoot`, `DomainEvent`, `BusinessRule` (+
  `BusinessRuleViolationException`), `DomainException`, `TenantId`. Every other module depends on it and
  builds its own aggregates/rules on top of these base types.
- **`web-shared`** (package `com.zim.shared.web`) is the shared web component: `ApiErrorResponse`,
  `FieldErrorMessages` (the pinned validation texts), the unscoped `GenericExceptionAdvice` and
  `WebSharedConfiguration`, which a module slice or `bootstrap` imports explicitly (no auto-configuration, so a
  forgotten import fails loudly). It depends on `shared` only, never on a business module. Its test-jar
  (`com.zim.shared.web.testing`: `DefaultLocaleExtension`, `LocaleScenario`, `RequestDtoConstraintGuard`,
  `AdviceAssertions`) is the shared test support. `maven-enforcer-plugin` `bannedDependencies` in each
  module pom forbids `organization` and `identity` from depending on each other.
- **`bootstrap`** is the composition root: a Spring Boot app (`QuineApplication`) that wires the
  business modules (today `organization` and `identity`) and holds **the security chain** (T7b-2, the only module with
  a Spring Security starter: `spring-boot-starter-security-oauth2-resource-server`). The tenant is the `tenantId` claim
  of a valid bearer token, read by `JwtTenantProvider` (`identity`), the only `CurrentTenantProvider` bean. There is
  no `smoke` profile, no stand-in provider and no header-based tenant any more: the app starts with no profile.
  `TenantSecurityConfiguration` declares one named catch-all chain (order constant `TENANT_CHAIN_ORDER`, no
  `securityMatcher`): stateless, CSRF off, no form login, no HTTP Basic, no logout, the qualified `tenantJwtDecoder`
  (HS256, audience `quine-tenant`, canonical-UUID `tenantId`, expiry required), one `ApiAuthenticationEntryPoint` and
  `ApiAccessDeniedHandler` writing the standard `ApiErrorResponse` (header exactly `WWW-Authenticate: Bearer
  realm="quine-erp"`, never any OAuth2 detail). **Every route is authenticated by default**, mapped or not (`/smoke/**`
  answers 401); the only public routes are in `PublicRoutes` (`POST /api/organizations`, `POST /api/auth/login`). A
  platform chain (T9b) will be a second chain at a higher precedence in another configuration.

  ```bash
  # No credential is committed. Export them first (random, local only; or put them in a git-ignored .env):
  export QUINE_DB_PASSWORD=$(openssl rand -hex 16)
  export QUINE_JWT_SECRET=$(openssl rand -base64 32)   # key of the TENANT token chain

  docker compose up -d --build   # Postgres + app (no profile) on 127.0.0.1:18080
  # or, with Postgres on localhost:15432 (same QUINE_DB_PASSWORD), no profile needed:
  java -jar bootstrap/target/bootstrap-1.0-SNAPSHOT.jar
  # add --quine.organization.tenant-activation.enabled=true for the activation route (see below)
  ```

  `QUINE_JWT_SECRET` is the key of the **tenant** chain (standard Base64, at least 32 decoded bytes): the application
  refuses to start without it, and the error never prints its value. `QUINE_DB_PASSWORD` has no default either.
  Compose publishes Postgres and the app on `127.0.0.1` only and stops with the name of any missing variable.

  **Tenant self-activation flag (transitional).** `POST /api/organizations/{id}/activation` is served by
  `TenantActivationController`, which exists only with `quine.organization.tenant-activation.enabled=true` (env
  `QUINE_ORGANIZATION_TENANT_ACTIVATION_ENABLED`); the default is `false` and the route is absent. With it on, anyone who
  registers can log in and activate their own organization, skipping the platform's validation (T7 PO-1). It is
  `true` only in `docker-compose.yml` and in the tests that exercise the route. **Keep it `false` in any environment
  reachable by anyone outside the team** (none may exist before T9c+T9d ship); T9d deletes the whole controller and the
  flag. `spring.jpa.open-in-view` is `false` (no Hibernate session held while BCrypt runs).

  The Postman collection in `docs/postman/` runs against it (register, log in, tokens in run-time variables).
- **`organization`** is the only fully implemented business module and is the reference to
  follow when building out the others (same package layout, same CQRS-ish handler pattern,
  same test layering).
- **`identity`** is implemented as far as T7b-2: the `User` aggregate (e-mail, BCrypt password hash, one owner
  per tenant), `CreateOwnerHandler`, its own `identity` schema, and **login and token issue**: `POST /api/auth/login`
  (`LoginHandler`, public) checks the credentials and returns `{accessToken, tokenType, expiresIn}`, an HS256 JWT
  (`sub` = user id, claim `tenantId`, `aud` = `quine-tenant`, no e-mail or password) signed by `NimbusJwtEncoder`.
  Failures are one `401 INVALID_CREDENTIALS` (unknown or malformed e-mail, wrong, short or over-72-byte password) with
  one BCrypt check on every path. Properties: `quine.security.tenant.jwt.secret` (from `QUINE_JWT_SECRET`) and
  `quine.security.tenant.jwt.ttl-minutes` (default 30, allowed 1 to 120). `identity` has only
  `spring-security-oauth2-jose` (never a `spring-boot-starter-security*`); it provides the `tenantJwtDecoder` (from the
  same key as the issuer) and `JwtTenantProvider`, and the chain in `bootstrap` honours the token (T7b-2). Registration creates the owner (T7c): `POST /api/organizations` takes `ownerEmail` and
  `ownerPassword`, and `OwnerRegistrar` (an `organization` port) is implemented in `bootstrap` over `CreateOwnerHandler`,
  in the registration transaction (all or nothing). See `docs/tickets/identity-T7.md`.
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
        └── exception            # the module's own ApiExceptionHandler (ApiErrorResponse is in web-shared)
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
- The one `CurrentTenantProvider` is `JwtTenantProvider` (`identity`): it reads only the `tenantId` claim of the
  authenticated token and fails closed (`TenantNotResolvedException`, 401), never a default tenant. The default of the
  application is deny: a request without a valid token never reaches a controller. Modules ship no provider of their own
  (their tests use a stub). No stand-in provider exists.
- **A new route is authenticated by default.** Only a route listed in `PublicRoutes` (`bootstrap`, method and path) is
  public; classifying a route as public means editing that list and the route-walking test (`RouteWalkingIT`).
- Tenant-scoped endpoints get their own controller; the public one (register) never injects the provider.

**Errors** (`docs/tickets/endpoints-T5.md`, shared contract)
- Step order: authentication (401, in the security chain, before the controller), request validation (400), then in the handler: ownership (404), value
  objects (422), business rules (409). Body: `ApiErrorResponse {code, message, timestamp}`.
- `message` is developer-facing English. Clients branch on `code` only; `Accept-Language` is ignored.
- A repeat of a state-changing action returns `<ENTITY>_ALREADY_<STATE>` (e.g.
  `ORGANIZATION_ALREADY_CLOSED`, `STORE_ALREADY_INACTIVE`, `STORE_IS_ALREADY_HEADQUARTERS`): target state
  reached. `..._ALREADY_EXISTS` is a real duplicate conflict. `..._CANNOT_BE_...` means refused from the
  current status. No endpoint is a silent no-op.
- Family `ORGANIZATION_<STATE>` (`ORGANIZATION_PENDING_VALIDATION`, `ORGANIZATION_SUSPENDED`): a tenant action
  refused because of the current state, never "target reached" (not in the "already" list). Closure only from `ACTIVE`.
- `ApiErrorResponse` and the generic handlers (validation, malformed body, 401, `BusinessRuleViolationException`,
  `DomainException`, `CONCURRENT_MODIFICATION`, `DATA_INTEGRITY_VIOLATION`) live once in `web-shared`
  (`docs/tickets/web-shared-T7b-0.md`), never copied. The generic advice is unscoped and `@Order(AdviceOrder.GENERIC)`.
- A module advice is scoped with `basePackageClasses`, annotated `@Order(AdviceOrder.MODULE)` (on the class),
  and handles only the exceptions its module owns, so each exception type has exactly one advice (the first
  advice with a matching handler wins, even over a more specific one later). Every slice or IT that imports a
  controller also imports `WebSharedConfiguration`, and `bootstrap` imports it once.

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

**Deferred:** ArchUnit rules (separate ticket T7b-0b). The shared validation-message helper and the shared web
component for the error contract are done: `web-shared` (T7b-0).

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

## Security

- Credentials (passwords, API keys, tokens, secrets, connection strings with embedded passwords, private
  keys) must never be written in files that will be committed. Use environment variables or untracked
  local files (git-ignored) instead. This applies to every session.