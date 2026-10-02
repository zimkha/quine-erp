package com.zim.identity.domain.model;

import com.zim.identity.domain.valueobject.Email;
import com.zim.identity.domain.valueobject.PasswordHash;
import com.zim.identity.domain.valueobject.UserId;
import com.zim.shared.domain.TenantId;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

  private static final Instant NOW = Instant.parse("2026-10-02T10:00:00Z");

  @Test
  void shouldCreateAnOwnerOfTheTenant() {
    TenantId tenant = TenantId.generate();

    User user = User.createOwner(
        UserId.generate(), tenant, new Email("a@b.com"),
        new PasswordHash("$2a$hash"), NOW
    );

    assertThat(user.isOwner()).isTrue();
    assertThat(user.tenantId()).isEqualTo(tenant);
    assertThat(user.email()).isEqualTo(new Email("a@b.com"));
    assertThat(user.createdAt()).isEqualTo(NOW);
  }

  @Test
  void shouldRestoreAStoredUserWithoutChangingIt() {
    User user = User.restore(
        UserId.generate(), TenantId.generate(), new Email("a@b.com"),
        new PasswordHash("$2a$hash"), false, NOW
    );

    assertThat(user.isOwner()).isFalse();
  }

  @Test
  void shouldRejectMissingParts() {
    assertThatThrownBy(() -> User.createOwner(
        UserId.generate(), null, new Email("a@b.com"),
        new PasswordHash("$2a$hash"), NOW
    )).isInstanceOf(NullPointerException.class);
  }
}
