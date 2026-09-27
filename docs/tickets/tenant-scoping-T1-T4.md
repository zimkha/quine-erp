# Tenant scoping: tickets T1–T4 (organization module)

- **Status:** written by the BA and validated by the Architect (checked against HEAD `b3c3176`). T1 and T3 are validated. T2 and T4 are validated with changes, which are already applied to the ticket text below. Product-owner decisions are still open.
- **Source:** the Architect's tenant-scoping design, which answers code-review finding #12.
- **Workflow:** BA → Architect → Developer → Lead Developer (see `CLAUDE.md`).

## Architect decisions

| # | Question | Decision |
|---|---|---|
| 1 | Move the scoped query from T3 to T2 | **Confirmed.** T2 also switches `update()` to the scoped query and deletes `findWithStoresById`. T3 depends on T2 for merge order only. |
| 2 | Where the fail-closed default provider lives | **Once, in `bootstrap`, not in any module.** Handlers get the tenant through commands, so only controllers (and so only web tests) need a provider, and tests supply a stub. The `bootstrap` default is deferred to the ticket that makes `bootstrap` a runnable Spring Boot app, and it is deleted once `identity` supplies the real provider. Per-module `@ConditionalOnMissingBean` defaults are rejected because they depend on registration order and cause duplicate beans. |
| 3 | Status code for an unresolved tenant | **401 with `WWW-Authenticate: Bearer realm="quine-erp"`.** Revisit in T7: once a security filter answers unauthenticated requests with 401 itself, "authenticated but no tenant" should become 403. |
| 4 | Exception hierarchy | **`TenantNotResolvedException extends RuntimeException`** (not `DomainException`), in `com.zim.shared.tenant`, with a `CODE` constant. Also scope `ApiExceptionHandler` with `@RestControllerAdvice(basePackageClasses = OrganizationController.class)`, so exception handlers from different modules can't take each other's exceptions. |
| 5 | Malformed tenant ids | **T1 stays a pure move; don't align now.** `TenantId.from(String)` has no production caller. The future `identity` adapter must turn a malformed claim into `TenantNotResolvedException`. `OrganizationId.from` behaves the same, so the two stay consistent. |
| 6 | Unused tenant lookups | **Remove `findByTenantId` and `existsByTenantId` in T2.** Neither has a production caller, and both build the unresolved 1:1 assumption (decision 1) into the domain port. |
| 7 | V3 on existing data | **One migration, one transaction is fine.** No deployed database exists because `bootstrap` isn't runnable. **Confirmed by the product owner (2026-09-27): no shared or staging database exists.** The backfill IT uses its own fresh database (see T3). |

**Rules to add to T6 (conventions for other modules):**
- Each module scopes its `@RestControllerAdvice` to its own package.
- Once production has large tables, apply migrations like V3 in stages:
  1. Add the column as nullable.
  2. Backfill in batches.
  3. Add the FK as `NOT VALID`, then run `VALIDATE CONSTRAINT`.
  4. Set `NOT NULL`, backed by a validated `CHECK (col IS NOT NULL)`.

## Findings from checking the design against the code

