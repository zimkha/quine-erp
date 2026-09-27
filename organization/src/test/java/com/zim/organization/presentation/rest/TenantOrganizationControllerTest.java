package com.zim.organization.presentation.rest;

import com.zim.organization.application.command.AddStoreCommand;
import com.zim.organization.application.command.ChangeHeadquartersCommand;
import com.zim.organization.application.exception.OrganizationNotFoundException;
import com.zim.organization.application.handler.AddStoreHandler;
import com.zim.organization.application.handler.ChangeHeadquartersHandler;
import com.zim.organization.application.result.AddStoreResult;
import com.zim.organization.application.result.ChangeHeadquartersResult;
import com.zim.organization.domain.model.Organization;
import com.zim.organization.domain.model.OrganizationStatus;
import com.zim.organization.domain.model.Store;
import com.zim.organization.domain.rule.OrganizationMustBeActiveToAddStoreRule;
import com.zim.organization.domain.valueobject.CurrencyCode;
import com.zim.organization.domain.valueobject.LegalName;
import com.zim.organization.domain.valueobject.OrganizationId;
import com.zim.organization.domain.valueobject.OrganizationName;
import com.zim.organization.domain.valueobject.StoreCode;
import com.zim.organization.domain.valueobject.StoreId;
import com.zim.organization.domain.valueobject.StoreName;
import com.zim.organization.presentation.rest.exception.ApiExceptionHandler;
import com.zim.organization.presentation.rest.request.AddStoreRequest;
import com.zim.organization.presentation.rest.request.ChangeHeadquartersRequest;
import com.zim.organization.testing.InMemoryDomainEventPublisher;
import com.zim.organization.testing.InMemoryOrganizationRepository;
import com.zim.shared.domain.BusinessRuleViolationException;
import com.zim.shared.domain.TenantId;
import com.zim.shared.tenant.CurrentTenantProvider;
import com.zim.shared.tenant.TenantNotResolvedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TenantOrganizationController.class)
// The test application does not component-scan, so the web slice has to be
// imported explicitly.
@Import({TenantOrganizationController.class, ApiExceptionHandler.class})
class TenantOrganizationControllerTest {

  private static final UUID ORGANIZATION_ID =
      UUID.fromString("5c80d578-83f7-4b44-b5f7-598530067a09");

  private static final UUID TENANT_ID =
      UUID.fromString("2d3a7d37-ef2c-4794-b248-b08acf42eb38");

  private static final UUID OTHER_TENANT_ID =
      UUID.fromString("9a4c2e71-5b3d-4f8a-b6c1-0d2e4f6a8b13");

  private static final UUID HEADQUARTERS_ID =
      UUID.fromString("4ee0d038-4617-435c-b7c8-48697d4cf909");

  private static final UUID NEW_STORE_ID =
      UUID.fromString("86fd6eb4-23f6-4381-842e-e5d57def4a39");

  private static final Instant CREATED_AT =
      Instant.parse("2026-08-25T10:00:00Z");

  private static final Instant ADDED_AT =
      Instant.parse("2026-08-26T10:00:00Z");

  private static final Instant CHANGED_AT =
      Instant.parse("2026-08-27T10:00:00Z");

  private static final UUID OTHER_ORGANIZATION_ID =
      UUID.fromString("c1f0a9d2-7e34-4b1a-9c5d-2f8e6b4a1d07");

  private static final UUID OTHER_TENANT_STORE_ID =
      UUID.fromString("e7b2c4a9-1d3f-4e5a-8b6c-9f0a2d4c6e18");

  private static final String STORES_PATH =
      "/api/organizations/{id}/stores";

  private static final String HEADQUARTERS_PATH =
      "/api/organizations/{id}/headquarters";

  private static final String VALID_BODY = """
      {"storeCode": "thies-02", "storeName": "Magasin 2"}
      """;

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private AddStoreHandler addStoreHandler;

  @MockitoBean
  private ChangeHeadquartersHandler changeHeadquartersHandler;

  @MockitoBean
  private CurrentTenantProvider currentTenantProvider;

  @BeforeEach
  void resolveTenantA() {
    when(currentTenantProvider.currentTenant())
        .thenReturn(new TenantId(TENANT_ID));
  }

  // --- Success -------------------------------------------------------------

