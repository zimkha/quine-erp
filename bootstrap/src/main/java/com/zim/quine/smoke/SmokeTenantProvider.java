package com.zim.quine.smoke;

import com.zim.shared.domain.TenantId;
import com.zim.shared.tenant.CurrentTenantProvider;
import com.zim.shared.tenant.TenantNotResolvedException;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Local smoke runs only: until identity (T7) exists, the tenant comes from
 * the X-Smoke-Tenant header, which anyone can forge. Never active outside the
 * "smoke" profile, and refuses to start together with a non-local profile.
 */
@Component
@Profile("smoke")
class SmokeTenantProvider implements CurrentTenantProvider {

  static final String HEADER = "X-Smoke-Tenant";

  static final Set<String> NON_LOCAL_PROFILES =
      Set.of("prod", "production", "staging");

  SmokeTenantProvider(Environment environment) {
    for (String profile : environment.getActiveProfiles()) {
      if (NON_LOCAL_PROFILES.contains(profile.toLowerCase(Locale.ROOT))) {
        throw new IllegalStateException(
            "The smoke tenant provider trusts a forgeable header and must "
                + "never run with the '" + profile + "' profile"
        );
      }
    }
  }

  @Override
  public TenantId currentTenant() {
    RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
    if (!(attributes instanceof ServletRequestAttributes servlet)) {
      throw new TenantNotResolvedException();
    }
    String value = servlet.getRequest().getHeader(HEADER);
    if (value == null || value.isBlank()) {
      throw new TenantNotResolvedException();
    }
    try {
      return new TenantId(UUID.fromString(value.trim()));
    } catch (IllegalArgumentException e) {
      throw new TenantNotResolvedException();
    }
  }
}
