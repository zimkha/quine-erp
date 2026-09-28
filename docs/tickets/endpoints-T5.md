# Tenant-scoped organization REST endpoints: tickets T5a–T5e

- **Status:** written by the BA against `main` at `c035c0d`, after T1–T4 were merged, and validated by the Architect. The Architect's changes are already applied to the ticket text below.
  - T5a, T5b and T5c are validated with changes.
  - T5d is validated with changes. **Its merge blockers are resolved** (product owner, 2026-09-28: the tenant closes, and no confirmation is needed).
  - T5e is **blocked** on product-owner decision 2. Its domain prerequisite, T5e-0 (`docs/tickets/domain-activate-reinstate-split.md`), is validated.
- **Source:** the "Later tickets" section of `docs/tickets/tenant-scoping-T1-T4.md`.
- **Workflow:** BA → Architect → Developer → Lead Developer (see `CLAUDE.md`).

## Findings from the code

1. **T5a–T5d can't succeed end to end through the API until something can activate an organization.**
   - Registration creates organizations in `PENDING_ACTIVATION`.
   - Add store, change headquarters and deactivate store all require `ACTIVE`. Close requires `ACTIVE` or `SUSPENDED`.
   - While T5e is blocked, every T5a–d call on an organization created over HTTP returns 409.
   - The tickets can still be built and tested, with tests seeding an `ACTIVE` organization through the handlers. But they deliver no usable value until decision 2 is answered.
2. **Until identity exists, the endpoints always return 401.**
   - `organization` main code has no `CurrentTenantProvider` bean. Once the controller injects one, any context that loads it must supply a provider.
   - The only planned default is the fail-closed one in `bootstrap`, which always throws.
   - So a real caller gets 401 until T7.
3. **Two input errors aren't mapped by `ApiExceptionHandler` today:**
   - a malformed path UUID (`MethodArgumentTypeMismatchException`)
   - malformed JSON, a missing body, or a non-UUID body field (`HttpMessageNotReadableException`)

   Both fall through to Spring's default 400, which doesn't use the `ApiErrorResponse` shape. T5 introduces the first path variables and the first UUID body field, so this becomes reachable. The fix is in T5a's scope.
4. **Validation runs before the tenant is resolved.** `@Valid` and path conversion run before the controller body, so a malformed request with no tenant gets 400, not 401. This reveals no tenant data. See cross-cutting question 5.
5. **`ORGANIZATION_MUST_KEEP_ONE_ACTIVE_STORE` can't be reached through deactivate store.** The headquarters can't be deactivated, and an inactive store can't become headquarters, so the headquarters is always active. The rule stays covered by domain tests only.
6. **`PUT /headquarters` isn't idempotent in practice.** A repeat returns 409 `STORE_IS_ALREADY_HEADQUARTERS`.
7. **`STORE_ID_ALREADY_EXISTS` can't be triggered by a client**, because the store id is server-generated.
8. **Several register-endpoint tests throw exceptions register can never produce.** `shouldReturnNotFoundWhenOrganizationDoesNotExist`, `shouldMapWrongTenantAndMissingOrganizationToIdenticalNotFound`, `shouldReturnConflictWhenBusinessRuleIsViolated` and `shouldReturnConflictOnConcurrentModification` drive `ApiExceptionHandler` through register. T5 gives them real endpoints to move to.

---

## Architect decisions

