package com.zim.organization.application.command;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantScopedCommandTest {

  private static final UUID ORGANIZATION_UUID =
      UUID.fromString("5c80d578-83f7-4b44-b5f7-598530067a09");

  private static final UUID STORE_UUID =
      UUID.fromString("86fd6eb4-23f6-4381-842e-e5d57def4a39");

  private static final String TENANT_REQUIRED = "Tenant id cannot be null";

  @Test
  void activateOrganizationShouldRequireTenant() {
    assertThatThrownBy(() ->
        new ActivateOrganizationCommand(null, ORGANIZATION_UUID)
    )
        .isInstanceOf(NullPointerException.class)
        .hasMessage(TENANT_REQUIRED);
  }

  @Test
  void addStoreShouldRequireTenant() {
    assertThatThrownBy(() ->
        new AddStoreCommand(
            null,
            ORGANIZATION_UUID,
            "DAKAR-01",
            "Magasin Dakar"
        )
    )
        .isInstanceOf(NullPointerException.class)
        .hasMessage(TENANT_REQUIRED);
  }

  @Test
  void changeHeadquartersShouldRequireTenant() {
    assertThatThrownBy(() ->
        new ChangeHeadquartersCommand(null, ORGANIZATION_UUID, STORE_UUID)
    )
        .isInstanceOf(NullPointerException.class)
        .hasMessage(TENANT_REQUIRED);
  }

  @Test
  void deactivateStoreShouldRequireTenant() {
    assertThatThrownBy(() ->
        new DeactivateStoreCommand(null, ORGANIZATION_UUID, STORE_UUID)
    )
        .isInstanceOf(NullPointerException.class)
        .hasMessage(TENANT_REQUIRED);
  }

  @Test
  void closeOrganizationShouldRequireTenant() {
    assertThatThrownBy(() ->
        new CloseOrganizationCommand(null, ORGANIZATION_UUID)
    )
        .isInstanceOf(NullPointerException.class)
        .hasMessage(TENANT_REQUIRED);
  }
}
