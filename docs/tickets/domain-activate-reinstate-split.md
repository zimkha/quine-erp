# T5e-0: Split organization activation into activate (first time) and reinstate (lifting a suspension)

- **Status:** written by the BA against `main` at `382fa99`. The domain and application layers are identical at `34de4a2`. **Validated by the Architect with changes (2026-09-28); the changes are already applied below.**
- **Blocks:** T5e. **Blocked by:** nothing (see Readiness).
- **Related:** `docs/tickets/endpoints-T5.md` (T5e and the Architect decisions there).

## Architect decisions

| # | Question | Decision |
|---|---|---|
| A1 | Narrow the activate rule, or replace it? | **Narrow `OrganizationMustBeActivatableRule` in place and keep its name.** "Activatable" still describes the check, and it mirrors `OrganizationMustBeClosableRule`: a status-set rule whose code depends on the status. This is the smallest diff, and `ruleName` is internal. |
| A2 | One code, or split it? | **Split it, like close does.** `ACTIVE` → `ORGANIZATION_ALREADY_ACTIVE` ("Organization is already active"). `SUSPENDED` or `CLOSED` → `ORGANIZATION_CANNOT_BE_ACTIVATED` (message unchanged). Decision 3 promises that a retry can tell "done" from "refused", and a single code covering all three statuses breaks that promise. It was already wrong for `CLOSED`, so this corrects decision 3. For **reinstate**, `ACTIVE` → `ORGANIZATION_ALREADY_ACTIVE` too (same target state), and `PENDING_ACTIVATION` or `CLOSED` → `ORGANIZATION_CANNOT_BE_REINSTATED`. |
| A3 | Build the tenant-scoped Reinstate command and handler now? | **No. This ticket is the domain split only.** A `ReinstateOrganizationCommand(TenantId, UUID)` would answer PO decision 2 ("the tenant reinstates") before the PO has. T9 needs a platform actor and an unscoped load. A tenant-scoped reinstate bean sitting in the context is exactly what a later controller could wire by mistake. The command, handler, result and bean come with their actor in the suspension-lifecycle ticket or T9. |
| A4 | A distinct event, or `OrganizationActivated` with a flag? | **A distinct `OrganizationReinstated`, type `organization.reinstated.v1`.** Same four fields and `TYPE` constant as `OrganizationSuspended`. A flag would change the `organization.activated.v1` schema and make every consumer branch on it. No reason or actor field for now. |
| R1 | Suspend command and handler | **A separate "suspension lifecycle" ticket** (before T9, or part of it), which bundles the Suspend and Reinstate commands and handlers. It should also apply the A2 convention to suspend: `SUSPENDED` → `ORGANIZATION_ALREADY_SUSPENDED` instead of today's `ORGANIZATION_CANNOT_BE_SUSPENDED`. |
| R2 | `OrganizationCannotBeClosedTwiceRule` cleanup | **Fold it into this ticket, as a separate commit.** It is a one-file deletion with no references (verified). |

## Context / Why

Today `Organization.activate()` is the only way to make an organization `ACTIVE`. It is guarded by `OrganizationMustBeActivatableRule`, which accepts both `PENDING_ACTIVATION` and `SUSPENDED`. So "first activation" and "lifting a suspension" are the same operation.

If T5e exposes activate to tenants, a tenant could lift a suspension that the platform put on it. Nothing can suspend an organization today (`suspend()` has no command or handler), so the gap can't be exploited yet. It opens the moment suspension ships.

This ticket closes the gap in the domain before any endpoint exists:
- `activate()` becomes first activation only.
- A new `reinstate()` handles leaving a suspension.

## Actors

- **Activate:** unchanged. The owning tenant, through the existing tenant-scoped command. Its REST exposure is T5e, which depends on PO decision 2.
- **Reinstate:** no actor in this ticket. It exists only as a domain operation, exercised only by domain tests. Its command, handler and actor come in the suspension-lifecycle ticket or T9, which also depend on PO decision 2.

## Technical story

As the organization bounded context, I want first activation and reinstatement to be separate operations, with separate rules and events. Then whoever may activate an organization can't, by the same means, lift a suspension imposed by someone else.

## Scope

- **Domain, `activate()`:** accepts only `PENDING_ACTIVATION`.
  - `OrganizationMustBeActivatableRule` is narrowed in place and keeps its name.
  - Its code depends on the status, as in `OrganizationMustBeClosableRule`:
    - `ACTIVE` → `ORGANIZATION_ALREADY_ACTIVE`, message `"Organization is already active"`
    - `SUSPENDED` or `CLOSED` → `ORGANIZATION_CANNOT_BE_ACTIVATED`, message `"Organization cannot be activated from status '<status>'"` (unchanged)