  @Test
  void shouldAddStoreToActiveOrganization() throws Exception {
    InMemoryOrganizationRepository repository = repositoryWith(
        organization(OrganizationStatus.ACTIVE)
    );
    delegateToRealHandler(repository);

    addStore(ORGANIZATION_ID.toString(), VALID_BODY)
        .andExpect(status().isCreated())
        .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
        .andExpect(jsonPath("$", aMapWithSize(7)))
        .andExpect(jsonPath("$.organizationId")
            .value(ORGANIZATION_ID.toString()))
        .andExpect(jsonPath("$.storeId").value(NEW_STORE_ID.toString()))
        .andExpect(jsonPath("$.storeCode").value("THIES-02"))
        .andExpect(jsonPath("$.storeName").value("Magasin 2"))
        .andExpect(jsonPath("$.headquarters").value(false))
        .andExpect(jsonPath("$.active").value(true))
        .andExpect(jsonPath("$.addedAt").value(ADDED_AT.toString()))
        .andExpect(jsonPath("$.tenantId").doesNotExist());

    assertThat(repository.saveCount()).isEqualTo(1);
    assertThat(storedOrganization(repository).stores())
        .anySatisfy(store -> {
          assertThat(store.id().value()).isEqualTo(NEW_STORE_ID);
          assertThat(store.code().value()).isEqualTo("THIES-02");
          assertThat(store.isHeadquarters()).isFalse();
          assertThat(store.isActive()).isTrue();
        })
        .hasSize(2);
  }

  /**
   * The command carries the provider's tenant and the path id. A tenantId
   * in the body and a tenant header, both set to another tenant, have no
   * effect.
   */
  @Test
  void shouldTakeTenantOnlyFromTheProvider() throws Exception {
    when(addStoreHandler.handle(any())).thenReturn(new AddStoreResult(
        ORGANIZATION_ID,
        NEW_STORE_ID,
        "THIES-02",
        "Magasin 2",
        false,
        true,
        ADDED_AT
    ));

    String body = """
        {
         "tenantId": "%s",
         "storeCode": "thies-02",
         "storeName": "Magasin 2"
        }
        """.formatted(OTHER_TENANT_ID);

    mockMvc.perform(
            post(STORES_PATH, ORGANIZATION_ID)
                .header("X-Tenant-Id", OTHER_TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        )
        .andExpect(status().isCreated());

    ArgumentCaptor<AddStoreCommand> command =
        ArgumentCaptor.forClass(AddStoreCommand.class);
    verify(addStoreHandler).handle(command.capture());
    assertThat(command.getValue()).isEqualTo(new AddStoreCommand(
        new TenantId(TENANT_ID),
        ORGANIZATION_ID,
        "thies-02",
        "Magasin 2"
    ));
    verify(currentTenantProvider, times(1)).currentTenant();

    assertThat(
        Arrays.stream(AddStoreRequest.class.getRecordComponents())
            .map(RecordComponent::getName)
    ).doesNotContain("tenantId");
  }

  // --- 401 -----------------------------------------------------------------

  @Test
  void shouldReturnUnauthorizedWhenTenantIsNotResolved() throws Exception {
    when(currentTenantProvider.currentTenant())
        .thenThrow(new TenantNotResolvedException());

    addStore(ORGANIZATION_ID.toString(), VALID_BODY)
        .andExpect(status().isUnauthorized())
        .andExpect(header().string(
            HttpHeaders.WWW_AUTHENTICATE,
            "Bearer realm=\"quine-erp\""
        ))
        .andExpect(jsonPath("$", aMapWithSize(3)))
        .andExpect(jsonPath("$.code").value("TENANT_NOT_RESOLVED"));

    verifyNoInteractions(addStoreHandler);
  }

  // --- 400 -----------------------------------------------------------------

  @ParameterizedTest
  @CsvSource({
      "storeCode, A",
      "storeCode, THIES 01",
      "storeCode, ''",
      "storeCode, '   '",
      "storeName, X",
      "storeName, ''",
      "storeName, '   '"
  })
  void shouldReturnBadRequestWhenFieldIsInvalid(
      String field,
      String value
  ) throws Exception {
    String body = field.equals("storeCode")
        ? requestBody(value, "Magasin 2")
        : requestBody("THIES-02", value);

    addStore(ORGANIZATION_ID.toString(), body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$", aMapWithSize(3)))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.message").value(startsWith(field + ": ")))
        .andExpect(jsonPath("$.timestamp").exists());

    verifyNoInteractions(addStoreHandler);
  }

