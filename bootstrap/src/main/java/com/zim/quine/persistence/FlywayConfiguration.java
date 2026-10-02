package com.zim.quine.persistence;

import org.flywaydb.core.Flyway;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * One Flyway per module, each with its own schema and its own migration
 * history, so module version numbers never clash (every module starts at V1).
 * Spring Boot makes the JPA setup wait for these beans, so the schemas exist
 * before Hibernate validates the entities.
 *
 * <p>A new module adds its own pair of beans here.
 */
@Configuration
class FlywayConfiguration {

  @Bean
  Flyway organizationFlyway(DataSource dataSource) {
    return moduleFlyway(dataSource, "organization");
  }

  @Bean
  FlywayMigrationInitializer organizationFlywayInitializer(
      Flyway organizationFlyway
  ) {
    return new FlywayMigrationInitializer(organizationFlyway);
  }

  @Bean
  Flyway identityFlyway(DataSource dataSource) {
    return moduleFlyway(dataSource, "identity");
  }

  @Bean
  FlywayMigrationInitializer identityFlywayInitializer(
      Flyway identityFlyway
  ) {
    return new FlywayMigrationInitializer(identityFlyway);
  }

  private static Flyway moduleFlyway(DataSource dataSource, String module) {
    return Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration/" + module)
        .schemas(module)
        .defaultSchema(module)
        .load();
  }
}
