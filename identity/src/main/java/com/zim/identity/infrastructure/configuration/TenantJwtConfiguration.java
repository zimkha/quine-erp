package com.zim.identity.infrastructure.configuration;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.zim.identity.application.port.AccessTokenIssuer;
import com.zim.identity.infrastructure.security.JwtAccessTokenIssuer;
import com.zim.identity.infrastructure.security.TenantJwtValidators;
import com.zim.identity.infrastructure.security.TenantJwtSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.spec.SecretKeySpec;

/**
 * Token signing of the tenant chain (properties under
 * {@code quine.security.tenant.jwt.*}). The application refuses to start
 * when the key is missing or unusable. A platform chain would be a sibling
 * with its own prefix, key and encoder: there is no generic "JWT secret".
 */
@Configuration(proxyBeanMethods = false)
public class TenantJwtConfiguration {

  @Bean
  TenantJwtSettings tenantJwtSettings(Environment environment) {
    return TenantJwtSettings.from(environment);
  }

  @Bean
  JwtEncoder tenantJwtEncoder(TenantJwtSettings settings) {
    return new NimbusJwtEncoder(
        new ImmutableSecret<>(new SecretKeySpec(settings.key(), "HmacSHA256"))
    );
  }

  /**
   * The decoder of the tenant chain, HS256, from the same key as the
   * encoder. Named and qualified at every injection point: never the one
   * unqualified {@code JwtDecoder}, so a second decoder (platform) with
   * another key causes no ambiguity.
   */
  @Bean
  JwtDecoder tenantJwtDecoder(TenantJwtSettings settings) {
    NimbusJwtDecoder decoder = NimbusJwtDecoder
        .withSecretKey(new SecretKeySpec(settings.key(), "HmacSHA256"))
        .macAlgorithm(MacAlgorithm.HS256)
        .build();
    decoder.setJwtValidator(TenantJwtValidators.tenant());
    return decoder;
  }

  @Bean
  AccessTokenIssuer tenantAccessTokenIssuer(
      JwtEncoder tenantJwtEncoder,
      TenantJwtSettings settings
  ) {
    return new JwtAccessTokenIssuer(tenantJwtEncoder, settings.ttl());
  }
}
