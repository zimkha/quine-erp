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
  `BusinessRuleViolationException`), `DomainException`. Every other module depends on it and
  builds its own aggregates/rules on top of these base types.
- **`bootstrap`** is the composition root intended to wire the business modules into one
  runnable Spring Boot application. It currently has no dependencies on the other modules and
  no Spring Boot parent/starter wired in yet — it's a placeholder, not yet runnable.
- **`organization`** is the only fully implemented business module and is the reference to
  follow when building out the others (same package layout, same CQRS-ish handler pattern,
  same test layering).
- The other business modules (`identity`, `customer`, `supplier`, `catalog`, `inventory`,
  `purchasing`, `sales`, `cash`, `notification`, `reporting`) are scaffolded (POM only, single
  placeholder `Main.java`) and not yet implemented.

### Per-module layering (hexagonal / DDD), e.g. `organization`

```
com.zim.organization
├── domain
│   ├── model        # aggregates (Organization, Store) extending shared AggregateRoot
│   ├── valueobject   # OrganizationId, StoreId, TenantId, CurrencyCode, ... (records)
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

Aggregates carry a `TenantId` value object (`com.zim.organization.domain.valueobject.TenantId`,
a `UUID` wrapper). Keep this in mind when adding new aggregates/tables in any module — tenant
scoping is a cross-cutting concern of the domain model, not bolted on at the persistence layer.

### Database

- **PostgreSQL** + **Flyway**, one migration path per module:
  `<module>/src/main/resources/db/migration/<module>/V*__*.sql` (e.g.
  `organization/src/main/resources/db/migration/organization/V1__create_organization_schema.sql`).
- Each module's Flyway config scopes itself to its own Postgres **schema** (see
  `organization/src/test/resources/application-test.yaml`: `flyway.schemas` /
  `default-schema: organization`, `hibernate.default_schema`) — modules do not share a schema.

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