- **Domain, new `reinstate(UUID eventId, Instant occurredAt)`:** moves `SUSPENDED` → `ACTIVE`, with the same null checks as `suspend()`, run before the rule.
  - It is guarded by the new record `OrganizationMustBeSuspendedToBeReinstatedRule(OrganizationStatus currentStatus)`.
    - `currentStatus` is null-checked, and the rule is broken whenever it isn't `SUSPENDED`.
    - `ACTIVE` → `ORGANIZATION_ALREADY_ACTIVE`, `"Organization is already active"`
    - `PENDING_ACTIVATION` or `CLOSED` → `ORGANIZATION_CANNOT_BE_REINSTATED`, `"Organization cannot be reinstated from status '<status>'"`
  - It raises a new record in `domain/event`, `OrganizationReinstated(UUID eventId, OrganizationId organizationId, TenantId tenantId, Instant occurredAt)`. It follows `OrganizationSuspended` exactly:
    - `public static final String TYPE = "organization.reinstated.v1"`
    - null checks on every field
    - `eventType()` returns `TYPE`
- **Cleanup (in its own commit):** delete the unused `domain/rule/OrganizationCannotBeClosedTwiceRule.java`.
- **No changes** in `application/`, `infrastructure/` (including `OrganizationConfiguration`) or `presentation/`.

## Out of scope

- Any REST endpoint.
- A `ReinstateOrganizationCommand`, `ReinstateOrganizationHandler`, `ReinstateOrganizationResult` or their bean. These come with the actor in the suspension-lifecycle ticket or T9 (A3).
- A `SuspendOrganization` command or handler, and changing `OrganizationMustBeActiveToBeSuspendedRule`'s code for `SUSPENDED` (suspension-lifecycle ticket, R1).
- The platform path, the unscoped load port, and deciding who suspends or reinstates (T9, PO decision 2).
- Changes to `close()`. It stays allowed from `ACTIVE` or `SUSPENDED`.
- Suspension reasons, durations or automatic expiry.

## Acceptance criteria

**Domain: activate**
1. **Kept behaviour.** Given a `PENDING_ACTIVATION` organization, when it is activated, then:
   - the status is `ACTIVE`
   - exactly one `OrganizationActivated` event is raised (`organization.activated.v1`, fields unchanged)
2. **Suspended is refused (new).** Given a `SUSPENDED` organization, when `activate()` is called, then:
   - a `BusinessRuleViolationException` is raised with code `ORGANIZATION_CANNOT_BE_ACTIVATED`, `ruleName` `OrganizationMustBeActivatableRule`, and a message containing `SUSPENDED`
   - the status stays `SUSPENDED`, and no event is raised
3. **Already active (code changed).** Given an `ACTIVE` organization, when `activate()` is called, then:
   - the code is `ORGANIZATION_ALREADY_ACTIVE`, with the message `"Organization is already active"`
   - the status is unchanged, and no event is raised
4. **Closed is refused.** Given a `CLOSED` organization, when `activate()` is called, then:
   - the code is `ORGANIZATION_CANNOT_BE_ACTIVATED`
   - the status stays `CLOSED`, and no event is raised
   - Neither AC 3 nor AC 4 has a domain test today; add both to `OrganizationTest`.

**Domain: reinstate**

5. **Happy path.** Given a `SUSPENDED` organization (register → activate → suspend → `clearDomainEvents`), when it is reinstated, then:
   - the status is `ACTIVE`
   - exactly one `OrganizationReinstated` event is raised, carrying the given `eventId`, the `organizationId`, the `tenantId` and the given `occurredAt`
   - its `eventType()` is `"organization.reinstated.v1"`
6. **Refused.** In each case below, the status is unchanged, no event is raised, and `ruleName` is `OrganizationMustBeSuspendedToBeReinstatedRule`:
   - `PENDING_ACTIVATION` → `ORGANIZATION_CANNOT_BE_REINSTATED`
   - `CLOSED` → `ORGANIZATION_CANNOT_BE_REINSTATED`
   - `ACTIVE` → `ORGANIZATION_ALREADY_ACTIVE`
7. **Null guards.** On an `ACTIVE` organization, where the rule would break, a null `eventId` or a null `occurredAt` throws `NullPointerException`, not `BusinessRuleViolationException`, and no event is raised.
8. **Reinstated means fully active.** A reinstated organization can add a store, be suspended again, and be closed.

**Handler: activate**

9. **Suspended is refused.** Given A's `SUSPENDED` organization in `InMemoryOrganizationRepository`, when A sends `ActivateOrganizationCommand`, then:
   - the code is `ORGANIZATION_CANNOT_BE_ACTIVATED`
   - nothing is published
   - the stored organization is still `SUSPENDED`
10. **Kept behaviour.** Every existing `ActivateOrganizationHandlerTest` scenario still passes. The one exception is `shouldRejectAlreadyActiveOrganization`:
    - its **code** assertion becomes `ORGANIZATION_ALREADY_ACTIVE`
    - its `ruleName` assertion is unchanged

**Cleanup**

11. `OrganizationCannotBeClosedTwiceRule` no longer exists, and `mvn -pl organization -am clean install` passes.

## Business rules

