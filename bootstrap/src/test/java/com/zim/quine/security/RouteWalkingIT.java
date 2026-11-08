package com.zim.quine.security;

import com.zim.quine.PostgresContainerSupport;
import com.zim.quine.QuineApplication;
import com.zim.quine.TestJwtSecret;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * T7b-AC10, route walking, on the FULL context (a slice only sees the
 * controllers it imports): every handler mapping of the application is called
 * without a token. Every route that is not in {@link PublicRoutes} must answer
 * 401, so a new route is secured by default and a new public route fails here
 * until it is classified. The tenant activation flag is on so that its route
 * is walked too.
 */
@SpringBootTest(
    classes = QuineApplication.class,
    properties = "quine.organization.tenant-activation.enabled=true"
)
@AutoConfigureMockMvc
class RouteWalkingIT {

  @DynamicPropertySource
  static void configure(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", PostgresContainerSupport.POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", PostgresContainerSupport.POSTGRES::getUsername);
    registry.add("spring.datasource.password", PostgresContainerSupport.POSTGRES::getPassword);
    registry.add("quine.security.tenant.jwt.secret", TestJwtSecret::value);
  }

  @Autowired
  MockMvc mockMvc;

  @Autowired
  RequestMappingHandlerMapping handlerMapping;

  @Test
  void shouldKeepThePublicListToRegistrationAndLoginOnly() {
    assertThat(PublicRoutes.ROUTES).containsExactlyInAnyOrder(
        new PublicRoutes.Route(HttpMethod.POST, "/api/organizations"),
        new PublicRoutes.Route(HttpMethod.POST, "/api/auth/login")
    );
  }

  @Test
  void shouldRequireATokenOnEveryMappedRouteExceptThePublicOnes() throws Exception {
    List<String> walked = new ArrayList<>();
    List<String> publicRoutesSeen = new ArrayList<>();

    for (Map.Entry<RequestMappingInfo, HandlerMethod> mapping
        : handlerMapping.getHandlerMethods().entrySet()) {
      RequestMappingInfo info = mapping.getKey();
      Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
      // A mapping with no method (Boot's /error) accepts all: GET stands for it.
      List<RequestMethod> toCall = methods.isEmpty() ? List.of(RequestMethod.GET) : List.copyOf(methods);

      for (String pattern : info.getPathPatternsCondition().getPatternValues()) {
        for (RequestMethod method : toCall) {
          boolean isPublic = PublicRoutes.ROUTES.contains(
              new PublicRoutes.Route(HttpMethod.valueOf(method.name()), pattern));
          String path = pattern.replaceAll("\\{[^}]+}", UUID.randomUUID().toString());

          MvcResult result = mockMvc.perform(request(HttpMethod.valueOf(method.name()), path)
              .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn();

          String route = method + " " + pattern;
          walked.add(route);
          if (isPublic) {
            publicRoutesSeen.add(route);
            // No token needed: the (empty) body is refused by validation.
            assertThat(result.getResponse().getStatus()).as(route).isEqualTo(400);
          } else {
            TenantSecurityChainTest.assertTenantNotResolved(result);
          }
        }
      }
    }

    assertThat(publicRoutesSeen)
        .containsExactlyInAnyOrder("POST /api/organizations", "POST /api/auth/login");
    assertThat(walked).contains(
        "POST /api/organizations/{id}/stores",
        "PUT /api/organizations/{id}/headquarters",
        "POST /api/organizations/{id}/stores/{storeId}/deactivation",
        "POST /api/organizations/{id}/closure",
        "POST /api/organizations/{id}/activation"
    );
  }

  @Test
  void shouldAnswer401ToAPathNobodyMappedAndToAnUnmappedMethod() throws Exception {
    Map<String, HttpMethod> unmapped = Map.of(
        "/smoke/x", HttpMethod.GET,
        "/smoke/organizations/" + UUID.randomUUID() + "/activate", HttpMethod.POST,
        // T9b adds a platform chain for /api/platform/**: this case then moves to that chain
        // (its own 401 code), and must be changed with it.
        "/api/platform/anything", HttpMethod.GET,
        "/api/nothing-here", HttpMethod.GET,
        "/actuator/health", HttpMethod.GET,
        "/", HttpMethod.GET,
        // A mapped path with a method nobody mapped: not public by accident.
        "/api/organizations", HttpMethod.GET,
        "/api/auth/login", HttpMethod.GET
    );

    for (Map.Entry<String, HttpMethod> entry : unmapped.entrySet()) {
      MvcResult result = mockMvc.perform(request(entry.getValue(), entry.getKey())).andReturn();

      assertThat(result.getResponse().getStatus()).as(entry.toString()).isEqualTo(401);
      TenantSecurityChainTest.assertTenantNotResolved(result);
    }
  }

  @Test
  void shouldHaveWalkedAtLeastTheKnownControllers() {
    Set<String> controllers = handlerMapping.getHandlerMethods().values().stream()
        .map(handler -> handler.getBeanType().getSimpleName())
        .collect(Collectors.toSet());

    assertThat(controllers).contains(
        "OrganizationController", "TenantOrganizationController",
        "TenantActivationController", "AuthController");
  }
}
