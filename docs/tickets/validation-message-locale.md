# T5f: Make 400 `VALIDATION_FAILED` messages deterministic English, whatever the server locale (organization module)

- **Status:** written by the BA against `main` at `c4b11e7`. **Architect validation is pending.**
- **Related:** `docs/tickets/endpoints-T5.md` (Architect decision 8a: fixed 400 messages, no echo).

## Context / Why

A live smoke run of `POST /api/organizations/{id}/stores` with `storeCode: "THIES 09"` returned:
```
400 {"code":"VALIDATION_FAILED","message":"storeCode: doit correspondre à \"^[A-Za-z0-9][A-Za-z0-9_-]{1,19}$\"", ...}
```

Every other API message is fixed English text:
- domain exceptions and business rules
- `OrganizationNotFoundException` and `TENANT_NOT_RESOLVED`
- the T5a fixed 400s: `"<param>: must be a valid UUID"` and `"Request body is missing or malformed"`

Bean Validation messages are the only ones whose language depends on the runtime. This makes the API contract differ across machines, CI and production, and makes it hard to test.

What the code does today:
- **How the message is built:** `ApiExceptionHandler.handleInvalidRequest` builds `error.getField() + ": " + error.getDefaultMessage()` for each field error, sorts the parts, and joins them with `"; "`. `getDefaultMessage()` is Hibernate Validator's interpolated message, in whatever locale the interpolator resolved.
- **No explicit messages:** none of the DTOs sets a `message` attribute.
  - `RegisterOrganizationRequest`: `@NotBlank`, `@Size` (2..120, 2..160, 3..3), and `@Pattern(^[A-Za-z0-9][A-Za-z0-9_-]{1,19}$)` on `headquartersCode`.
  - `AddStoreRequest`: `@NotBlank` and `@Pattern` on `storeCode`; `@NotBlank` and `@Size(2..120)` on `storeName`.
  - `ChangeHeadquartersRequest`: `@NotNull` on `storeId`.
- **No configuration:** no `ValidationMessages.properties`, no `messages.properties`, no validator, `MessageInterpolator` or `LocaleResolver` configuration, and no locale in `application-test.yaml`.
- **The order also depends on the locale.** One value can break two constraints: `""` breaks `@NotBlank` and `@Pattern` on `storeCode`, and `@NotBlank` and `@Size` on `storeName`. The handler sorts on the translated text, so the order of the parts changes with the language.
- **The current tests wouldn't catch it.** They only assert `startsWith("<field>: ")`, which passes in any language:
  - `OrganizationControllerTest.shouldReturnBadRequestWhenFieldDoesNotMatchDomainFormat`
  - `TenantOrganizationControllerTest.shouldReturnBadRequestWhenFieldIsInvalid`
  - `TenantOrganizationControllerTest.shouldReturnBadRequestWhenStoreIdIsMissingOrNull`

  The no-echo test `shouldNotEchoSubmittedValueInValidationMessage` doesn't depend on the language either.
- **Probable mechanism (to verify):** Spring's validator wraps interpolation in `LocaleContextMessageInterpolator`, which uses the request locale from `LocaleContextHolder`.
  - In a real servlet container, a request without `Accept-Language` falls back to the JVM default locale: French on the developer machine.
  - Under MockMvc, `MockHttpServletRequest` defaults to `Locale.ENGLISH`.
  - If this is right, `Locale.setDefault(FRENCH)` alone may not reproduce the bug under MockMvc, but `Accept-Language: fr` probably does. So the language depends on both the JVM locale and the request's `Accept-Language`.

## Actors

API clients of the organization endpoints (front-end, integrators), and the operators and developers who read the responses.

## User story

As an API client, I want the 400 `VALIDATION_FAILED` message to be the same English text on every server and for every request, so that the API contract is predictable, testable and consistent with every other error message.

## Acceptance criteria

Every scenario must hold with the JVM default locale set to `FRENCH`, and again separately to `GERMAN`.

1. **Locale independence.**
   - Given the JVM default locale is French (and, in a separate run, German),
   - when `POST /api/organizations/{id}/stores` is sent with `storeCode: "THIES 09"`,
   - then the response is 400 `VALIDATION_FAILED`, and the `message` is exactly the English text the Architect pins, identical in both runs and on an English JVM.
