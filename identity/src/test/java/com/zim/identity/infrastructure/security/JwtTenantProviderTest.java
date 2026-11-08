package com.zim.identity.infrastructure.security;

import com.zim.shared.domain.TenantId;
import com.zim.shared.tenant.TenantNotResolvedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The tenant is the {@code tenantId} claim of the authenticated token, and
 * nothing else. Anything else fails closed.
 */
class JwtTenantProviderTest {

  private static final UUID TENANT = UUID.fromString("2d3a7d37-ef2c-4794-b248-b08acf42eb38");

  private final JwtTenantProvider provider = new JwtTenantProvider();

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void shouldReturnTheTenantOfTheClaim() {
    authenticate(jwt(Map.of("tenantId", TENANT.toString())));

    assertThat(provider.currentTenant()).isEqualTo(new TenantId(TENANT));
  }

  @Test
  void shouldFailWhenTheClaimIsAbsent() {
    authenticate(jwt(Map.of("other", "x")));

    assertNotResolved();
  }

  @Test
  void shouldNeverFallBackToTheSubjectNorAnotherClaim() {
    authenticate(jwt(Map.of(
        "sub", TENANT.toString(),
        "tenant", TENANT.toString(),
        "tenant_id", TENANT.toString(),
        "userId", TENANT.toString()
    )));

    assertNotResolved();
  }

  @Test
  void shouldFailWhenTheClaimIsNotACanonicalUuid() {
    for (Object claim : List.of(
        "not-a-uuid", "", "1-1-1-1-1", TENANT.toString().toUpperCase(),
        " " + TENANT, 12345, List.of(TENANT.toString()))) {
      authenticate(jwt(Map.of("tenantId", claim)));

      assertNotResolved();
    }
  }

  @Test
  void shouldFailWhenThePrincipalIsNotAToken() {
    SecurityContextHolder.getContext().setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(
            TENANT.toString(), null, AuthorityUtils.NO_AUTHORITIES));

    assertNotResolved();
  }

  @Test
  void shouldFailWhenTheAuthenticationIsNotAuthenticated() {
    SecurityContextHolder.getContext().setAuthentication(
        UsernamePasswordAuthenticationToken.unauthenticated(
            jwt(Map.of("tenantId", TENANT.toString())), null));

    assertNotResolved();
  }

  @Test
  void shouldFailForAnAnonymousRequest() {
    SecurityContextHolder.getContext().setAuthentication(
        new AnonymousAuthenticationToken(
            "key", "anonymous", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

    assertNotResolved();
  }

  @Test
  void shouldFailWithoutAnyAuthentication() {
    SecurityContextHolder.clearContext();

    assertNotResolved();
  }

  private void assertNotResolved() {
    assertThatThrownBy(provider::currentTenant)
        .isInstanceOf(TenantNotResolvedException.class)
        .hasMessage(TenantNotResolvedException.MESSAGE);
  }

  private static void authenticate(Jwt jwt) {
    SecurityContextHolder.getContext().setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(
            jwt, null, AuthorityUtils.NO_AUTHORITIES));
  }

  private static Jwt jwt(Map<String, Object> claims) {
    return Jwt.withTokenValue("token")
        .header("alg", "HS256")
        .claims(existing -> existing.putAll(claims))
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(600))
        .build();
  }
}