1. **Seven events reference `TenantId`, not six.** `OrganizationSuspended` also carries it. T1 must update all seven.
2. **The tenant-scoped query moves from T3 to T2.** T2 needs a tenant-scoped query, and `findWithStoresByIdAndTenantId` only needs `organizations.tenant_id`, which V1 already has. It does not need `stores.tenant_id`. The query and the "cross-tenant load returns empty" IT therefore belong to T2. T3 then depends on T2 only for merge order (both touch the adapter). *Architect to confirm.*
3. **Two constraint ITs would pass for the wrong reason after V3.** The raw `INSERT INTO organization.stores` statements in `OrganizationDatabaseConstraintsIT` (lines 82 and 139 at least) leave out `tenant_id` and only assert `isInstanceOf(Exception.class)`. After V3 they would fail on the NOT NULL rule, not on the constraint under test. T3 must fix them.
4. **The fail-closed default depends on bean order.** `@ConditionalOnMissingBean` in a regular `@Configuration` is only reliable in auto-configuration, so once several modules each declare a default, the real provider could collide with it. *Open question for the Architect (T4).*
5. **Registration must not consult the current tenant**, or `POST /api/organizations` would always return 401 while the fail-closed default is in place.
6. **No endpoint uses a tenant yet.** `OrganizationController` only exposes register, so the rule "tenant comes only from the provider" is written down in T4 but tested in T5.
7. **Suspend has no command or handler.** `Organization.suspend()` and `OrganizationSuspended` exist, but nothing calls them, so T2 has nothing to scope. Product-owner decision 2 covers suspend too.
8. **`findByTenantId` and `existsByTenantId` have no production callers**, only ITs. Both assume one organization per tenant (`Optional`), so product-owner decision 1 touches them.
9. **`OrganizationNotFoundException` puts the organization id in its message.** That is fine, because the caller supplied the id. T2 requires the missing-organization and wrong-tenant responses to be identical apart from the timestamp.

## Product-owner decisions (open)

1. Can one tenant own several organizations or legal entities, now or later?
2. Who activates, closes (and suspends) an organization: the tenant or the platform?
3. Who may register: anonymous self-signup or a platform admin?
4. Is it acceptable that registration reveals whether a legal name is already registered on the platform?

| Ticket | Affected by | Can go ahead regardless? |
|---|---|---|
| T1 | none | yes |
| T2 | decision 2 (activate/close), decision 1 (`findByTenantId`/`existsByTenantId`) | yes, because tenant-scoping activate and close is the more restrictive default |
| T3 | none | yes |
| T4 | decision 3 (registration stays outside tenant resolution) | yes |

---

## T1: Move `TenantId` to the shared kernel

**Technical story**
As the team building quine-erp, we want `TenantId` in the shared kernel (`com.zim.shared.domain.TenantId`). Then every business module (identity, catalog, sales, …) can tag its data with the same tenant key without depending on the `organization` module. Today `TenantId` lives in `organization.domain.valueobject`. Any other module that needs it would have to depend on `organization`, which breaks bounded-context independence.

**Scope**
- Create `com.zim.shared.domain.TenantId` with the same behaviour as today:
  - a record wrapping a `UUID`
  - null rejected with "Tenant id cannot be null"
  - `generate()`, `from(String)`, and `toString()` returning the UUID text
- Delete `organization/src/main/java/com/zim/organization/domain/valueobject/TenantId.java`.
- Update every reference:
  - `Organization`
  - the seven events: `OrganizationRegistered`, `OrganizationActivated`, `OrganizationSuspended`, `OrganizationClosed`, `StoreAdded`, `StoreDeactivated`, `HeadquartersChanged`
  - `TenantIdGenerator` (stays in `organization.application.port`), `RegisterOrganizationHandler`, `OrganizationRepository`, `OrganizationRepositoryAdapter`, `OrganizationPersistenceMapper`, `OrganizationConfiguration`
  - all tests that use `TenantId`
- `Organization`, `RegisterOrganizationHandler` and the mapper import `domain.valueobject.*`. They now need an explicit import from `shared`.

**Out of scope:** any behaviour change; `OrganizationId` (stays in organization); tenant scoping (T2–T4).

**Acceptance criteria**
- **Build:** Given the refactored codebase, when `mvn clean install` runs, then it succeeds and the class `com.zim.organization.domain.valueobject.TenantId` no longer exists.
- **Existing tests:** Given the existing organization tests (handler unit tests, `*IT`, `OrganizationControllerTest`), when they run, then they all pass with only their imports changed.
- **Null rejection:** Given a null UUID, when a `TenantId` is created, then it is rejected with "Tenant id cannot be null".
- **Parsing:** Given a valid UUID string, when `TenantId.from(value)` is called, then `value()` equals `UUID.fromString(value)` and `toString()` returns the same text.
- **Events:** Given a registered organization, when `OrganizationRegistered` is published, then its tenant id equals `organization.tenantId()`. Event payloads keep the same shape.
- **Framework-free kernel:** Given the `shared` POM, when it is inspected, then no Spring or JPA dependency has been added.

