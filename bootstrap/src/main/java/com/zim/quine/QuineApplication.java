package com.zim.quine;

import com.zim.organization.infrastructure.configuration.OrganizationConfiguration;
import com.zim.organization.presentation.rest.OrganizationController;
import com.zim.organization.presentation.rest.TenantOrganizationController;
import com.zim.organization.presentation.rest.exception.ApiExceptionHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@EnableAutoConfiguration
@ComponentScan("com.zim.quine")
@EntityScan(basePackages = "com.zim.organization.infrastructure.persistence.entity")
@EnableJpaRepositories(
    basePackages = "com.zim.organization.infrastructure.persistence.repository"
)
@Import({
    OrganizationConfiguration.class,
    OrganizationController.class,
    TenantOrganizationController.class,
    ApiExceptionHandler.class
})
public class QuineApplication {

  public static void main(String[] args) {
    SpringApplication.run(QuineApplication.class, args);
  }
}
