# Tenant-scoped organization REST endpoints: tickets T5a–T5e

- **Status:** written by the BA against `main` at `c035c0d`, after T1–T4 were merged. **Architect validation is pending.** Product-owner decision 2 is still open.
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

## Contract shared by all T5 tickets

**Where the tenant comes from.** The controller calls `CurrentTenantProvider.currentTenant()` once per request and passes the result as the command's `tenantId`.
- The tenant is never read from the path, the query string, the body or any header.
- No request DTO declares a `tenantId`.
- `{id}` is only the organization id.

**Order of steps:**
1. Request-format validation (400)
2. Tenant resolution (401)
3. Handler: ownership (404) is checked before any business rule (409)

**Error mapping (`ApiErrorResponse {code, message, timestamp}`):**

| Status | Code | When |
|---|---|---|
| 400 | `VALIDATION_FAILED` | Bean validation fails. **New in T5a:** also a malformed path UUID and a malformed or missing JSON body. |
| 401 | `TENANT_NOT_RESOLVED` | The provider throws. Header `WWW-Authenticate: Bearer realm="quine-erp"`. The handler is never called. |
| 404 | `ORGANIZATION_NOT_FOUND` | The organization is missing **or** belongs to another tenant. Bodies are identical apart from `timestamp`. |
| 409 | rule code (per ticket) | `BusinessRuleViolationException` |
| 409 | `CONCURRENT_MODIFICATION` | `OptimisticLockingFailureException` |
| 409 | `DATA_INTEGRITY_VIOLATION` | A database constraint rejected the write. Generic message. |
| 422 | value-object code | A domain `InvalidValueException` that DTO validation didn't catch |

**Business rules for every T5 ticket:**
- No operation runs without a resolved tenant.
- To another tenant, an organization it doesn't own is indistinguishable from a missing one. A 409 is never returned for another tenant's organization.

**Product-owner decisions 1, 3 and 4** don't affect T5a–d. Decision 2 is covered in T5d and T5e.

---

## T5a: Add a store to my organization

**User story.** As a tenant, I want to add a store to my active organization, so that I can run a new point of sale under the same legal entity.

**Scope**
- `POST /api/organizations/{id}/stores` → `AddStoreCommand(tenant, id, storeCode, storeName)`.
- The controller injects `CurrentTenantProvider`. Whether that's `OrganizationController` or a sibling controller is for the Architect. This sets the pattern for T5b–e.
- **Shared prerequisite:** map `MethodArgumentTypeMismatchException` and `HttpMessageNotReadableException` to 400 `VALIDATION_FAILED`. The message must not echo raw input or parser internals.
- Move the register-driven mapping tests from finding 8 onto this endpoint.

**Out of scope:**
- reading or listing stores
- editing or reactivating a store
- `Idempotency-Key`

**Contract**
- **Request body:**
  - `storeCode`: `@NotBlank`, `@Pattern("^[A-Za-z0-9][A-Za-z0-9_-]{1,19}$")`. The domain uppercases it.
  - `storeName`: `@NotBlank`, `@Size(2,120)`
- **Success:** **201** with body `{organizationId, storeId, storeCode, storeName, headquarters, active, addedAt}`, mirroring `AddStoreResult`. `headquarters=false`, `active=true`. For `Location`, see the open questions.
- **Errors:** the shared table, plus these, in domain order:
  - 409 `ORGANIZATION_MUST_BE_ACTIVE_TO_ADD_STORE`: status is `PENDING_ACTIVATION`, `SUSPENDED` or `CLOSED`
  - 409 `STORE_CODE_ALREADY_EXISTS`: the code matches any existing store, active or inactive. The check is case-insensitive.
  - 409 `STORE_ID_ALREADY_EXISTS`: not triggerable by a client
  - 422 `INVALID_STORE_NAME`: passes the DTO but is too short once trimmed, e.g. `" a"`

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
- **Domain value error:** `storeName=" a"` gives 422 `INVALID_STORE_NAME`.
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
- **Tests moved off register:**
  - `shouldMapWrongTenantAndMissingOrganizationToIdenticalNotFound` is replaced by an add-store test, with the real `AddStoreHandler` over `InMemoryOrganizationRepository` behind the mock.
  - The other three register-driven mapping tests move here and are removed from register.
- **Handler unit tests:** none new, unless a gap shows up, such as a case-insensitive duplicate code.
- **End-to-end `*IT`** (MockMvc over the real handler, adapter and Postgres, with a `@TestConfiguration` stub provider):
  - Tenant A's `ACTIVE` organization is seeded through the register and activate handlers.
  - A adds a store: 201, and the row has A's `tenant_id`.
  - B adds a store to A's organization: 404, equal to the random-id 404, and A's store count is unchanged.

