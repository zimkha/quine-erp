package com.zim.organization.testing;

import com.zim.organization.application.exception.OrganizationNotFoundException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public final class OrganizationAssertions {

  private OrganizationAssertions() {
  }

  /**
   * Another tenant's organization must be reported exactly like a missing
   * one: same exception, code and message, and no side effect (nothing
   * saved to {@code repository}, nothing published by {@code publisher}).
   */
  public static void assertOrganizationNotFound(
      ThrowingCallable call,
      UUID expectedOrganizationId,
      InMemoryOrganizationRepository repository,
      InMemoryDomainEventPublisher publisher
  ) {
    assertThatThrownBy(call)
        .isInstanceOfSatisfying(
            OrganizationNotFoundException.class,
            exception -> {
              assertThat(exception.code())
                  .isEqualTo("ORGANIZATION_NOT_FOUND");
              assertThat(exception.organizationId())
                  .isEqualTo(expectedOrganizationId);
              assertThat(exception.getMessage())
                  .isEqualTo(
                      new OrganizationNotFoundException(expectedOrganizationId)
                          .getMessage()
                  );
            }
        );

    assertThat(repository.saveCount()).isZero();
    assertThat(publisher.publishedEvents()).isEmpty();
  }
}