| # | Question | Decision |
|---|---|---|
| 1 | Response shape | **One response DTO per result** in `presentation/rest/response`, mapped field for field like `RegisterOrganizationResponse`. Returning the full organization needs a read model that doesn't exist. A 204 would lose `storeId` and the timestamps, and the client has no GET endpoint to recover them. |
| 2 | `Location` and 201 on add store | **201 with the body, and no `Location` on any endpoint.** A `Location` pointing at a URL that returns 404 or 405 is misleading, and the body already carries `storeId`. `Location` is added to register and add store together once a GET endpoint exists. |
| 3 | Idempotency of repeated actions | **Keep the domain's 409 "already" codes on every T5 endpoint, and keep `PUT /headquarters`.** HTTP idempotency is about the effect on the server, not the status code. A client retrying after a timeout treats `STORE_IS_ALREADY_HEADQUARTERS`, `STORE_ALREADY_INACTIVE`, `ORGANIZATION_ALREADY_CLOSED` and `ORGANIZATION_ALREADY_ACTIVE` as "target state already reached". `…_CANNOT_BE_…` codes are never success: they mean the action is refused from the current status. *(Corrected 2026-09-28 in T5e-0: `ORGANIZATION_CANNOT_BE_ACTIVATED` had been listed by mistake, since it was already returned for `CLOSED`.)* Silent no-ops would be a separate domain ticket. |
| 4 | Moving the register-driven mapping tests | **Confirmed.** Three tests move to `TenantOrganizationControllerTest`, driven through add store: `…NotFound…`, `…BusinessRuleIsViolated` and `…ConcurrentModification`. The identical-404 test is rewritten with the real `AddStoreHandler`. `…DataIntegrityViolation` stays on register, which can really raise it (the legal-name race). |
| 5 | 400 vs 401 precedence | **400 before 401 for now (Spring's natural order).** It reveals no tenant data. Revisit in T7, when a security filter authenticates before validation. |
| 6 | Where the provider comes from at runtime | **T5 waits for neither `bootstrap` nor T7.** The "make `bootstrap` runnable" ticket **must** include the fail-closed `CurrentTenantProvider` bean, because the context won't start without one once a controller injects it. Every tenant-scoped endpoint answers 401 until T7. |
| 7 | Controller split | **Split by tenant scope.** `OrganizationController` keeps register only and does **not** inject `CurrentTenantProvider`. A new `TenantOrganizationController` in `com.zim.organization.presentation.rest` (`@RequestMapping("/api/organizations/{id}")`) injects the provider and the tenant-scoped handlers, and stays inside `ApiExceptionHandler`'s scope. Its tests go in a new `TenantOrganizationControllerTest`. |
| 8a | Where the prerequisite 400 mappings live | **In T5a.** Fixed messages: `"<param>: must be a valid UUID"`, using `getName()` and never the value, with explicit `@PathVariable("id")` and `@PathVariable("storeId")`; and `"Request body is missing or malformed"`. They also apply to register. |
| 8b | `storeCode` and `storeName` validation | **Correct, and identical to register.** The DTO pattern equals the `StoreCode` format, so `INVALID_STORE_CODE` can't be reached. `" a"` passes the DTO and then gets 422 `INVALID_STORE_NAME`. Known edge case, accepted: a name of 121 or more characters that trims to 120 or fewer gets a 400. |
| 9 | T5b: unknown store id, and `previousHeadquartersId` | **Keep 409 `STORE_DOES_NOT_BELONG_TO_ORGANIZATION`** on T5b and T5c. Ownership is already proven, so nothing leaks. **No `previousHeadquartersId`.** |
| 10 | T5c: 200 or 204 | **200 with a body** (decision 1). |
| 11a | Echo `tenantId` on close and activate | **No.** Only register keeps `tenantId`, because that's where the tenant is created. |
| 11b | T5d: build now, merge after decision 2 | **Agreed.** The confirmation question (retyping the legal name) must also be answered before merge, because it would change the request. **Resolved 2026-09-28:** the tenant closes, and no confirmation is needed. |
| 12 | T5e | **Blocked on decision 2.** Domain prerequisite: T5e-0 (`activate()` only from `PENDING_ACTIVATION`, a new `reinstate()` for `SUSPENDED`, `ORGANIZATION_ALREADY_ACTIVE` for repeats). The Reinstate and Suspend commands and handlers come with their actor in the suspension-lifecycle ticket or T9. |

**Other Architect notes:**
- `OrganizationCannotBeClosedTwiceRule` was dead code. It is removed in T5e-0.
- `SUSPENDED` can't be reached today, because nothing calls `suspend()`. Tests that need it seed it through the aggregate: `register` → `activate` → `suspend` → `repository.save`.
- Out of scope, as a known follow-up: 405 and 415 still use Spring's default error body, not `ApiErrorResponse`.

---

## Contract shared by all T5 tickets

**Where the tenant comes from.** The controller calls `CurrentTenantProvider.currentTenant()` once per request and passes the result as the command's `tenantId`.
- The tenant is never read from the path, the query string, the body or any header.
- No request DTO declares a `tenantId`.
- `{id}` is only the organization id.

**Order of steps:**
1. Request-format validation (400)
2. Tenant resolution (401)
3. Handler: ownership (404), then value-object construction (422), then business rules (409), each in the domain's order

**Error mapping (`ApiErrorResponse {code, message, timestamp}`):**

| Status | Code | When |
|---|---|---|
| 400 | `VALIDATION_FAILED` | Bean validation fails. **New in T5a:** also a malformed path UUID and a malformed or missing JSON body. The messages are fixed: `"<param>: must be a valid UUID"` and `"Request body is missing or malformed"`. Raw input is never echoed. |
| | | **Bean validation text (T5f):** `"<field>: <text>"` with the fixed English texts `must not be blank` (`@NotBlank`), `must not be null` (`@NotNull`), `size must be between {min} and {max}` (`@Size`), `must match the required format` (`@Pattern`), and `is invalid` for anything else. Parts are sorted and deduplicated. Built from the constraint type, never locale-resolved. See `docs/tickets/validation-message-locale.md`. |
| 401 | `TENANT_NOT_RESOLVED` | The provider throws. Header `WWW-Authenticate: Bearer realm="quine-erp"`. The handler is never called. |
| 404 | `ORGANIZATION_NOT_FOUND` | The organization is missing **or** belongs to another tenant. Bodies are identical apart from `timestamp`. |
| 409 | rule code (per ticket) | `BusinessRuleViolationException` |
| 409 | `CONCURRENT_MODIFICATION` | `OptimisticLockingFailureException` |
| 409 | `DATA_INTEGRITY_VIOLATION` | A database constraint rejected the write. Generic message. |
| 422 | value-object code | A domain `InvalidValueException` that DTO validation didn't catch |

**Business rules for every T5 ticket:**
- No operation runs without a resolved tenant.
- To another tenant, an organization it doesn't own is indistinguishable from a missing one. A 409 is never returned for another tenant's organization.

**Conventions:**
- **Error `message` is developer-facing English (T5f, PO 2026-09-28):** clients must branch only on `code`, never parse `message`. The server never localizes errors and ignores `Accept-Language` for them. Localized end-user text is the client's job, mapping `code`.
- **Repeated actions:** a repeat returns its 409 "already" code. Clients treat `STORE_IS_ALREADY_HEADQUARTERS`, `STORE_ALREADY_INACTIVE`, `ORGANIZATION_ALREADY_CLOSED` and `ORGANIZATION_ALREADY_ACTIVE` as "target state already reached". `…_CANNOT_BE_…` codes (e.g. `ORGANIZATION_CANNOT_BE_CLOSED`, `ORGANIZATION_CANNOT_BE_ACTIVATED`) mean the action is refused from the current status, and must not be treated as success. No T5 endpoint is a silent no-op.
- **No `tenantId` in responses:** no T5 response echoes it.
- **Controllers:** tenant-scoped endpoints live in `TenantOrganizationController` (`presentation.rest`). `OrganizationController` keeps register only and doesn't inject `CurrentTenantProvider`.
- **Out of scope:** mapping 405 and 415 into `ApiErrorResponse`.

**Product-owner decisions 1, 3 and 4** don't affect T5a–d. Decision 2 is covered in T5d and T5e.

**Product-owner decisions recorded 2026-09-28:** the **tenant closes** its organization (decision 2, closing half), and closing needs **no confirmation step**. Who activates, suspends and reinstates is still open.

---

## T5a: Add a store to my organization

**User story.** As a tenant, I want to add a store to my active organization, so that I can run a new point of sale under the same legal entity.

**Scope**
- `POST /api/organizations/{id}/stores` → `AddStoreCommand(tenant, id, storeCode, storeName)`.
- Create `TenantOrganizationController` (`@RequestMapping("/api/organizations/{id}")`, explicit `@PathVariable("id")`). It injects `CurrentTenantProvider` and the tenant-scoped handlers. `OrganizationController` is unchanged. This sets the pattern for T5b–e.
- **Shared prerequisite:** map `MethodArgumentTypeMismatchException` (`"<param>: must be a valid UUID"`, using `getName()`) and `HttpMessageNotReadableException` (`"Request body is missing or malformed"`) to 400 `VALIDATION_FAILED`. The mappings apply to register too.
- Move the register-driven mapping tests from finding 8 onto this endpoint.

**Out of scope:**
- reading or listing stores
- editing or reactivating a store
- `Idempotency-Key`

**Contract**
- **Request body:**
  - `storeCode`: `@NotBlank`, `@Pattern("^[A-Za-z0-9][A-Za-z0-9_-]{1,19}$")`. The domain uppercases it.
  - `storeName`: `@NotBlank`, `@Size(2,120)`
- **Success:** **201** with body `{organizationId, storeId, storeCode, storeName, headquarters, active, addedAt}`, mirroring `AddStoreResult`. `headquarters=false`, `active=true`. **No `Location` header**: it is added to register and add store together once a GET endpoint exists.
- **Errors:** the shared table, plus these, in evaluation order:
  - 422 `INVALID_STORE_NAME`: passes the DTO but is too short once trimmed, e.g. `" a"`. The value objects are built **before** the rules run, so `" a"` sent to a non-active organization gives 422, not 409.
  - 409 `ORGANIZATION_MUST_BE_ACTIVE_TO_ADD_STORE`: status is `PENDING_ACTIVATION`, `SUSPENDED` or `CLOSED`
  - 409 `STORE_CODE_ALREADY_EXISTS`: the code matches any existing store, active or inactive. The check is case-insensitive.
  - 409 `STORE_ID_ALREADY_EXISTS`: not triggerable by a client
  - `INVALID_STORE_CODE` can't be reached, because the DTO pattern equals the domain format.

**Acceptance criteria**
- **Happy path:**
  - Given tenant A's `ACTIVE` organization and the provider returns A.
  - When A posts `{"storeCode":"thies-02","storeName":"Magasin 2"}`.
  - Then the response is 201 with `storeCode="THIES-02"`, `headquarters=false`, `active=true`, and the store is saved.
- **Tenant only from the provider:**
  - Given the provider returns A, and the request carries a body `tenantId` and an `X-Tenant-Id` header, both set to B.
  - Then the command's `tenantId` is A. `AddStoreRequest` has no `tenantId`.
- **No tenant:** Given the provider throws `TenantNotResolvedException`, then the response is 401 with the `WWW-Authenticate` header and `TENANT_NOT_RESOLVED`, and the handler is never called.
- **Other tenant:**
  - Given A's organization in any status, including `CLOSED`, and B posts to its id.
  - Then the response is 404 `ORGANIZATION_NOT_FOUND`, identical apart from `timestamp` to the one for a random id. It is never 409, and A's stores are unchanged.
- **Missing organization:** 404 `ORGANIZATION_NOT_FOUND`.
- **Validation:**
  - `storeCode` of `"A"`, `"THIES 01"` or blank, or `storeName` of `"X"` or blank: 400 `VALIDATION_FAILED` with the message starting `"<field>: "`. The handler is never called.
  - A malformed path id, malformed JSON or a missing body: 400 `VALIDATION_FAILED`.
- **Business rules:**
  - Organization `PENDING_ACTIVATION`, `SUSPENDED` or `CLOSED`: 409 `ORGANIZATION_MUST_BE_ACTIVE_TO_ADD_STORE`.
  - `"thies-01"` when `THIES-01` exists, active or inactive: 409 `STORE_CODE_ALREADY_EXISTS`.
- **Domain value error:** `storeName=" a"` gives 422 `INVALID_STORE_NAME`, including on a `PENDING_ACTIVATION` organization (422 before 409).
- **Register also gets the new 400s:** register with malformed JSON returns 400 `VALIDATION_FAILED` in the `ApiErrorResponse` shape.
- **No echo:** the 400 messages don't contain the submitted value.
- **Concurrency:** an optimistic-lock conflict gives 409 `CONCURRENT_MODIFICATION`.

**Business rules**
- A store can only be added to an `ACTIVE` organization.
- Store codes are unique within an organization, ignoring case, including codes of inactive stores.
- A new store is active and is never the headquarters.

**Dependencies:** T2, T3 and T4 (merged). T5b–e depend on T5a for merge order and the prerequisite mappings.

**Definition of done**
- **`@WebMvcTest`:**
  - the full contract, with the provider as `@MockitoBean`
  - an `ArgumentCaptor` asserting the command carries the provider's tenant and the path id
  - `verifyNoInteractions(handler)` for the 400 and 401 cases
- **Tests moved off register**, into a new `TenantOrganizationControllerTest`:
  - `shouldMapWrongTenantAndMissingOrganizationToIdenticalNotFound` is rewritten with the real `AddStoreHandler` over `InMemoryOrganizationRepository` behind the mock.
  - `…NotFound…`, `…BusinessRuleIsViolated` and `…ConcurrentModification` move here and are removed from register.
  - `…DataIntegrityViolation` stays on register.
  - `OrganizationControllerTest` keeps its throwing provider mock and `verifyNoInteractions(currentTenantProvider)`.
- **Handler unit tests:** add three tests to `AddStoreHandlerTest`: a lowercase duplicate code (`"thies-01"`), a duplicate of an inactive store's code, and 422 before 409 on a `PENDING_ACTIVATION` organization.
- **End-to-end `*IT`** (MockMvc over the real handler, adapter and Postgres, with a `@TestConfiguration` stub provider):
  - Tenant A's `ACTIVE` organization is seeded through the register and activate handlers.
  - A adds a store: 201, and the row has A's `tenant_id`.
  - B adds a store to A's organization: 404, equal to the random-id 404, and A's store count is unchanged.
  - The stub provider must be settable, returning the tenant from the register result. A fixed constant won't work, because register generates the tenant.
  - This is the first MockMvc IT. It needs `@AutoConfigureMockMvc` and explicit `@Import`s (`OrganizationConfiguration`, `TenantOrganizationController`, `ApiExceptionHandler`, the stub provider), since the test apps don't component-scan.

**Open questions**
- ~~Architect: `Location`, where the 400 mappings live, moving the register tests~~ **Resolved** (decisions 2, 8a and 4).
- **Product owner:** should an inactive store's code stay reserved? The domain says yes today, and V1 `uk_stores_organization_code` enforces it.

---

## T5b: Change my organization's headquarters

**User story.** As a tenant, I want to make another of my active stores the headquarters, so that the organization's main site matches reality.

**Scope:** `PUT /api/organizations/{id}/headquarters` → `ChangeHeadquartersCommand(tenant, id, storeId)`.

**Out of scope:**
- choosing a headquarters in another organization
- creating the store in the same call
- a history of headquarters changes

**Contract**
- **Request body:** `storeId` is a `@NotNull` UUID. It maps to `newHeadquartersId`. A non-UUID value gives 400 through T5a's mapping.
- **Success:** **200** with `{organizationId, headquartersId, changedAt}`, mirroring `ChangeHeadquartersResult`. There is no `previousHeadquartersId`.
- **Verb:** `PUT` is kept, because its effect on the server is idempotent. A repeat gives 409 `STORE_IS_ALREADY_HEADQUARTERS`, which clients treat as success.
- **Errors:** the shared table, plus these, in domain order:
  - 409 `ORGANIZATION_MUST_BE_ACTIVE_TO_CHANGE_HEADQUARTERS`
  - 409 `STORE_DOES_NOT_BELONG_TO_ORGANIZATION`
  - 409 `STORE_IS_ALREADY_HEADQUARTERS`
  - 409 `INACTIVE_STORE_CANNOT_BECOME_HEADQUARTERS`

**Acceptance criteria**
- **Happy path:**
  - Given A's `ACTIVE` organization with headquarters H and active store S.
  - When A sends `PUT {"storeId":S}`.
  - Then the response is 200 with `headquartersId=S`, and there is exactly one headquarters.
- **Tenant only from the provider:** a caller-supplied tenant is ignored.
- **No tenant:** 401 with the header, and the handler is never called.
- **Other tenant:**
  - Given B sends A's organization id and a real store id of A.
  - Then the response is 404, identical to the missing-organization 404. It is never 409, even if A's organization isn't `ACTIVE` or S is inactive. A's headquarters is unchanged.
- **Missing organization:** 404.
- **Validation:** a missing, null or non-UUID `storeId`, or a malformed path id, gives 400, and the handler is never called.
- **Business rules:**
  - Organization not `ACTIVE`: 409 `ORGANIZATION_MUST_BE_ACTIVE_TO_CHANGE_HEADQUARTERS`.
  - A random store id, or another tenant's store: 409 `STORE_DOES_NOT_BELONG_TO_ORGANIZATION`. Both give the same response, so nothing leaks. The same-tenant, other-organization case can't be tested while `uk_organizations_tenant_id` allows one organization per tenant (decision 1).
  - The current headquarters: 409 `STORE_IS_ALREADY_HEADQUARTERS`.
  - An inactive store: 409 `INACTIVE_STORE_CANNOT_BECOME_HEADQUARTERS`.
- **Concurrency:** 409 `CONCURRENT_MODIFICATION`.

**Business rules**
- Only an `ACTIVE` organization can change its headquarters.
- The new headquarters must be a different, active store of the same organization.
- An organization always has exactly one headquarters.

**Dependencies:** T5a.

**Definition of done**
- **`@WebMvcTest`:** the contract, tenant capture, and 400/401 with no handler calls.
- **End-to-end `*IT`:** the happy path, plus B sends A's organization id and A's real store id: 404, and A's headquarters is unchanged in Postgres.
- **Handler tests:** covered by T2.

**Open questions:** ~~verb and idempotency, unknown store id, previous headquarters~~ **Resolved** (decisions 3 and 9).

---

## T5c: Deactivate one of my stores

**User story.** As a tenant, I want to deactivate a store that no longer trades, so that it stops being used while its history is kept.

**Scope:**
- `POST /api/organizations/{id}/stores/{storeId}/deactivation` → `DeactivateStoreCommand(tenant, id, storeId)`.
- No request body.

**Out of scope:**
- reactivating a store
- deleting a store
- automatically reassigning the headquarters

**Contract**
- **Success:** **200** with `{organizationId, storeId, active:false, deactivatedAt}`, mirroring `DeactivateStoreResult`.
- **Errors:** the shared table, plus these, in domain order:
  - 409 `ORGANIZATION_MUST_BE_ACTIVE_TO_DEACTIVATE_STORE`
  - 409 `STORE_DOES_NOT_BELONG_TO_ORGANIZATION`
  - 409 `HEADQUARTERS_CANNOT_BE_DEACTIVATED`
  - 409 `STORE_ALREADY_INACTIVE`
  - 409 `ORGANIZATION_MUST_KEEP_ONE_ACTIVE_STORE`: can't be reached here (finding 5). This is also guaranteed by V1 `ck_stores_headquarters_active`.

**Acceptance criteria**
- **Happy path:** Given A's `ACTIVE` organization with an active non-headquarters store S, then deactivating it returns 200 with `active=false`, and the headquarters is untouched.
- **Tenant only from the provider:** a caller-supplied tenant is ignored.
- **No tenant:** 401 with the header, and the handler is never called.
- **Other tenant:**
  - Given B sends A's organization id and A's real store id.
  - Then the response is 404, identical to the missing-organization 404. It is never 409, even if S is the headquarters or already inactive, and S is unchanged.
- **Missing organization:** 404.
- **Validation:** a malformed `{id}` or `{storeId}` gives 400. Use explicit `@PathVariable("storeId")`, so the message names the parameter.
- **Business rules:**
  - Organization not `ACTIVE`: 409 `ORGANIZATION_MUST_BE_ACTIVE_TO_DEACTIVATE_STORE`.
  - Unknown store: 409 `STORE_DOES_NOT_BELONG_TO_ORGANIZATION`.
  - The headquarters: 409 `HEADQUARTERS_CANNOT_BE_DEACTIVATED`.
  - A repeat: 409 `STORE_ALREADY_INACTIVE`.
- **Concurrency:** 409 `CONCURRENT_MODIFICATION`.

**Business rules**
- Only an `ACTIVE` organization can deactivate stores.
- The headquarters can't be deactivated.
- An inactive store can't be deactivated again.
- An organization always keeps at least one active store.

**Dependencies:** T5a.

**Definition of done**
- **`@WebMvcTest`:** the contract, tenant capture, both path ids captured, and 400/401 with no handler calls.
- **End-to-end `*IT`:** the happy path, plus the cross-tenant case with a real store id (404, and S is still active in Postgres).
- **Handler tests:** covered by T2.

**Open questions:** ~~repeat behaviour, 200 or 204~~ **Resolved** (decisions 3 and 10: a repeat gives 409; success is 200 with a body).

---

## T5d: Close my organization

**User story** (the product owner confirmed on 2026-09-28 that the tenant closes). As a tenant, I want to close my organization when I stop trading, so that no further changes can be made to it.

**Scope:**
- `POST /api/organizations/{id}/closure` → `CloseOrganizationCommand(tenant, id)`.
- No request body.

**Out of scope:**
- reopening an organization
- a platform or admin close (T9)
- data retention or export
- effects on other modules

**Contract**
- **Success:** **200** with `{organizationId, status:"CLOSED", closedAt}`. There is no `tenantId` (decision 11a), and `CloseOrganizationResult` is unchanged.
- **Errors:** the shared table, plus these:
  - 409 `ORGANIZATION_ALREADY_CLOSED`: status is `CLOSED`
  - 409 `ORGANIZATION_CANNOT_BE_CLOSED`: status is `PENDING_ACTIVATION`

  Closing is allowed only from `ACTIVE` or `SUSPENDED`.

**Acceptance criteria**
- **Happy path:** Given A's `ACTIVE` organization (and separately a `SUSPENDED` one, seeded through the aggregate because there is no suspend handler), then closing returns 200 with `status=CLOSED`.
- **Tenant only from the provider; no tenant:** as in the shared contract.
- **Other tenant:** Given B sends A's organization id, in any status of A including `CLOSED`, then the response is 404, identical to the missing-organization 404. It is never `ORGANIZATION_ALREADY_CLOSED`, and A is unchanged.
- **Validation:** a malformed `{id}` gives 400.
- **Business rules:**
  - A repeat: 409 `ORGANIZATION_ALREADY_CLOSED`.
  - `PENDING_ACTIVATION`: 409 `ORGANIZATION_CANNOT_BE_CLOSED`.
- **Concurrency:** 409 `CONCURRENT_MODIFICATION`.

**Business rules**
- Only an `ACTIVE` or `SUSPENDED` organization can be closed.
- Closing is irreversible.
- A closed organization accepts no further changes.

**Dependencies:**
- T5a
- ~~Product-owner decision 2~~ (resolved 2026-09-28: the tenant closes)

**What changes depending on decision 2** (resolved 2026-09-28: **the tenant closes**):
- **Tenant closes:** build the ticket as written. **← chosen**
- ~~Platform closes: cancel T5d, and closing moves to T9.~~
- ~~Both: build T5d, and add T9 for the platform.~~

**Readiness:** ready to build and merge. Both merge blockers were resolved on 2026-09-28: the tenant closes, and no confirmation is needed, so the request has no body.

**Definition of done**
- **`@WebMvcTest`:** the contract, tenant capture, and 400/401 with no handler calls.
- **End-to-end `*IT`:**
  - A closes: 200, and the status is `CLOSED` in Postgres.
  - After closing, T5a, T5b and T5c on the closed organization return their "must be active" 409s. This belongs here, not in `@WebMvcTest` with a mocked handler.
  - B tries to close A's `ACTIVE` organization: 404, and A is still `ACTIVE`.
- **Handler tests:** covered by T2. `SUSPENDED` → `CLOSED` is already covered by `CloseOrganizationHandlerTest.shouldCloseSuspendedOrganization`.

**Open questions**
- ~~**Product owner, decision 2:** who closes?~~ **Resolved 2026-09-28:** the tenant.
- ~~**Product owner:** should closing require a confirmation?~~ **Resolved 2026-09-28:** no.
- **Product owner:** should a tenant be able to abandon a `PENDING_ACTIVATION` organization? The domain refuses today.
- ~~Architect: repeat behaviour~~ **Resolved** (decision 3: keep 409 `ORGANIZATION_ALREADY_CLOSED`).

---

## T5e: Activate my organization (BLOCKED on product-owner decision 2)

**User story (valid only if decision 2 = "the tenant activates").** As a tenant, I want to activate my newly registered organization, so that I can start adding stores and trading.

**Status:** **blocked.** Don't design or implement this until decision 2 is answered.

**Scope:**
- `POST /api/organizations/{id}/activation` → `ActivateOrganizationCommand(tenant, id)`.
- No request body.

**Out of scope:**
- platform activation (T9)
- suspension
- activation prerequisites such as KYC or payment

**Contract**
- **Success:** **200** with `{organizationId, status:"ACTIVE", activatedAt}`. There is no `tenantId` (decision 11a).
- **Errors:** the shared table, plus:
  - 409 `ORGANIZATION_ALREADY_ACTIVE` when the status is `ACTIVE` (a repeat)
  - 409 `ORGANIZATION_CANNOT_BE_ACTIVATED` when it is `SUSPENDED` or `CLOSED`

  Activation is allowed only from `PENDING_ACTIVATION` (T5e-0).

**Acceptance criteria**
- **Happy path:** Given A's `PENDING_ACTIVATION` organization, then activating returns 200 with `status=ACTIVE`, and T5a now succeeds on it.
- **Tenant only from the provider; no tenant → 401; malformed id → 400:** as in the shared contract.
- **Other tenant:** in any status of A, the response is 404, identical to the missing-organization 404. It is never 409.
- **Business rules:**
  - An `ACTIVE` organization gives 409 `ORGANIZATION_ALREADY_ACTIVE`.
  - A `SUSPENDED` or `CLOSED` organization gives 409 `ORGANIZATION_CANNOT_BE_ACTIVATED`. For `SUSPENDED`, the status stays `SUSPENDED` in Postgres (end-to-end `*IT`).
- **Concurrency:** 409 `CONCURRENT_MODIFICATION`.

**Business rules**
- Only a `PENDING_ACTIVATION` organization can be activated. Lifting a suspension is `reinstate()`, which T5e never exposes.
- A closed organization can never be reactivated.

**What changes depending on decision 2:**
- **Tenant activates** (with the platform suspending and reinstating, or with no suspension concept at all):
  - Depends on T5e-0 (domain split, merged).
  - The Reinstate command and handler come with their actor in the suspension-lifecycle ticket or T9.
  - The product owner accepts that `PENDING_ACTIVATION` is then not a real gate, unless it has prerequisites (KYC, payment).
- **Platform activates and reinstates:** cancel T5e.
  - T9 exposes `activate()` (from `PENDING_ACTIVATION`) and `reinstate()` (from `SUSPENDED`) behind a platform path, which needs a platform actor and an unscoped load port.
  - T5a–d stay unusable end to end until T9 exists.
- **Tenant for first activation, platform for reinstatement:** covered by T5e-0 as it stands.

**Dependencies:**
- **Product-owner decision 2**
- T5a
- T5e-0 (merged)

**Definition of done:** same layers as T5d, plus an end-to-end `*IT` that goes through register, then activate, then add store, all over HTTP.

**Open questions**
- **Product owner, decision 2:** who activates, and who lifts a suspension?
- ~~Architect: repeat behaviour~~ **Resolved** (decision 3: keep the 409).

---

## Cross-cutting questions

All resolved: see **Architect decisions** 1–12 at the top of this file.

## Readiness

| Ticket | Can be built now? | Can be merged now? | Usable end to end |
|---|---|---|---|
| T5a add store | yes | yes | after activation exists (T5e or T9), bootstrap and T7 |
| T5b change headquarters | yes, after T5a | yes | same |
| T5c deactivate store | yes, after T5a | yes | same |
| T5d close | yes, after T5a | yes (both blockers resolved 2026-09-28) | same |
| T5e activate | **blocked on decision 2**; T5e-0 must be merged first | no | not applicable |
