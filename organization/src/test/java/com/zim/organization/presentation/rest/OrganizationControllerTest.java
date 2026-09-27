package com.zim.organization.presentation.rest;

import com.zim.organization.application.command.RegisterOrganizationCommand;
import com.zim.organization.application.exception.OrganizationAlreadyExistsException;
import com.zim.organization.application.exception.OrganizationNotFoundException;
import com.zim.organization.application.handler.RegisterOrganizationHandler;
import com.zim.organization.application.result.RegisterOrganizationResult;
import com.zim.organization.domain.model.OrganizationStatus;
import com.zim.organization.domain.rule.OrganizationMustBeActiveToAddStoreRule;
import com.zim.organization.domain.valueobject.CurrencyCode;
import com.zim.organization.presentation.rest.exception.ApiExceptionHandler;
import com.zim.shared.domain.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrganizationController.class)
// The test application does not component-scan, so the web slice has to be
// imported explicitly.
@Import({OrganizationController.class, ApiExceptionHandler.class})
class OrganizationControllerTest {

  private static final UUID ORGANIZATION_ID =
      UUID.fromString(
          "5c80d578-83f7-4b44-b5f7-598530067a09"
      );

  private static final UUID TENANT_ID =
      UUID.fromString(
          "2d3a7d37-ef2c-4794-b248-b08acf42eb38"
      );

  private static final UUID HEADQUARTERS_ID =
      UUID.fromString(
          "4ee0d038-4617-435c-b7c8-48697d4cf909"
      );

  private static final Instant CREATED_AT =
      Instant.parse("2026-08-25T10:00:00Z");

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private RegisterOrganizationHandler registerOrganizationHandler;

