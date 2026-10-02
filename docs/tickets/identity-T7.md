# T7: Identity: authenticated users and the real current tenant

- **Status:** drafted by the BA against `main` at `a708069` (2026-10-02). Product owner answered questions 1–5 on 2026-10-02. **Architect: validated with changes (2026-10-02, checked against `a708069`).** The changes are applied below. **Sequencing: hybrid (decided 2026-10-02 on the BA's recommendation): T7a and T7c now; T7b and T8 later, but before any real (non-local) deployment.** Small product-owner confirmations remain, listed at the end.
- **Workflow:** BA → Architect → Developer → Lead Developer (see `CLAUDE.md`).
- **Sources:** the "T7" follow-ups in `tenant-scoping-T1-T4.md`, `endpoints-T5.md` and `cross-module-conventions-T6.md`.

## User story

As a tenant user, I want to authenticate and have every request act for my own organization, so that I can use the tenant endpoints (add store, change headquarters, deactivate store, activate, close) without anyone being able to act for a tenant that isn't theirs.

## Product-owner decisions (2026-10-02)

| # | Question | Decision |
|---|---|---|
| 1 | Authentication scheme | **Our own e-mail and password.** `identity` stores and verifies the credentials. An external provider (Google, Microsoft, company login) is a possible later ticket. |
| 2 | Who may register | **Anyone: public self-signup, as today.** Registration therefore also collects the owner's e-mail and password. A platform-admin or invitation-only registration is out of scope. |
| 3 | First user | **Yes: the person who registers is the owner of the new tenant, and there is exactly one owner to begin with.** Adding more users later is a separate ticket. |
| 4 | Owner creation fails | **All or nothing:** the whole registration fails and nothing is created (no organization, no tenant, no user). |
| 5 | One tenant per user | **Yes: one user belongs to exactly one tenant.** A request does not say which tenant it acts for. Allowing several tenants per user later costs a data-model change. |

## Architect decisions

| # | Question | Decision |
|---|---|---|
| 6 | How the owner is created atomically | **A driven port `OwnerRegistrar` (`organization`, `application/port`, framework-free).** `RegisterOrganizationHandler` calls it after `save` and before it publishes `OrganizationRegistered`. `bootstrap` provides the adapter, which calls `identity`'s `CreateOwnerHandler`. A failure propagates as a plain exception and rolls everything back. **Not an `@EventListener`:** that hides the atomicity requirement behind an implicit contract (the `SpringDomainEventPublisher` javadoc). The event stays a notification. Update that javadoc to say events are not used for atomicity. |
| 7 | Module boundaries | `bootstrap` depends on `organization` and `identity`; both depend on `shared` only; **they never reference each other.** Only `bootstrap` knows both, so the `OwnerRegistrar` adapter lives there. `identity` owns the token verification and a `CurrentTenantProvider` implementation (reading the authenticated principal); `CurrentTenantProvider` stays in `shared`, which stays framework-free. The `SecurityFilterChain` bean (route rules) is a `@Configuration` in `bootstrap`, because it sees all routes: `POST /api/organizations` and `POST /api/auth/login` are public, everything else is authenticated. |
| 8 | Slicing | **T7a, then T7c, then T7b.** The owner must exist before a real login can be tested. See the slicing section. |
| 9 | 400 before 401 | **For tenant endpoints, 401 comes before 400** once the filter chain runs first (it runs before the `DispatcherServlet`). The public registration and login endpoints keep 400. The 401 header and body are produced today in `ApiExceptionHandler.handleTenantNotResolved`, but an exception thrown in a filter never reaches an `@RestControllerAdvice`: the authentication entry point must write the same `ApiErrorResponse` and header itself. |
| 10 | Local runs | **Seed through registration plus login.** No dev token endpoint, no seeded user in a migration, no profile-gated bypass. The `smoke` profile stays only for `SmokeController` (the activate shortcut), now using the real tenant. Postman: a "Register then Login" setup folder that stores `{{tokenA}}` and `{{tokenB}}`. |
| 11 | How login is carried | **A signed stateless JWT access token.** Spring Security `oauth2-resource-server` with a `NimbusJwtDecoder` and an HS256 symmetric key from the env var `QUINE_JWT_SECRET` (**the app fails to start if it is missing**), short TTL, no refresh in T7. The tenant is a `tenantId` claim, validated as a UUID, otherwise `TenantNotResolvedException`. Passwords use `BCryptPasswordEncoder` (or a `DelegatingPasswordEncoder` for upgrades). Login is `POST /api/auth/login` in `identity/presentation`, response `{accessToken, tokenType, expiresIn}`. Credentials live in the `identity` schema. Spring pieces: `spring-boot-starter-security`, `spring-boot-starter-oauth2-resource-server`, a `BearerTokenAuthenticationEntryPoint` customised to write the `ApiErrorResponse` body with `code: TENANT_NOT_RESOLVED`, and an `AccessDeniedHandler` for 403. **Why:** stateless, no session store, fits a multi-instance Postgres setup, and matches the existing `Bearer` header. Revocation and refresh are acknowledged limits (see Risks). |
| 12 | Validation of the new request fields | **`@Email` with the pinned text `must be a valid e-mail address`.** One new row in `validation-message-locale.md`, a branch in `FieldErrorMessages`, and an update to `RequestDtoConstraintGuardTest`. Password: `@NotBlank` plus `@Size(min=12, max=72)` (72 bytes is bcrypt's limit). E-mail is capped with `@Size`. The password never appears in any message, and the guard test asserts it. |
| 13 | Breaking registration request | **`POST /api/organizations` gains required `ownerEmail` and `ownerPassword`.** It ships in T7c with the Postman collection updated in the same PR, with no versioning or compatibility shim, because no shared or staging database exists (PO, 2026-09-27). *The Architect assumes there is no external client; the product owner confirms (below).* |

## Sequencing decision: hybrid (2026-10-02)

The BA compared building identity now, last, or split. **Decision: split.**

- **Now:** T7a (users, credentials, `identity` schema) and T7c (registration creates the owner). They fix the registration contract and the user-to-tenant link while only one module exists, which is the expensive part to change later.
- **Later, before any real deployment or demo, and before T8:** T7b (login, JWT, filter, real `CurrentTenantProvider`) and T8 (roles and permissions).
- **Until T7b ships, only local runs are possible.** The `smoke` profile (tenant from the forgeable `X-Smoke-Tenant` header) is the only way to call tenant endpoints, and the default provider answers 401.

**Rules while T7b is deferred** (they keep the deferral cheap, and apply to every new module):
1. A module gets its tenant only from `CurrentTenantProvider`, never from a header, path, query or body. No module reads `X-Smoke-Tenant`.
2. Every endpoint a module adds is classified as **tenant-scoped** or **public** in its ticket, so T8 can classify them later.
3. **Deferral guard (part of T7a):** the application must refuse to start if the `smoke` provider is active together with a non-local profile (for example `prod`), so the forgeable header can never run in a real environment. The Architect chooses the mechanism and the exact profile list.
4. T7b and T8 are not "nice to have": they are the gate before any real deployment.

## Context: what exists today

- `shared` has the framework-free port `CurrentTenantProvider` and `TenantNotResolvedException`. Controllers call the provider once per request and pass the tenant in the command.
- Two stand-in providers live in `bootstrap`, and both are marked "removed in T7":
  - `FailClosedTenantProvider` (no profile): always throws, so every tenant endpoint answers 401.
  - `SmokeTenantProvider` (`smoke` profile): reads the tenant from the `X-Smoke-Tenant` header. Anyone can forge it, so it is for local runs only.
- Registration (`POST /api/organizations`) is anonymous. It **generates** the tenant (`TenantIdGenerator`) and returns `tenantId` in its response. Its duplicate check (`existsByLegalName`) is platform-wide, which is legitimate because registration is public. Its command carries only organization and headquarters data: **no user, e-mail or password**.
- `OrganizationRegistered(eventId, organizationId, tenantId, headquartersId, occurredAt)` is published as a Spring application event, synchronously and inside the command's transaction (`SpringDomainEventPublisher`).
- The `identity` module is an empty scaffold (a POM and a placeholder `Main.java`).
- `bootstrap` depends only on `organization`, and `application.yaml` hardcodes one Flyway location and schema and `default_schema: organization`. `CLAUDE.md` requires one schema per module.
- Decisions already taken for T7 (from earlier tickets):
  - A malformed tenant claim becomes `TenantNotResolvedException`, never a guess.
  - **A valid token with a missing or malformed `tenantId` claim is 401 `TENANT_NOT_RESOLVED`.** 403 is reserved for T8 (authenticated but not allowed). This replaces the earlier "authenticated but no tenant should become 403" follow-up.

## Slicing (Architect-confirmed order; hybrid timing)

Each slice is independently mergeable. **T7a and T7c are built now. T7b is deferred** (see the sequencing decision).

### T7a: `identity` domain and persistence
- **Scope:**
  - `User` aggregate with `UserId`, `TenantId`, `Email` and `PasswordHash` value objects.
  - `EmailMustBeUniqueRule`, `CreateOwnerHandler`, and a `PasswordHasher` port with a BCrypt adapter.
  - The `identity` schema and Flyway migration, with its own `@Table(schema = "identity")` entities, repositories, and `@EntityScan` / `@EnableJpaRepositories` entries.
  - Splitting the Flyway and Hibernate configuration in `bootstrap` per module (locations and schemas as lists), and the `bootstrap` dependency on `identity`. `identity/pom.xml` gets its dependencies (`shared`, JPA, Flyway, Spring Security crypto).
  - E-mail normalisation: trim and lower-case before the uniqueness check, with a case-insensitive unique index.
  - The deferral guard (rule 3 above): the app fails to start with the smoke provider and a non-local profile.
- **Definition of done:** unit tests for the aggregate, the rule and the handler (no Spring); an `*IT` with Testcontainers for the repository and the unique-e-mail constraint; a test that the context refuses to start with `smoke` plus a non-local profile. Not wired into the registration flow yet.

### T7c: registration creates the owner
- **Scope:** the request and command change, the `OwnerRegistrar` port, the `bootstrap` adapter, and the wiring. New DTO validation (`@Email`, password size) with the pinned text and guard-test update.
- **Definition of done:**
  - unit test of the handler: when the port fails, no organization is saved;
  - `@WebMvcTest` for the new DTO validation messages and the guard test;
  - an `*IT` in `bootstrap` proving a duplicate e-mail rolls back the organization;
  - the Postman setup updated, including the existing "no `tenantId` in response" and "`tenantId` is generated" checks. The collection still uses the `smoke` header until T7b ships.
- **Note:** after T7c the owner exists with a hashed password but **cannot log in yet**, because login is T7b.

### T7b: authentication (deferred: before any real deployment, and before T8)
- **Scope:** `POST /api/auth/login`, JWT issue and verification, the filter chain, the real `CurrentTenantProvider`. Removes both stand-ins, `FailClosedTenantProviderTest`, `SmokeTenantProviderTest`, the `X-Smoke-Tenant` headers in Postman and the `smoke` reference in `docker-compose.yml`.
- **Definition of done:** unit tests for token issue and verify; `@WebMvcTest` for 401 with the `WWW-Authenticate` header, for the 401-before-400 order, and for 403; an `*IT` that logs in and then calls a tenant endpoint; a cross-tenant test returning the same 404; the Postman collection run end to end.

## Out of scope

- Roles and permissions inside a tenant, and 403 for "authenticated but not allowed" (T8).
- A platform-admin identity and the platform path for activate, close, suspend and reinstate (T9).
- Postgres row-level security (T10).
- Password reset, e-mail verification, social or company login, MFA, token refresh and revocation, rate limiting, inviting more users (separate tickets).
- Registration by invitation or by a platform admin (decision 2).
- A user belonging to several tenants (decision 5).
- Changing `code` values or the `ApiErrorResponse` shape.

## Acceptance criteria

The criteria about tokens, 401, 400 order, login and the removed stand-ins apply **when T7b ships**. The registration, owner, e-mail, password and deferral-guard criteria apply with T7a and T7c.

- **Authenticated request:** Given a valid token of user U in tenant A, when U calls a tenant endpoint, then the command's tenant is A and the response is as before.
- **Unauthenticated or invalid:** Given no token, an invalid or expired one, or a valid token whose `tenantId` claim is missing or malformed, then the response is 401 `TENANT_NOT_RESOLVED` with `WWW-Authenticate: Bearer realm="quine-erp"`, the standard `ApiErrorResponse` body, and no handler runs.
- **401 before 400:** On tenant endpoints, an unauthenticated request with a bad body answers 401. The public registration and login endpoints keep answering 400.
- **Cross-tenant:** Given user U of tenant A and an organization of tenant B, then the response is the same 404 as a missing organization, and the 404 bodies (`code`, `message`) are identical for a missing and a foreign organization.
- **Caller input ignored:** The tenant comes only from the token's `tenantId` claim, never from the path, query, body or any header (including `X-Smoke-Tenant`).
- **Registration stays public:** `POST /api/organizations` and `POST /api/auth/login` need no token. Registration does not return a token, a password or a hash.
- **Registration creates the owner:** Given valid registration data with the owner's e-mail and password, then the organization, its tenant and one owner user are created together, and, once T7b ships, the owner can log in and reach tenant endpoints for that tenant. (After T7c alone, the owner exists with a hashed password but cannot log in.)
- **All or nothing:** Given owner creation fails (a rejected credential, or an e-mail already taken), then registration fails and no organization, tenant or user exists afterwards.
- **E-mail rules:** E-mails are trimmed and lower-cased before the uniqueness check, which is case-insensitive and platform-wide. `@Email` has the pinned text `must be a valid e-mail address`.
- **Password rules:** The password is `@NotBlank` with `@Size(min=12, max=72)`. It is never returned, echoed in an error, logged, or present in any message, and responses never contain a hash.
- **Login failure:** An unknown e-mail and a wrong password both answer the same 401 `INVALID_CREDENTIALS` with the same message. The comparison does the same hashing work on both paths, to avoid revealing which e-mails exist.
- **Fail closed:** If a token cannot be verified, the answer is 401, never a default tenant. The app refuses to start without `QUINE_JWT_SECRET`.
- **Deferral guard (T7a):** With the `smoke` provider active and a non-local profile, the app refuses to start.
- **Stand-ins gone:** `FailClosedTenantProvider` and `SmokeTenantProvider` no longer exist, the app still starts, and the Postman collection runs against real authentication.

## Business rules

- No operation on tenant-owned data runs without a resolved tenant.
- The tenant is established by the platform from the authenticated identity, never from caller-controlled input.
- A user belongs to exactly one tenant (decision 5), and a tenant has exactly one owner at first (decision 3).
- Registration is all or nothing (decision 4).

## Files and docs that change or break

- `docker-compose.yml`: `SPRING_PROFILES_ACTIVE: smoke` and its comment, plus the new `QUINE_JWT_SECRET`.
- `docs/postman/quine-erp-organization.postman_collection.json`: about 20 requests carry `X-Smoke-Tenant`; the description says identity is not built; the register body gains the owner fields; the request that sends a `tenantId` in the body must keep it ignored; the "no `tenantId`" assertions need review. Also `quine-erp-local.postman_environment.json`.
- `bootstrap/src/main/resources/application.yaml`: per-module Flyway and Hibernate configuration.
- `CLAUDE.md`: the `bootstrap` description and the Conventions section (providers, 401 and 400 order).
- `SpringDomainEventPublisher` javadoc, and `validation-message-locale.md` (the new `@Email` row).
- `README.md`, if it mentions the smoke setup.

## Risks (accepted for T7)

- No token revocation or refresh: a stolen token works until it expires.
- No rate limiting or lockout on login (brute force).
- A single symmetric signing key for all tokens.

## Dependencies

- T1–T5, T5f, T6 and the fail-closed provider: merged.
- T8, T9, T10 depend on T7.

## Open questions

**Architect:** ~~6–13~~ all **resolved**, see the decisions table.

**Product owner (small confirmations; the ticket proceeds on the suggested answers)**
- **Real deployment or demo before all modules are ready?** If yes, T7b moves earlier. *(Hybrid assumes no.)*
- **Is it acceptable that only local runs are possible until T7b?** *(Hybrid assumes yes.)*
- **E-mail uniqueness:** platform-wide, because a user belongs to one tenant. *(Suggested: yes.)*
- **Password length:** the Architect proposes a minimum of 12 characters and a maximum of 72. *(Confirm.)*
- **External clients of the registration endpoint:** none, so the request can change without versioning. *(Confirm.)*