2. **Accept-Language is ignored.**
   - Given `Accept-Language: fr` (and separately `de`),
   - when the request breaks a constraint,
   - then the message is identical to the one sent without the header. This holds unless the Architect decides otherwise.
3. **`@NotBlank`.** A blank `storeName` (`"   "`, add store), or a blank `organizationName`, `legalName`, `currencyCode`, `headquartersCode` or `headquartersName` (register), returns the pinned English "blank" text as `"<field>: <text>"`.
4. **`@Size`.** A too-short `storeName`, `organizationName`, `legalName` or `headquartersName`, or a `currencyCode` of 2 or 4 characters, returns the pinned English "size" text for that field.
5. **`@Pattern`.** A `storeCode` or `headquartersCode` of `"A"` or `"THIES 01"` returns the pinned English "format" text for that field.
6. **`@NotNull`.** Change headquarters with `{}` or `{"storeId": null}` returns exactly `"storeId: <pinned English text>"`.
7. **Several violations.** A value that breaks two constraints (e.g. `storeCode: ""`) returns a deterministic message: the same parts, in the same order, in every locale.
8. **Format.** Each part is `"<field>: <text>"`, and multiple parts are joined with `"; "`. Unchanged.
9. **No echo.** A `storeCode` of `"LEAK 4711"`, or an over-long `storeName`, never appears in the response, in any locale.
10. **Other messages unchanged.**
    - The fixed `"<param>: must be a valid UUID"` and `"Request body is missing or malformed"` messages are unchanged.
    - So is every other code and message (409, 404, 422, 401).

## Business rules

- **R1.** All API error messages are in English (product-owner decision, 2026-09-28). The server locale never affects them.
- **R2.** A validation message never echoes the submitted value (unchanged from decision 8a).
- **R3.** The validation message format stays `"<field>: <text>"`, joined with `"; "`, in a deterministic order.
- **R4.** Clients rely only on `code`, the stable, machine-readable contract. `message` is developer-facing, human-readable English, and clients must not parse it (product-owner decision, 2026-09-28).
- **R5.** Localized end-user error text is the client's job: it maps `code` to translated text. The server always returns English `message` text and ignores `Accept-Language` for errors (product-owner decision, 2026-09-28).

## Options (for the Architect to decide)

**(a) Explicit English `message` on every constraint annotation**, for example `@Pattern(regexp = ..., message = "must match the required format")`.
- **Where it lives:** presentation (the request DTOs).
- **Pros:**
  - explicit and local, with no configuration
  - removes the regex, if the text doesn't include it
  - easy to pin in tests
