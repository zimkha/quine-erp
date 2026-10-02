# T7: Identity: authenticated users and the real current tenant

- **Status:** **draft** by the BA against `main` at `a708069` (2026-10-02). **Waiting for product-owner answers (below) and then Architect validation.** Do not design or build until the blocking questions are answered.
- **Workflow:** BA → Architect → Developer → Lead Developer (see `CLAUDE.md`).
- **Sources:** the "T7" follow-ups in `tenant-scoping-T1-T4.md`, `endpoints-T5.md` and `cross-module-conventions-T6.md`.

## User story

As a tenant user, I want to authenticate and have every request act for my own organization, so that I can use the tenant endpoints (add store, change headquarters, deactivate store, activate, close) without anyone being able to act for a tenant that isn't theirs.

## Context: what exists today

- `shared` has the framework-free port `CurrentTenantProvider` and `TenantNotResolvedException`. Controllers call the provider once per request and pass the tenant in the command.
- Two stand-in providers live in `bootstrap`, and both are marked "removed in T7":
  - `FailClosedTenantProvider` (no profile): always throws, so every tenant endpoint answers 401.
  - `SmokeTenantProvider` (`smoke` profile): reads the tenant from the `X-Smoke-Tenant` header. Anyone can forge it, so it is for local runs only.
- Registration (`POST /api/organizations`) is anonymous. It **generates** the tenant (`TenantIdGenerator`) and returns `tenantId` in its response. Its command carries only organization and headquarters data: **no user, e-mail or password**.
- `OrganizationRegistered(eventId, organizationId, tenantId, headquartersId, occurredAt)` is published as a Spring application event, **synchronously and inside the command's transaction** (`SpringDomainEventPublisher`). Consumers choose `@TransactionalEventListener(AFTER_COMMIT)` or a plain `@EventListener` (an exception then rolls registration back).
- The `identity` module is an empty scaffold (a POM and a placeholder `Main.java`).
- Decisions already taken for T7 (from earlier tickets):
  - A malformed tenant claim becomes `TenantNotResolvedException`, never a guess.
  - 400 before 401 today (Spring's natural order). **Revisit** when a security filter authenticates before validation.
  - "Authenticated but no tenant" should become **403**. If the scheme isn't Bearer, update `WWW-Authenticate`.

## Proposed slicing (for the Architect to confirm)

T7 is too large for one change. Suggested order, each independently mergeable:

- **T7a: users and credentials in `identity`.** A `User` aggregate belonging to a tenant, a way to create the first one, and verification of a credential. No HTTP security yet.
- **T7b: authentication and the real `CurrentTenantProvider`.** A security filter that authenticates the request and a provider that returns the user's tenant, failing closed. Removes `FailClosedTenantProvider` and `SmokeTenantProvider`. Revisits 400-before-401 and the 403 case.
- **T7c: owner linking.** Consuming `OrganizationRegistered` to attach the first user to the new tenant, and changing registration so an owner exists.

## Scope (all slices)

- A user identity concept in `identity`, tenant-owned (`TenantId` from the shared kernel), following the `organization` layout (domain, application, infrastructure, presentation).
- Authenticating a request and resolving its tenant through `CurrentTenantProvider`, with the fail-closed behaviour unchanged for unauthenticated requests (401 `TENANT_NOT_RESOLVED`).
- Linking the first user to the tenant created at registration.
- Removing the two stand-in providers from `bootstrap`, and updating the Postman collection and the `smoke` setup accordingly.

## Out of scope

- Roles and permissions inside a tenant, and 403 for "authenticated but not allowed" (T8).
- A platform-admin identity and the platform path for activate, close, suspend and reinstate (T9).
- Postgres row-level security (T10).
- Password reset, e-mail verification, social login, MFA, inviting more users (separate tickets once the scheme is chosen).
- Changing `code` values or the `ApiErrorResponse` shape.

## Acceptance criteria (draft; some depend on the open decisions)

- **Authenticated request:** Given a valid credential of user U in tenant A, when U calls a tenant endpoint, then the command's tenant is A and the response is as before.
- **Unauthenticated or invalid:** Given no credential, an invalid or expired one, or a malformed tenant claim, then the response is 401 `TENANT_NOT_RESOLVED` with the `WWW-Authenticate` header, and no handler runs.
- **No tenant for a valid user:** Given an authenticated user with no resolvable tenant, then the response follows the decided status (403 per the earlier follow-up, to be confirmed).
- **Cross-tenant:** Given user U of tenant A and an organization of tenant B, then the response is the same 404 as a missing organization.
- **Caller input ignored:** The tenant is never taken from the path, query, body or any header (including `X-Smoke-Tenant`), once the smoke provider is removed.
- **Registration:** Given a valid registration, then an owner user exists for the new tenant and can authenticate. The exact request shape depends on decision 3.
- **Stand-ins gone:** `FailClosedTenantProvider` and `SmokeTenantProvider` no longer exist, the app still starts, and the Postman collection runs against real authentication.
- **Fail closed:** If identity cannot be reached or a token cannot be verified, the answer is 401, never a default tenant.

## Business rules

- No operation on tenant-owned data runs without a resolved tenant.
- The tenant is established by the platform from the authenticated identity, never from caller-controlled input.
- A user belongs to a tenant. Whether a user may belong to several is **open** (decision 5).

## Dependencies

- T1–T5, T5f, T6 and the fail-closed provider: merged.
- T8, T9, T10 depend on T7.

## Open questions

**Product owner (blocking)**

1. **Authentication scheme.** JWT bearer tokens issued by `identity` itself, sessions and cookies, or an external identity provider (OIDC)? This decides the dependencies, the `WWW-Authenticate` value and how tokens are verified.
2. **Who may register, and does registration create a user?** Today registration is anonymous and takes no credentials. Must the registration request carry the owner's e-mail and password (self-signup), or is the owner created by invitation or by a platform admin?
3. **First user.** Is the registering person the owner of the tenant, and is there exactly one owner at first?
4. **Registration failure.** If creating the owner fails, must registration fail as a whole (nothing created), or may an organization exist temporarily without an owner?
5. **One tenant per user?** The earlier tickets assumed 1:1 between tenant and organization (decision 1). May one person belong to several tenants, and if so how does a request say which one it acts for?

**Architect**

6. **How `identity` consumes `OrganizationRegistered`.** The event is synchronous and in-transaction. A plain `@EventListener` rolls registration back if identity fails (consistent with question 4's "all or nothing"). An `AFTER_COMMIT` listener can leave an organization without an owner and needs a retry or compensation. Which one, given a modular monolith with one database?
7. **Module boundaries.** Does `organization` stay unaware of users (events only), and does `identity` publish the `CurrentTenantProvider` implementation that `bootstrap` wires? Which module owns the security filter?
8. **Slicing.** Confirm T7a, T7b and T7c, or propose another split.
9. **400 before 401.** With a filter that authenticates first, requests with a bad body from an unauthenticated caller become 401. Confirm this is the intended order and list the ticket and test updates.
10. **Local runs.** What replaces the `smoke` profile for local Postman runs: a seeded test user, a dev-only token endpoint, or something else?
