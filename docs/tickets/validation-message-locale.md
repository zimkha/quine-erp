# T5f: Make 400 `VALIDATION_FAILED` messages deterministic English, whatever the server locale (organization module)

- **Status:** written by the BA against `main` at `c4b11e7`.
- **Architect:** validated with changes (checked against `c4b11e7`). **Decision: option (d) plus a DTO constraint guard test.** The changes are applied below.
- **Product owner (2026-09-28):**
  - English is the API language (R1).
  - Clients rely only on `code` (R4).
  - Localized end-user text is the client's job (R5).
- **Related:** `docs/tickets/endpoints-T5.md` (Architect decision 8a: fixed 400 messages, no echo).

## Architect decisions

| # | Question | Decision |
|---|---|---|
| 1 | Which option | **(d) plus a guard test.** `handleInvalidRequest` builds each message from the **constraint annotation type** and never calls `getDefaultMessage()`. It finds the type through `fieldError.contains/unwrap(ConstraintViolation.class)` → `getConstraintDescriptor().getAnnotation().annotationType()`, and compares it to the `jakarta.validation.constraints` classes. It does **not** use `getCode()`, which is only a simple name, so a custom `@Size` would be misread. The mapping lives in a small final helper in `presentation.rest.exception`, e.g. `FieldErrorMessages`; don't call it `ValidationMessages`. **Why:** the same layer and style as 8a, no need for `bootstrap` or global config, deterministic by construction, and new DTOs are covered by the fallback. **Rejected:** (b), because only one `ValidationMessages` bundle per classpath can win, which breaks with several modules; it also keeps `{regexp}` and has no home until `bootstrap` runs. (c), because it is a global side effect that overrides a Spring Boot bean, has no home, and keeps the regex. (a), because it's easy to forget, and (d) ignores `message` attributes anyway. |
| 2 | Regex in the message? | **No:** `must match the required format`. Clients rely only on `code` (R4). The format is documented in the API contract, not in error text. |
| 3 | Keep the `@Size` bounds? | **Yes:** `size must be between {min} and {max}`. Read `min` and `max` from the `ConstraintDescriptor` attributes, **not** by position in `getArguments()`, which is alphabetical (`[resolvable, max, min]`). Render them with concatenation or `Integer.toString`. **Never** use `String.format`, `NumberFormat` or `MessageFormat`: `String.format("%d", 120)` gives `١٢٠` under `ar-EG`. When `min == max`, the text is still "between 3 and 3". |
| 4 | Several constraints on one field | **Return all parts.** Canonical order: build each `"<field>: <text>"`, then `.distinct()`, then `.sorted()` (plain `String` order, never a `Collator`), then join with `"; "`. Because the texts are fixed ASCII, this sorts by field name, then text. The sort is required: **the raw `FieldError` order changes between identical requests** (verified). |
| 5 | Ignore `Accept-Language`? | **Yes (R5).** It holds by construction, since nothing reads the request locale. |
| 6 | Regression guard | All in this module, with **no ArchUnit yet**: (i) by construction, no `getDefaultMessage()` in the handler; (ii) a unit test of the helper, including the fallback; (iii) a **DTO constraint guard test** (see Definition of done); (iv) exact-message web tests under a non-English locale and `Accept-Language`. The ArchUnit rule and a shared helper come in T6, once a second module exists. |
| 7 | MockMvc locale | **Verified.** MockMvc's request locale is `en`. `Locale.setDefault(FRENCH)` alone gives **English**, so it doesn't reproduce the bug. `Accept-Language: fr`/`de` gives French/German, so it does: the header wins over the JVM default. |

## Pinned texts

| Constraint | Text after `"<field>: "` |
|---|---|
| `@NotBlank` | `must not be blank` |
| `@NotNull` | `must not be null` |
| `@Size` | `size must be between {min} and {max}` |
| `@Pattern` | `must match the required format` |
| Anything else, or a `FieldError` without a `ConstraintViolation` (e.g. binding `typeMismatch`) | `is invalid` |

`@NotBlank`, `@NotNull` and `@Size` are identical to today's English output. Only `@Pattern` changes.

**Exact messages:**