- **Cons:**
  - verbose, and easy to forget on the next DTO, with nothing enforcing it
  - the same texts get repeated across DTOs and modules
  - `{min}`/`{max}` placeholders still go through interpolation (safe in practice, since they're numbers)

**(b) A `ValidationMessages.properties` with English defaults**, overriding `jakarta.validation.constraints.*.message`.
- **Where it lives:** a classpath resource. This is really an app-wide concern, so `bootstrap` or `shared`.
- **Pros:**
  - one place to change
  - no annotation noise
  - new DTOs are covered automatically
- **Cons:**
  - Bean Validation resolves a single `ValidationMessages` bundle at the classpath root, so several modules each shipping one would collide, and the first one found wins.
  - It's unclear whether it fully shadows Hibernate Validator's built-in `_fr`/`_de` bundles when the requested locale is `fr`. This must be verified by a test.
  - `@Pattern` keeps `{regexp}` unless the file removes it.
  - `bootstrap` isn't runnable yet.

**(c) Force interpolation to a fixed locale**, through a custom `LocalValidatorFactoryBean` / `MessageInterpolator` bean or through config.
- **Where it lives:** infrastructure/configuration, moving to `bootstrap` later.
- **Pros:** one configuration point that covers every constraint, including future ones.
- **Cons:**
  - Setting the JVM default locale or `Locale.ROOT` isn't enough: `LocaleContextMessageInterpolator` passes the request locale explicitly, so the interpolator has to ignore the locale it's given.
  - It's a global side effect across every module.
  - With `ROOT`/`ENGLISH`, the default message still quotes the regex.
  - It overrides a Spring Boot auto-configured bean.

**(d) Build the message in `ApiExceptionHandler` from the constraint type**, using `FieldError.getCode()` (`NotBlank`, `Size`, `Pattern`, `NotNull`) instead of `getDefaultMessage()`. For example: `"must not be blank"`, `"must match the required format"`, `"size must be between 2 and 120"`.
- **Where it lives:** presentation (the REST exception mapping), in the same place and style as the decision 8a fixed messages.
- **Pros:**
  - fully deterministic, whatever the locale or interpolator
  - removes the regex
  - consistent with the fixed-message approach
  - easy to pin in tests
  - no dependency on `bootstrap`
- **Cons:**
  - it needs a fallback text for unknown constraint types
  - `@Size` bounds must be read from the error's arguments, or dropped
  - each module's scoped advice would repeat the mapping unless it's extracted to shared presentation support (T6)

Options can be combined, for example (d) with a test guard, or (c) as a safety net under (a).

## Definition of done

- **Locale forcing:** `@WebMvcTest` tests force a non-English default locale, `Locale.setDefault(FRENCH)` plus a German variant, through `@BeforeEach`/`@AfterEach` or a small JUnit extension. They always restore the original locale, even when a test fails.
- **Accept-Language:** the tests also send `Accept-Language: fr` and `de`, because under MockMvc the request locale defaults to English.
- **Red first:** the new tests are first shown failing on the current code, before the fix.
- **Exact messages:** tests pin the exact message text with `value("storeCode: ...")` rather than `startsWith`, for `@NotBlank`, `@Size`, `@Pattern` and `@NotNull` across all three DTOs, including one multi-violation case.
- **Existing tests:** update the assertions in the three tests listed in Context to exact messages. The no-echo tests stay, and also run under a non-English locale.
- **Build:** `mvn -pl organization test` passes on both an English and a French JVM.
- **Documentation:** the pinned texts are recorded in the ticket's error table, next to decision 8a.

## Out of scope

- Full i18n: localized messages chosen from `Accept-Language`, and translation bundles. This is permanently out of scope for the server; the client handles localization (R5).
- Changing `code` values or the `ApiErrorResponse` shape, including per-field structured errors, which could be its own ticket.
- Domain and business-rule messages, which are already English.
- Other modules. They aren't implemented yet. The chosen approach becomes a **T6 convention**: "API error messages are fixed English, never locale-resolved; validation messages are pinned in tests under a non-English locale."
- Wiring `bootstrap` as the composition root.

## Dependencies

- Builds on T5a decision 8a (fixed 400 messages, no echo).
- Options (b) and (c) depend on where the app-wide configuration lives, and there's no runnable `bootstrap` yet.
- Feeds the T6 conventions.

## Open questions

**Architect**
1. Which option (a/b/c/d), or which combination?
2. Should the regex appear in the message? It leaks no user data, but it exposes an internal rule. A human-readable text could replace it, for example "2–20 letters, digits, '-' or '_', starting with a letter or digit".
3. Should `@Size` messages keep the bounds ("between 2 and 120")?
4. When one field breaks several constraints, should all parts be returned, or only one per field? What is the canonical order?
5. Confirm that `Accept-Language` must be ignored (the proposal).
6. Can we enforce this against regressions in new DTOs or modules, for example with an ArchUnit-style test or a shared handler in T6?
7. Confirm by test that MockMvc defaults to `Locale.ENGLISH`, which would make `Locale.setDefault` alone insufficient to reproduce the bug.

**Product owner**
1. ~~Is English the official language of the API's error messages?~~ **Resolved 2026-09-28 by the product owner: English is the API language.**
2. ~~Should clients rely only on `code`?~~ **Resolved 2026-09-28 by the product owner: clients rely only on `code`.** `message` is developer-facing text. Whether and where the API contract documents this is for the Architect.
3. ~~Is localized end-user error text a future need, and where would it live?~~ **Resolved 2026-09-28 by the product owner: localized end-user text is handled by the client, which maps `code` to its own translated text.** The server never localizes error messages, so no server-side i18n ticket is needed.