**Business rules:** none new. `TenantId` is the isolation key for all tenant-owned data in every module. `OrganizationId` stays the organization's identity only. They are distinct concepts even though they are 1:1 today.

**Dependencies:** none.

**Definition of done**
- **Unit (shared):** a new `TenantIdTest` that records the current behaviour:
  - null throws NPE
  - a malformed string throws `IllegalArgumentException`
  - a valid string round-trips
  - `generate()` returns distinct ids
  - `toString()` returns the UUID text

  Keep `generate()` as is: its only caller is `OrganizationRepositoryAdapterIT:232`, and removing it is not part of a pure move.
- **Unit (organization):** existing domain and handler tests pass with only their imports changed.
- **`*IT` with Testcontainers:** existing ITs pass. Run them explicitly, because surefire's default includes skip them.
- **`@WebMvcTest`:** `OrganizationControllerTest` passes unchanged.
- **Cleanup:** no references to the old package remain (grep).

**Open questions**
- Product decisions 1–4 don't affect this ticket, which can go ahead safely whatever the answers.
- ~~Align malformed-id handling?~~ **Resolved by the Architect (decision 5):** keep it as is.

---

## T2: Scope organization commands to the caller's tenant

**User story**
As a tenant, I want every change to my organization (activate, add store, change headquarters, deactivate store, close) to apply only to my own organization. Another tenant must never be able to change my data, or learn that it exists, by guessing an organization id.

**Scope**
- **Repository port:**
  - `OrganizationRepository.findById(TenantId, OrganizationId)` replaces `findById(OrganizationId)`, which is removed.
  - `findByTenantId` and `existsByTenantId` are **removed** (Architect decision 6) from `OrganizationRepository`, `OrganizationRepositoryAdapter`, `SpringDataOrganizationRepository` and `InMemoryOrganizationRepository`.
  - `existsByLegalName` stays unchanged. It is global on purpose, to keep legal names unique at registration.
- **Commands:** five commands get `TenantId tenantId` as their first field, null-checked with "Tenant id cannot be null":
  - `ActivateOrganizationCommand`, `AddStoreCommand`, `ChangeHeadquartersCommand`, `DeactivateStoreCommand`, `CloseOrganizationCommand`
  - The other fields stay raw `UUID`s. The tenant is typed because it comes from the platform (T4), not from the caller.
- **Handlers:** the five handlers load the organization with the scoped `findById`. When nothing is found they throw the existing `OrganizationNotFoundException(organizationId)`.
- **Adapter:**
  - `findById` uses a new Spring Data method, `findWithStoresByIdAndTenantId(UUID id, UUID tenantId)`, with `@EntityGraph(attributePaths = "stores")`.
  - `update()` switches from `findWithStoresById` to the same method, with `organization.tenantId()`.
  - `findWithStoresById` is deleted from `SpringDataOrganizationRepository`, so no unscoped aggregate load is left.
  - This method is moved here from T3 (finding 2, confirmed by the Architect).
- **Callers of the commands:** every place that builds the five commands is updated, not just the handler tests. That includes `OrganizationConfigurationIT`, which builds Activate, AddStore and ChangeHeadquarters.
- **Test repository:** `InMemoryOrganizationRepository.findById` only returns the organization when both the id and the tenant match.
- **Registration:** `RegisterOrganizationCommand` and its handler are unchanged, because registration creates the tenant.

**Out of scope:**
- REST endpoints for these commands (T5)
- where the tenant comes from (T4)
- `stores.tenant_id` (T3)
- suspend, because there is no command or handler
- a platform-admin path that bypasses tenant scoping
- roles and 403 responses