| Input | Message |
|---|---|
| `storeCode` `"THIES 09"`, `"A"`, `"THIES 01"`, `"LEAK 4711"` | `storeCode: must match the required format` |
| `headquartersCode` `"A"`, `"THIES 01"` | `headquartersCode: must match the required format` |
| `storeName` `"X"` or 128 characters | `storeName: size must be between 2 and 120` |
| `organizationName` `"X"` | `organizationName: size must be between 2 and 120` |
| `legalName` `"Y"` | `legalName: size must be between 2 and 160` |
| `headquartersName` `"Z"` | `headquartersName: size must be between 2 and 120` |
| `currencyCode` `"XO"`, `"XOFF"` | `currencyCode: size must be between 3 and 3` |
| change headquarters `{}` / `{"storeId": null}` | `storeId: must not be null` |
| `storeCode` `""` or `"   "` | `storeCode: must match the required format; storeCode: must not be blank` |
| `storeName` `""` | `storeName: must not be blank; storeName: size must be between 2 and 120` |
| `storeName` `"   "` (length 3 passes `@Size`) | `storeName: must not be blank` |
| `organizationName` / `legalName` / `currencyCode` / `headquartersName` `"   "` | `<field>: must not be blank` |
| `headquartersCode` `"   "` | `headquartersCode: must match the required format; headquartersCode: must not be blank` |

The all-invalid register body (`""`, `""`, `"XO"`, `""`, `""`) gives:

`currencyCode: size must be between 3 and 3; headquartersCode: must match the required format; headquartersCode: must not be blank; headquartersName: must not be blank; headquartersName: size must be between 2 and 120; legalName: must not be blank; legalName: size must be between 2 and 160; organizationName: must not be blank; organizationName: size must be between 2 and 120`

## Context / Why

A live smoke run of `POST /api/organizations/{id}/stores` with `storeCode: "THIES 09"` returned:
```
400 {"code":"VALIDATION_FAILED","message":"storeCode: doit correspondre à \"^[A-Za-z0-9][A-Za-z0-9_-]{1,19}$\"", ...}
```

Every other API message is fixed English text:
- domain exceptions and business rules
- `OrganizationNotFoundException` and `TENANT_NOT_RESOLVED`
- the T5a fixed 400s: `"<param>: must be a valid UUID"` and `"Request body is missing or malformed"`

Bean Validation messages are the only ones whose language depends on the runtime. That makes the API contract non-deterministic and hard to test.

What the code does today:
- **How the message is built:** `ApiExceptionHandler.handleInvalidRequest` builds `error.getField() + ": " + error.getDefaultMessage()` for each error, sorts the parts and joins them with `"; "`. `getDefaultMessage()` is Hibernate Validator's interpolated message, in whatever locale was resolved.
- **No explicit messages:** none of the DTOs sets a `message` attribute.
  - `RegisterOrganizationRequest`: `@NotBlank`, `@Size` (2..120, 2..160, 3..3), and `@Pattern` on `headquartersCode`.
  - `AddStoreRequest`: `@NotBlank` and `@Pattern` on `storeCode`; `@NotBlank` and `@Size(2..120)` on `storeName`.
  - `ChangeHeadquartersRequest`: `@NotNull` on `storeId`.
- **No configuration:** no `ValidationMessages.properties`, no validator or locale configuration, and no locale in `application-test.yaml`.
- **The order depends on the language** (verified):
  - `storeCode: ""`: English and French put the `@Pattern` part first; German puts `@NotBlank` first.
  - `storeName: ""`: English puts `@NotBlank` first; French and German put `@Size` first.
- **The raw order is unstable too.** The `FieldError` order changes between identical requests (verified over 5 identical requests). So the sort must stay, and it must not sort on translated text.
- **The current tests can't catch this.** These only assert `startsWith("<field>: ")`, or only the `code`:
  - `OrganizationControllerTest.shouldReturnBadRequestWhenFieldDoesNotMatchDomainFormat`
  - `OrganizationControllerTest.shouldReturnBadRequestWhenRegistrationRequestIsInvalid`
  - `TenantOrganizationControllerTest.shouldReturnBadRequestWhenFieldIsInvalid`
  - `TenantOrganizationControllerTest.shouldReturnBadRequestWhenStoreIdIsMissingOrNull`
