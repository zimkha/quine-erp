# T7: Identity: authenticated users and the real current tenant

- **Status:** drafted by the BA against `main` at `a708069` (2026-10-02). **Product owner answered questions 1–5 on 2026-10-02** (recorded below). **Waiting for Architect validation.** The Architect questions that remain are listed at the end.
- **Workflow:** BA → Architect → Developer → Lead Developer (see `CLAUDE.md`).
- **Sources:** the "T7" follow-ups in `tenant-scoping-T1-T4.md`, `endpoints-T5.md` and `cross-module-conventions-T6.md`.

## User story

As a tenant user, I want to authenticate and have every request act for my own organization, so that I can use the tenant endpoints (add store, change headquarters, deactivate store, activate, close) without anyone being able to act for a tenant that isn't theirs.

## Product-owner decisions (2026-10-02)

| # | Question | Decision |
|---|---|---|
| 1 | Authentication scheme | **Our own e-mail and password.** `identity` stores and verifies the credentials. An external provider (Google, Microsoft, company login) is a possible later ticket. How the proof of login is carried per request (token or session) is now an Architect question. |
| 2 | Who may register | **Anyone: public self-signup, as today.** Registration therefore must also collect the owner's e-mail and password. A platform-admin or invitation-only registration is out of scope. |
| 3 | First user | **Yes: the person who registers is the owner of the new tenant, and there is exactly one owner to begin with.** Adding more users later is a separate ticket. |
| 4 | Owner creation fails | **All or nothing:** the whole registration fails and nothing is created (no organization, no tenant, no user). |
| 5 | One tenant per user | **Yes: one user belongs to exactly one tenant.** A request does not say which tenant it acts for. Allowing several tenants per user later is possible but costs a data-model change. |

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
- Registration collects the owner's e-mail and password and creates the owner in the same all-or-nothing operation (decisions 2, 3, 4). This **changes the `POST /api/organizations` request**: it gains owner credentials fields.
- Removing the two stand-in providers from `bootstrap`, and updating the Postman collection and the `smoke` setup accordingly.

## Out of scope

- Roles and permissions inside a tenant, and 403 for "authenticated but not allowed" (T8).
- A platform-admin identity and the platform path for activate, close, suspend and reinstate (T9).
- Postgres row-level security (T10).
- Password reset, e-mail verification, social or company login, MFA, inviting more users (separate tickets).
- Registration by invitation or by a platform admin (decision 2).
- A user belonging to several tenants (decision 5).
- Changing `code` values or the `ApiErrorResponse` shape.

## Acceptance criteria (draft; some depend on the open decisions)

- **Authenticated request:** Given a valid credential of user U in tenant A, when U calls a tenant endpoint, then the command's tenant is A and the response is as before.
- **Unauthenticated or invalid:** Given no credential, an invalid or expired one, or a malformed tenant claim, then the response is 401 `TENANT_NOT_RESOLVED` with the `WWW-Authenticate` header, and no handler runs.
- **No tenant for a valid user:** Given an authenticated user with no resolvable tenant, then the response follows the decided status (403 per the earlier follow-up, to be confirmed).
- **Cross-tenant:** Given user U of tenant A and an organization of tenant B, then the response is the same 404 as a missing organization.
- **Caller input ignored:** The tenant is never taken from the path, query, body or any header (including `X-Smoke-Tenant`), once the smoke provider is removed.
- **Registration creates the owner:** Given a valid registration with the owner's e-mail and password, then the organization, its tenant and one owner user are created together, and the owner can then authenticate and reach tenant endpoints for that tenant.
- **All or nothing:** Given owner creation fails (for example a rejected credential or an e-mail that is already taken), then registration fails, and no organization, tenant or user exists afterwards.
- **No secrets leak:** The password is never returned, echoed in an error or logged, and responses never contain a password hash.
- **Stand-ins gone:** `FailClosedTenantProvider` and `SmokeTenantProvider` no longer exist, the app still starts, and the Postman collection runs against real authentication.
- **Fail closed:** If identity cannot be reached or a token cannot be verified, the answer is 401, never a default tenant.

## Business rules

- No operation on tenant-owned data runs without a resolved tenant.
- The tenant is established by the platform from the authenticated identity, never from caller-controlled input.
- A user belongs to exactly one tenant (decision 5).
- A tenant has exactly one owner at first (decision 3).
- Registration is all or nothing (decision 4).

## Dependencies

- T1–T5, T5f, T6 and the fail-closed provider: merged.
- T8, T9, T10 depend on T7.

## Open questions

**Product owner:** ~~1–5~~ all **resolved 2026-10-02**, see the decisions table. New, smaller questions the answers raise:
- **E-mail uniqueness.** Since a user belongs to one tenant, must an e-mail be unique across the whole platform? (Suggested: yes.)
- **Password policy.** Minimum length or other rules? (Suggested: a minimum length only, to start.)

**Architect**

6. ~~How `identity` consumes `OrganizationRegistered`~~ **Requirement now fixed by decision 4: all or nothing.** The Architect chooses the mechanism that gives it: a plain `@EventListener` in the registration transaction, or `identity` called through a port by the registration handler. An `AFTER_COMMIT` listener is ruled out because it can leave an organization without an owner.
7. **Module boundaries.** Does `organization` stay unaware of users (events only), and does `identity` publish the `CurrentTenantProvider` implementation that `bootstrap` wires? Which module owns the security filter?
8. **Slicing.** Confirm T7a, T7b and T7c. With decisions 2–4, T7c is "registration collects credentials and creates the owner atomically"; confirm it may land after T7b, or whether registration must change first.
9. **400 before 401.** With a filter that authenticates first, requests with a bad body from an unauthenticated caller become 401. Confirm this is the intended order and list the ticket and test updates.
10. **Local runs.** What replaces the `smoke` profile for local Postman runs: a seeded test user, a dev-only token endpoint, or something else?
11. **How login is carried per request** (decision 1 leaves it open): signed token or server session, where credentials are stored (hashing algorithm), and where the login endpoint lives.
12. **Registration request validation (T6 convention).** The owner's e-mail needs a validation constraint, and `@Email` has no pinned text yet: a pinned text, a table row and a guard-test update are required (`validation-message-locale.md`). The password must never appear in any message.
13. **Existing API consumers.** Changing the registration request is a breaking change for the Postman collection and any client. Confirm how it is rolled out.
