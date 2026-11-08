package com.zim.quine.security;

import org.springframework.http.HttpMethod;

import java.util.List;

/**
 * The only routes that need no token: one list, used by the security chain
 * and by the route-walking test, so that a new public route cannot be added
 * without editing it here. Everything else, existing or future, mapped or
 * not, is authenticated by default. Matching is by HTTP method and path
 * pattern: {@code GET /api/organizations} is not public by accident.
 */
public final class PublicRoutes {

  /** A route open to anyone: an HTTP method and a path pattern. */
  public record Route(HttpMethod method, String pattern) {
  }

  /** Registration (it generates the tenant) and login. */
  public static final List<Route> ROUTES = List.of(
      new Route(HttpMethod.POST, "/api/organizations"),
      new Route(HttpMethod.POST, "/api/auth/login")
  );

  private PublicRoutes() {
  }
}