**Acceptance criteria.** For each of Activate, AddStore, ChangeHeadquarters, DeactivateStore and Close:
- **Same tenant:** Given an organization owned by tenant A, when tenant A sends the command for it, then it behaves exactly as it does today (same result, same event, same saved state).
- **Other tenant:** Given an organization owned by tenant A, when tenant B sends the command with A's organization id, then:
  - `OrganizationNotFoundException` is thrown with code `ORGANIZATION_NOT_FOUND`
  - A's organization is unchanged (status, stores, headquarters)
  - no domain event is published
- **Missing organization:** Given no organization with the given id, when any tenant sends the command, then the same `OrganizationNotFoundException` is thrown, with the same code and message pattern as in the wrong-tenant case.
- **Other tenant, rule-breaking state:** Given tenant A's organization is in a state that would break a business rule (for example CLOSED for AddStore, or PENDING_ACTIVATION for ChangeHeadquarters), when tenant B sends the command, then the result is `ORGANIZATION_NOT_FOUND` and **not** the business-rule violation. The other tenant's organization state must not be revealed.
- **Other tenant, real store id:** Given ChangeHeadquarters or DeactivateStore from tenant B, with A's organization id and a store id that exists in A's organization, then the result is `ORGANIZATION_NOT_FOUND` and A's store is not changed.
- **Null tenant:** Given a command built with a null tenant id, when it is created, then it is rejected with "Tenant id cannot be null".

Registration and persistence:
- **Round trip:** Given a newly registered organization, when its returned `tenantId` and `organizationId` are used in `ActivateOrganizationCommand`, then activation succeeds.
- **Adapter:** Given two organizations (tenants A and B) stored in Postgres:
  - when the adapter loads `(B, A's organization id)`, then the result is empty
  - when it loads `(A, A's organization id)`, then A's organization is returned with all its stores
- **HTTP mapping:** Given `ORGANIZATION_NOT_FOUND` thrown for a wrong tenant and for a missing organization, when each is mapped by `ApiExceptionHandler`, then both give 404 with bodies that differ only in the timestamp.

**Business rules**
- A tenant can only act on organizations it owns.
- To another tenant, an organization owned by someone else is indistinguishable from one that does not exist.
- Ownership is checked before any other business rule is evaluated.
- Legal-name uniqueness stays global across all tenants.

**Dependencies:** T1.

**Definition of done**
- **Handler unit tests:** each of the five handler tests gains:
  - an "other tenant → not found, no state change, no event" test
  - a "missing organization → not found" test, where one doesn't already exist
  - for AddStore, an "other tenant's organization in a rule-violating state → not found" test
  - the existing happy paths, now passing a tenant
- **Unit tests for commands:** null-tenant rejection for each of the five commands.
- **`*IT` with Testcontainers:**
  - `OrganizationRepositoryAdapterIT` covers the cross-tenant load (empty) and the same-tenant load (stores eagerly loaded).
  - The `update()` path still passes `OrganizationOptimisticLockingIT` and `OrganizationAggregatePersistenceIT`.
  - `OrganizationIntegrationTestSupport.reloadOrganization()` and `OrganizationConfigurationIT` are updated to the scoped `findById`.
  - The assertions that used the removed methods (`OrganizationRepositoryAdapterIT:164`, `OrganizationRepositoryPersistenceIT:99, :162`) are rewritten to use the scoped `findById`.
  - The `existsByTenantId` tests (`OrganizationRepositoryPersistenceIT:281, :297`) are deleted.
  - A new DB constraint IT covers `uk_organizations_tenant_id`, which no test covers today. It can go here or in T3 with the other constraint ITs.
- **`@WebMvcTest`:** no new endpoint. The existing 404 `ORGANIZATION_NOT_FOUND` test in `OrganizationControllerTest` stays green. Controller tests for the scoped commands come with T5.
- **Cleanup:** the unscoped `findById(OrganizationId)` is gone from the port, the adapter and the in-memory repository.

**Open questions**
- **Decision 2 (who activates or closes).** It does not block this ticket, because scoping Activate and Close to the owning tenant is the safer default.
  - If the platform turns out to own activation and closing, those actions need a separate platform path (a new ticket), and T5 must not expose them on tenant endpoints.
  - If a tenant can activate its own organization, the `PENDING_ACTIVATION` status is not a real gate. The product owner should confirm that is what they want.
