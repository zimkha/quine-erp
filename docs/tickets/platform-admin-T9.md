# T9: Platform administration: validate, suspend and reinstate organizations

- **Status:** drafted by the BA against `main` (2026-11-08). Product owner decisions 1-4 recorded on 2026-11-08 (table below). **Architect validation: pending.** **Slice T9a is blocked on a product-owner decision (see T9a).** Technical constraints in "Constraints to validate" are formulated as proposals for the Architect, not decisions.
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
| 3 | The blocking point | A `PENDING_ACTIVATION` organization can neither add a store, nor change headquarters, nor deactivate a store (the `OrganizationMustBeActiveTo...Rule` classes), so a new customer can do nothing before validation. **Lifted by this recommendation, recorded as a decision:** (a) the tenant endpoint `POST /api/organizations/{id}/activation` is **removed in T9** (not earlier, so the module is never left without any activation path), and its tenant-side handler and command are replaced by the platform version; (b) until T9, the shortcut `/smoke/organizations/{id}/activate` is the only way to activate locally, restricted to local profiles by the T7a startup guard; (c) the `PENDING_ACTIVATION` state must be explicit to the customer: the tenant endpoints refused in `PENDING_ACTIVATION` **keep their existing 409 codes, no contract change**. |
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
