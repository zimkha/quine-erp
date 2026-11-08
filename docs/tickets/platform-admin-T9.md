# T9: Platform administration: validate, suspend and reinstate organizations

- **Status:** drafted by the BA against `main` (2026-11-08). Product owner decisions 1-4 recorded on 2026-11-08 (table below). **Architect: validated with changes (2026-11-08, checked against the code). The changes are in "Architect validation" below and prevail over any earlier section that disagrees.** **Slice T9a is blocked on a product-owner decision (see T9a), but that decision no longer blocks T9b.** **Product-owner round 2 recorded on 2026-11-08 (section "Product-owner decisions, round 2"): it supersedes decision 3c, answers the old P2 to P6 and leaves T9a unchanged. New slices T9e to T9h. Precedence when sections disagree: round 2, then "Architect validation", then the older text. Architect validation of the round 2 additions: validated with changes (2026-11-08, checked against commit 408adc9), see "Architect validation, round 2", which prevails over the round 2 text where they differ.** **Product-owner round 3 recorded on 2026-11-08 ("Product-owner decisions, round 3"): PO-1 to PO-10 are all answered; only T9a stays open. T9g is unblocked.** Technical constraints in "Constraints to validate" are formulated as proposals for the Architect, not decisions.
- **Workflow:** BA → Architect → Developer → Lead Developer (see `CLAUDE.md`).
- **Sources:**
  - `identity-T7.md` (out of scope: "A platform-admin identity and the platform path for activate, close, suspend and reinstate (T9)"; T7b login and JWT)
  - `endpoints-T5.md` (T5e "tenant activates", the shared error contract, the order of steps)
  - `cross-module-conventions-T6.md` (tenant, error and wiring conventions)
  - `domain-activate-reinstate-split.md` (T5e-0: `activate()` from `PENDING_ACTIVATION` only, `reinstate()` for `SUSPENDED`)
  - code read: `TenantOrganizationController`, `OrganizationController`, `ActivateOrganizationHandler` / `ActivateOrganizationCommand`, `OrganizationRepository`, `Organization.activate / suspend / reinstate`, the `OrganizationMustBeActiveTo...Rule` classes, `bootstrap` `SmokeController`.

## User story

As a platform administrator, I want to review newly registered organizations and validate (activate), suspend or reinstate them, so that only organizations the platform has accepted can trade, and a misbehaving organization can be stopped without the tenant's involvement.

## Product-owner decisions (2026-11-08)

| # | Question | Decision |
|---|---|---|
| 1 | Who activates an organization | **Activation is a validation by the platform, no longer an action of the customer.** Registered organizations stay `PENDING_ACTIVATION` until the platform validates them. This **supersedes** the 2026-10-01 decision of `endpoints-T5.md` (T5e: "the tenant activates"). |
| 2 | Edits after registration | **Deferred, out of scope:** renaming the organization or a store, changing the legal name, changing the currency, reactivating a store. Listed under "Out of scope". |
| 3 | The blocking point | A `PENDING_ACTIVATION` organization can neither add a store, nor change headquarters, nor deactivate a store (the `OrganizationMustBeActiveTo...Rule` classes), so a new customer can do nothing before validation. **Lifted by this recommendation, recorded as a decision:** (a) the tenant endpoint `POST /api/organizations/{id}/activation` is **removed in T9** (not earlier, so the module is never left without any activation path), and its tenant-side handler and command are replaced by the platform version; (b) until T9, the shortcut `/smoke/organizations/{id}/activate` is the only way to activate locally, restricted to local profiles by the T7a startup guard; (c) the `PENDING_ACTIVATION` state must be explicit to the customer: the tenant endpoints refused in `PENDING_ACTIVATION` **keep their existing 409 codes, no contract change**. **Superseded by PO 2026-11-08 (round 2), P6: they now answer a dedicated code, see "Dedicated refusal codes".** |
| 4 | How the first platform administrator is created | **Not decided in T9.** Isolated in sub-ticket **T9a**, marked "to be decided by the PO before implementation". Self-registration is excluded (anyone could validate themselves). |

## Current state

- Registration (`POST /api/organizations`, public) creates the organization in `PENDING_ACTIVATION` and generates the tenant.
- `Organization` already has `activate()` (from `PENDING_ACTIVATION` only), `suspend()` (from `ACTIVE` only) and `reinstate()` (from `SUSPENDED` only), with rules, codes and events (`OrganizationActivated`, `OrganizationSuspended`, `OrganizationReinstated`).
- **Only activation has a handler and an endpoint**, and it is a tenant endpoint: `TenantOrganizationController` `POST /api/organizations/{id}/activation` → `ActivateOrganizationCommand(tenantId, organizationId)` → `ActivateOrganizationHandler`, which loads with `OrganizationRepository.findById(tenantId, id)`. There is no suspend or reinstate handler, command or endpoint.
- `OrganizationRepository.findById` always filters by tenant, and another tenant's organization is a 404 (T1-T4). There is no read without a tenant and no listing.
- `SmokeController` (`@Profile("smoke")`, `/smoke/organizations/{id}/activate`) calls the same handler with the tenant from `CurrentTenantProvider`. It is how Postman and five `*EndToEndIT` seeding paths get an `ACTIVE` organization today.
- Domain code gap: `OrganizationMustBeActiveToBeSuspendedRule` answers `ORGANIZATION_CANNOT_BE_SUSPENDED` for every non-`ACTIVE` status, including an already-suspended one. There is no `ORGANIZATION_ALREADY_SUSPENDED`, which the repeat convention (`<ENTITY>_ALREADY_<STATE>`) would call for. See question A5.
- Identity: T7a done (tenant users, owner). Login, JWT and the security chain are T7b (deferred). There is no identity that belongs to no tenant.
- Tenant close (T5d) stays a tenant action and is **not** changed by this ticket.

## Constraints to validate (proposals, the Architect decides)

1. **Second kind of identity: the platform administrator.** Belongs to no tenant. Its routes live in a separate zone `/api/platform/...` (all routes start with `/api`). A platform controller **never** injects or calls `CurrentTenantProvider`. A distinct security entry point, for example a dedicated claim or role in the T7b JWT (and no `tenantId` claim). A platform token on a tenant route is already a 401 `TENANT_NOT_RESOLVED` under the T7 rule (missing `tenantId` claim).
2. **Cross-tenant read, a deliberate exception.** The platform needs a read without a tenant (for example `findById(organizationId)`). This is a **voluntary exception** to "another tenant's organization is a 404", reserved to the platform context. Proposal: keep it off the tenant-facing port path so a tenant handler cannot call it by accident (separate port or an explicitly named method, to be decided).
3. **Commands.** The platform activation command carries no `tenantId` supplied by the caller (`ActivateOrganizationCommand(tenantId, id)` becomes id-only for the platform); the tenant is read from the loaded aggregate (which also feeds the event). The platform suspend and reinstate commands follow the same shape.
4. **Listing.** A listing is needed to find what to validate. See the contract below. It needs a read-side port; its shape is the Architect's.
5. **Future RLS (T10).** A global read has to bypass tenant row-level security cleanly (a distinct database role or an explicit platform context). T9 must not make that harder: the cross-tenant read lives in one place, in the platform context only.
6. **Errors.** Platform errors reuse `ApiErrorResponse {code, message, timestamp}` and the code conventions of T5/T6. New codes for 401/403 are proposed below.

## Slicing

Order: **T9a (decision) → T9b → T9a (implementation) → T9c → T9d.**

### T9a: Bootstrap of the first platform administrator — TO BE DECIDED BY THE PO BEFORE IMPLEMENTATION

**Question.** Platform admins cannot register themselves (otherwise anyone could validate their own organization). So how does the first one come to exist? Once one exists, adding others (by an existing admin) is a separate ticket.

**Options and security trade-offs** (no credential value is ever written in a file; any secret is read from an env var such as `QUINE_PLATFORM_ADMIN_EMAIL` / `QUINE_PLATFORM_ADMIN_PASSWORD`, to be named by the Architect):

| Option | Idea | For | Against / risk |
|---|---|---|---|
| 1. Env vars at startup | On start, if no platform admin exists and the env vars are set, create one (password hashed with the same `PasswordHasher`). | Simple, 12-factor, no secret in the repo, works in docker compose and CI. | The initial password sits in the process environment and deployment tooling (visible to anyone who can read env or orchestration config); must be rotated after first login; needs a forced change or a documented rotation; create-if-absent logic must be idempotent and race-safe with several instances. |
| 2. Flyway migration | A migration inserts the first admin. | Nothing to run by hand. | **Rejected on security grounds in the BA's view:** a migration is committed, so it would hold either a credential (forbidden by `CLAUDE.md`) or a placeholder that must be edited per environment; the data is in migration history forever; every environment gets the same admin. Listed for completeness. |
| 3. Operations tool (one-shot CLI command or Spring runner or admin script) | An operator runs a command that prompts or reads the secret and creates the admin, then it can be disabled. | The secret is never stored in config; auditable one-off action; closest to least privilege. | More to build and document; needs controlled access to the production environment; harder in local/CI (script needed). |
| 4. Hybrid | Option 1 for `local/dev/docker` profiles, option 3 for real environments. | Easy local runs (and Postman), strict in production. | Two mechanisms to maintain and test; risk that the local path leaks into production unless guarded like the T7a startup guard. |

**Questions for the PO:** which option? Is a forced password change at first login required? Is the first admin allowed to exist only in non-local environments through an operator action? Should the creation be audited?

**Why it is a separate slice.** The answer changes only how the first row gets created, not the platform routes or the activation logic, so T9b and T9c can be designed against any option. It cannot be guessed: it is a security decision.

**Scope when decided:** the chosen creation mechanism, the guard that keeps any local-only shortcut out of real environments, the Postman/compose setup for local runs.

**Dependencies:** the decision is a gate to start T9b; the implementation needs the platform-admin aggregate and storage from T9b. T9c needs T9a implemented to be usable end to end.

### T9b: Platform administrator identity and platform routes