- **Decision 1 (several organizations per tenant).** It does not affect the scoped `findById`, which is keyed by both tenant and organization. `findByTenantId` and `existsByTenantId` are removed in this ticket. If the answer is yes, `uk_organizations_tenant_id` changes in a later ticket, and a "GET my organization" read is designed then, returning one result or a list.
- **Decisions 3 and 4:** do not affect this ticket.
- ~~Confirm the query move~~ **Resolved (Architect decision 1).**

---

## T3: Store `tenant_id` on stores and enforce it in the database

**Technical story**
As the team, we want every store row to carry its tenant, and the database to guarantee it matches its organization's tenant. That way:
- tenant isolation holds even for writes that bypass the domain (raw SQL, future modules, reporting)
- store queries can filter by tenant directly

The domain `Store` does not change, because the tenant belongs to the `Organization` aggregate.

**Scope**
- **Migration:** add `organization/src/main/resources/db/migration/organization/V3__add_tenant_id_to_stores.sql`:
  1. Add `UNIQUE (id, tenant_id)` on `organization.organizations`. It is needed as the FK target.
  2. Add `stores.tenant_id UUID`, fill it from `organizations.tenant_id` through `organization_id`, then set it `NOT NULL`.
  3. Add `fk_stores_organization_tenant (organization_id, tenant_id) → organizations (id, tenant_id)` with `ON DELETE RESTRICT`, then drop `fk_stores_organization`.
  4. Add index `idx_stores_tenant_id_id (tenant_id, id)`.
- **Entity:** `StoreEntity` maps `tenant_id` (`nullable = false`, `updatable = false`), and `attachTo(organization)` sets it from `organization.getTenantId()`. It must pass `ddl-auto: validate`.
- **Domain:** `Store` and `Organization` are unchanged. The mapper does not read `tenant_id` back into the domain.
- **Tests:** update the raw store inserts in `OrganizationDatabaseConstraintsIT` (~82, ~139) to supply `tenant_id` (finding 3). Also change every `isInstanceOf(Exception.class)` assertion in that file (`:111, :182, :272, :346, :398, :439, :505`) to assert the constraint name.
- **Mapping notes:**
  - Hibernate's schema validation doesn't check foreign keys, so the composite FK needs no `@JoinColumns`. `tenant_id` is a plain column.
  - Keep store insertion going through `OrganizationEntity.addStore`, which calls `attachTo` on both the insert and the update path.

**Out of scope:**
- Postgres row-level security
- changing `uk_stores_organization_code` or the single-headquarters index
- any domain or API change
- tenant columns in other modules

**Acceptance criteria**
- **Backfill:** Given a database at V2 with organizations that have stores, when V3 is applied, then every store's `tenant_id` equals its organization's `tenant_id`, and none is null.
- **Empty data:** Given a database at V2 with an organization that has no stores, or no data at all, when V3 is applied, then the migration succeeds.
- **Registration:** Given an organization registered through the adapter, when it is saved, then every store row, including the headquarters, has `tenant_id` equal to the organization's tenant.
- **Adding a store:** Given an existing organization, when a store is added and the organization is saved through `update()`, then the new store row has the organization's `tenant_id`.
- **Mismatched insert:** Given organizations A (tenant A) and B (tenant B), when a store is inserted with A's `organization_id` and B's `tenant_id`, then the insert is rejected by `fk_stores_organization_tenant`.
- **Mismatched update:** Given an existing store of A, when its `tenant_id` is updated to B's tenant with raw SQL, then the update is rejected by `fk_stores_organization_tenant`.
- **Null tenant:** Given a store insert with a null `tenant_id`, then it is rejected.
- **Constraint tests:** Given the updated constraint ITs, when a duplicate store code or a second headquarters is inserted with the correct `tenant_id`, then:
  - the rejection comes from `uk_stores_organization_code` or `uk_stores_one_headquarters_per_organization` respectively, and not from the NOT NULL rule
  - the test asserts the constraint name, not just `Exception`
