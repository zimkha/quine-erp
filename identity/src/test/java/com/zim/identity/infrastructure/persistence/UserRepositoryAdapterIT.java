package com.zim.identity.infrastructure.persistence;

import com.zim.identity.domain.model.User;
import com.zim.identity.domain.valueobject.Email;
import com.zim.identity.domain.valueobject.PasswordHash;
import com.zim.identity.domain.valueobject.UserId;
import com.zim.identity.infrastructure.persistence.adapteur.UserRepositoryAdapter;
import com.zim.identity.infrastructure.persistence.mapper.UserPersistenceMapper;
import com.zim.identity.infrastructure.persistence.repository.SpringDataUserRepository;
import com.zim.identity.infrastructure.persistence.support.PostgresIntegrationTest;
import com.zim.shared.domain.BusinessRuleViolationException;
import com.zim.shared.domain.TenantId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserRepositoryAdapterIT extends PostgresIntegrationTest {

  private static final Instant NOW = Instant.parse("2026-10-02T10:00:00Z");

  @Autowired
  SpringDataUserRepository springDataRepository;

  @Autowired
  JdbcTemplate jdbcTemplate;

  UserRepositoryAdapter repository;

  @BeforeEach
  void setUp() {
    repository = new UserRepositoryAdapter(
        springDataRepository,
        new UserPersistenceMapper()
    );
  }

  @Test
  void shouldSaveAndReadBackAnOwner() {
    TenantId tenant = TenantId.generate();
    String email = unique("awa");

    repository.save(owner(tenant, email));

    User found = repository.findByEmail(new Email(email)).orElseThrow();
    assertThat(found.tenantId()).isEqualTo(tenant);
    assertThat(found.isOwner()).isTrue();
    assertThat(found.email()).isEqualTo(new Email(email));
    assertThat(found.passwordHash().value()).isEqualTo("$2a$10$hash");
    assertThat(found.createdAt()).isEqualTo(NOW);
  }

  @Test
  void shouldFindNothingForAnUnknownEmail() {
    assertThat(repository.findByEmail(new Email(unique("nobody")))).isEmpty();
  }

  @Test
  void shouldReportExistingEmailsAndOwners() {
    TenantId tenant = TenantId.generate();
    String email = unique("moussa");
    repository.save(owner(tenant, email));

    assertThat(repository.existsByEmail(new Email(email))).isTrue();
    assertThat(repository.existsByEmail(new Email(unique("other")))).isFalse();
    assertThat(repository.existsOwnerByTenantId(tenant)).isTrue();
    assertThat(repository.existsOwnerByTenantId(TenantId.generate())).isFalse();
  }

  @Test
  void shouldTranslateADuplicateEmailIntoTheDomainRule() {
    String email = unique("dup");
    repository.save(owner(TenantId.generate(), email));

    assertThatThrownBy(
        () -> repository.save(owner(TenantId.generate(), email))
    )
        .isInstanceOf(BusinessRuleViolationException.class)
        .extracting("code")
        .isEqualTo("EMAIL_ALREADY_EXISTS");
  }

  @Test
  void shouldTranslateASecondOwnerOfATenantIntoTheDomainRule() {
    TenantId tenant = TenantId.generate();
    repository.save(owner(tenant, unique("first")));

    assertThatThrownBy(() -> repository.save(owner(tenant, unique("second"))))
        .isInstanceOf(BusinessRuleViolationException.class)
        .extracting("code")
        .isEqualTo("TENANT_ALREADY_HAS_OWNER");
  }

  @Test
  void shouldAllowSeveralNonOwnersInTheSameTenant() {
    TenantId tenant = TenantId.generate();
    repository.save(owner(tenant, unique("owner")));

    repository.save(member(tenant, unique("member1")));
    repository.save(member(tenant, unique("member2")));

    assertThat(count(tenant)).isEqualTo(3);
  }

  @Test
  void shouldRejectAnEmailThatIsNotNormalizedAtTheDatabaseLevel() {
    assertThatThrownBy(() -> insertRaw("Upper@Case.com", "$2a$hash"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void shouldRejectABlankPasswordHashAtTheDatabaseLevel() {
    assertThatThrownBy(() -> insertRaw(unique("blank"), "   "))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private void insertRaw(String email, String passwordHash) {
    jdbcTemplate.update(
        "INSERT INTO identity.users "
            + "(id, tenant_id, email, password_hash, owner, created_at) "
            + "VALUES (?, ?, ?, ?, false, now())",
        UUID.randomUUID(), UUID.randomUUID(), email, passwordHash
    );
  }

  private int count(TenantId tenant) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM identity.users WHERE tenant_id = ?",
        Integer.class,
        tenant.value()
    );
  }

  private static String unique(String prefix) {
    return prefix + "-" + UUID.randomUUID() + "@example.com";
  }

  private static User owner(TenantId tenant, String email) {
    return User.createOwner(
        UserId.generate(), tenant, new Email(email),
        new PasswordHash("$2a$10$hash"), NOW
    );
  }

  private static User member(TenantId tenant, String email) {
    return User.restore(
        UserId.generate(), tenant, new Email(email),
        new PasswordHash("$2a$10$hash"), false, NOW
    );
  }
}