**Open questions**
- **Architect:**
  - Should the response include `Location: /api/organizations/{id}/stores/{storeId}` even though no GET store endpoint exists and register has no `Location`? Add it to both endpoints or to neither.
  - Confirm the prerequisite 400 mappings belong here.
  - Confirm the register tests move as described.
- **Product owner:** should an inactive store's code stay reserved? The domain says yes today.

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
- **Success:** **200** with `{organizationId, headquartersId, changedAt}`, mirroring `ChangeHeadquartersResult`.
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
  - A random store id, or another organization's store (same tenant or not): 409 `STORE_DOES_NOT_BELONG_TO_ORGANIZATION`. Both cases give the same response, so nothing leaks.
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

**Open questions (Architect)**
- **Idempotency and verb:** a repeated PUT returns 409. Options:
  - keep PUT and make "already the headquarters" a 200 no-op, which is a domain change and needs product-owner agreement
  - switch to `POST /headquarters-change`
  - keep PUT with a 409 and document the exception
- **Unknown store id:** keep 409 `STORE_DOES_NOT_BELONG_TO_ORGANIZATION`, or use 404? The BA recommends keeping the 409.
- **Previous headquarters:** should the response include `previousHeadquartersId`? It's in the `HeadquartersChanged` event but not in the result.

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
  - 409 `ORGANIZATION_MUST_KEEP_ONE_ACTIVE_STORE`: can't be reached here (finding 5)

**Acceptance criteria**
- **Happy path:** Given A's `ACTIVE` organization with an active non-headquarters store S, then deactivating it returns 200 with `active=false`, and the headquarters is untouched.
- **Tenant only from the provider:** a caller-supplied tenant is ignored.
- **No tenant:** 401 with the header, and the handler is never called.
- **Other tenant:**
  - Given B sends A's organization id and A's real store id.
  - Then the response is 404, identical to the missing-organization 404. It is never 409, even if S is the headquarters or already inactive, and S is unchanged.
- **Missing organization:** 404.
- **Validation:** a malformed `{id}` or `{storeId}` gives 400.
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

**Open questions (Architect)**
- Should a repeat return 409 `STORE_ALREADY_INACTIVE`, or 200 as a no-op? Decide once for all action endpoints.
- 200 with a body, or 204?

---

## T5d: Close my organization

**User story (valid only if decision 2 = "the tenant closes").** As a tenant, I want to close my organization when I stop trading, so that no further changes can be made to it.

**Scope:**
- `POST /api/organizations/{id}/closure` → `CloseOrganizationCommand(tenant, id)`.
- No request body.

**Out of scope:**
- reopening an organization
- a platform or admin close (T9)
- data retention or export
- effects on other modules

**Contract**
- **Success:** **200** with `{organizationId, tenantId, status:"CLOSED", closedAt}`, mirroring `CloseOrganizationResult`. The Architect decides whether to keep echoing `tenantId`.
- **Errors:** the shared table, plus these:
  - 409 `ORGANIZATION_ALREADY_CLOSED`: status is `CLOSED`
  - 409 `ORGANIZATION_CANNOT_BE_CLOSED`: status is `PENDING_ACTIVATION`

  Closing is allowed only from `ACTIVE` or `SUSPENDED`.

**Acceptance criteria**
- **Happy path:** Given A's `ACTIVE` organization (and separately a `SUSPENDED` one), then closing returns 200 with `status=CLOSED`.
- **After closing:** T5a, T5b and T5c on the closed organization return their "must be active" 409s.
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
- **Product-owner decision 2**

**What changes depending on decision 2:**
- **Tenant closes:** build the ticket as written.
- **Platform closes:** cancel T5d, and closing moves to T9.
- **Both:** build T5d, and add T9 for the platform.

**Readiness:** it can be built now, since tenant-scoping the close is the more restrictive default. **Don't merge it until decision 2 is answered.** Closing is irreversible, so exposing it by mistake is the costliest error in the T5 set.

**Definition of done**
- **`@WebMvcTest`:** the contract, tenant capture, and 400/401 with no handler calls.
- **End-to-end `*IT`:**
  - A closes: 200, and the status is `CLOSED` in Postgres.
  - B tries to close A's `ACTIVE` organization: 404, and A is still `ACTIVE`.
- **Handler tests:** covered by T2. Add a `SUSPENDED` → `CLOSED` test if one is missing.