  @Test
  void shouldReturnBadRequestWhenPathIdIsNotUuid() throws Exception {
    String body = addStore("not-a-uuid-4711", VALID_BODY)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$", aMapWithSize(3)))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.message").value("id: must be a valid UUID"))
        .andReturn().getResponse().getContentAsString();

    assertThat(body).doesNotContain("not-a-uuid-4711");
    verifyNoInteractions(addStoreHandler);
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "",
      "{\"storeCode\": \"LEAK-4711\",",
      "[\"LEAK-4711\"]"
  })
  void shouldReturnBadRequestWhenBodyIsMissingOrMalformed(String body)
      throws Exception {
    String response = addStore(ORGANIZATION_ID.toString(), body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$", aMapWithSize(3)))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.message")
            .value("Request body is missing or malformed"))
        .andReturn().getResponse().getContentAsString();

    assertThat(response).doesNotContain("LEAK-4711");
    verifyNoInteractions(addStoreHandler);
  }

  @ParameterizedTest
  @CsvSource({
      "storeCode, LEAK 4711",
      "storeName, "
          + "leak4711leak4711leak4711leak4711leak4711leak4711leak4711leak4711"
          + "leak4711leak4711leak4711leak4711leak4711leak4711leak4711leak4711"
  })
  void shouldNotEchoSubmittedValueInValidationMessage(
      String field,
      String value
  ) throws Exception {
    String body = field.equals("storeCode")
        ? requestBody(value, "Magasin 2")
        : requestBody("THIES-02", value);

    String response = addStore(ORGANIZATION_ID.toString(), body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andReturn().getResponse().getContentAsString();

    assertThat(response).doesNotContain(value);
  }

  /**
   * Request-format validation runs before the tenant is resolved (Architect
   * decision 5): a malformed request with no tenant gets 400, not 401.
   */
  @Test
  void shouldValidateBeforeResolvingTenant() throws Exception {
    when(currentTenantProvider.currentTenant())
        .thenThrow(new TenantNotResolvedException());

    addStore("not-a-uuid", VALID_BODY)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

    verifyNoInteractions(currentTenantProvider);
    verifyNoInteractions(addStoreHandler);
  }

  // --- 404 -----------------------------------------------------------------

  @Test
  void shouldReturnNotFoundWhenOrganizationDoesNotExist() throws Exception {
    when(addStoreHandler.handle(any())).thenThrow(
        new OrganizationNotFoundException(ORGANIZATION_ID)
    );

    addStore(ORGANIZATION_ID.toString(), VALID_BODY)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ORGANIZATION_NOT_FOUND"));
  }

  /**
   * Tenant scoping: another tenant's organization, in any status, must be
   * indistinguishable from a missing one over HTTP, and is never a 409.
   * Both responses come from the real handler behind the mock.
   */
  @ParameterizedTest
  @EnumSource(OrganizationStatus.class)
  void shouldMapWrongTenantAndMissingOrganizationToIdenticalNotFound(
      OrganizationStatus status
  ) throws Exception {
    when(currentTenantProvider.currentTenant())
        .thenReturn(new TenantId(OTHER_TENANT_ID));

    Organization owned = organization(status);
    InMemoryOrganizationRepository ownedByA = repositoryWith(owned);
    delegateToRealHandler(ownedByA);
    String wrongTenantBody = addStore(ORGANIZATION_ID.toString(), VALID_BODY)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ORGANIZATION_NOT_FOUND"))
        .andExpect(jsonPath("$.timestamp").exists())
        .andReturn().getResponse().getContentAsString();

    delegateToRealHandler(new InMemoryOrganizationRepository());
    String missingBody = addStore(ORGANIZATION_ID.toString(), VALID_BODY)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ORGANIZATION_NOT_FOUND"))
        .andExpect(jsonPath("$.timestamp").exists())
        .andReturn().getResponse().getContentAsString();

    assertThat(withoutTimestamp(wrongTenantBody))
        .isEqualTo(withoutTimestamp(missingBody));
    assertThat(ownedByA.saveCount()).isZero();
    assertThat(owned.status()).isEqualTo(status);
    assertThat(owned.stores())
        .singleElement()
        .satisfies(store -> assertThat(store.id().value())
            .isEqualTo(HEADQUARTERS_ID));
  }

  // --- 409 -----------------------------------------------------------------

  @ParameterizedTest
  @EnumSource(
      value = OrganizationStatus.class,
      names = {"PENDING_ACTIVATION", "SUSPENDED", "CLOSED"}
  )
  void shouldRejectStoreWhenOrganizationIsNotActive(
      OrganizationStatus status
  ) throws Exception {
    Organization organization = organization(status);
    InMemoryOrganizationRepository repository = repositoryWith(organization);
    delegateToRealHandler(repository);

    addStore(ORGANIZATION_ID.toString(), VALID_BODY)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code")
            .value("ORGANIZATION_MUST_BE_ACTIVE_TO_ADD_STORE"));

    assertThat(repository.saveCount()).isZero();
    assertThat(organization.stores()).hasSize(1);
  }

  @Test
  void shouldRejectDuplicateStoreCodeIgnoringCase() throws Exception {
    InMemoryOrganizationRepository repository = repositoryWith(
        organization(OrganizationStatus.ACTIVE)
    );
    delegateToRealHandler(repository);

    addStore(
        ORGANIZATION_ID.toString(),
        requestBody("thies-01", "Magasin 2")
    )
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("STORE_CODE_ALREADY_EXISTS"));

    assertThat(repository.saveCount()).isZero();
  }

  @Test
  void shouldRejectCodeOfInactiveStore() throws Exception {
    Organization organization = organization(OrganizationStatus.ACTIVE);
    StoreId dakar = new StoreId(UUID.randomUUID());
    organization.addStore(
        dakar,
        new StoreCode("DAKAR-01"),
        new StoreName("Magasin Dakar"),
        UUID.randomUUID(),
        CREATED_AT
    );
    organization.deactivateStore(dakar, UUID.randomUUID(), CREATED_AT);
    organization.clearDomainEvents();
    InMemoryOrganizationRepository repository = repositoryWith(organization);
    delegateToRealHandler(repository);

    addStore(
        ORGANIZATION_ID.toString(),
        requestBody("dakar-01", "Magasin 2")
    )
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("STORE_CODE_ALREADY_EXISTS"));

    assertThat(repository.saveCount()).isZero();
  }

  @Test
  void shouldReturnConflictWhenBusinessRuleIsViolated() throws Exception {
    when(addStoreHandler.handle(any())).thenThrow(
        new BusinessRuleViolationException(
            new OrganizationMustBeActiveToAddStoreRule(
                OrganizationStatus.CLOSED
            )
        )
    );

    addStore(ORGANIZATION_ID.toString(), VALID_BODY)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code")
            .value("ORGANIZATION_MUST_BE_ACTIVE_TO_ADD_STORE"));
  }

  @Test
  void shouldReturnConflictOnConcurrentModification() throws Exception {
    when(addStoreHandler.handle(any())).thenThrow(
        new OptimisticLockingFailureException("stale")
    );

    addStore(ORGANIZATION_ID.toString(), VALID_BODY)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
  }

  // --- 422 -----------------------------------------------------------------

  /**
   * " a" passes the DTO but not StoreName. Value objects are built before
   * the rules run, so a non-active organization still gets 422, not 409.
   */
  @ParameterizedTest
  @EnumSource(
      value = OrganizationStatus.class,
      names = {"ACTIVE", "PENDING_ACTIVATION"}
  )
  void shouldReturnUnprocessableEntityWhenStoreNameIsTooShortOnceTrimmed(
      OrganizationStatus status
  ) throws Exception {
    InMemoryOrganizationRepository repository = repositoryWith(
        organization(status)
    );
    delegateToRealHandler(repository);

    addStore(ORGANIZATION_ID.toString(), requestBody("THIES-02", " a"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("INVALID_STORE_NAME"));

    assertThat(repository.saveCount()).isZero();
  }

  // =========================================================================
  // PUT /api/organizations/{id}/headquarters (T5b)
  // =========================================================================

  // --- Success -------------------------------------------------------------

  @Test
  void shouldChangeHeadquartersToAnotherActiveStore() throws Exception {
    Organization organization =
        organizationWithSecondStore(OrganizationStatus.ACTIVE, true);
    InMemoryOrganizationRepository repository = repositoryWith(organization);
    delegateToRealChangeHeadquartersHandler(repository);

    changeHeadquarters(ORGANIZATION_ID.toString(), storeIdBody(NEW_STORE_ID))
        .andExpect(status().isOk())
        .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
        .andExpect(jsonPath("$", aMapWithSize(3)))
        .andExpect(jsonPath("$.organizationId")
            .value(ORGANIZATION_ID.toString()))
        .andExpect(jsonPath("$.headquartersId")
            .value(NEW_STORE_ID.toString()))
        .andExpect(jsonPath("$.changedAt").value(CHANGED_AT.toString()))
        .andExpect(jsonPath("$.previousHeadquartersId").doesNotExist())
        .andExpect(jsonPath("$.tenantId").doesNotExist());

    assertThat(repository.saveCount()).isEqualTo(1);
    assertThat(storedOrganization(repository).stores())
        .filteredOn(Store::isHeadquarters)
        .singleElement()
        .satisfies(store -> assertThat(store.id().value())
            .isEqualTo(NEW_STORE_ID));
  }

  /**
   * The command carries the provider's tenant, the path id and the body's
   * storeId. A tenantId in the body and a tenant header, both set to
   * another tenant, have no effect.
   */
  @Test
  void shouldTakeTenantOnlyFromTheProviderWhenChangingHeadquarters()
      throws Exception {
    when(changeHeadquartersHandler.handle(any())).thenReturn(
        new ChangeHeadquartersResult(ORGANIZATION_ID, NEW_STORE_ID, CHANGED_AT)
    );

    String body = """
        {"tenantId": "%s", "storeId": "%s"}
        """.formatted(OTHER_TENANT_ID, NEW_STORE_ID);

    mockMvc.perform(
            put(HEADQUARTERS_PATH, ORGANIZATION_ID)
                .header("X-Tenant-Id", OTHER_TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.headquartersId")
            .value(NEW_STORE_ID.toString()));

    ArgumentCaptor<ChangeHeadquartersCommand> command =
        ArgumentCaptor.forClass(ChangeHeadquartersCommand.class);
    verify(changeHeadquartersHandler).handle(command.capture());
    assertThat(command.getValue()).isEqualTo(new ChangeHeadquartersCommand(
        new TenantId(TENANT_ID),
        ORGANIZATION_ID,
        NEW_STORE_ID
    ));
    verify(currentTenantProvider, times(1)).currentTenant();

    assertThat(
        Arrays.stream(ChangeHeadquartersRequest.class.getRecordComponents())
            .map(RecordComponent::getName)
    ).containsExactly("storeId");
  }

  // --- 401 -----------------------------------------------------------------

  @Test
  void shouldReturnUnauthorizedOnHeadquartersWhenTenantIsNotResolved()
      throws Exception {
    when(currentTenantProvider.currentTenant())
        .thenThrow(new TenantNotResolvedException());

    changeHeadquarters(ORGANIZATION_ID.toString(), storeIdBody(NEW_STORE_ID))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string(
            HttpHeaders.WWW_AUTHENTICATE,
            "Bearer realm=\"quine-erp\""
        ))
        .andExpect(jsonPath("$", aMapWithSize(3)))
        .andExpect(jsonPath("$.code").value("TENANT_NOT_RESOLVED"));

    verifyNoInteractions(changeHeadquartersHandler);
  }

  // --- 400 -----------------------------------------------------------------

  @ParameterizedTest
  @ValueSource(strings = {"{}", "{\"storeId\": null}"})
  void shouldReturnBadRequestWhenStoreIdIsMissingOrNull(String body)
      throws Exception {
    changeHeadquarters(ORGANIZATION_ID.toString(), body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$", aMapWithSize(3)))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.message").value(startsWith("storeId: ")))
        .andExpect(jsonPath("$.timestamp").exists());

    verifyNoInteractions(changeHeadquartersHandler);
  }

  /**
   * A non-UUID storeId, malformed JSON or a missing body are all rejected
   * by the JSON reader, through T5a's HttpMessageNotReadableException
   * mapping, without echoing the input.
   */
  @ParameterizedTest
  @ValueSource(strings = {
      "{\"storeId\": \"LEAK-4711\"}",
      "{\"storeId\": 4711}",
      "",
      "{\"storeId\": \"LEAK-4711\",",
      "[\"LEAK-4711\"]"
  })
  void shouldReturnBadRequestWhenHeadquartersBodyIsNotReadable(String body)
      throws Exception {
    String response = changeHeadquarters(ORGANIZATION_ID.toString(), body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$", aMapWithSize(3)))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.message")
            .value("Request body is missing or malformed"))
        .andReturn().getResponse().getContentAsString();

    assertThat(response).doesNotContain("LEAK-4711").doesNotContain("4711");
    verifyNoInteractions(changeHeadquartersHandler);
  }

  @Test
  void shouldReturnBadRequestOnHeadquartersWhenPathIdIsNotUuid()
      throws Exception {
    String body = changeHeadquarters(
        "not-a-uuid-4711",
        storeIdBody(NEW_STORE_ID)
    )
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$", aMapWithSize(3)))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.message").value("id: must be a valid UUID"))
        .andReturn().getResponse().getContentAsString();

    assertThat(body).doesNotContain("not-a-uuid-4711");
    verifyNoInteractions(changeHeadquartersHandler);
  }

  /** 400 before 401 (Architect decision 5). */
  @Test
  void shouldValidateHeadquartersRequestBeforeResolvingTenant()
      throws Exception {
    when(currentTenantProvider.currentTenant())
        .thenThrow(new TenantNotResolvedException());

    changeHeadquarters(ORGANIZATION_ID.toString(), "{}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

    verifyNoInteractions(currentTenantProvider);
    verifyNoInteractions(changeHeadquartersHandler);
  }

  // --- 404 -----------------------------------------------------------------

  @Test
  void shouldReturnNotFoundOnHeadquartersWhenOrganizationDoesNotExist()
      throws Exception {
    delegateToRealChangeHeadquartersHandler(
        new InMemoryOrganizationRepository()
    );

    changeHeadquarters(ORGANIZATION_ID.toString(), storeIdBody(NEW_STORE_ID))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$", aMapWithSize(3)))
        .andExpect(jsonPath("$.code").value("ORGANIZATION_NOT_FOUND"));
  }

  /**
   * Tenant B sends A's organization id and a real store id of A. Whatever
   * A's status and whatever that store is (an active store, an inactive
   * one, or the current headquarters), B gets exactly the missing
   * organization's 404, never one of the 409s A would get, and A's
   * headquarters is unchanged. Both responses come from the real handler.
   */
  @ParameterizedTest
  @CsvSource({
      "ACTIVE, ACTIVE_STORE",
      "ACTIVE, INACTIVE_STORE",
      "ACTIVE, HEADQUARTERS",
      "PENDING_ACTIVATION, ACTIVE_STORE",
      "SUSPENDED, ACTIVE_STORE",
      "SUSPENDED, INACTIVE_STORE",
      "CLOSED, ACTIVE_STORE",
      "CLOSED, INACTIVE_STORE"
  })
  void shouldMapWrongTenantAndMissingOrganizationToIdenticalNotFoundOnHeadquarters(
      OrganizationStatus status,
      String target
  ) throws Exception {
    when(currentTenantProvider.currentTenant())
        .thenReturn(new TenantId(OTHER_TENANT_ID));

    Organization owned = organizationWithSecondStore(
        status,
        !target.equals("INACTIVE_STORE")
    );
    UUID storeOfA = target.equals("HEADQUARTERS")
        ? HEADQUARTERS_ID
        : NEW_STORE_ID;
    InMemoryOrganizationRepository ownedByA = repositoryWith(owned);

    delegateToRealChangeHeadquartersHandler(ownedByA);
    String wrongTenantBody = changeHeadquarters(
        ORGANIZATION_ID.toString(),
        storeIdBody(storeOfA)
    )
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ORGANIZATION_NOT_FOUND"))
        .andExpect(jsonPath("$.timestamp").exists())
        .andReturn().getResponse().getContentAsString();

    delegateToRealChangeHeadquartersHandler(
        new InMemoryOrganizationRepository()
    );
    String missingBody = changeHeadquarters(
        ORGANIZATION_ID.toString(),
        storeIdBody(storeOfA)
    )
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ORGANIZATION_NOT_FOUND"))
        .andExpect(jsonPath("$.timestamp").exists())
        .andReturn().getResponse().getContentAsString();

    assertThat(withoutTimestamp(wrongTenantBody))
        .isEqualTo(withoutTimestamp(missingBody));
    assertThat(ownedByA.saveCount()).isZero();
    assertThat(owned.status()).isEqualTo(status);
    assertThat(owned.stores())
        .filteredOn(Store::isHeadquarters)
        .singleElement()
        .satisfies(store -> assertThat(store.id().value())
            .isEqualTo(HEADQUARTERS_ID));
  }

  // --- 409 -----------------------------------------------------------------

  /**
   * The status rule is checked first: even an unknown store id gets the
   * status code, not STORE_DOES_NOT_BELONG_TO_ORGANIZATION.
   */
  @ParameterizedTest
  @EnumSource(
      value = OrganizationStatus.class,
      names = {"PENDING_ACTIVATION", "SUSPENDED", "CLOSED"}
  )
  void shouldRejectHeadquartersChangeWhenOrganizationIsNotActive(
      OrganizationStatus status
  ) throws Exception {
    InMemoryOrganizationRepository repository = repositoryWith(
        organizationWithSecondStore(status, true)
    );
    delegateToRealChangeHeadquartersHandler(repository);

    for (UUID storeId : List.of(NEW_STORE_ID, UUID.randomUUID())) {
      changeHeadquarters(ORGANIZATION_ID.toString(), storeIdBody(storeId))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.code")
              .value("ORGANIZATION_MUST_BE_ACTIVE_TO_CHANGE_HEADQUARTERS"));
    }

    assertThat(repository.saveCount()).isZero();
    assertThat(storedOrganization(repository).stores())
        .filteredOn(Store::isHeadquarters)
        .singleElement()
        .satisfies(store -> assertThat(store.id().value())
            .isEqualTo(HEADQUARTERS_ID));
  }

  /**
   * A random store id and a store of another tenant's organization get the
   * same 409, so the answer reveals nothing about other tenants' stores.
   * The message quotes the storeId from the caller's own body; apart from
   * that and the timestamp the bodies are identical.
   *
   * <p>A store of another organization of the same tenant can't be tested
   * here: {@code uk_organizations_tenant_id} allows only one organization
   * per tenant.
   */
  @Test
  void shouldAnswerUnknownAndOtherTenantStoreWithSameConflict()
      throws Exception {
    InMemoryOrganizationRepository repository = repositoryWith(
        organizationWithSecondStore(OrganizationStatus.ACTIVE, true)
    );
    repository.add(organizationOfOtherTenant());
    delegateToRealChangeHeadquartersHandler(repository);
    UUID randomStoreId = UUID.randomUUID();

    String otherTenantStoreBody = changeHeadquarters(
        ORGANIZATION_ID.toString(),
        storeIdBody(OTHER_TENANT_STORE_ID)
    )
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$", aMapWithSize(3)))
        .andExpect(jsonPath("$.code")
            .value("STORE_DOES_NOT_BELONG_TO_ORGANIZATION"))
        .andReturn().getResponse().getContentAsString();

    String randomStoreBody = changeHeadquarters(
        ORGANIZATION_ID.toString(),
        storeIdBody(randomStoreId)
    )
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code")
            .value("STORE_DOES_NOT_BELONG_TO_ORGANIZATION"))
        .andReturn().getResponse().getContentAsString();

    assertThat(
        withoutTimestamp(otherTenantStoreBody)
            .replace(OTHER_TENANT_STORE_ID.toString(), "{storeId}")
    ).isEqualTo(
        withoutTimestamp(randomStoreBody)
            .replace(randomStoreId.toString(), "{storeId}")
    );
    assertThat(repository.saveCount()).isZero();
  }

  /** A repeat of a successful change lands here (Architect decision 3). */
  @Test
  void shouldRejectCurrentHeadquarters() throws Exception {
    InMemoryOrganizationRepository repository = repositoryWith(
        organizationWithSecondStore(OrganizationStatus.ACTIVE, true)
    );
    delegateToRealChangeHeadquartersHandler(repository);

    changeHeadquarters(ORGANIZATION_ID.toString(), storeIdBody(HEADQUARTERS_ID))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("STORE_IS_ALREADY_HEADQUARTERS"));

    assertThat(repository.saveCount()).isZero();
  }

  @Test
  void shouldAnswerRepeatedChangeWithAlreadyHeadquarters() throws Exception {
    InMemoryOrganizationRepository repository = repositoryWith(
        organizationWithSecondStore(OrganizationStatus.ACTIVE, true)
    );
    delegateToRealChangeHeadquartersHandler(repository);

    changeHeadquarters(ORGANIZATION_ID.toString(), storeIdBody(NEW_STORE_ID))
        .andExpect(status().isOk());
    changeHeadquarters(ORGANIZATION_ID.toString(), storeIdBody(NEW_STORE_ID))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("STORE_IS_ALREADY_HEADQUARTERS"));

    assertThat(repository.saveCount()).isEqualTo(1);
  }

  @Test
  void shouldRejectInactiveStoreAsHeadquarters() throws Exception {
    InMemoryOrganizationRepository repository = repositoryWith(
        organizationWithSecondStore(OrganizationStatus.ACTIVE, false)
    );
    delegateToRealChangeHeadquartersHandler(repository);

    changeHeadquarters(ORGANIZATION_ID.toString(), storeIdBody(NEW_STORE_ID))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code")
            .value("INACTIVE_STORE_CANNOT_BECOME_HEADQUARTERS"));

    assertThat(repository.saveCount()).isZero();
    assertThat(storedOrganization(repository).stores())
        .filteredOn(Store::isHeadquarters)
        .singleElement()
        .satisfies(store -> assertThat(store.id().value())
            .isEqualTo(HEADQUARTERS_ID));
  }

  @Test
  void shouldReturnConflictOnConcurrentHeadquartersChange() throws Exception {
    when(changeHeadquartersHandler.handle(any())).thenThrow(
        new OptimisticLockingFailureException("stale")
    );

    changeHeadquarters(ORGANIZATION_ID.toString(), storeIdBody(NEW_STORE_ID))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
  }

  // --- Helpers -------------------------------------------------------------

  private ResultActions addStore(String id, String body) throws Exception {
    return mockMvc.perform(
        post(STORES_PATH, id)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body)
    );
  }

  private static String requestBody(String storeCode, String storeName) {
    return """
        {"storeCode": "%s", "storeName": "%s"}
        """.formatted(storeCode, storeName);
  }

  /**
   * Runs the real handler over {@code repository} behind the mock, so the
   * response comes from the production ownership check, value objects and
   * rules.
   */
  private void delegateToRealHandler(
      InMemoryOrganizationRepository repository
  ) {
    AddStoreHandler realHandler = new AddStoreHandler(
        repository,
        () -> new StoreId(NEW_STORE_ID),
        UUID::randomUUID,
        () -> ADDED_AT,
        new InMemoryDomainEventPublisher()
    );
    doAnswer(invocation -> realHandler.handle(invocation.getArgument(0)))
        .when(addStoreHandler).handle(any());
  }

  private static InMemoryOrganizationRepository repositoryWith(
      Organization organization
  ) {
    InMemoryOrganizationRepository repository =
        new InMemoryOrganizationRepository();
    repository.add(organization);
    return repository;
  }

  private static Organization storedOrganization(
      InMemoryOrganizationRepository repository
  ) {
    return repository
        .findById(new TenantId(TENANT_ID), new OrganizationId(ORGANIZATION_ID))
        .orElseThrow();
  }

  /**
   * Tenant A's organization in the given status. SUSPENDED is seeded through
   * the aggregate, since no handler suspends.
   */
  private static Organization organization(OrganizationStatus status) {
    Organization organization = Organization.register(
        new OrganizationId(ORGANIZATION_ID),
        new TenantId(TENANT_ID),
        new OrganizationName("Quincaillerie Thiès"),
        new LegalName("Quincaillerie Thiès SARL"),
        CurrencyCode.xof(),
        new StoreId(HEADQUARTERS_ID),
        new StoreCode("THIES-01"),
        new StoreName("Magasin principal"),
        UUID.randomUUID(),
        CREATED_AT
    );

    if (status != OrganizationStatus.PENDING_ACTIVATION) {
      organization.activate(UUID.randomUUID(), CREATED_AT);
    }
    if (status == OrganizationStatus.SUSPENDED) {
      organization.suspend(UUID.randomUUID(), CREATED_AT);
    }
    if (status == OrganizationStatus.CLOSED) {
      organization.close(UUID.randomUUID(), CREATED_AT);
    }

    organization.clearDomainEvents();
    assertThat(organization.status()).isEqualTo(status);
    return organization;
  }

  private static String withoutTimestamp(String body) {
    return body.replaceAll("\"timestamp\"\\s*:\\s*\"[^\"]*\"", "");
  }

  // --- Helpers for PUT /headquarters ---------------------------------------

  private ResultActions changeHeadquarters(String id, String body)
      throws Exception {
    return mockMvc.perform(
        put(HEADQUARTERS_PATH, id)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body)
    );
  }

  private static String storeIdBody(UUID storeId) {
    return """
        {"storeId": "%s"}
        """.formatted(storeId);
  }

  private void delegateToRealChangeHeadquartersHandler(
      InMemoryOrganizationRepository repository
  ) {
    ChangeHeadquartersHandler realHandler = new ChangeHeadquartersHandler(
        repository,
        UUID::randomUUID,
        () -> CHANGED_AT,
        new InMemoryDomainEventPublisher()
    );
    doAnswer(invocation -> realHandler.handle(invocation.getArgument(0)))
        .when(changeHeadquartersHandler).handle(any());
  }

  /**
   * Tenant A's organization in the given status, with the headquarters
   * THIES-01 and a second store THIES-02 ({@code NEW_STORE_ID}), active or
   * not. Restored as the repository would load it, since no handler path
   * leads to PENDING_ACTIVATION with two stores.
   */
  private static Organization organizationWithSecondStore(
      OrganizationStatus status,
      boolean secondStoreActive
  ) {
    return Organization.restore(
        new OrganizationId(ORGANIZATION_ID),
        new TenantId(TENANT_ID),
        new OrganizationName("Quincaillerie Thiès"),
        new LegalName("Quincaillerie Thiès SARL"),
        CurrencyCode.xof(),
        status,
        List.of(
            Store.restore(
                new StoreId(HEADQUARTERS_ID),
                new StoreCode("THIES-01"),
                new StoreName("Magasin principal"),
                true,
                true,
                CREATED_AT
            ),
            Store.restore(
                new StoreId(NEW_STORE_ID),
                new StoreCode("THIES-02"),
                new StoreName("Magasin 2"),
                false,
                secondStoreActive,
                CREATED_AT
            )
        ),
        CREATED_AT,
        0L
    );
  }

  /** Tenant B's active organization, whose second store is active. */
  private static Organization organizationOfOtherTenant() {
    return Organization.restore(
        new OrganizationId(OTHER_ORGANIZATION_ID),
        new TenantId(OTHER_TENANT_ID),
        new OrganizationName("Boutique Dakar"),
        new LegalName("Boutique Dakar SARL"),
        CurrencyCode.xof(),
        OrganizationStatus.ACTIVE,
        List.of(
            Store.restore(
                new StoreId(UUID.randomUUID()),
                new StoreCode("DAKAR-01"),
                new StoreName("Magasin principal"),
                true,
                true,
                CREATED_AT
            ),
            Store.restore(
                new StoreId(OTHER_TENANT_STORE_ID),
                new StoreCode("DAKAR-02"),
                new StoreName("Magasin 2"),
                false,
                true,
                CREATED_AT
            )
        ),
        CREATED_AT,
        0L
    );
  }
}