- An organization can be **activated** only from `PENDING_ACTIVATION`.
- An organization can be **reinstated** only from `SUSPENDED`.
- A `CLOSED` organization can be neither activated nor reinstated, because closing is irreversible.
- **Activation rule:** `OrganizationMustBeActivatableRule` (narrowed). `ACTIVE` → `ORGANIZATION_ALREADY_ACTIVE`; `SUSPENDED` or `CLOSED` → `ORGANIZATION_CANNOT_BE_ACTIVATED`.
- **Reinstatement rule:** `OrganizationMustBeSuspendedToBeReinstatedRule`. `ACTIVE` → `ORGANIZATION_ALREADY_ACTIVE`; `PENDING_ACTIVATION` or `CLOSED` → `ORGANIZATION_CANNOT_BE_REINSTATED`.
- **Code convention:**
  - `…_ALREADY_…` means the target state is already reached.
  - `…_CANNOT_BE_…` means the operation is refused from the current status.

## Findings from the code

1. **No existing test relies on `SUSPENDED` → `ACTIVE` through `activate()`.**
   - Every fixture that reaches `SUSPENDED` does `activate()` then `suspend()`, which stays valid. That includes the T5 controller tests.
   - The only assertion that changes is the **code** assertion in `ActivateOrganizationHandlerTest.shouldRejectAlreadyActiveOrganization`, which becomes `ORGANIZATION_ALREADY_ACTIVE` (A2). Its `ruleName` assertion is unchanged (A1).
2. **It's safe to merge now, under any answer to PO decision 2.**
   - The transitions only become more restrictive.
   - No production code calls `suspend()`, and no deployed database exists (PO, 2026-09-27).
   - One error code changes (ACTIVE → `ORGANIZATION_ALREADY_ACTIVE`), but activate isn't exposed over REST, so no client depends on it.
   - No command or handler presupposes who reinstates (A3).
3. **No persistence impact, and no migration.**
   - V1 `ck_organizations_status` already allows all four statuses.
   - There is no event or outbox table.
   - The entity and mapper are unchanged.
4. **The refusal messages stay generic.** A "use reinstate" hint would point a tenant at an operation it can't call. This is no cross-tenant leak: ownership is checked first, so the "already active" message only ever tells the owner about its own organization.

## Dependencies

- **Blocking:** none.
- **Merge order:** must merge before T5e is designed or implemented. It touches only `domain/` and the tests, so it doesn't conflict with the T5 presentation work.

## When this ticket is needed (by PO decision 2)

| Who activates / who reinstates | This ticket |
|---|---|
| Tenant activates, platform suspends and reinstates | **Required** before T5e |
| Tenant activates, no suspension concept | **Required (Architect recommendation)**, so that adding suspension later can't open a hole |
| Tenant activates and also reinstates | **Optional**: no security gap, but it gives a distinct audit event |
| Platform activates and reinstates | **Optional / unnecessary**: T5e is cancelled |

**Readiness:** it can be built **and merged now**, under any answer.

## Definition of done

- **`OrganizationTest`:** AC 1–8, asserting both the event type string and the `TYPE` constant.
- **Rule unit tests (optional):** both rules across every `OrganizationStatus`, checking code and message.
- **`ActivateOrganizationHandlerTest`:** AC 9, plus the one code-assertion change in AC 10.
- **No changes to:**
  - `OrganizationConfiguration` or `OrganizationConfigurationIT`
  - migrations (none added)
  - anything under `presentation/`
- **Everything else stays green,** including the `*IT` suites run explicitly (`OrganizationAggregatePersistenceIT`, `OrganizationConfigurationIT`).
- **The dead-rule deletion (AC 11) is its own commit.**

## Open questions

**Architect**, all resolved above:
- ~~A1: narrow or replace the rule~~
- ~~A2: one code or two~~
- ~~A3: reinstate command and handler now~~
- ~~A4: distinct event~~
- ~~R1: suspend command and handler~~
- ~~R2: dead-rule cleanup~~

**Product owner**
- **Decision 2 (still open):** who activates, who suspends, and who reinstates? This ticket doesn't depend on the answer.
- **If the tenant activates:** is `PENDING_ACTIVATION` a real gate (KYC, payment)?
- *(Moved to the suspension-lifecycle or T9 ticket; doesn't block this one:)* should a reinstatement record a reason or an actor?

**Architect note (not a blocker).** T5d lets the tenant close a `SUSPENDED` organization. That isn't an escape from suspension: closing is irreversible, and more restrictive.

## How T5e's text changes once this lands

These changes are already applied to `docs/tickets/endpoints-T5.md`.

- **Contract:**
  - 409 `ORGANIZATION_ALREADY_ACTIVE` when the status is `ACTIVE`. This is a repeat, and clients treat it as success.
  - 409 `ORGANIZATION_CANNOT_BE_ACTIVATED` when it is `SUSPENDED` or `CLOSED`.
  - Activation is allowed only from `PENDING_ACTIVATION`.
- **New AC:**
  - A's `SUSPENDED` organization → `POST /activation` gives 409 `ORGANIZATION_CANNOT_BE_ACTIVATED`, and the status stays `SUSPENDED` in Postgres.
  - A repeat on an `ACTIVE` organization gives 409 `ORGANIZATION_ALREADY_ACTIVE`.