- **Delete:** Given an organization that has stores, when someone tries to delete it, then the delete is still refused (`ON DELETE RESTRICT` is kept).
- **Schema validation:** Given the Spring context with `ddl-auto: validate`, when it starts, then schema validation passes.

**Business rules**
- A store always belongs to the same tenant as its organization. The database enforces this, independently of the application.
- A store's tenant never changes after creation.

**Dependencies:** T2, for merge order on the adapter only. It is technically independent once the query moves to T2 (finding 2).

**Definition of done**
- **Domain/handler unit tests:** none new (no domain change). Existing ones stay green.
- **`*IT` with Testcontainers:**
  - a backfill IT, written as a plain JUnit `*IT` with no Spring. It must not use the shared `@DataJpaTest` context, which has already migrated to the latest version and is cached. The migrations also hard-code the `organization.` schema, so a separate schema won't work either. Steps:
    1. `CREATE DATABASE` a fresh database on the singleton Testcontainers container.
    2. Run `Flyway.configure().dataSource(url, user, pw).locations("classpath:db/migration/organization").schemas("organization").target("2").migrate()`.
    3. Seed through JDBC: two tenants, each with one organization and at least two stores.
    4. Migrate fully.
    5. Assert that every `stores.tenant_id` matches its organization's and that the column is `NOT NULL`.
  - a composite-FK rejection IT, for insert and for update
  - a NOT NULL IT
  - adapter ITs that assert `stores.tenant_id` on insert and on update
  - the corrected `OrganizationDatabaseConstraintsIT` inserts
  - all existing ITs green
- **`@WebMvcTest`:** not applicable (no API change). `OrganizationControllerTest` stays green.

**Open questions**
- Product decisions 1–4 don't affect this ticket, which can go ahead safely whatever the answers. The composite FK is valid whether a tenant owns one organization or many (decision 1).
- ~~Does V3 run against existing non-test data?~~ **Resolved (Architect decision 7):** no deployed database exists, so a single transaction is fine. **Confirmed by the product owner (2026-09-27):** no shared or staging database exists.

---

## T4: Resolve the current tenant through a fail-closed port

**Technical story**
As the team, we want a single framework-free port that tells the application which tenant the current request acts for. It must refuse to proceed when the tenant cannot be established. Tenant identity then always comes from the platform (later, the authenticated caller through the identity module) and never from caller-controlled input. Until identity exists, the system denies by default instead of guessing.

**Scope**
- **In `shared`, framework-free:**
  - `com.zim.shared.tenant.CurrentTenantProvider { TenantId currentTenant(); }`
  - `com.zim.shared.tenant.TenantNotResolvedException extends RuntimeException` (**not** `DomainException`), with the stable code `TENANT_NOT_RESOLVED` and a generic message that reveals nothing about the request.
- **No provider bean in organization's main code** (Architect decision 2). Handlers get the tenant through commands, so only controllers inject the provider, and tests supply a stub (`@MockitoBean` or a `@TestConfiguration`). The fail-closed default goes once in `bootstrap`, in the ticket that makes `bootstrap` a runnable Spring Boot app.
- **HTTP mapping:** in `ApiExceptionHandler`, `TenantNotResolvedException` → **401** with header `WWW-Authenticate: Bearer realm="quine-erp"` and body `ApiErrorResponse(code = "TENANT_NOT_RESOLVED", ...)`.
- **Scope the advice:** `@RestControllerAdvice(basePackageClasses = OrganizationController.class)`.
- **Registration:** `RegisterOrganizationHandler` and `POST /api/organizations` must not depend on `CurrentTenantProvider`.

**Out of scope:**
- the identity-backed provider
- authentication, roles and 403 responses
- exposing the scoped commands (T5), where the rule "tenant comes only from the provider" is applied to real endpoints
- a platform-admin path
- writing the convention into `CLAUDE.md` or an ADR (T6)