- **Mechanism (verified by the Architect):**
  - Spring's `LocaleContextMessageInterpolator` uses the request locale.
  - MockMvc's request locale is `en`, so `Locale.setDefault(FRENCH)` alone gives English.
  - `Accept-Language: fr` or `de` gives French or German; the header wins over the JVM default.
  - In a real servlet container, a request with no `Accept-Language` falls back to the JVM default. This is consistent with the smoke run, but can't be verified until `bootstrap` is runnable.

## Actors

API clients of the organization endpoints, and the operators and developers who read the responses.

## User story

As an API client, I want the 400 `VALIDATION_FAILED` message to be the same English text on every server and for every request, so that the API contract is predictable, testable and consistent with every other error message.

## Acceptance criteria

Each scenario must hold with the JVM default locale set to `FRENCH`, and separately `GERMAN`, **with and without a matching `Accept-Language`**.

1. **Locale independence.**
   - Given the JVM default is French (and separately German), **and the request carries `Accept-Language` matching that locale**,
   - when `POST /api/organizations/{id}/stores` is sent with `storeCode: "THIES 09"`,
   - then the response is 400 `VALIDATION_FAILED` with message exactly `storeCode: must match the required format`, identical to an English JVM with no header.
   - Without the header, AC1 already passes on today's code under `@WebMvcTest` (decision 7).
2. **Accept-Language is ignored.** Given `Accept-Language: fr` (and separately `de`), the message is identical to the one sent without the header (R5).
3. **`@NotBlank`**, using `"   "` as the blank value:
   - Every field gives `"<field>: must not be blank"`.
   - Except `headquartersCode`, which gives `headquartersCode: must match the required format; headquartersCode: must not be blank`, because `@Pattern` also fails.
4. **`@Size`:** the exact strings in the table, e.g. `storeName: size must be between 2 and 120` and `currencyCode: size must be between 3 and 3`.
5. **`@Pattern`:** the exact strings in the table, e.g. `storeCode: must match the required format`.
6. **`@NotNull`:** change headquarters with `{}` or `{"storeId": null}` gives exactly `storeId: must not be null`.
7. **Several violations:** these are pinned exactly, in every locale, with parts sorted in plain `String` order (field name, then text).
   - `storeCode: ""` gives `storeCode: must match the required format; storeCode: must not be blank`.
   - `storeName: ""` gives `storeName: must not be blank; storeName: size must be between 2 and 120`.
8. **Format:** each part is `"<field>: <text>"`, joined with `"; "`. Unchanged.
9. **No echo:** a `storeCode` of `"LEAK 4711"`, or an over-long `storeName`, never appears in the response, in any locale.
10. **Other messages unchanged:** `"<param>: must be a valid UUID"`, `"Request body is missing or malformed"`, and every other code and message (409, 404, 422, 401) are unchanged.
11. **Fallback:** a field error whose constraint has no pinned text gives `"<field>: is invalid"`, in every locale. Test this at unit level on the helper with:
    - a plain `new FieldError("obj", "field", "typeMismatch")`
    - a violation from a test-only record using e.g. `@Email`
12. **Full register message:** `OrganizationControllerTest.shouldReturnBadRequestWhenRegistrationRequestIsInvalid` pins the full multi-field message from the table. Today it asserts only `code`.

## Business rules

- **R1.** All API error messages are in English (product-owner decision, 2026-09-28). The server locale never affects them.
- **R2.** A validation message never echoes the submitted value (unchanged from decision 8a).
- **R3.** The validation message format stays `"<field>: <text>"`, joined with `"; "`. Parts are in natural `String` order (no `Collator`), with duplicates removed.
- **R4.** Clients rely only on `code`, the stable machine-readable contract. `message` is developer-facing, human-readable English, and clients must not parse it (product-owner decision, 2026-09-28).
- **R5.** Localized end-user error text is the client's job: it maps `code` to its own translated text. The server always returns English `message` text and ignores `Accept-Language` for errors (product-owner decision, 2026-09-28).

## Definition of done

