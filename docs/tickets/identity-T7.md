# T7: Identity: authenticated users and the real current tenant

- **Status:** drafted by the BA against `main` at `a708069` (2026-10-02). Product owner answered questions 1–5 on 2026-10-02. **Architect: validated with changes (2026-10-02, checked against `a708069`).** The changes are applied below. **Sequencing: hybrid (decided 2026-10-02 on the BA's recommendation): T7a and T7c now; T7b and T8 later, but before any real (non-local) deployment.** Small product-owner confirmations remain, listed at the end. **Implementation status: T7a implemented; T7c implemented (registration creates the owner in the same transaction; the Postman collection is updated; the owner cannot log in until T7b); T7b not started.**
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

### T7b, detail and adjustments (BA, 2026-11-08)

**Status:** this block detailed and adjusted T7b on 2026-11-08, checked against branch `claude/gallant-ritchie-u9aa1v` (T7a, T7c and T9g merged). **Architect decisions 6 to 13 above are unchanged and are not reopened here.** In particular HS256, `oauth2-resource-server` with `NimbusJwtDecoder`, the `tenantId` claim validated as a UUID, BCrypt, a short TTL, no refresh, and `QUINE_JWT_SECRET` with startup refusal. This block only adds slicing, verifiable criteria and the changes caused by decisions taken since T7. Where this block disagrees with the older T7b text above (scope, definition of done, "Stand-ins gone"), this block wins. **Prerequisite: T7b-0 (`web-shared-T7b-0.md`).** Architect validation of this block: pending.

#### 1. Slicing into mergeable slices

| Slice | Scope | Mergeable because |
|---|---|---|
| **T7b-1: login and token issue** | `POST /api/auth/login` (`identity/presentation`, its own scoped advice for `INVALID_CREDENTIALS`), login use case, password verification, JWT issue (HS256, `NimbusJwtEncoder`), `QUINE_JWT_SECRET` read and checked at startup, `identity` pom dependencies. | No Spring Security chain yet, so the existing stand-ins and every route behave as before. Spring Security's *starter* must **not** be on the classpath in this slice (it auto-secures every route with a default chain). Use the JOSE artefact only. The login route is open because nothing is secured yet, which matches its final status (public). Owners created by T7c can already obtain a token. Tokens are not honoured yet. |
| **T7b-2: filter chain, real tenant provider, end of the stand-ins** | Spring Security chain, decoder, claim validation, entry point and access-denied handler, `CurrentTenantProvider` implementation, deletion of both stand-ins, the smoke shortcut and the profile guard, migration of bootstrap tests, Postman, compose, `CLAUDE.md`. | It is atomic by necessity: the real provider and `FailClosedTenantProvider` would be two `CurrentTenantProvider` beans, and the chain makes `/smoke/**` unreachable (T9 A7). Splitting would leave a broken Postman or an ambiguous bean. The Architect may still split the Postman and docs into a follow-up PR **only if** the collection is not left red on `main`. |

Order: T7b-0, then T7b-1, then T7b-2.

#### 2. Changes induced by decisions taken since T7

**(a) T9 will add a second, ordered chain (T9b). The T7b chain must allow it without being rewritten. T7b builds none of the platform parts.** Design constraints (verifiable in review):
- The tenant chain is a named bean with an **explicit order constant** declared in one place. It is the **catch-all** (no `securityMatcher`), ordered last, so a later chain with `securityMatcher("/api/platform/**")` and a higher precedence slots in front without touching it. Nothing in the tenant chain mentions `/api/platform`.
- The tenant `JwtDecoder` is **named or qualified and wired explicitly into the chain** (not the single unqualified `JwtDecoder` bean that Boot's auto-configuration picks up), so a second decoder with another key does not cause ambiguity.
- The signing configuration is namespaced **per chain** (a property prefix for the tenant chain fed by `QUINE_JWT_SECRET`), so a platform key can be added as a sibling. There is no generic "the JWT secret" shared by design.
- Tenant tokens carry an **audience claim** (BA proposal: `quine-tenant`), and the tenant decoder requires it. A token minted for another audience (T9: `quine-platform`) is then refused even if a key were shared by mistake. See A11.
- The entry point is a class **parameterised by code and message**. The tenant instance uses `TenantNotResolvedException.CODE` and its message. T9b can add an instance with `AUTHENTICATION_REQUIRED`. The entry point obtains the code and message from `new TenantNotResolvedException()` (`code()` and `getMessage()`), so the text is not duplicated.
- The public routes live in **one list** used by the chain and by the route-walking test (criterion T7b-AC10).
- Principal-to-tenant extraction is in `identity` and reads only the `tenantId` claim. It never falls back to another claim or to `sub`.

**(b) The stand-ins and the smoke shortcut disappear in T7b.** Removed in T7b-2:
- `FailClosedTenantProvider`, `SmokeTenantProvider`, `SmokeController` (`/smoke/organizations/{id}/activate`), the `smoke` profile reference in `docker-compose.yml`, and the profile guard `SmokeProfileGuardTest` / `LOCAL_PROFILES`.
- Test deletions: `FailClosedTenantProviderTest`, `SmokeTenantProviderTest`, `SmokeProfileGuardTest`.
- After removal nothing in the application is forgeable. The T7a guard has nothing left to protect, but a new hazard appears (below).

**How a developer activates an organization locally after T7b:**
- **Postman / manual:** register, log in (the collection stores the run-time variable `tokenA`), then call the real tenant route `POST /api/organizations/{id}/activation` with that token in the `Authorization` header. This is the route T5e built. It exists until T9c+T9d, and the setup step "Activate A (smoke runner only)" is replaced by it. Folder "6. Activate organization (T5e)" already exercises that route (it must also move to tokens).
- **Java tests in `organization`:** unchanged. The four ITs call `ActivateOrganizationHandler` directly with `new ActivateOrganizationCommand(tenant, orgId)`. `ActivateOrganizationEndToEndIT` tests the HTTP route.
- **Java tests in `bootstrap`:** the new end-to-end IT uses the real chain: register, login, activate through the tenant route with the token (this also proves ownership), then tenant calls. Other `bootstrap` ITs may call the `ActivateOrganizationHandler` bean directly. No new route is added.
- After T9c+T9d: the tenant route is removed. Postman uses platform login, list and activate. The first platform admin comes from T9a (undecided).

**Is the tenant activation route acceptable, callable with a real token, until T9c+T9d?** Acceptable **only as a transitional, local-only situation**, and the risk window must be stated precisely.
- Before T7b, the route is reachable only through the forgeable local header, confined to local profiles by the T7a guard. At T7b, registration is public (decision 2) and login is real. So **anyone can register, log in and activate their own organization without platform validation**. The PO decision that "validation is a gate" (T9) is not enforced from T7b until T9c+T9d.
- Impact: the validation gate is bypassable (spam organizations, burned e-mails, a validation queue that means nothing). Tenant isolation is intact: the route is tenant-scoped and only activates the caller's own organization. No cross-tenant data is exposed.
- Bounding it: **no environment reachable by anyone outside the team before T9c+T9d ship** (T7 rule 4, and T9d "must ship before any real deployment"). T7b release notes state it. Optional technical bound (A16): a startup guard refusing non-local profiles while the tenant activation route exists, removed in T9d. Its weakness: it only catches an environment that is labelled. The process rule remains the real control. PO confirmation: question PO-1 below.

**(c) T7c is merged.** Registration creates the owner (`OwnerRegistrar` adapter `IdentityOwnerRegistrar` in `bootstrap`), so login has real users from day one. The owner can log in as soon as T7b-1 ships, whatever the organization status (login does not depend on `organization`, T9 P5 and PO-4). The acceptance criterion "(After T7c alone, the owner exists with a hashed password but cannot log in)" is resolved by T7b-1.

**(d) Codes and order.**
- Tenant chain, no or bad token: 401 `TENANT_NOT_RESOLVED`, header exactly `WWW-Authenticate: Bearer realm="quine-erp"`, standard body. `AUTHENTICATION_REQUIRED` is the platform chain's code (T9 A4) and names the same condition with a code per chain. Both documents state it.
- Login failure: 401 `INVALID_CREDENTIALS`.
- **401 before 400 on tenant endpoints:** the chain runs before the `DispatcherServlet`. A token whose `tenantId` claim is missing or malformed must also be refused **in the chain** (the claim check belongs to the decoder's validators, not to the provider called in the controller), otherwise a bad body would still answer 400 before 401. The provider's `TenantNotResolvedException` is only a safety net.
- The public endpoints (`POST /api/organizations`, `POST /api/auth/login`) keep 400.
- 403: the access-denied handler writes the standard body. Nothing produces a 403 in T7b (no authorization rule beyond "authenticated"). The 403 code name is open (A15).

**(e) The password never leaks, and unknown e-mail costs the same as wrong password.**
- The password is a raw `String` in the request DTO, the command and the use case. Every one of them masks it in `toString()` (as `RegisterOrganizationRequest`, `RegisterOrganizationCommand` and `CreateOwnerCommand` do today). It never appears in a log, a message, an exception message or a response.
- Trap found in the code: `PlainPassword` and `Email` enforce the **registration** policy and throw `InvalidValueException` (422). The login path must not reuse them in a way that turns a short password, an over-long password or a malformed e-mail into a 422. Those cases answer 401 `INVALID_CREDENTIALS` like any other wrong credential (A13).
- Anti-enumeration by timing: unknown e-mail and wrong password both perform **exactly one** BCrypt comparison. For an unknown e-mail the comparison runs against a precomputed dummy hash generated with the same encoder, so the cost factor follows the real one. The evidence is a unit test counting hashing calls on both paths, not a wall-clock measurement. The e-mail is normalised (trim, lower case) as at registration. The residual difference (database lookup time) is accepted.

**(f) The secret.**
- `QUINE_JWT_SECRET` absent, blank or too short for HS256: the application **fails to start** with a message that names the variable and never prints a value.
- No secret value in any committed file: not in `application.yaml` (no default), not in `docker-compose.yml` (it **references** the variable with no value, so compose itself refuses to start without it), not in the Postman files, not in the docs. The README and `CLAUDE.md` tell the developer to export a random value locally, without showing one.
- Tests generate a **random key per run** (a dynamic property in the test support). CI needs no secret. A test proves the context refuses to start without the variable.
- Rotating the key invalidates every token (no `kid`). Accepted, to be documented.

**(g) Postman and tests.**
- The collection: the roughly 40 uses of `X-Smoke-Tenant` and `/smoke/...` are replaced by the `Authorization` header carrying the run-time variables `tokenA` / `tokenB`. The Setup folder becomes "Register then Login": register A, register B, log in A (stores `tokenA`), log in B (stores `tokenB`), activate A through the real route. Tokens are collection variables set at run time (no initial values, nothing exported). The collection description (it says identity is not built, and that T7b is pending) is rewritten. The environment file `quine-erp-local.postman_environment.json` is renamed (it says "smoke runner") and gets no secret.
- New requests: login with unknown e-mail and with wrong password (same 401 body), no token on a tenant route (401 with the header), a token of tenant A on B's organization (same 404 as a missing one).
- **Inventory of what must change (verified in the code):**
  - `bootstrap` tests: `QuineApplicationIT` (`@ActiveProfiles("smoke")` and the test asserting `SmokeTenantProvider`: remove both, add the test key, assert the identity-backed provider); `QuineApplicationWithoutProfileIT` (asserts `FailClosedTenantProvider`: replace by "starts with a key", plus "refuses to start without it"); `SmokeProfileGuardTest`, `SmokeTenantProviderTest`, `FailClosedTenantProviderTest` (deleted; the 401 contract they covered moves to the chain test below); `SliceTestApplication` (a slice for the chain tests).
  - `organization` tests: **no migration needed.** `SettableTenantProvider` and the five `*EndToEndIT`, `OrganizationConfigurationIT`, `TenantOrganizationControllerTest`, `OrganizationControllerTest`, `TenantNotResolvedMappingTest` test `organization` in isolation with a stub provider, and `organization` cannot depend on `identity` or Spring Security. Only the javadoc "until identity exists" is outdated. The real-authentication proof is the new `bootstrap` end-to-end IT.
  - `identity` tests: `BcryptPasswordHasherTest` and the handler tests extended for verification.
  - Docs and assets: `docker-compose.yml`, both Postman files, `CLAUDE.md` (bootstrap paragraph and run commands, "Tenant" convention bullet, the deferred note), this ticket's status line, `README.md` if it mentions the smoke run (a search found no match today).

**(h) Public vs authenticated.**

| Route | Access |
|---|---|
| `POST /api/organizations` (register) | public |
| `POST /api/auth/login` | public |
| every other route, existing or future, and any unmapped path | authenticated by default (`anyRequest().authenticated()`), no exception |

- The public list is method-specific and lives in one place. `GET /api/organizations` (not mapped today) is not public by accident.
- Adding a public route requires editing that list and the route-walking test (T7b-AC10). T7 rule 2 (classify every new endpoint) is now checked by the build.
- Anything such as an actuator or health endpoint is a new classification decision, not covered by the default.

**(i) Token life, revocation, throttling.**
- Short TTL, configurable, no refresh, no revocation (accepted by T7, unchanged). The value and re-login cadence are PO-2. The Architect chose HS256 and a short TTL; the number is not decided.
- `expiresIn` is in seconds. The decoder's default clock skew (60 s in Spring Security) is accepted and noted.
- **Out of scope for T7b, but recorded as a prerequisite for any real deployment:** login throttling or lockout (a brute-force vector, and unauthenticated login also costs CPU through BCrypt), registration rate limiting (e-mail enumeration through `EMAIL_ALREADY_EXISTS`, queue flooding), a database migration role distinct from the runtime role (T9 A-2), a single symmetric key without rotation. A later ticket owns each (PO-3).

#### 3. Acceptance criteria (verifiable)

Every criterion names its test level. "Chain test" means a `bootstrap` `@WebMvcTest` or `@SpringBootTest` slice that imports the real security configuration. "Full IT" means the full context over Testcontainers.

**T7b-1 (login and issue)**
- **T7b-AC1 login success (full IT).** Given the owner registered through `POST /api/organizations`, when `POST /api/auth/login` is called with the e-mail (any case, surrounding spaces) and the password, then 200 with `accessToken`, `tokenType` (the bearer scheme name) and `expiresIn`, and the decoded token has the owner's tenant in the `tenantId` claim (UUID), the user id as subject, the tenant audience, an expiry equal to the issue time plus `expiresIn`, and **no e-mail, no password, no hash**.
- **T7b-AC2 login failure (unit and chain test).** Unknown e-mail, wrong password, a password shorter than 12 characters, a password over 72 bytes, and a malformed e-mail all answer the **same** 401 `INVALID_CREDENTIALS`: same message, same body shape, same header. Never 422, never 404.
- **T7b-AC3 equal work (unit).** Both failing paths perform exactly one password verification, and the unknown-e-mail path uses the dummy hash.
- **T7b-AC4 login validation (chain test).** A blank e-mail or password and a missing or malformed body answer 400 `VALIDATION_FAILED` (pinned texts, the guard test covers the login DTO). The login DTO does **not** enforce the registration password policy. No echo of the input.
- **T7b-AC5 no leak (unit and log capture).** `toString()` of the request, command and credential types masks the password. A test captures the log output of a successful and a failed login and asserts it contains neither the password nor the token. A response never contains a hash.
- **T7b-AC6 secret (context test).** The context refuses to start when `QUINE_JWT_SECRET` is absent, blank or too short. The failure message names the variable and prints no value. No file under version control contains a secret value (a search of the diff by the reviewer).
- **T7b-AC7 policy types.** The login path does not construct `PlainPassword` or `Email` through their registration constructors, or catches their rejection and converts it to the same 401 (the Architect picks the mechanism, A13). A test per case of T7b-AC2 proves it.

**T7b-2 (chain, provider, stand-ins)**
- **T7b-AC8 authenticated request (full IT).** Given a token from login of the owner of tenant A, when the owner calls a tenant endpoint on A's organization, then the command's tenant is A and the response equals today's.
- **T7b-AC9 unauthenticated (chain test).** No token, an invalid signature, an expired token, a token with another audience, a token with a missing or non-UUID `tenantId`, a `Basic` header, and a malformed `Bearer` header each answer 401 `TENANT_NOT_RESOLVED`, with the header **exactly** `Bearer realm="quine-erp"` (no `error=` or `error_description=` detail), the standard three-key body, and `verifyNoInteractions` on the application handler.
- **T7b-AC10 401 before 400 and default deny (chain test and full IT).**
  - An unauthenticated request with an invalid body or a malformed path id on a tenant endpoint answers 401. The same request with a valid token answers 400.
  - Login and registration with an invalid body answer 400 with no token.
  - **Route walking:** a test reads every handler mapping of the full context. Every route not in the public list answers 401 without a token. The public list is exactly the two routes above, so a new route is secured by default and a new public route fails the test until it is classified. An unmapped path without a token answers 401.
- **T7b-AC11 cross-tenant (full IT).** Given the owner of A and an organization of B (every tenant endpoint), the response is the same 404 as a missing organization, with identical `code` and `message`.
- **T7b-AC12 caller input ignored (chain test).** A `tenantId` in the path, query or body, and `X-Smoke-Tenant` or `X-Tenant-Id` headers, never change the tenant. Only the claim does.
- **T7b-AC13 registration stays public.** `POST /api/organizations` and `POST /api/auth/login` need no token. A registration response contains no token, password or hash.
- **T7b-AC14 stand-ins gone.** `FailClosedTenantProvider`, `SmokeTenantProvider`, `SmokeController` and the smoke profile guard no longer exist. The application starts with no profile, exactly one `CurrentTenantProvider` bean exists, `/smoke/**` answers 401 (default deny), `docker-compose.yml` has no `smoke` reference.
- **T7b-AC15 no accidental defaults (full IT with log capture).** Startup logs contain no generated security password. Sessions are not created (no `Set-Cookie`, no `JSESSIONID`). There is no form login and no HTTP Basic. An authenticated request to an unmapped path answers the framework's 404, and an unexpected server error answers 500 (neither becomes a 401 through the `/error` dispatch).
- **T7b-AC16 403 handler (unit).** The access-denied handler writes status 403 and the standard body. No live route is expected to produce it before T8.
- **T7b-AC17 extensibility (review).** The T9b constraints of section 2(a) are met: explicit order constant, catch-all tenant chain, qualified decoder, per-chain key namespace, audience claim, parameterised entry point, one public-route list. No platform class or matcher exists.
- **T7b-AC18 Postman (run).** The collection runs end to end with the new Register then Login setup. It uses no `X-Smoke-Tenant` and no `/smoke/` route, stores `tokenA` and `tokenB` at run time, and contains no secret value.
- **T7b-AC19 timestamps.** The `timestamp` format of a filter-written error (401, 403) equals the one written by the advice (same JSON mapper and ISO format).

#### 4. Business rules (additions)

- A token authenticates a user of exactly one tenant, and the tenant is read only from the validated claim.
- Authentication failures of the tenant zone never reveal why (expired, wrong signature, missing claim): one code, one message.
- Login never reveals whether an e-mail exists.
- A route is protected unless it is explicitly classified as public.
- Login does not depend on the organization's status (T9 P5, PO-4).

#### 5. Out of scope (additions to the list below)

The platform chain, platform admin and `AUTHENTICATION_REQUIRED` (T9b); roles and 403 rules (T8); login throttling, registration rate limiting, refresh and revocation, key rotation, password reset (separate tickets); an actuator or health endpoint; CORS; moving organization tests to real authentication; the DB credential defaults (A17, unless the Architect says otherwise).

#### 6. Dependencies

T7b-1 needs T7b-0 and T7c (merged). T7b-2 needs T7b-1. T9b needs T7b-2. T9g is independent. **Gate: T7b-2 may merge to `main`, but nothing may be deployed to an environment reachable outside the team until T9c+T9d ship** (section 2(b)).

#### 7. Open questions for T7b (each with the BA recommendation)

**Product owner**
- **PO-1: self-activation window.** From T7b-2 until T9c+T9d, any person who registers can log in and activate their own organization, skipping the platform's validation. Do you confirm that no environment reachable by outsiders (demo, staging, production) will exist until T9c+T9d ship? *Recommendation: yes, accept for local and team-only use. T7 rule 4 already says this.*
- **PO-2: token lifetime.** With no refresh, a tenant user must log in again every N minutes. What cadence is acceptable? *Recommendation: 30 minutes, configurable (T9 already sets 15 minutes for the platform). Revisit when refresh tokens are scheduled.*
- **PO-3: tickets for the deployment prerequisites.** Login throttling or lockout, registration rate limiting, a separate database migration role. Do you want them scheduled as tickets that block the first real deployment? *Recommendation: yes, as one "pre-production hardening" group of tickets before the first outside-facing environment.*

**Architect**
- **A1 (T7b-0).** Module name and package. *`web-shared` and `com.zim.shared.web`, as T6 decided. The package prefix `com.zim.shared` is shared with the `shared` jar but the sub-packages differ, so there is no split package.*
- **A2 (T7b-0).** Is `BusinessRuleViolationException` (409) and `DomainException` (422) generic? *Yes. They are shared-kernel types, and T6's list omitted them.*
- **A3 (T7b-0).** `CONCURRENT_MODIFICATION` wording versus "no message changes". *Two commits: byte-identical move first, then the neutral wording as the single documented diff, pinned by a test. No test or Postman asserts the text today.*
- **A4 (T7b-0).** Advice ordering and guard tests. *Generic advice at lowest precedence, module advices ahead and limited to module-owned exceptions, plus the one-mapping and precedence tests (AC5 to AC7).*
- **A5 (T7b-0).** Wiring of the generic advice. *One explicit configuration class imported by `bootstrap` and by every slice or IT. Not an import hidden inside `ApiExceptionHandler`.*
- **A6 (T7b-0).** Shared test support (locale extension, DTO constraint guard logic). *A `web-shared` test-jar consumed in test scope. No duplication.*
- **A7 (T7b-0).** ArchUnit. *Trigger it now as a separate ticket T7b-0b, with the three rules listed in `web-shared-T7b-0.md`. Do not mix it into T7b-0.*
- **A8 (T7b-2).** The servlet-side writer for the entry point and access-denied handler: where it lives and which JSON mapper it uses. *In `web-shared`, using the application's configured mapper bean so the `timestamp` format equals the advice's. It takes code and message as parameters.*
- **A9 (T7b-1 and T7b-2).** Where the Spring Security artefacts go. *Slice 1: the JOSE/encoder artefact in `identity`, no starter. Slice 2: the starters in `bootstrap` (chain), token verification and the provider in `identity` (decision 7). Add a test that no default chain or generated password appears.*
- **A10 (T7b-1).** Secret format and checks. *Base64-encoded, at least 32 bytes decoded, refused if absent, blank, invalid or short. The error names the variable and prints no value. Property prefix namespaced per chain, fed by `QUINE_JWT_SECRET`.*
- **A11 (T7b-1).** Audience claim on tenant tokens. *Yes, `quine-tenant`, validated by the tenant decoder. It is the cheap part of T9's key and audience separation and stops a later rewrite.*
- **A12 (T7b-2).** Where `tenantId` claim validation lives. *In the decoder's validator chain, so a bad claim is refused in the filter (401 before 400). The provider keeps a defensive check.*
- **A13 (T7b-1).** Password verification and login input types. *A separate verification port (adding a method to `PasswordHasher` breaks it as a functional interface and the lambdas in tests). A login credential type with a masked `toString` and no registration policy. A dummy hash for unknown e-mail. Malformed e-mail, short or over-long password end in the same 401 as any wrong credential.*
- **A14 (T7b-1).** Should `INVALID_CREDENTIALS` carry `WWW-Authenticate`? *Yes, the same header as other 401s, for uniformity and RFC 9110. Pin it in the criterion either way.*
- **A15 (T7b-2).** 403 code name. *`ACCESS_DENIED`, unit-tested only, T8 may rename. Alternative: do not build the handler until T8. T7 decision 11 lists it, hence the recommendation.*
- **A16 (T7b-2).** Technical gate on the activation window. *A small startup guard that refuses non-local profiles while the tenant activation route exists, reusing the allow-list idea of the T7a guard, removed in T9d. Be honest about its limit: an unlabelled environment passes. The process rule stays the main control. Only needed if PO-1 is "accept with a technical backstop".*
- **A17 (T7b-2).** Committed literal database credentials. `docker-compose.yml` and the `application.yaml` defaults contain literal local DB credentials, against `CLAUDE.md` "Security". Not a T7b regression, but T7b edits `docker-compose.yml`. *Make compose reference the variables (as for the JWT secret) in the same PR, and decide separately whether the local `application.yaml` defaults stay. Otherwise open a tiny ticket.*
- **A18 (T7b-2).** The entry point must map every authentication failure, including a malformed `Authorization` header and Spring's "invalid request" cases, to the same 401, with no OAuth2 error detail in header or body. *Add this to the entry point's contract tests (T7b-AC9).*

#### 8. Order of delivery and risks

Order: `T7b-0` (needs T7c merged) → `T7b-0b` (ArchUnit baseline, optional, parallel with T7b-1) and `T7b-1` (needs T7b-0, T7c) → `T7b-2` (needs T7b-1) → `T9b` → `T9f-2` → `T9a` (implementation) → `T9c + T9d` (one delivery) → `T9e`. `T9g` was independent and is merged. No deployment outside the team before T9c+T9d.

Risks added by T7b:
1. **Self-activation window (T7b-2 to T9c+T9d).** Bounded by the deployment rule, optionally by the A16 guard.
2. **Silent contract regression during extraction (T7b-0).** Mitigated by the characterisation test written before the move, by "no assertion changes" in existing tests, and by the Postman run before and after.
3. **Double mapping or shadowing of exceptions.** Mitigated by ordering, the one-mapping test and the precedence test.
4. **Spring Security defaults appearing by accident.** Adding the starter creates a default chain and may log a generated password. Mitigated by slice 1 using no starter and by T7b-AC15.
5. **Timing and enumeration.** Hash-equalisation covers the dominant cost only; the database lookup difference is accepted. E-mail enumeration through public registration remains (known risk above).
6. **No revocation or refresh, single symmetric key without rotation.** All tokens die at once on rotation. Accepted (T7). Weighs more once platform tokens exist (T9).
7. **Brute force and CPU cost.** No throttling, and unauthenticated login triggers BCrypt each time. A prerequisite for real deployment (PO-3).
8. **`PlainPassword` and `Email` registration policy leaking into login.** Prevented by T7b-AC2 and T7b-AC7.
9. **The generic `must be a valid UUID` message now applies to every module.** A future non-UUID path or query parameter in `identity` would get a wrong message until T9 A6. Low.
10. **Postman and developer experience.** About 40 request edits. The collection must never contain a token or secret value (variables set at run time).
11. **Documentation drift.** `CLAUDE.md` (bootstrap paragraph, Tenant and Errors conventions, deferred list), `tenant-scoping-T1-T4.md` line 34, T6, this ticket's status line and the Postman description all change.

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
- **E-mail enumeration through public registration (known since T7c):** the uniqueness is platform-wide, so `POST /api/organizations` answers 409 `EMAIL_ALREADY_EXISTS` for an e-mail that already has an account. This follows the "E-mail rules" criterion. Rate limiting, or a neutral answer, is a later ticket.

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
