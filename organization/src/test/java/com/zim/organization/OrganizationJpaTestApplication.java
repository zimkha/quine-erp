package com.zim.organization;


import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;


@EnableAutoConfiguration
@EntityScan(
        basePackages =
                "com.zim.organization.infrastructure.persistence.entity"
)
@EnableJpaRepositories(
        basePackages =
                "com.zim.organization.infrastructure.persistence.repository"
)
public class OrganizationJpaTestApplication {
}
