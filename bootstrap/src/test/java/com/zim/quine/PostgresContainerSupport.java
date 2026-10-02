package com.zim.quine;

import org.testcontainers.postgresql.PostgreSQLContainer;

/** One Postgres for all bootstrap ITs; Spring caches contexts across classes. */
public final class PostgresContainerSupport {

  public static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17-alpine")
          .withDatabaseName("quine")
          .withUsername("quine")
          .withPassword("quine");

  static {
    POSTGRES.start();
  }

  private PostgresContainerSupport() {
  }
}