- **Scope:**
  - The platform administrator (e-mail, password hash, no tenant), its storage and login, reusing the T7 credential rules (BCrypt hash, normalised e-mail, same failure answer for unknown e-mail and wrong password). Which module and schema hold it is the Architect's call (see question A1).
  - A JWT that carries the platform identity and **no** `tenantId` claim (T7b mechanism).
  - The `SecurityFilterChain` rule: `/api/platform/**` requires a platform identity; tenant routes keep requiring a tenant identity.
  - The 401 and 403 answers for the platform zone (below), written as `ApiErrorResponse` by the authentication entry point and the access-denied handler.
  - A first, minimal platform route to prove the chain (for example the listing, if T9c is not yet merged, or a test-only route).
- **Out of scope here:** creating the first admin (T9a), the organization actions (T9c).
- **Definition of done:** unit tests for the aggregate and login; `@WebMvcTest` for 401, 403 and the step order; an `*IT` where a platform admin logs in and reaches a platform route; a test that a tenant token gets 403 on `/api/platform/**` and a platform token gets 401 `TENANT_NOT_RESOLVED` on a tenant route; a test that no platform controller references `CurrentTenantProvider`.
- **Dependencies:** **T7b** (login, JWT, filter chain) is required, and the T9a decision.

### T9c: Activate, suspend and reinstate organizations, and the list of organizations to validate

- **Scope:**
  - `GET /api/platform/organizations?status=PENDING_ACTIVATION` (listing).
  - `POST /api/platform/organizations/{id}/activation`, `POST .../suspension`, `POST .../reinstatement`.
  - New platform commands and handlers (activation without caller-supplied tenant; suspend and reinstate are new handlers for the existing domain methods), and the cross-tenant read.
  - Domain: a distinct code for a repeated suspension (question A5).
- **Mergeability.** The old tenant handler and endpoint stay until T9d so the module is never without an activation path (decision 3a). T9c therefore **adds** the platform versions; the replacement of the tenant versions completes in T9d. The Architect confirms naming so that the final names are not ugly (see question A2).
- **Definition of done:** handler unit tests for each action and for "the tenant comes from the aggregate"; `@WebMvcTest` for the contract; an `*IT` register → list → activate → tenant adds a store, over HTTP; suspend and reinstate round trips; a missing organization is 404; event published with the aggregate's tenant.
- **Dependencies:** T9b, T9a (implemented).

### T9d: Remove the tenant activation endpoint; clean-up

- **Scope:**
  - Remove `POST /api/organizations/{id}/activation`, the activation method of `TenantOrganizationController`, and the tenant-side `ActivateOrganizationCommand` / handler / response if no longer used (the platform version replaces them).
  - Migrate everything that seeds an `ACTIVE` organization through the old handler: `SmokeController`, the five `*EndToEndIT` classes (`AddStore`, `ChangeHeadquarters`, `DeactivateStore`, `CloseOrganization`, `ActivateOrganization`), `OrganizationConfigurationIT`, `TenantScopedCommandTest`, `TenantOrganizationControllerTest`.
  - Decide the fate of the `/smoke/organizations/{id}/activate` shortcut (remove it, or keep it as a local-only call to the platform handler). Proposal: remove it once Postman can activate through the platform route.
  - Postman: replace the activation step by the platform flow (platform login, list, activate) in the setup folder, and drop the old request.
  - Documentation updates (below).
- **Definition of done:** the old route no longer exists; the app starts; all tests pass; Postman collection runs end to end; no reference to the removed endpoint remains in `CLAUDE.md` or the tickets (T5e is marked superseded).
- **Dependencies:** T9c. **T9d must ship in the same release as T9c and before any real deployment**, otherwise tenants could still activate themselves.

## Contract (proposal, to validate)

### Step order for `/api/platform/**`

1. Authentication (401), 2. authorization of the platform role (403), 3. request validation (400), then in the handler: 4. existence (404), 5. value objects (422), 6. business rules (409). There is no tenant-resolution step and no "another tenant" case: a platform admin sees every tenant, so 404 means the organization really does not exist.

This differs from the tenant zone only in that the filter chain decides 401 and 403 before 400 (consistent with T7 decision 9).

### Errors specific to the platform zone

| Status | Code | When |
|---|---|---|
| 401 | `AUTHENTICATION_REQUIRED` (new, proposed) | No token, invalid or expired token. Header `WWW-Authenticate: Bearer realm="quine-erp"`. The handler never runs. `TENANT_NOT_RESOLVED` is not reused: there is no tenant in this zone. |
| 403 | `PLATFORM_ACCESS_DENIED` (new, proposed) | Valid token that is not a platform administrator, for example a tenant user calling `/api/platform/...`. No handler runs. |

Everything else reuses the shared table: 400 `VALIDATION_FAILED`, 404 `ORGANIZATION_NOT_FOUND`, 409 rule codes, 409 `CONCURRENT_MODIFICATION`, 409 `DATA_INTEGRITY_VIOLATION`, 422 value-object codes.

### `GET /api/platform/organizations`

- Query: `status` (optional, one of `PENDING_ACTIVATION`, `ACTIVE`, `SUSPENDED`, `CLOSED`), `page` (zero-based, default 0), `size` (default 20, maximum 100).
- Order: oldest registration first (`createdAt` ascending, then id), so the review queue is stable and first-come-first-served.
- Success **200**: `{items: [{organizationId, tenantId, organizationName, legalName, currencyCode, status, createdAt}], page, size, totalElements}`. No stores, no owner data (see question P4). An empty result is 200 with an empty `items`.
- Errors: 400 `VALIDATION_FAILED` for an unknown `status`, a non-numeric or out-of-range `page` / `size` (fixed messages, raw input never echoed). Note for the Architect: the existing malformed-parameter message is specific to UUIDs, and `@Min` / `@Max` have no pinned text (T6 rules on request parameters): either pin texts or validate explicitly.

### Actions

| Endpoint | Success (200) | Errors (409 unless stated) |
|---|---|---|
| `POST /api/platform/organizations/{id}/activation` | `{organizationId, status:"ACTIVE", activatedAt}` | `ORGANIZATION_ALREADY_ACTIVE` (repeat, target reached); `ORGANIZATION_CANNOT_BE_ACTIVATED` (`SUSPENDED` or `CLOSED`) |
| `POST /api/platform/organizations/{id}/suspension` | `{organizationId, status:"SUSPENDED", suspendedAt}` | `ORGANIZATION_ALREADY_SUSPENDED` (repeat, **new code**, question A5); `ORGANIZATION_CANNOT_BE_SUSPENDED` (`PENDING_ACTIVATION` or `CLOSED`) |
| `POST /api/platform/organizations/{id}/reinstatement` | `{organizationId, status:"ACTIVE", reinstatedAt}` | `ORGANIZATION_ALREADY_ACTIVE` (repeat, target reached); `ORGANIZATION_CANNOT_BE_REINSTATED` (`PENDING_ACTIVATION` or `CLOSED`) |

No request body (any body sent is ignored). A malformed `{id}` gives 400. 404 `ORGANIZATION_NOT_FOUND` when no organization has that id. No silent no-op. Whether the responses echo `tenantId` is question P4.

## Acceptance criteria

- **Review queue:** Given organizations A (`PENDING_ACTIVATION`), B (`ACTIVE`) in different tenants, when a platform admin calls the listing with `status=PENDING_ACTIVATION`, then only A is returned, oldest first, with paging fields.
- **Pagination:** `size` above 100, `size` of 0, a negative `page` or an unknown `status` is 400 `VALIDATION_FAILED`. A page beyond the end is 200 with empty `items`.
- **Validate (activate):** Given a `PENDING_ACTIVATION` organization, when the platform admin activates it, then the response is 200 `ACTIVE`, `OrganizationActivated` is published with the organization's own tenant, and the tenant's add-store call now succeeds.
- **Activate repeat and wrong status:** an `ACTIVE` organization gives 409 `ORGANIZATION_ALREADY_ACTIVE`; a `SUSPENDED` or `CLOSED` one gives 409 `ORGANIZATION_CANNOT_BE_ACTIVATED` and the status is unchanged.
- **Suspend:** an `ACTIVE` organization becomes `SUSPENDED` (200); then the tenant's add store, change headquarters and deactivate store answer their existing 409 "must be active" codes; closing is still allowed (tenant close from `SUSPENDED`, T5d). A repeat gives 409 `ORGANIZATION_ALREADY_SUSPENDED`; `PENDING_ACTIVATION` or `CLOSED` gives 409 `ORGANIZATION_CANNOT_BE_SUSPENDED`.
- **Reinstate:** a `SUSPENDED` organization becomes `ACTIVE` (200). `ACTIVE` gives 409 `ORGANIZATION_ALREADY_ACTIVE`; `PENDING_ACTIVATION` or `CLOSED` gives 409 `ORGANIZATION_CANNOT_BE_REINSTATED`.
- **Missing organization:** 404 `ORGANIZATION_NOT_FOUND` on all three actions.
- **Cross-tenant read is platform only:** Given a tenant user of tenant A, a tenant endpoint on tenant B's organization is still the same 404 as a missing one. Only `/api/platform/**` reads across tenants.
- **No authentication:** no token, or an invalid or expired one, on any platform route gives 401 `AUTHENTICATION_REQUIRED` with `WWW-Authenticate`, the standard body, and no handler runs.
- **Tenant user on platform route:** a valid tenant token gives 403 `PLATFORM_ACCESS_DENIED`, no handler runs, and no information about the organization is revealed.
- **Platform token on tenant route:** a platform token on a tenant endpoint gives 401 `TENANT_NOT_RESOLVED` (no `tenantId` claim, T7).
- **Order:** an unauthenticated request with a bad body or bad query on a platform route answers 401; an authenticated tenant user with a bad body answers 403, not 400.
- **No tenant provider in the platform zone:** platform controllers do not use `CurrentTenantProvider` (guard test).
- **Tenant endpoint retired (T9d):** `POST /api/organizations/{id}/activation` no longer exists; a call gets the framework's not-found answer, with no replacement route and no compatibility shim.
- **Customer sees the pending state (decision 3c):** the tenant endpoints refused in `PENDING_ACTIVATION` keep exactly their existing 409 codes and contract.
- **Concurrency:** an optimistic-lock conflict gives 409 `CONCURRENT_MODIFICATION`.
- **No credential in the repo:** no admin identifier or secret appears in any committed file; local setup reads env vars (T9a).

