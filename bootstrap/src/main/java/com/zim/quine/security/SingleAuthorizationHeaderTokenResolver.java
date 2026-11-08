package com.zim.quine.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.BearerTokenError;
import org.springframework.security.oauth2.server.resource.BearerTokenErrors;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

import java.util.Collections;

/**
 * Reads the token from the {@code Authorization} header only (never from the
 * query string or a form body: the default resolver's switches stay off), and
 * refuses a request that carries two {@code Authorization} headers, which is
 * ambiguous. The default resolver would read the first and ignore the rest.
 * The refusal ends in the same 401 as any other failure.
 */
final class SingleAuthorizationHeaderTokenResolver implements BearerTokenResolver {

  private final DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();

  SingleAuthorizationHeaderTokenResolver() {
    delegate.setAllowUriQueryParameter(false);
    delegate.setAllowFormEncodedBodyParameter(false);
  }

  @Override
  public String resolve(HttpServletRequest request) {
    if (Collections.list(request.getHeaders("Authorization")).size() > 1) {
      BearerTokenError error = BearerTokenErrors.invalidRequest(
          "Multiple Authorization headers");
      throw new OAuth2AuthenticationException(error);
    }
    return delegate.resolve(request);
  }
}