  @Test
  void shouldRegisterOrganization() throws Exception {
    // Given
    RegisterOrganizationResult result =
        new RegisterOrganizationResult(
            ORGANIZATION_ID,
            TENANT_ID,
            "Quincaillerie Thiès",
            "PENDING_ACTIVATION",
            HEADQUARTERS_ID,
            CREATED_AT
        );

    when(registerOrganizationHandler.handle(any()))
        .thenReturn(result);

    String request = """
        {
         "organizationName": "Quincaillerie Thiès",
         "legalName": "Quincaillerie Thiès SARL",
         "currencyCode": "XOF",
         "headquartersCode": "THIES-01",
         "headquartersName": "Magasin principal"
        }
        """;

    // When / Then
    mockMvc.perform(
            post("/api/organizations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request)
        )
        .andExpect(status().isCreated())
        .andExpect(
            jsonPath("$.organizationId")
                .value(ORGANIZATION_ID.toString())
        )
        .andExpect(
            jsonPath("$.tenantId")
                .value(TENANT_ID.toString())
        )
        .andExpect(
            jsonPath("$.organizationName")
                .value("Quincaillerie Thiès")
        )
        .andExpect(
            jsonPath("$.status")
                .value("PENDING_ACTIVATION")
        )
        .andExpect(
            jsonPath("$.headquartersId")
                .value(HEADQUARTERS_ID.toString())
        )
        .andExpect(
            jsonPath("$.createdAt")
                .value(CREATED_AT.toString())
        );

    verify(registerOrganizationHandler)
        .handle(any());
  }
  @Test
  void shouldReturnBadRequestWhenRegistrationRequestIsInvalid()
      throws Exception {

    String request = """
      {
       "organizationName": "",
       "legalName": "",
       "currencyCode": "XO",
       "headquartersCode": "",
       "headquartersName": ""
      }
      """;

    mockMvc.perform(
            post("/api/organizations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request)
        )
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

    verify(
        registerOrganizationHandler,
        org.mockito.Mockito.never()
    ).handle(any());
  }
  @Test
  void shouldReturnConflictWhenLegalNameAlreadyExists()
      throws Exception {

    when(registerOrganizationHandler.handle(any()))
        .thenThrow(
            new OrganizationAlreadyExistsException(
                "Quincaillerie Thiès SARL"
            )
        );

    String request = """
      {
       "organizationName": "Quincaillerie Thiès",
       "legalName": "Quincaillerie Thiès SARL",
       "currencyCode": "XOF",
       "headquartersCode": "THIES-01",
       "headquartersName": "Magasin principal"
      }
      """;

    mockMvc.perform(
            post("/api/organizations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request)
        )
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.code")
                .value("ORGANIZATION_ALREADY_EXISTS")
        )
        .andExpect(
            jsonPath("$.message")
                .value(
                    "An organization with legal name "
                        + "'Quincaillerie Thiès SARL' "
                        + "already exists"
                )
        );
  }

  @ParameterizedTest
  @CsvSource({
      "headquartersCode, A",
      "headquartersCode, THIES 01",
      "organizationName, X",
      "legalName, Y",
      "headquartersName, Z"
  })
  void shouldReturnBadRequestWhenFieldDoesNotMatchDomainFormat(
      String field,
      String value
  ) throws Exception {
    register(validRequestWith(field, value))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.message").value(
            startsWith(field + ": ")
        ))
        .andExpect(jsonPath("$.timestamp").exists());

    verify(registerOrganizationHandler, org.mockito.Mockito.never())
        .handle(any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"GBP", "ABC"})
  void shouldReturnUnprocessableEntityWhenCurrencyIsNotSupported(
      String currencyCode
  ) throws Exception {
    // The handler builds the real value object, as the production one does
    when(registerOrganizationHandler.handle(any())).thenAnswer(invocation -> {
      RegisterOrganizationCommand command = invocation.getArgument(0);
      new CurrencyCode(command.currencyCode());
      return null;
    });

    register(validRequestWith("currencyCode", currencyCode))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("UNSUPPORTED_CURRENCY"));
  }

  @Test
  void shouldReturnConflictWhenBusinessRuleIsViolated() throws Exception {
    when(registerOrganizationHandler.handle(any())).thenThrow(
        new BusinessRuleViolationException(
            new OrganizationMustBeActiveToAddStoreRule(
                OrganizationStatus.CLOSED
            )
        )
    );

    register(validRequestWith("legalName", "Quincaillerie Thiès SARL"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code")
            .value("ORGANIZATION_MUST_BE_ACTIVE_TO_ADD_STORE"));
  }

  @Test
  void shouldReturnNotFoundWhenOrganizationDoesNotExist() throws Exception {
    when(registerOrganizationHandler.handle(any())).thenThrow(
        new OrganizationNotFoundException(ORGANIZATION_ID)
    );

    register(validRequestWith("legalName", "Quincaillerie Thiès SARL"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ORGANIZATION_NOT_FOUND"));
  }

  @Test
  void shouldReturnConflictOnConcurrentModification() throws Exception {
    when(registerOrganizationHandler.handle(any())).thenThrow(
        new OptimisticLockingFailureException("stale")
    );

    register(validRequestWith("legalName", "Quincaillerie Thiès SARL"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
  }

  @Test
  void shouldReturnConflictWithoutSqlDetailsOnDataIntegrityViolation()
      throws Exception {
    when(registerOrganizationHandler.handle(any())).thenThrow(
        new DataIntegrityViolationException(
            "duplicate key value violates unique constraint \"secret\""
        )
    );

    register(validRequestWith("legalName", "Quincaillerie Thiès SARL"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DATA_INTEGRITY_VIOLATION"))
        .andExpect(jsonPath("$.message")
            .value("The request conflicts with existing data"));
  }

  private ResultActions register(String body) throws Exception {
    return mockMvc.perform(
        post("/api/organizations")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body)
    );
  }

  private static String validRequestWith(String field, String value) {
    Map<String, String> fields = new LinkedHashMap<>();
    fields.put("organizationName", "Quincaillerie Thiès");
    fields.put("legalName", "Quincaillerie Thiès SARL");
    fields.put("currencyCode", "XOF");
    fields.put("headquartersCode", "THIES-01");
    fields.put("headquartersName", "Magasin principal");
    fields.put(field, value);

    return fields.entrySet()
        .stream()
        .map(entry -> "\"%s\": \"%s\"".formatted(
            entry.getKey(),
            entry.getValue()
        ))
        .collect(Collectors.joining(", ", "{", "}"));
  }
}
