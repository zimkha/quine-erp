package com.zim.quine;

import com.zim.identity.infrastructure.configuration.IdentityConfiguration;
import com.zim.identity.presentation.rest.AuthController;
import com.zim.identity.presentation.rest.exception.IdentityApiExceptionHandler;
import com.zim.organization.infrastructure.configuration.OrganizationConfiguration;
import com.zim.organization.presentation.rest.OrganizationController;
import com.zim.organization.presentation.rest.TenantActivationController;
import com.zim.organization.presentation.rest.TenantOrganizationController;
import com.zim.organization.presentation.rest.exception.ApiExceptionHandler;
import com.zim.shared.web.WebSharedConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

// No UserDetailsService: authentication is the bearer token only, and the
// default one would log a generated password.
@EnableAutoConfiguration(exclude = UserDetailsServiceAutoConfiguration.class)
@ComponentScan("com.zim.quine")
@EntityScan(basePackages = {
    "com.zim.organization.infrastructure.persistence.entity",
    "com.zim.identity.infrastructure.persistence.entity"
})
@EnableJpaRepositories(basePackages = {
    "com.zim.organization.infrastructure.persistence.repository",
    "com.zim.identity.infrastructure.persistence.repository"
})
@Import({
    OrganizationConfiguration.class,
    IdentityConfiguration.class,
    OrganizationController.class,
    TenantOrganizationController.class,
    // Exists only with quine.organization.tenant-activation.enabled=true.
    TenantActivationController.class,
    ApiExceptionHandler.class,
    AuthController.class,
    IdentityApiExceptionHandler.class,
    WebSharedConfiguration.class
})
public class QuineApplication {

  public static void main(String[] args) {
    SpringApplication.run(QuineApplication.class, args);
  }
}
