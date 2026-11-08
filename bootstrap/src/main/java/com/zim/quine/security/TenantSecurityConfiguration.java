package com.zim.quine.security;

import com.zim.shared.tenant.TenantNotResolvedException;
import com.zim.shared.web.ServletErrorWriter;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.json.JsonMapper;

/**
 * The security chain of the tenant zone: the catch-all, ordered last. It has
 * no {@code securityMatcher}, so every request not taken by a chain with a
 * higher precedence (the platform chain, T9b, declared in another
 * configuration) ends here, and a path nobody mapped is refused by default.
 * Nothing in this class knows about another chain.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class TenantSecurityConfiguration {

  /** The one place where the order of the tenant chain is declared. */
  public static final int TENANT_CHAIN_ORDER = Ordered.LOWEST_PRECEDENCE;

  public static final String TENANT_CHAIN = "tenantSecurityFilterChain";
  public static final String TENANT_JWT_DECODER = "tenantJwtDecoder";

  @Bean
  ServletErrorWriter servletErrorWriter(JsonMapper jsonMapper) {
    return new ServletErrorWriter(jsonMapper);
  }

  /** Tenant instance: code and message of {@link TenantNotResolvedException}. */
  @Bean
  ApiAuthenticationEntryPoint tenantAuthenticationEntryPoint(
      ServletErrorWriter writer
  ) {
    return new ApiAuthenticationEntryPoint(
        writer,
        TenantNotResolvedException.CODE,
        TenantNotResolvedException.MESSAGE
    );
  }

  @Bean
  ApiAccessDeniedHandler apiAccessDeniedHandler(ServletErrorWriter writer) {
    return new ApiAccessDeniedHandler(writer);
  }

  @Bean(TENANT_CHAIN)
  @Order(TENANT_CHAIN_ORDER)
  SecurityFilterChain tenantSecurityFilterChain(
      HttpSecurity http,
      @Qualifier(TENANT_JWT_DECODER) JwtDecoder tenantJwtDecoder,
      @Qualifier("tenantAuthenticationEntryPoint")
      ApiAuthenticationEntryPoint entryPoint,
      ApiAccessDeniedHandler accessDeniedHandler
  ) throws Exception {
    http
        // Stateless bearer API, no cookie: CSRF protects nothing here, and
        // left on it would answer 403 to the public POST /api/organizations.
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(session ->
            session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        // Anonymous authentication stays on, so that a missing token or a
        // Basic header reaches the entry point (401), not a 403.
        .authorizeHttpRequests(requests -> {
          // A 404 or a 500 is forwarded to /error: it must not turn into a 401.
          requests.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll();
          for (PublicRoutes.Route route : PublicRoutes.ROUTES) {
            requests.requestMatchers(route.method(), route.pattern()).permitAll();
          }
          requests.anyRequest().authenticated();
        })
        .oauth2ResourceServer(resourceServer -> resourceServer
            .jwt(jwt -> jwt.decoder(tenantJwtDecoder))
            .bearerTokenResolver(new SingleAuthorizationHeaderTokenResolver())
            // The same instances in both places: the resource server handles
            // a bad token, the exception handling a missing one.
            .authenticationEntryPoint(entryPoint)
            .accessDeniedHandler(accessDeniedHandler))
        .exceptionHandling(exceptions -> exceptions
            .authenticationEntryPoint(entryPoint)
            .accessDeniedHandler(accessDeniedHandler));
    return http.build();
  }
}