## Business rules

- An organization starts `PENDING_ACTIVATION` and only the platform can activate it.
- Only a `PENDING_ACTIVATION` organization can be activated; only an `ACTIVE` one can be suspended; only a `SUSPENDED` one can be reinstated (existing domain rules, unchanged).
- A closed organization can never be activated, suspended or reinstated.
- A platform administrator belongs to no tenant and can act on any organization; a tenant user can never act in the platform zone.
- Platform administrators cannot self-register.
- Platform actions are visible only through platform routes; the tenant isolation rule (another tenant's aggregate = 404) is unchanged for tenant routes.
- The tenant of an event comes from the aggregate, never from the caller.
- A repeat of a state-changing action returns its `..._ALREADY_...` 409; `..._CANNOT_BE_...` is a refusal from the current status; nothing is a silent no-op.

## Out of scope

- The creation mechanism of the first admin (T9a), adding further platform admins, password reset, MFA, platform admin roles/permissions (T8 covers tenant roles; platform roles are a later ticket).
- Rejecting or refusing a registration (a "reject" outcome distinct from "not yet validated"); see question P2.
- Edits after registration, decision 2: rename organization or store, legal name, currency, reactivating a store.
- Platform close of an organization (the tenant keeps closing, T5d).
- Reading one organization's detail or its stores from the platform; searching beyond the status filter; listing tenants' users.
- Notifications to the tenant on validation or suspension.
- Postgres row-level security (T10), roles inside a tenant (T8).
- Effect of suspension on login or on other modules.
- Mapping 405 and 415 into `ApiErrorResponse`.

## Files and docs that change or break

- `organization/.../presentation/rest/TenantOrganizationController.java` (activation removed, T9d), a new platform controller (in the module the Architect chooses), new platform requests/responses.
- `organization/.../application/command/ActivateOrganizationCommand.java`, `application/handler/ActivateOrganizationHandler.java`, `application/result/ActivateOrganizationResult.java`, `presentation/rest/response/ActivateOrganizationResponse.java`, and the new suspend and reinstate command/handler/result classes; `OrganizationConfiguration` bean wiring.
- `OrganizationRepository` and its adapter (cross-tenant read, listing); possibly a new read port.
- `OrganizationMustBeActiveToBeSuspendedRule` (a repeat code, question A5) and its tests.
- `ApiExceptionHandler` (parameter-type message beyond UUID for `status`, `page`, `size`).
- `bootstrap`: `SmokeController`, `QuineApplication` wiring, the security chain, `docker-compose.yml` (new env var references, no values).
- Tests: `ActivateOrganizationHandlerTest`, `TenantOrganizationControllerTest`, `TenantScopedCommandTest`, `OrganizationConfigurationIT`, and the five `*EndToEndIT` classes that seed through the old activation.
- `docs/postman/quine-erp-organization.postman_collection.json` (9 references to activation/smoke) and `quine-erp-local.postman_environment.json` (platform login variables, no values committed).
- `CLAUDE.md`: the `bootstrap` description (smoke shortcut), the Conventions section (second identity kind, platform zone, the tenant exception).
- `docs/tickets/endpoints-T5.md` (T5e marked superseded by T9 / PO 2026-11-08, readiness table), `identity-T7.md` (T9 pointer), `tenant-scoping-T1-T4.md` and `domain-activate-reinstate-split.md` (activation references), `validation-message-locale.md` if new constraints get pinned texts.

## Risks

- **Platform admin is a very powerful identity.** A stolen platform token can activate or suspend any organization. T7's accepted limits (no revocation, no refresh, no login rate-limiting, a single signing key) weigh more here. Recommendation: a short TTL for platform tokens and a separate signing key or audience, to be decided by the Architect.
- **First-admin secret (T9a).** Depending on the option, the initial secret sits in environment or operator hands; must be rotated.
- **The cross-tenant read** is the first code path allowed to see every tenant. If it leaks to tenant handlers, tenant isolation breaks. It must stay in the platform context only, and be handled explicitly when RLS (T10) arrives.
- **No audit trail.** Events carry the organization and tenant but not who acted; suspension is a high-impact action (question P3).
- **Window between T9c and T9d** where tenants can still self-activate. Mitigated by shipping them together and before any real deployment.
- **Blocking point not fully removed.** Until the platform validates, a customer can register but do nothing. Decision 3c accepts this; the customer experience (how they learn they are waiting) is outside this ticket.
- **Seeding migration.** Many tests and Postman rely on tenant/smoke activation; migrating them in T9d is a sizeable but mechanical change.

## Dependencies

- Merged: T1-T6, T5e-0, T7a (and T7c if merged).
- **T7b (login, JWT, filter chain) is required before T9b.**
- T9a decision gates T9b; T9a implementation needs T9b; T9c needs T9a and T9b; T9d needs T9c.
- T8 (roles) and T10 (RLS) are out of scope; T10 is impacted (global read must ignore RLS cleanly).

## Architect validation (2026-11-08)

**Verdict: validated with changes.** Where this section disagrees with an earlier one (slicing, contract, acceptance criteria, risks), this section wins; the earlier text is to be aligned when the ticket is next edited.

### Decisions (answers to A1-A7)

| # | Question | Decision |
|---|---|---|
| A1 | Where the platform admin lives | **`identity`, new aggregate `PlatformAdmin` with no `TenantId`, new table `identity.platform_admins`** (migration `V2__create_platform_admins.sql` in the `identity` Flyway path). No new module: it reuses `Email`, `PasswordHash`, `PlainPassword`, `PasswordHasher` and the T7b token code. `identity.users.tenant_id` stays `NOT NULL` and "one user = one tenant" is untouched (a `PlatformAdmin` is not a `User`). Unique index `lower(email)` on `platform_admins` with the same normalisation CHECK as `users`. **No cross-uniqueness between `users` and `platform_admins`**: it would make public registration answer `EMAIL_ALREADY_EXISTS` for an admin's e-mail, an oracle revealing admin e-mails. Domain: `PlatformAdminRepository`, reuse `EmailMustBeUniqueRule`, a `CreatePlatformAdminHandler` with no endpoint (used by T9a and by integration tests). |
| A2 | Naming, mergeability, controller location | **Change activation in place and ship T9c and T9d as one delivery.** `ActivateOrganizationCommand` becomes `(UUID organizationId)`; the handler loads through the cross-tenant port; the result and HTTP response carry no `tenantId`. `SuspendOrganization*` and `ReinstateOrganization*` are created under their final names. The tenant route `POST /api/organizations/{id}/activation` is removed in the same PR that adds the platform routes. No window where tenants can self-activate, and no moment without an activation path (T9c already requires T9b and T9a). Test migration is mechanical: four `*EndToEndIT` only drop the tenant argument. **Controller: `PlatformOrganizationController` in `organization`, package `com.zim.organization.presentation.rest.platform`.** Reason: `ApiExceptionHandler` is scoped with `basePackageClasses` to `organization.presentation.rest`, so a controller in `bootstrap` would get none of the 400/404/409 bodies, and copying the advice is forbidden by T6. Authentication of `/api/platform/**` is the security chain's job (A4). Guard test (plain JUnit scanning the package): no platform controller references `CurrentTenantProvider`. |
| A3 | Cross-tenant read and listing | **Separate ports, no new method on `OrganizationRepository`.** `domain/repository/PlatformOrganizationRepository.findByIdAcrossTenants(OrganizationId)` and `application/port/PlatformOrganizationQuery.list(status, page, size)` returning a projection (`OrganizationSummary`, `PageResult`), not the aggregate. One adapter, `PlatformOrganizationAdapter`, implements both, so all code that sees every tenant lives in one place. Writes still go through `OrganizationRepository.save`, which reloads by the aggregate's own tenant, so events carry the aggregate's tenant. Unreachable from tenant handlers by construction (only platform handlers inject these ports) and by a reflection test on the constructors of `application/handler`. New `organization` migration: index `(status, created_at, id)`. Wire handlers in `OrganizationConfiguration` with the same transactional wrapping; the listing handler is read-only. **Under RLS (T10):** without a platform context the cross-tenant read returns nothing (404), which fails closed. The bypass is a policy clause or a dedicated DB role set per transaction by platform handlers only, and it must cover **reads and writes of the same transaction** (the ticket only mentioned reads). |
| A4 | Platform token, keys, errors | **Two ordered `SecurityFilterChain`s.** Chain 1 (`securityMatcher("/api/platform/**")`): its own `JwtDecoder` with a dedicated secret from an env var (for example `QUINE_PLATFORM_JWT_SECRET`, startup refused if missing, no value in any file), audience `quine-platform`, no `tenantId` claim, entry point writes 401 `AUTHENTICATION_REQUIRED` with `WWW-Authenticate: Bearer realm="quine-erp"`, TTL 15 minutes, no refresh. `POST /api/platform/auth/login` is `permitAll`, the only exception in the zone. Chain 2: everything else, with the tenant key and `TENANT_NOT_RESOLVED` as decided in T7. **Consequence: a tenant token on `/api/platform/**` fails signature verification and gets 401 `AUTHENTICATION_REQUIRED`, not 403. `PLATFORM_ACCESS_DENIED` is removed from T9** (all platform admins have the same rights, so there is no "authenticated but not allowed" case; 403 stays reserved to roles, as in T7; it returns with platform roles). T7 and T9 must both state that `AUTHENTICATION_REQUIRED` (platform chain) and `TENANT_NOT_RESOLVED` (tenant chain) name the same condition with a code per chain. Login keeps the T7 behaviour: the same `INVALID_CREDENTIALS` for unknown e-mail and wrong password, with identical hashing work. |
| A5 | Repeated suspension | **Add `ORGANIZATION_ALREADY_SUSPENDED`.** `OrganizationMustBeActiveToBeSuspendedRule` gets a `switch` on status like the activate and reinstate rules (`SUSPENDED` → `ALREADY_SUSPENDED`, other statuses → `CANNOT_BE_SUSPENDED`); the rule's name does not change. This is **not a new question: it was already decided in `domain-activate-reinstate-split.md`, R1.** No HTTP contract breaks (no suspension endpoint exists). Tests: `OrganizationTest.shouldRejectSuspendingAlreadySuspendedOrganization` moves to the new code; create `OrganizationMustBeActiveToBeSuspendedRuleTest` on the model of `OrganizationMustBeSuspendedToBeReinstatedRuleTest`. Update the "already" code lists in `endpoints-T5.md` and `CLAUDE.md`. |
| A6 | Query parameters | **Explicit validation in the presentation layer, no `@Min`/`@Max`/`@Pattern` on parameters** (they have no pinned text and would force the `HandlerMethodValidationException` mapping and guard-test changes of T6). A small page-parameters object throws a presentation exception (`InvalidQueryParameterException`) mapped to 400 `VALIDATION_FAILED`; not a `DomainException` (that would be 422). Pinned texts, built without the locale and never echoing the input: `size: must be between 1 and 100`, `page: must be greater than or equal to 0`, `status: must be one of [PENDING_ACTIVATION, ACTIVE, SUSPENDED, CLOSED]`; add them to `validation-message-locale.md`. Generalise `ApiExceptionHandler.handleArgumentTypeMismatch` (today hardcoded "must be a valid UUID") by required type: UUID, `Integer` ("must be an integer"), enum ("must be one of [...]"). |
| A7 | Shared web component, smoke shortcut | **T9 does not trigger the extraction: T7b does, and it becomes slice `T7b-0` (extract `web-shared`) before T7b**, because `identity` is already the second module and its login controller and security handlers must write `ApiErrorResponse`, which `identity` cannot take from `organization`. Generalise the hardcoded "organization" in `CONCURRENT_MODIFICATION` at the same time. **Remove `/smoke/organizations/{id}/activate`** in the T9c/T9d delivery: once activation takes no tenant, it is an unauthenticated cross-tenant activation guarded only by a profile list. It also conflicts with T7 decision 10: the T7b chain makes `/smoke/**` a 401 unless it is `permitAll`, so the shortcut dies at T7b anyway; remove it together with `SmokeTenantProvider` and `FailClosedTenantProvider`. Postman: replace the activation step by platform login, list, activate. |

### Corrections to the ticket (checked against the code)

1. The five `*EndToEndIT` classes live in `organization`, which has no `SmokeController`: they do **not** use `/smoke`. Four (`AddStore`, `ChangeHeadquarters`, `DeactivateStore`, `CloseOrganization`) activate by calling `ActivateOrganizationHandler` with `new ActivateOrganizationCommand(tenantA, orgId)`; `ActivateOrganizationEndToEndIT` tests the HTTP activation route itself and must be rewritten, not just migrated. Only Postman used `/smoke`.
2. Postman: about 13 references to `smoke/organizations`, `/activation` or "Activate" in `quine-erp-organization.postman_collection.json`, not 9. Folder "6. Activate organization (T5e)" is replaced entirely, and the collection description (line 5) calls T5e the real path.
3. `ORGANIZATION_ALREADY_SUSPENDED` is not an open question (A5).
4. "Merged: T7c if merged" is wrong: **T7c is not merged** (no `OwnerRegistrar` exists). T7b is not built either.
5. The T9a gate was inconsistent. The correct reading: **the T9a decision does not block T9b**, because how the first row is created affects neither login nor the routes.
6. The acceptance criteria "tenant token on a platform route → 403 `PLATFORM_ACCESS_DENIED`" and "authenticated tenant user with a bad body → 403, not 400" are void (A4): both are 401 `AUTHENTICATION_REQUIRED`.
7. `tenantId` is **removed from the listing items** (T6: tenant-scoped responses do not echo the tenant, and no T9 action takes a tenant). Revisit if the reviewer really needs it (question P4).
8. The shortcut `/smoke/.../activate` cannot survive T7b (A7).

### Revised slicing and order

`T7b-0` (extract `web-shared`) → `T7b` (login, JWT, chain) → `T9b` → `T9a` (implementation) → `T9c` + `T9d` as **one delivery**.

- **T9b** now also contains `CreatePlatformAdminHandler` (no endpoint), the `PlatformAdmin` aggregate with an `active` flag checked at login (cheap, and the only way to disable a compromised admin), and the two-chain security setup. It is no longer blocked by the T9a decision.
- **T9a** is reduced to one trigger for `CreatePlatformAdminHandler` (env vars, runner or operator command, per the PO's choice) plus the profile guard. Only the PO decision is needed before it.
- **T9c + T9d** merged. What remains of the old T9d: Postman, `docker-compose.yml` (env var names, no values), `CLAUDE.md`, marking T5e as superseded.
- Part of T9c (A5, suspend/reinstate handlers, ports, listing, index) may merge before the security chain **only if the controller is not `@Import`ed in `QuineApplication`**.

### Risks added

1. **`bootstrap` has no Spring Security today.** A platform route imported before T7b/T9b would be open to everyone: `FailClosedTenantProvider` does not protect it (a platform controller must not call the provider). Rule: do not import the controller until the chain exists; add an integration test "no token → 401" on every `/api/platform/**` route and a default `authenticated()`/`denyAll()` for any other request.
2. **RLS (T10) must cover platform writes**, not only reads (A3).
3. **Compromised platform admin:** no token revocation; mitigated by the 15-minute TTL and the `active` flag checked at login.
4. **Brute force on platform login:** no rate limiting or lockout is **a prerequisite for any real deployment** (stronger than the "accepted limit" of T7, because this is the most powerful account).
5. **Audit (P3):** events carry no actor. At minimum log the admin id next to the handler call. An actor field on events is an event-schema change, to decide before any consumer exists.
6. **Offset pagination** can skip or duplicate rows when an organization changes status between two pages. Acceptable for a review queue.
7. **Secrets (T9a):** environment variables only, including compose and the Postman environment file (no values committed).
8. **Documentation:** `CLAUDE.md` (smoke description; Conventions: second identity kind, platform zone, the cross-tenant read exception), `endpoints-T5.md` (T5e superseded, readiness table).

## Product-owner decisions, round 2 (2026-11-08)

Drafted by the BA against the code. Alignment with older text: the criterion "Customer sees the pending state (decision 3c)" is replaced by D1 to D18 below; in the criterion "Suspend", "closing is still allowed" and "answer their existing 409 'must be active' codes" no longer hold (see PO-1 and the dedicated codes); "Rejecting or refusing a registration" and "Effect of suspension on login" leave "Out of scope" (see "Out of scope, revised"); the risks "No audit trail" and "Blocking point not fully removed" are replaced by the risks below; the old open questions P2 to P6 are answered and closed, P1 stays as is; the listing item becomes `{organizationId, organizationName, legalName, currencyCode, status, ownerEmail, createdAt}` (no `tenantId`) and its `status` filter accepts `REJECTED`, so the pinned text becomes `status: must be one of [PENDING_ACTIVATION, ACTIVE, SUSPENDED, CLOSED, REJECTED]`.

| # | Question | PO answer (translated) | Decision recorded |
|---|---|---|---|
| P1 | First platform administrator (T9a) | "No need for a change for the moment." | **T9a is unchanged and still undecided.** The option stays "to be decided by the PO before implementation". The sub-questions "forced password change at first login" and "audit of the creation" are **not asked for now**. |
| P2 | Refusing a registration | "A real outcome." | A refusal is a **result in its own right**, distinct from "not yet validated": a new final status `REJECTED`, reached by a platform rejection of a `PENDING_ACTIVATION` organization. |
| P3 | Audit | "Yes, the platform must record and trace the validation and suspension actions." | The platform records **who** validated and who suspended (and, by extension, who reinstated and who rejected), **when**, on **which organization**, and a **reason** where relevant (suspension, rejection). Storage is the Architect's call; who can read it is a PO question (PO-3). |
| P4 | Data shown to the validator | "Only the owner's e-mail must appear, no need for the tenantId." | The platform listing shows `ownerEmail` and **no `tenantId`**. The e-mail lives in `identity` and the list in `organization`: see "Owner e-mail in the list". |
| P5 | Suspended tenant | "Yes, users can log in; they will not be able to do anything when a tenant is suspended." | Login stays possible. **Every tenant action is refused while the organization is `SUSPENDED`.** Inconsistency to settle: the ticket said the tenant may still close from `SUSPENDED` (`OrganizationMustBeClosableRule`); see PO-1. |
| P6 | Message to the customer | "We need a proper business and customer message to say that the customer is waiting for a validation." | **Reverses decision 3c.** A tenant action on a `PENDING_ACTIVATION` organization gets a dedicated code and message. By consistency (BA proposal, PO-6), the same for `SUSPENDED` and `REJECTED`. Tension with the `endpoints-T5.md` convention: see PO-5. |

### Rejection (new status `REJECTED`)

`OrganizationStatus` gains `REJECTED`, a **final** state (BA proposal, PO-7): no activation, suspension, reinstatement or closure leaves it. The `ck_organizations_status` CHECK is extended by a new migration. Only the platform rejects, and only a `PENDING_ACTIVATION` organization (a suspended or active customer is not "refused": suspension is the tool).

Endpoint (proposal): `POST /api/platform/organizations/{id}/rejection`, body `{"reason": "<text>"}`, 200 `{organizationId, status:"REJECTED", rejectedAt}` (no `tenantId`). Same step order as the other platform actions.

| Status of the organization | Result |
|---|---|
| `PENDING_ACTIVATION` | 200 `REJECTED` |
| `REJECTED` (repeat) | 409 `ORGANIZATION_ALREADY_REJECTED` |
| `ACTIVE`, `SUSPENDED`, `CLOSED` | 409 `ORGANIZATION_CANNOT_BE_REJECTED` |
| missing | 404 `ORGANIZATION_NOT_FOUND` |

The rule (proposed name `OrganizationMustBePendingToBeRejectedRule`) switches on status, as the activate and reinstate rules do. From `REJECTED`, activate gives `ORGANIZATION_CANNOT_BE_ACTIVATED`, suspend `ORGANIZATION_CANNOT_BE_SUSPENDED`, reinstate `ORGANIZATION_CANNOT_BE_REINSTATED`, tenant actions `ORGANIZATION_REJECTED`. Event `OrganizationRejected` with the aggregate's tenant; the BA recommends it does **not** carry the reason (events go to consumers; the reason stays in the audit journal), see A-3.

Consequences the PO has not stated are in the open questions: whether a rejected organization keeps its legal name locked (global unique constraint, as `CLOSED` does today), whether the owner keeps their e-mail locked (`EMAIL_ALREADY_EXISTS` is platform-wide), whether the owner can log in and what they see, whether the tenant sees the reason, whether a rejection can be undone.

### Reason on suspension

`POST /api/platform/organizations/{id}/suspension` now takes a body `{"reason": "<text>"}` (the endpoint does not exist yet, so nothing breaks). Activation and reinstatement stay body-less. The reason is required, non-blank, with a maximum length (BA proposal: 500, PO-2), validated with constraint types that have a pinned text, never echoed. It is stored in the audit journal, never logged in clear, and not returned in responses or in the listing.

### Dedicated refusal codes on the tenant side (reverses 3c)

When an organization is not `ACTIVE`, tenant actions answer a **status-specific** code instead of the generic "must be active" code (names to be confirmed by the Architect):

| Organization status | New code | Business meaning |
|---|---|---|
| `PENDING_ACTIVATION` | `ORGANIZATION_PENDING_VALIDATION` | The registration is waiting for the platform's validation; the customer must wait. |
| `SUSPENDED` | `ORGANIZATION_SUSPENDED` | The organization is suspended by the platform. |
| `REJECTED` | `ORGANIZATION_REJECTED` | The registration was refused by the platform. |
| `CLOSED` | unchanged (existing generic codes) | Out of this ticket. |

These are **refusals from the current status, never "target state reached"**. They stay HTTP 409 (rule family); not 401 or 403, because the user is authenticated and allowed to act in general (Architect confirms).

| Tenant endpoint | `PENDING_ACTIVATION` | `SUSPENDED` | `REJECTED` | `CLOSED` |
|---|---|---|---|---|
| `POST /api/organizations/{id}/stores` | was `ORGANIZATION_MUST_BE_ACTIVE_TO_ADD_STORE`; **`ORGANIZATION_PENDING_VALIDATION`** | **`ORGANIZATION_SUSPENDED`** | `ORGANIZATION_REJECTED` | unchanged generic |
| `PUT /api/organizations/{id}/headquarters` | was `..._TO_CHANGE_HEADQUARTERS`; **`ORGANIZATION_PENDING_VALIDATION`** | **`ORGANIZATION_SUSPENDED`** | `ORGANIZATION_REJECTED` | unchanged generic |
| `POST /api/organizations/{id}/stores/{storeId}/deactivation` | was `..._TO_DEACTIVATE_STORE`; **`ORGANIZATION_PENDING_VALIDATION`** | **`ORGANIZATION_SUSPENDED`** | `ORGANIZATION_REJECTED` | unchanged generic |
| `POST /api/organizations/{id}/closure` | was `ORGANIZATION_CANNOT_BE_CLOSED`; **`ORGANIZATION_PENDING_VALIDATION`** | was allowed (200); **BA recommendation: `ORGANIZATION_SUSPENDED`** (PO-1) | `ORGANIZATION_REJECTED` | unchanged `ORGANIZATION_ALREADY_CLOSED` |

Notes: registration is public and unaffected; the tenant activation route is removed by T9; there is no tenant read endpoint today, so no read is refused (PO-9, A-5). Step order is unchanged: request validation (400), tenant (401), ownership (404; another tenant's organization stays an indistinguishable 404, never a dedicated code), value objects (422), then rules (409), where the dedicated status code is the **first** business rule. This changes the contract of the three "must be active" codes for non-closed statuses; the Architect assumes no external client (T7 decision 13), the PO confirms (PO-8).

### Audit (P3)

Traced for each platform action: admin id, action (`ACTIVATED`, `REJECTED`, `SUSPENDED`, `REINSTATED`), organization id, timestamp (`ClockProvider`), reason (suspension and rejection only); the tenant id may be stored (it comes from the aggregate). The admin's e-mail is **not** copied (id only). Where: actor on events (schema change, and the reason becomes a leak vector), a persistent append-only journal written in the same transaction, or both (A-2). BA leaning: the journal is the source of truth. **Hard requirement:** no validation, rejection, suspension or reinstatement succeeds without its trace, and no trace exists for an action that did not happen. Who can read it: PO-3; until decided, T9f only records.

### Owner e-mail in the list (P4), problem for the Architect

The list is served by `organization`; the owner's e-mail is held by `identity`; the two never reference each other (only `bootstrap` knows both). `RegisterOrganizationCommand` has no `ownerEmail` yet (T7c is not merged and adds it).

| Option | Idea | For | Against / risk |
|---|---|---|---|
| A. Copy at registration | `organization` stores `owner_email` from the registration command. | One local query, no runtime dependency on `identity`. | Duplicated personal datum in two schemas; stale if the e-mail ever changes; rectification and erasure hit two places; must roll back together with owner creation; never put it in `OrganizationRegistered`. |
| B. Composition in `bootstrap` | A platform query in `bootstrap` reads the page, then the owners' e-mails from `identity` by tenant. | Single source of truth, fresh. | The join key `tenantId` must be in the internal projection though not in the HTTP response; new `identity` query port; reads not atomic; null e-mail if the owner row is missing; the controller would live in `bootstrap` and lose the `ApiExceptionHandler` scoping. |
| C. Port in `organization`, adapter in `bootstrap` | `organization` declares a driven port (e.g. `OwnerContactLookup`: tenant ids in, e-mails out), `bootstrap` implements it through `identity`. | As B, and the controller stays in `organization`. | As B; the adapter must exist in every deployment, so a missing one must fail at startup. |

BA leaning (not a decision): **C**, provided the PO accepts "no sorting or filtering on e-mail". If A is chosen, it must be decided **before T7c merges**. Privacy: every platform admin sees the e-mail of anyone who registers, including those later refused; the e-mail must not appear in logs, error messages, the audit journal or events.

### Acceptance criteria, round 2

**Listing and platform actions**
- **L1:** Given a `PENDING_ACTIVATION` organization whose owner registered with an e-mail, when a platform admin lists `status=PENDING_ACTIVATION`, then the item carries `ownerEmail` and **no `tenantId`** field.
- **L2:** `status=REJECTED` is accepted; an unknown status is 400 `VALIDATION_FAILED` with the updated pinned text.
- **L3:** an organization whose owner cannot be found is listed with a null `ownerEmail` (display rule: A-1).
- **S1:** suspending an `ACTIVE` organization with a reason gives 200 `SUSPENDED`, publishes `OrganizationSuspended` with the aggregate's tenant, and traces the reason. A missing, blank or too long reason is 400 and nothing changes.
- **S2:** a repeat gives 409 `ORGANIZATION_ALREADY_SUSPENDED`; `PENDING_ACTIVATION`, `REJECTED` or `CLOSED` gives 409 `ORGANIZATION_CANNOT_BE_SUSPENDED`.
- **R1:** rejecting a `PENDING_ACTIVATION` organization with a reason gives 200 `REJECTED`, publishes `OrganizationRejected` with the aggregate's tenant, and traces action and reason.
- **R2:** a repeat gives 409 `ORGANIZATION_ALREADY_REJECTED`; `ACTIVE`, `SUSPENDED` or `CLOSED` gives 409 `ORGANIZATION_CANNOT_BE_REJECTED`, status unchanged.
- **R3:** a rejection without a valid reason is 400; a missing organization is 404; without a platform token it is 401 `AUTHENTICATION_REQUIRED`.
- **R4:** a `REJECTED` organization cannot be activated, suspended or reinstated and stays `REJECTED`.
- **A1:** activation and reinstatement are unchanged, except that each is traced.
- **A2:** two platform actions racing on the same version: the second gets 409 `CONCURRENT_MODIFICATION`.

**Dedicated codes on tenant endpoints** (one scenario per cell of the table; may be parametrized)
- **D1 to D4:** on a `PENDING_ACTIVATION` organization, add store, change headquarters, deactivate store and close each answer 409 `ORGANIZATION_PENDING_VALIDATION`, the organization is unchanged.
- **D5 to D8:** same four on a `SUSPENDED` organization answer 409 `ORGANIZATION_SUSPENDED`. **D8 (closure) is provisional until PO-1**: if the PO keeps closing allowed, closure returns 200 and `OrganizationMustBeClosableRule` is unchanged.
- **D9 to D12:** same four on a `REJECTED` organization answer 409 `ORGANIZATION_REJECTED`.
- **D13:** on a `CLOSED` organization the existing codes are unchanged.
- **D14:** another tenant's organization, in any status, is still the same 404 as a missing one; dedicated codes are never returned to another tenant.
- **D15:** none of the three dedicated codes is in the "target state already reached" list of `endpoints-T5.md`.
- **D16:** the owner of a `SUSPENDED` organization can still log in; login for `PENDING_ACTIVATION` and `REJECTED` follows PO-4.
- **D17:** after the platform activates, the action that answered `ORGANIZATION_PENDING_VALIDATION` succeeds.
- **D18:** after reinstatement, the action that answered `ORGANIZATION_SUSPENDED` succeeds.

**Audit**
- **U1:** after a validation, rejection, suspension or reinstatement, one trace exists (admin id, action, organization id, timestamp, and reason where applicable).
- **U2:** a refused action (400, 401, 404, 409) leaves no trace.
- **U3:** if the state change cannot be saved no trace remains, and the reverse.
- **U4:** a trace cannot be modified or deleted through the application.
- **U5:** no password, token or secret appears in any trace or log; the owner e-mail appears in no trace.

**Message**
- **M1:** the error body keeps `ApiErrorResponse {code, message, timestamp}`; `message` is developer-facing English for the three codes. The customer-facing text depends on PO-5.

### Business rules, round 2

- A registered organization stays `PENDING_ACTIVATION` until the platform validates **or rejects** it.
- Only a `PENDING_ACTIVATION` organization can be rejected. `REJECTED` is final (subject to PO-7).
- A rejection and a suspension require a reason; activation and reinstatement do not (subject to PO-2).
- While an organization is not `ACTIVE`, no tenant action on it succeeds, and the tenant is told **why** with a status-specific code.
- A user of a non-active organization can still log in (suspended: P5; the others: PO-4); login grants no organization action.
- Tenant closure is allowed only from `ACTIVE` (BA recommendation, PO-1).
- Every platform action (validate, reject, suspend, reinstate) is traced with its author, time, organization and reason where applicable; none succeeds without its trace.
- The platform listing shows the owner's e-mail and never the tenant id.
- `ORGANIZATION_ALREADY_REJECTED` is a repeat; `ORGANIZATION_CANNOT_BE_REJECTED` is a refusal from the current status; the three `ORGANIZATION_<STATE>` codes are refusals, not repeats.

### Out of scope, revised

Removed from "Out of scope": "Rejecting or refusing a registration" and "Effect of suspension on login or on other modules". Added: a UI or notification for the customer (the client renders the business text per code, PO-5); reading the audit journal unless the PO says otherwise (PO-3); undoing a rejection and a tenant "withdraw my registration" action; showing the suspension or rejection reason to the tenant or in the list; sorting or filtering the list by owner e-mail; a tenant read endpoint telling the client the organization status without attempting an action (A-5); platform closure of a suspended organization; changing the owner's e-mail.

### Files and docs that change or break, round 2

- `OrganizationStatus` (`REJECTED`), `Organization.reject(...)`, `OrganizationRejected`, `OrganizationMustBePendingToBeRejectedRule` and its test.
- `OrganizationMustBeActiveToAddStoreRule`, `...ToChangeHeadquartersRule`, `...ToDeactivateStoreRule`, `OrganizationMustBeClosableRule` and their tests, `OrganizationTest`, handler tests and the four related `*EndToEndIT` classes (expected codes change for `PENDING_ACTIVATION` and `SUSPENDED`); `REJECTED` cases in the activate, suspend and reinstate rule tests.
- New `organization` migration: extend `ck_organizations_status`; depending on PO-8, the unique index on `normalized_legal_name`, `existsByLegalName` and the `DATA_INTEGRITY_VIOLATION` mapping in `OrganizationRepositoryAdapter`.
- Suspend command, handler, result and request gain `reason`; new `RejectOrganization*` classes; wiring in `OrganizationConfiguration`.
- Audit: journal table and port, and a "current platform admin" provider for the platform controller (not the tenant provider), covered by the guard test.
- `OrganizationSummary` gains `ownerEmail`; an `identity` query port and `bootstrap` adapter wiring for options B or C; the registration command and migration for option A.
- `ApiExceptionHandler` needs no new handler for the rule codes; the parameter-mismatch message (A6) lists `REJECTED`; `FieldErrorMessages` for `reason`.
- Docs: `endpoints-T5.md` (the three dedicated codes and the `REJECTED` codes in the code lists, a new family `ORGANIZATION_<STATE>` = refusal, T5d closable statuses, T5e superseded), `CLAUDE.md` (Errors convention, platform zone, audit), `validation-message-locale.md` (pinned texts for `reason` and the `status` enum), `identity-T7.md` (T7c `ownerEmail` if option A, login of non-active tenants), `domain-activate-reinstate-split.md` (status diagram), and the Postman collection (reject, suspend with reason, the tenant refusal scenarios; no values committed).

### Risks, round 2 (replace "No audit trail" and "Blocking point not fully removed")

- **Audit journal integrity:** append-only (no update or delete from the application, restricted DB grants), written in the same transaction as the state change, one trace per effective action and not per attempt. Without it the PO's requirement is not met.
- **Audit and personal data:** the reason is free text and may hold personal or commercial data: cap the length, do not log it, restrict reading, define retention; do not copy the admin or owner e-mail into the journal.
- **Owner e-mail visible to the platform:** a personal datum of people who may never become customers; restrict it to the platform zone and keep it out of logs, events, errors and the audit. A copy (option A) multiplies the places to protect and rectify.
- **Rejected registrations hold unique values:** if `REJECTED` keeps the legal name and e-mail locked, a customer with a typo cannot register again; releasing them complicates uniqueness and login (PO-8).
- **Dedicated codes leak the state:** limited to a tenant's own organization; the 404 rule for other tenants stays.
- **Contract change:** the three "must be active" codes change meaning for non-closed statuses (no external client assumed).
- **The gate is organization-only today:** other modules do not exist yet; "a suspended tenant can do nothing" holds only for organization endpoints until a cross-module mechanism exists (A-5).
- **A suspended tenant cannot withdraw** (if PO-1 is answered "refuse"): until a platform closure exists, the organization keeps its legal name.
- **The customer cannot learn the status without trying an action** (A-5).
- **Delivery size:** rejection and audit would make T9c+T9d too large, hence separate slices.

### Slicing, round 2

- **T9e: Reject a registration.** Status `REJECTED`, `reject()` with rule, event and migration, `POST /api/platform/organizations/{id}/rejection` with reason, the `REJECTED` list filter, the tenant `ORGANIZATION_REJECTED` code. Depends on T9b, T9f and PO-2, PO-3, PO-4, PO-7.
- **T9f: Platform audit.** The "current platform admin" port, the journal, the same-transaction write, entries for validate, suspend and reinstate (rejection adds its own in T9e). Depends on T9b and A-2. Part of the **same delivery** as T9c+T9d: no unaudited platform action may reach a real environment.
- **T9g: Dedicated tenant refusal codes for `PENDING_ACTIVATION` and `SUSPENDED`.** Rule changes, tests, `endpoints-T5.md`, Postman. It touches only domain rules, so it is **mergeable at any time**, independently of T7b and T9b, and recommended before T9c+T9d (when tenants lose self-activation their message must already be right). The `REJECTED` branch joins in T9e. Depends on PO-1, PO-5, PO-6.
- **T9h: Owner e-mail in the platform list.** Depends on A-1. Option A must land **before T7c merges**; options B or C after T7a and before the T9c+T9d delivery.
- T9c keeps its scope (list, activate, suspend with reason, reinstate); the suspension reason belongs to T9c because that is where the endpoint is created.

Order: `T9g` (any time) → `T7b-0` → `T7b` → `T9b` → `T9a` (implementation, still undecided) → `T9h` → `T9f` → **`T9c + T9d` as one delivery** → `T9e` (BA recommends the same release or immediately after, and before any real deployment, otherwise refused registrations stay in the pending queue). Dependencies: T9f needs T9b; T9c needs T9f and T9h; T9e needs T9f, T9c and the migration; T9g needs none.

## Architect validation, round 2 (2026-11-08)

**Verdict: validated with changes.** The slicing is right. Two points were not feasible as written: the conflict between PO-4 and PO-8, and the unreserved position of T9g.

### Decisions (answers to A-1 to A-6)

| # | Question | Decision |
|---|---|---|
| A-1 | Owner e-mail in the list | **Option C.** State of the code: T7c is not merged (no `OwnerRegistrar`, no `ownerEmail` in `RegisterOrganizationCommand`). A (copy) is rejected: `identity.users.email` stays the single source (unique index `uk_users_email`), a copy would be a second personal datum to rectify and erase, and it would make `organization` carry the e-mail, the opposite of the T7 module separation (decision 7). B is rejected: a controller in `bootstrap` loses `ApiExceptionHandler` (scoped by `basePackageClasses`). C is the same technique as `OwnerRegistrar`, in the read direction. Shape: port `OwnerContactLookup` in `organization/application/port`, `Map<TenantId,String> findOwnerEmails(Set<TenantId>)`, one batch read per page (max 100 tenants, no N+1), implemented in `bootstrap` over a new `identity` batch query (owners where `tenant_id` in the list, index `ix_users_tenant_id`). Two distinct types: an internal `OrganizationSummary` carrying `tenantId` as join key, and the HTTP item without `tenantId` and with `ownerEmail`; **the list handler does the enrichment, not the JPA adapter**, and the tenant id never reaches the response. Owner not found: `ownerEmail` is null (L3) and a warning is logged with the organization id, never the e-mail (abnormal after T7c, normal before it and for test data). Startup check: no special code; the port is a mandatory argument of the handler bean in `OrganizationConfiguration`, so a missing adapter fails the context. Forbidden: `ObjectProvider`, an optional bean, a no-op default in `organization` (it would hide the omission). Add a `QuineApplicationIT` loading the context and an explicit stub in `OrganizationConfigurationIT`. **Order: T7c, then T9h** (the "before T7c merges" constraint only applied to option A). |
| A-2 | Audit storage | **One append-only journal, no actor on events.** Spring events are synchronous, in memory, with no outbox (javadoc of `SpringDomainEventPublisher`): they are not durable, so they cannot be audit evidence, and an actor or reason on them would leak to consumers. Events stay unchanged; an actor can be added later without breaking consumers. **Where:** module `organization`, table `organization.platform_audit_log` (same schema and transaction as the aggregate; not in `shared`, which has no persistence; no new module for one table; if other modules get platform actions, each gets its own table). **Columns:** `id`, `occurred_at`, `actor_id` (UUID, no FK across schemas), `action` (CHECK on the 4 values), `organization_id` (FK RESTRICT), `tenant_id`, `reason` (CHECK: not null for SUSPENDED and REJECTED, length ≤ 500); no e-mail. **Actor id:** interface `CurrentPlatformAdminProvider` in `shared/tenant`, symmetric to `CurrentTenantProvider`, failing closed with `PlatformAdminNotResolvedException` mapped to 401 `AUTHENTICATION_REQUIRED`; implemented in `identity` from the platform chain principal; the platform controller calls it once and puts `actorId` (UUID) in the command, so handlers depend on no Spring interface and `organization` does not reference `identity`. Extend the guard test: no platform controller references `CurrentTenantProvider`, no tenant controller references `CurrentPlatformAdminProvider`. **"No action without trace, no trace without action":** port `PlatformAuditTrail.append(PlatformAuditEntry)` in `organization/application/port` (append is the only operation), JPA adapter with an `@Immutable` entity, injected into the activate, suspend, reinstate and reject handlers through `OrganizationConfiguration` (they already run inside the `transactional(...)` proxy; one `PlatformTransactionManager`, one `EntityManager`). Order inside `handle`: (1) aggregate action; (2) `organizationRepository.save` (forces the version increment, so a conflict raises `CONCURRENT_MODIFICATION` before any trace); (3) `auditTrail.append` with `saveAndFlush`, so a constraint violation surfaces before events are published; (4) event publication. A 400, 401, 404 or 409 throws before `append` (U2); an audit failure rolls the aggregate back (U3). **Immutability:** layer 1, no update or delete in the port and no exposed Spring Data method; layer 2, a `BEFORE UPDATE OR DELETE` trigger that raises, plus a TRUNCATE revoke. **Limit to document:** `FlywayConfiguration` migrates with the same `DataSource` as the application, which then owns the tables, so a `REVOKE` has no effect; the real guarantee needs a migration role distinct from the runtime role, a prerequisite for real deployment, to handle with T10. **Leak risk:** a command record's `toString()` prints every field; use a `Reason` type with a masked `toString` so the reason never enters a log or an exception message. |
| A-3 | `REJECTED` model | Event `OrganizationRejected(eventId, organizationId, tenantId, occurredAt)`, no reason. Rule `OrganizationMustBePendingToBeRejectedRule` with a `switch` (`REJECTED` → `ORGANIZATION_ALREADY_REJECTED`, others → `ORGANIZATION_CANNOT_BE_REJECTED`). **Final state needs no change to existing rules** (activatable and reinstate rules default to `CANNOT_BE_...`; suspend requires `ACTIVE`; the closable rule accepts only `{ACTIVE, SUSPENDED}`). **Migrations** (`db/migration/organization` has V1 to V3, so the next is **V4**): V4 recreates `ck_organizations_status` with `REJECTED` and creates the list index `(status, created_at, id)`; V5 creates the audit table; V6 changes legal-name uniqueness. **Legal name: release it for `REJECTED`** (drop the V1 constraint `uk_organizations_normalized_legal_name`, recreate a partial unique index with the **same name** `WHERE status <> 'REJECTED'`, so `UNIQUE_LEGAL_NAME_CONSTRAINT` in `OrganizationRepositoryAdapter` keeps working); `existsByLegalName` must follow the index (replace `existsByNormalizedLegalName` in `SpringDataOrganizationRepository` with a variant excluding `REJECTED`, otherwise the pre-check refuses what the index allows). Safe only because `REJECTED` is final: **PO-7 must stay "no undo"**. **PO-4 and PO-8 are incompatible for the e-mail:** `uk_users_email` is a platform-wide unique index on `lower(email)`, so releasing the e-mail forces disabling or deleting the `User`, while PO-4 recommends that the owner of a `REJECTED` organization can log in to see the message. Architect choice: **release the legal name only, keep the e-mail locked, login allowed in `REJECTED`, no change in `identity`.** Cost to flag to the PO: a customer who mistyped the e-mail cannot re-register with it. If the PO insists on releasing the e-mail, add a port `OwnerRelease` in `organization` (adapter in `bootstrap`, same transaction, not an `@EventListener`, T7 decision 6), a deactivation column in `identity.users` with a partial e-mail index, and refuse that owner's login in `REJECTED`; a `User` is never deleted implicitly. |
| A-4 | Dedicated codes | **One shared guard rule evaluated first**: `OrganizationMustAcceptTenantActionsRule(status)`, violated for `PENDING_ACTIVATION`, `SUSPENDED` and `REJECTED`, whose `code()` is a `switch` returning the three dedicated codes (with a `default`), not catching `CLOSED`. Reason: the policy "not active, so which reason" lives in one place for 4 actions now and future modules; four duplicated switches would drift. The `MustBeActive...` rules keep their meaning for `CLOSED` and stay as a safety net; their generic codes are not removed. It is added as the first `checkRule` of `addStore`, `changeHeadquarters`, `deactivateStore` and `close` (the status rule is already first today, so no reordering), and **not** to the platform actions. It must cover closure (PO-1); without it a `PENDING_ACTIVATION` organization that closes would still answer `CANNOT_BE_CLOSED`. After PO-1 the closable set reduces to `{ACTIVE}`; keep the rule for `CLOSED` semantics. **Family `ORGANIZATION_<STATE>`:** refusal tied to the current state, never "target reached"; document next to `ALREADY_` and `CANNOT_BE_` in `CLAUDE.md` and `endpoints-T5.md`; a guard test checks that no code of the family is in the "already" list (D15). `PENDING_VALIDATION` is a business name, not the status name `PENDING_ACTIVATION`: accepted, to be stated in the code table. |
| A-5 | "A suspended tenant can do nothing" for future modules | **A shared-kernel port**: `TenantActivityGuard.requireActive(TenantId)` in `shared/tenant`, throwing `TenantNotActiveException` carrying the three round 2 codes, mapped to 409. **Implemented in `organization`, not `bootstrap`** (`organization` owns the status and already depends on `shared`; `bootstrap` only imports it), through a tenant-scoped read `findStatusByTenantId` on `uk_organizations_tenant_id` (a unique-index access, so it respects tenant isolation). Every future tenant handler calls it first with the command's tenant, keeping the step order (400, 401, then 404/422/409). Mitigation for a module that forgets: a reflection test per module that each tenant-scoped handler receives the guard. Rejected: events plus a local table per module (eventual consistency, a suspended tenant keeps acting for a while, events are not durable, so projections can diverge silently); the security chain (a DB read per request, runs before 400 and breaks the step order; its only advantage is covering a forgetful module). **How the client learns the status without attempting an action:** a tenant read `GET /api/organization` returning the status of its own organization (tenant from the provider); it is the **explicit exception to PO-9** and answers even when `SUSPENDED` (the PO confirms). Optionally later, the status in the login response as a snapshot. **Not in the JWT**: it would be stale for the token's life while a suspension must be immediate (no revocation in T7), `identity` would have to know `organization`'s status, and a token would need re-issuing on each transition. |
| A-6 | Slicing and order | Correct, with three adjustments. (1) **T9g first: yes, with reservations.** The sentence "it touches only domain rules" is false: it also changes about ten tests, five Postman assertions (`quine-erp-organization.postman_collection.json`, lines around 554, 1609, 1720, 1770, 1820) and `endpoints-T5.md`. It changes an existing contract (no external client assumed, T7 decision 13; PO-8 confirms). `SUSPENDED` is not reachable over HTTP before T9c, so that part is covered at domain and controller level only. The guard handles `PENDING` and `SUSPENDED` in T9g; `REJECTED` joins in T9e (keep a `default` in the switch). If PO-1 is "refuse", closing from `SUSPENDED` goes from 200 to 409: name this behaviour change in T9g. (2) **Split T9f:** T9f-1 = table, port, adapter, `Reason`, transactional wiring (mergeable early, without importing the controller in `QuineApplication`); T9f-2 = `CurrentPlatformAdminProvider` and its implementation (needs T7b/T9b). (3) **T9h needs T7c, not T9b.** |

**Revised order:** `T9g` → `T7c` → `T9h` → `T9f-1` → `T7b-0` → `T7b` → `T9b` → `T9f-2` → `T9a` (implementation, still undecided) → **`T9c + T9d` as one delivery** → `T9e` (needs T9f-1, T9c and migration V4).

### Existing tests and assets broken by T9g (the CLOSED cases stay unchanged)

- `ActivateOrganizationEndToEndIT:107` (add store while pending → `ORGANIZATION_PENDING_VALIDATION`).
- `TenantOrganizationControllerTest:466` (parameterised `PENDING_ACTIVATION`, `SUSPENDED`, `CLOSED`: split), `:851` (same for headquarters: split), `:1244` (parameterised `SUSPENDED` and `CLOSED`: split), `:1551` (`PENDING_ACTIVATION, ORGANIZATION_CANNOT_BE_CLOSED` becomes `ORGANIZATION_PENDING_VALIDATION`; update the comment at line 1547, "Only ACTIVE and SUSPENDED"); `:528` (CLOSED) unchanged.
- `AddStoreHandlerTest:307`, `CloseOrganizationHandlerTest:210`, `OrganizationTest:605` (`shouldRejectClosingPendingOrganization`): new code. `OrganizationTest:212` and `:480`: check the status used and split per status.
- `CloseOrganizationEndToEndIT:159, 170, 181` assert on `CLOSED`, so unchanged; the Developer confirms there is no "close from SUSPENDED" case (200 today, 409 after PO-1). If PO-1 is "refuse", every close-from-`SUSPENDED` test changes (`OrganizationTest`, the closable rule tests, the handler tests, `TenantOrganizationControllerTest:1544`), and the T5d decision "closable from `SUSPENDED`" must be updated.
- Postman: five assertions on the old codes. `endpoints-T5.md`: code lists and T5d.

### Corrections to the round 2 text

1. T9g does not "touch only domain rules" (above).
2. `OrganizationSummary gains ownerEmail` is replaced by two types (A-1): the internal summary keeps `tenantId` for the join, the response item has `ownerEmail` and no `tenantId`.
3. The older "Open questions" lists (old P2 to P6, A1 to A7) are answered and closed by the later rounds; only the round 2 lists below remain open.
4. Numbering collisions to fix when the ticket is next edited: round 2 acceptance criteria `A1` and `A2` collide with decisions A1 and A2 of the first Architect validation; `R1` collides with R1 of `domain-activate-reinstate-split.md`. Rename the criteria (for example `AC-A1`, `AC-R1`).
5. The migration numbers are V4 (check and list index), V5 (audit), V6 (legal-name uniqueness).
6. The round 2 order is superseded by the revised order above.

### Conflicts resolved with the first validation and with T7

- First A3: the list summary carries `tenantId` internally.
- First risk 5 ("at minimum log the admin id") is replaced by the transactional journal.
- First A2 (activation changed in place): the activate handler also gains `actorId` and `PlatformAuditTrail`, so the four `*EndToEndIT` that call it change their call too.
- T7 decision 6 also applies to releasing the e-mail (explicit port, no event).
- P5 vs T7: login does not depend on the organization's status, which keeps `identity` independent of `organization`. Keep it that way.
- PO-4 vs PO-8: see A-3. PO-1 vs `OrganizationMustBeClosableRule` and T5d: see above.

### Risks added

- A command record's `toString()` can leak the reason into logs and exception messages (`Reason` type, A-2).
- Audit immutability by grants is ineffective until a separate runtime DB role exists (A-2).
- Personal data: a rejected owner's e-mail stays in `identity.users` with no retention period or deletion, and a platform admin reading owner e-mails is not itself traced.
- Registration spam: registration is public and the e-mail is locked, so the validation queue can be flooded and an e-mail can be "burned" by a third party. Rate limiting before deployment.
- The status guard adds one read per tenant request in future modules; no cache, because a suspension must be immediate.
- `GET /api/organization` must be exempt from the guard, otherwise a suspended tenant cannot learn its status.
- A `REJECTED` organization keeps its unique `tenant_id` and its stores; no purge is planned.

## Product-owner decisions, round 3 (2026-11-08)

The PO answered the round 2 questions by accepting the recommendations. **These decisions close PO-1 to PO-10 and prevail over the earlier text.** Everything not listed here stays as in the round 2 sections and the Architect validations.

| # | Question | Decision |
|---|---|---|
| PO-1 | Can a suspended tenant close its organization? | **No: refused with `ORGANIZATION_SUSPENDED`**, consistent with "a suspended tenant can do nothing". Accepted consequence: a suspended tenant cannot withdraw and its legal name stays locked until the platform reinstates it (platform closure is a later ticket). The closable set becomes `{ACTIVE}`. T5d ("closable from `SUSPENDED`") is superseded. T9g names this behaviour change (closing from `SUSPENDED` goes from 200 to 409). Criterion D8 is no longer provisional. |
| PO-2 | Reasons | **Mandatory for suspension and rejection, 500 characters maximum.** None for activation or reinstatement. |
| PO-3 | Who reads the audit | **Record now (T9f), read later** through a platform-only route in a separate ticket. Until then there is no read route. |
| PO-4 / PO-8 | Login and re-registration after a refusal | **The Architect's choice is kept:** the legal name is released after a rejection, the owner's e-mail stays locked, and the owner of a `REJECTED` (and of a `PENDING_ACTIVATION`) organization can log in to see the dedicated message. No change in `identity`. Accepted cost: a customer who mistyped the e-mail cannot re-register with it. Confirmed: there is no external client for the changed codes. |
| PO-5 | Who writes the customer-facing text | **The front end**, from the `code`. The server keeps `message` as developer-facing English; no server-supplied customer text. The `endpoints-T5.md` convention is unchanged. |
| PO-6 | Dedicated codes for `SUSPENDED` and `REJECTED` | Confirmed (recommendation accepted): dedicated codes for the three states, `CLOSED` unchanged. |
| PO-7 | Rejection | **Final, no undo.** (The partial unique index on the legal name relies on this.) |
| PO-9 | Tenant read while suspended | **`GET /api/organization` answers even when the organization is `SUSPENDED`**, so the client can learn its status; it is the explicit exception to "a suspended tenant can do nothing". Other future reads are refused like actions. |
| PO-10 | Reason and owner e-mail visibility | **The tenant does not see the suspension or rejection reason in this ticket** (only the code). Every platform administrator may see the owner's e-mail in the platform list. |

Still open and unchanged: **T9a** (how the first platform administrator is created; the PO asked for no change for now). Every other PO question of the earlier rounds is answered.

Effect on slices: **T9g is unblocked** (PO-1, PO-5 and PO-6 were its only dependencies) and is the first slice to implement. T9e keeps its dependency on the other slices only.

## Open questions

**Product owner**
- P1. **T9a:** which option for the first administrator (1 env vars, 2 migration, 3 operator tool, 4 hybrid)? Forced password change at first login? Audit of the creation?
- P2. **Refusing a registration.** Validation is now a gate, but there is no "reject": a refused organization stays `PENDING_ACTIVATION` forever and the tenant cannot close it (`ORGANIZATION_CANNOT_BE_CLOSED`, T5d open question). Is "not validated" enough, or do we need a reject outcome (new status or tenant abandon)? Out of scope here unless you say otherwise.
- P3. **Audit.** Must the platform record who validated, suspended or reinstated (admin id, reason on suspension)? Today events carry no actor and a suspension has no reason.
- P4. **What the platform may see in responses.** Proposal: `tenantId` in the listing only. Does the reviewer need owner contact (e-mail) to decide? That crosses `organization` and `identity` and would need its own design.
- P5. **Customer messaging in `PENDING_ACTIVATION`.** Decision 3c keeps the existing 409 codes. Is that enough for the customer, or do you want a dedicated reason on the tenant side later?
- P6. **What a suspended tenant can do.** Can its users still log in? Proposal in this ticket: yes (only organization changes are refused, close still allowed). Confirm.

**Architect**
- A1. Where the platform administrator lives: `identity` (a second aggregate, no tenant), or a new bounded context `platform`? Which schema?
- A2. Naming and mergeability: platform activation as a new command/handler next to the tenant one (removed in T9d), or an in-place change merged with T9d. Which package for the platform controller (organization or bootstrap)?
- A3. Shape of the cross-tenant read and the listing: a separate port, or new methods on `OrganizationRepository` with an explicit name; how it stays unreachable from tenant handlers; behaviour under future RLS.
- A4. Claim or role design for the platform identity in the T7b JWT, separate key or audience, TTL, login route (`/api/platform/auth/login`?), and the codes `AUTHENTICATION_REQUIRED` and `PLATFORM_ACCESS_DENIED`.
- A5. Repeated suspension: add `ORGANIZATION_ALREADY_SUSPENDED` (changes the existing rule, as T5e-0 did for activation) or keep `ORGANIZATION_CANNOT_BE_SUSPENDED`. The BA recommends adding it, to follow the repeat convention.
- A6. Pagination validation: pin texts for `page` / `size` (T6 rules for request parameters, `HandlerMethodValidationException`) or validate explicitly; extension of the malformed-parameter message beyond UUIDs.
- A7. Whether the shared web component extraction (T6) is triggered by the platform zone, and the fate of the `/smoke/.../activate` shortcut after T9d.

### Open questions, round 2 (each with the BA recommendation)

The old P2 to P6 above are answered by round 2 and closed; P1 (T9a) stays open.

**Product owner**
- **PO-1 (suspension and closure).** "Do nothing" while suspended: does it also forbid the tenant to close its organization? Today `OrganizationMustBeClosableRule` accepts `SUSPENDED`. Recommendation: refuse (`ORGANIZATION_SUSPENDED`), consistent with "nothing". Impact to accept: a suspended tenant cannot withdraw and its legal name stays locked until the platform reinstates it (platform closure is a later ticket).
- **PO-2 (reasons).** Is the reason mandatory for suspension and for rejection, and what maximum length? Recommendation: mandatory for both, 500 characters; none for activation or reinstatement.
- **PO-3 (audit reading).** Who can read the trace, and from where? Recommendation: record now (T9f), read later through a platform-only route in a separate ticket; do you accept to ship without a way to read it?
- **PO-4 (login by status).** Can the owner of a `PENDING_ACTIVATION` or `REJECTED` organization log in? Recommendation: yes for both, so the application can show the dedicated message.
- **PO-5 (customer text).** Who writes and shows the customer-facing text? `endpoints-T5.md` says `message` is developer-facing English and clients branch on `code`. Recommendation: keep the convention, the front end shows the business text per code in the customer's language. A server-supplied customer text would need its own field, a language rule and a wording owner (a contract change). Please also validate the wording, for example: "Your organization is waiting for validation by the platform. You will be able to use the application as soon as it is validated."
- **PO-6 (consistency).** Confirm that `SUSPENDED` and `REJECTED` also get dedicated codes and that `CLOSED` stays unchanged.
- **PO-7 (rejection is final).** Can the platform undo a rejection? Recommendation: no; a refused customer registers again (which makes PO-8 important).
- **PO-8 (re-registration).** Does a rejected organization keep its legal name and its owner's e-mail locked (as a `CLOSED` one does for the name)? Recommendation: release both so a customer can register again with corrections. Also confirm there is no external client for the changed codes (T7 decision 13).
- **PO-9 (reads while suspended).** When tenant read endpoints exist, are they refused in `SUSPENDED` too? Recommendation: yes, decided when the first read is built.
- **PO-10 (reason and e-mail visibility).** Does the tenant see the suspension or rejection reason? Recommendation: not in this ticket. And confirm that every platform administrator may see the owner's e-mail.

**Architect**
- **A-1 (owner e-mail).** Option A, B or C? Recommendation: C, with a fail-fast startup check if the adapter is missing and a null `ownerEmail` when the owner is not found. If A, decide before T7c merges.
- **A-2 (audit storage).** Journal, actor on events, or both; schema and location of the journal; how the platform controller obtains the admin id without `CurrentTenantProvider` and without `organization` referencing `identity` (a plain UUID in the command plus a platform-admin provider port, probably in `shared`). Recommendation: journal as source of truth, events untouched in this ticket.
- **A-3 (`REJECTED` model).** Event content (reason: no), final-state rule, unique legal-name index and `existsByLegalName` if PO-8 releases the name (partial unique index excluding `REJECTED`), and what happens to the owner `User` in `identity` if the e-mail is released. Decide together with PO-8.
- **A-4 (dedicated codes).** A `switch` inside each of the three `MustBeActive...` rules and the closable rule, or one shared guard rule. Recommendation: one shared guard evaluated first, with the generic codes remaining for `CLOSED` only.
- **A-5 (gate beyond organization).** How "a suspended tenant can do nothing" holds for future modules and reads (status via an event and a local table per module, a shared-kernel port implemented by `bootstrap`, or the security chain), and how the client learns the status without attempting an action. Recommendation: a small shared-kernel port plus a tenant "my organization" read in a later ticket; do not put the status in the token (stale).
- **A-6 (sequencing).** Confirm the slicing, that T9g can ship first, and the placement of T9h relative to T7c.