**Open questions**
- **Product owner, decision 2:** who closes?
- **Product owner:** should closing require a confirmation, such as retyping the legal name?
- **Product owner:** should a tenant be able to abandon a `PENDING_ACTIVATION` organization? The domain refuses today.
- **Architect:** on a repeat, keep 409 `ORGANIZATION_ALREADY_CLOSED`, or return 200 as a no-op?

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
- **Success:** **200** with `{organizationId, tenantId, status:"ACTIVE", activatedAt}`, mirroring `ActivateOrganizationResult`.
- **Errors:** the shared table, plus 409 `ORGANIZATION_CANNOT_BE_ACTIVATED` when the status is `ACTIVE` or `CLOSED`. Activation is allowed from `PENDING_ACTIVATION` and `SUSPENDED`.

**Acceptance criteria**
- **Happy path:** Given A's `PENDING_ACTIVATION` organization, then activating returns 200 with `status=ACTIVE`, and T5a now succeeds on it.
- **Tenant only from the provider; no tenant → 401; malformed id → 400:** as in the shared contract.
- **Other tenant:** in any status of A, the response is 404, identical to the missing-organization 404. It is never 409.
- **Business rules:** an `ACTIVE` or `CLOSED` organization gives 409 `ORGANIZATION_CANNOT_BE_ACTIVATED`.
- **Concurrency:** 409 `CONCURRENT_MODIFICATION`.

**Business rules**
- Only a `PENDING_ACTIVATION` or `SUSPENDED` organization can be activated.
- A closed organization can never be reactivated.

**What changes depending on decision 2:**
- **Tenant activates:**
  - Build as written. The product owner accepts that `PENDING_ACTIVATION` is then not a real gate.
  - If the platform suspends organizations, a tenant must not be able to lift a platform suspension. The endpoint would then need a domain change: the tenant can activate only from `PENDING_ACTIVATION`, which means a new rule or command.
- **Platform activates:** cancel T5e and move activation to T9. T5a–d stay unusable end to end until T9 exists.
- **Tenant for first activation, platform for reactivation:** split into two commands or rules, as a domain ticket before T5e.

**Dependencies:**
- **Product-owner decision 2**
- T5a
- possibly a domain ticket

**Definition of done:** same layers as T5d, plus an end-to-end `*IT` that goes through register, then activate, then add store, all over HTTP.

**Open questions**
- **Product owner, decision 2:** who activates, and who lifts a suspension?
- **Architect:** on a repeat, 409 `ORGANIZATION_CANNOT_BE_ACTIVATED`, or 200 as a no-op?

---

## Cross-cutting questions for the Architect

1. **Response shape.** The existing results aren't consistent:
   - Activate and Close return `{organizationId, tenantId, status, <time>}`.
   - AddStore returns the new store.
   - ChangeHeadquarters returns `{organizationId, headquartersId, changedAt}`.
   - DeactivateStore returns `{organizationId, storeId, active, deactivatedAt}`.

   The options are:
   - (a) one response DTO per result, as written above
   - (b) the full updated organization, which needs a new result type and a read model
   - (c) 204 for the action endpoints and 201 for add store only

   The BA default is (a).
2. **`Location` and 201 for add store:** see T5a.
3. **Idempotency of repeated actions.** Today a repeat returns 409 with an "already" code (`ORGANIZATION_ALREADY_CLOSED`, `STORE_ALREADY_INACTIVE`, `ORGANIZATION_CANNOT_BE_ACTIVATED`, `STORE_IS_ALREADY_HEADQUARTERS`). Should "already in the target state" instead be 200 with no event? Decide once for all T5 endpoints. `Idempotency-Key` and `If-Match`/ETag are out of scope.
4. **Moving the T2 test** `shouldMapWrongTenantAndMissingOrganizationToIdenticalNotFound` and the other register-driven mapping tests onto real endpoints (T5a).
5. **Validation versus tenant precedence.** Should a malformed request with no tenant get 400 (Spring's natural order, and what's written here) or 401? The latter needs tenant resolution in a filter, which is closer to T7.
6. **Where the fail-closed default comes from.**
   - Tests supply a stub.
   - At runtime the endpoints depend on the "make `bootstrap` runnable" ticket. **Until then they can't run in any application.**
   - After that ticket, and until **T7**, every T5 endpoint answers 401.
   - Neither ticket blocks merging T5, but both are needed before the endpoints are usable.
7. **Controller split.** One `OrganizationController`, or separate controllers such as `StoreController`? Either way it must stay inside `ApiExceptionHandler`'s `basePackageClasses` scope.

## Readiness

| Ticket | Can be built now? | Can be merged now? | Usable end to end |
|---|---|---|---|
| T5a add store | yes | yes | after activation exists (T5e or T9), bootstrap and T7 |
| T5b change headquarters | yes, after T5a | yes | same |
| T5c deactivate store | yes, after T5a | yes | same |
| T5d close | yes, after T5a | **wait for decision 2** | same |
| T5e activate | **blocked on decision 2** | no | not applicable |
