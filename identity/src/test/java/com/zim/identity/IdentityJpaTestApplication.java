package com.zim.identity;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@EnableAutoConfiguration
@EntityScan(basePackages = "com.zim.identity.infrastructure.persistence.entity")
@EnableJpaRepositories(
    basePackages = "com.zim.identity.infrastructure.persistence.repository"
)
public class IdentityJpaTestApplication {
}
