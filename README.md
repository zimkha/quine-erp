# Quine ERP

A multi-tenant ERP built as a **Maven multi-module modular monolith**, with each module
following a **DDD / hexagonal architecture** (`domain` → `application` → `infrastructure` → `presentation`).

## Tech stack

- **Java 21** (enforced: `[21,22)`)
- **Spring Boot 4.1.0** (`spring-boot-dependencies` BOM)
- **PostgreSQL** + **Flyway** for persistence and migrations
- **JUnit 6** / **AssertJ** / **Testcontainers** for testing
- **Maven** ≥ 3.9.0 (enforced)

## Project structure

```
quine-erp/
├── pom.xml            # parent POM: shared properties, dependency & plugin management
├── shared/             # shared kernel
├── organization/       # bounded context modules ...
├── ...
└── bootstrap/          # composition root / runnable application
```

Each business module is laid out in hexagonal layers, e.g. `organization`:

```
com.zim.organization
├── domain           # aggregates, value objects, domain events, business rules
├── application       # commands, handlers, ports, use-case results
├── infrastructure    # JPA persistence, event publishing, configuration
└── presentation       # REST controllers, requests/responses
```

## Modules

| Module | Role |
|---|---|
| **shared** | Shared kernel. Base building blocks reused by every module: `AggregateRoot`, `DomainEvent`, `BusinessRule`, and common domain exceptions. |
| **organization** | Organization & store lifecycle management: registering an organization, adding/deactivating stores, changing headquarters, activating/closing an organization. Multi-tenant (`TenantId`) aware. The most complete module — reference implementation for the others. |
| **identity** | Identity & access management: users, authentication/authorization, tenant onboarding. |
| **customer** | Customer master data and relationship management. |
| **supplier** | Supplier master data and vendor management. |
| **catalog** | Product/item catalog: SKUs, pricing, categories. |
| **inventory** | Stock management: warehouses, stock levels, movements. |
| **purchasing** | Purchase orders and procurement workflow. |
| **sales** | Sales orders and the order-to-cash workflow. |
| **cash** | Cash/treasury management: payments, receipts, reconciliation. |
| **notification** | Cross-cutting notifications (email, SMS, in-app). |
| **reporting** | Cross-module reporting and analytics. |
| **bootstrap** | Composition root: the runnable Spring Boot application that wires the business modules together. |

> Only `shared` and `organization` are implemented so far; the remaining business modules
> are scaffolded (POM + module skeleton) and ready to be built out following the same
> hexagonal layout.

## Build

```bash
mvn clean install
```

Modules requiring a database (e.g. `organization`) use Testcontainers for integration
tests, so Docker must be running to execute the full test suite.