- **Helper:** `FieldErrorMessages` (or similar) in `presentation.rest.exception`.
  - `handleInvalidRequest` **never** calls `getDefaultMessage()`.
  - Numbers are never formatted with the default locale.
  - Unit tests cover every pinned text and the fallback (AC11).
- **Locale forcing:**
  - The exact-message web tests run with a French JVM default, parameterized over `Accept-Language` none / `fr` / `de`.
  - Add a German-default variant.
  - Add one `@Size` case under the JVM default `ar-EG`, which catches `String.format` digits.
  - Save and restore `Locale.getDefault()` together with `Locale.getDefault(Category.FORMAT)` and `(Category.DISPLAY)`. Do it in `@BeforeEach`/`@AfterEach` or a small JUnit extension, and always restore, even when a test fails.
  - Surefire is not parallel, so changing the global default is safe.
- **Red first:** the failing-first evidence comes from the `Accept-Language: fr`/`de` cases. `Locale.setDefault` alone doesn't fail on the current code under MockMvc (decision 7).
- **Exact messages:**
  - Pin exact text with `value("…")`, not `startsWith`, for `@NotBlank`, `@Size`, `@Pattern` and `@NotNull` across all three DTOs, including the multi-violation cases (AC7) and the full register message (AC12).
  - Update the four existing tests listed in Context.
  - The no-echo tests stay, and also run under a non-English locale.
- **Guard test:** `RequestDtoConstraintGuardTest` in `organization/src/test/.../presentation/rest/request`.
  - Find every class in `com.zim.organization.presentation.rest.request` with Spring's `ClassPathScanningCandidateComponentProvider(false)` and an accept-all filter, and assert the set is non-empty.
  - Read their constraints through `Validation.buildDefaultValidatorFactory().getValidator().getConstraintsForClass(dto)`. Don't use `RecordComponent.getAnnotations()`: the jakarta constraints don't target record components, so it returns nothing.
  - Assert every property constraint type is in {`NotBlank`, `NotNull`, `Size`, `Pattern`}, and that there are no class-level constraints. `getFieldErrors()` would ignore class-level ones and produce an empty message.
  - The failure message tells the developer to add a pinned text and a table row.
- **Build:** `mvn -pl organization test` passes on an English JVM and on a French JVM (`-Duser.language=fr`). All ITs pass.
- **Docs:** the pinned texts are in this ticket and in `docs/tickets/endpoints-T5.md`'s error table next to 8a.

## Out of scope

- Full i18n (localized messages chosen from `Accept-Language`, translation bundles). Permanently out of scope for the server; the client handles localization (R5).
- Changing `code` values or the `ApiErrorResponse` shape, including per-field structured errors (a follow-up ticket; see the product-owner follow-up below).
- Domain and business-rule messages, which are already English.
- Mapping `HandlerMethodValidationException` (constraints on `@PathVariable`/`@RequestParam`). No controller has any today, so this goes to T6.
- Other modules, which follow the T6 conventions.
- Wiring `bootstrap` as the composition root.

## Dependencies

- Builds on T5a decision 8a (fixed 400 messages, no echo).
- No dependency on `bootstrap`: option (d) needs no global configuration.
- Feeds the T6 conventions (see `docs/tickets/tenant-scoping-T1-T4.md`).

## Open questions

**Architect:** ~~1–7~~ all **resolved**, see the decisions table.

**Product owner**
1. ~~Is English the official language of the API's error messages?~~ **Resolved 2026-09-28: English is the API language.**
2. ~~Should clients rely only on `code`?~~ **Resolved 2026-09-28: clients rely only on `code`.** `message` is developer-facing. This is documented in `endpoints-T5.md`'s shared contract, in T6, and later in the OpenAPI description.
3. ~~Is localized end-user text a future need, and where would it live?~~ **Resolved 2026-09-28: the client maps `code` to its own translated text.** The server never localizes error messages.

**Product-owner follow-up (not blocking).** R4 and R5 together mean a client can't show a localized **field-level** error, because `VALIDATION_FAILED` is the only code and parsing `message` is forbidden. **Per-field structured errors** (`field` plus a constraint code) are the way to deliver client-side localization for validation. The BA should raise this as a follow-up ticket.