**Acceptance criteria**
- **No provider in organization:** Given organization's main code, when it is inspected, then it declares no `CurrentTenantProvider` bean, and `OrganizationConfiguration` / `OrganizationConfigurationIT` start without one.
- **401 response:** Given a request whose handling throws `TenantNotResolvedException`, then:
  - the response is **401**
  - it has the header `WWW-Authenticate: Bearer realm="quine-erp"`
  - the body has `code = TENANT_NOT_RESOLVED`, a `message` and a `timestamp`
  - the body contains no stack trace, header value or tenant id
- **Registration still works:** Given a throwing stub provider in the web context, when a valid `POST /api/organizations` is sent without any tenant information, then it returns 201. Registration does not consult the provider.
- **Caller input ignored:** Given a `POST /api/organizations` body that includes a `tenantId` field and a request carrying a tenant header such as `X-Tenant-Id`, when it is handled, then the tenant id in the response is the newly generated one. Request DTOs declare no `tenantId`.
- **Scoped advice:** Given `ApiExceptionHandler`, then it is declared with `basePackageClasses = OrganizationController.class`.

**Business rules**
- No operation on tenant-owned data runs without a resolved tenant (fail closed).
- The current tenant is established by the platform, never taken from the path, the body or caller-supplied headers.
- Registering an organization is the only organization operation that runs without a current tenant, because it creates one.

**Dependencies:** T1. It is independent of T2 and T3.

**Definition of done**
- **Unit:** `TenantNotResolvedException` exposes `CODE = "TENANT_NOT_RESOLVED"` and a generic message.
- **`@WebMvcTest`:**
  - Test the 401 mapping through a **test-only controller** that calls a throwing stub provider inside the MockMvc slice (no real endpoint uses the provider until T5). Assert the status, the `WWW-Authenticate` header and the body.
  - In `OrganizationControllerTest`, supply the provider with `@MockitoBean`, and add:
    - register succeeding with a throwing stub provider in the context
    - register ignoring a caller-supplied `tenantId` and tenant header
- **`*IT`:** existing ITs stay green without any `CurrentTenantProvider` bean (Architect decision 2).
- **Framework-free kernel:** `shared` still has no Spring dependency.

**Open questions**
- **Decision 3 (who may register).**
  - If registration is anonymous self-signup, this ticket is correct as written.
  - If only a platform admin may register, registration will need an authenticated *platform* identity. That is a different concept from a tenant and will be a separate port or ticket.
  - Either way, registration must not use `CurrentTenantProvider`, so this ticket can go ahead.
- **Decision 2:** it affects T5, not this ticket. A platform-initiated activate or close would have no current tenant and would need the platform path.
- **Decisions 1 and 4:** do not affect this ticket.
- ~~Bean order~~, ~~401 header~~, ~~exception hierarchy~~: **resolved by Architect decisions 2, 3 and 4.**
- **Follow-up (T7):** once a security filter exists, "authenticated but no tenant" should become 403. If `identity` picks a scheme other than Bearer, update the header then.

---

## Later tickets (not detailed here)

- **T5a–e:** expose the endpoints: add store, change headquarters, deactivate store, close. Activate waits for decision 2.
  - `POST /api/organizations/{id}/stores`
  - `PUT /api/organizations/{id}/headquarters`
  - `POST /api/organizations/{id}/stores/{storeId}/deactivation`
  - `POST /api/organizations/{id}/closure`
  - `POST /api/organizations/{id}/activation`
- **T6:** write the cross-module tenant conventions into `CLAUDE.md` or an ADR, including the rules on scoped advice and staged migrations above.
- **Make `bootstrap` runnable:** Spring Boot parent and starters, wiring for the modules, and the single fail-closed `CurrentTenantProvider` default (removed again in T7).
- **With identity:**
  - **T7:** the real tenant provider based on the authenticated user, and linking the owner user on `OrganizationRegistered`.
  - **T8:** roles and permissions inside a tenant (403).
  - **T9:** a platform-admin path for activate and close.
  - **T10:** optional Postgres row-level security as a second layer.
