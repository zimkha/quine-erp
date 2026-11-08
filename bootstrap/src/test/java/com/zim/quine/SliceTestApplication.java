package com.zim.quine;

import org.springframework.boot.SpringBootConfiguration;

/**
 * The anchor of the bootstrap {@code @WebMvcTest} slices (they need a
 * {@code @SpringBootConfiguration}). No {@code @EnableAutoConfiguration}: a
 * slice brings its own auto-configurations, and with Spring Security on the
 * classpath a full auto-configuration here would load the security
 * auto-configurations, and OrganizationConfiguration (JPA) too, into every
 * slice. A slice imports what it tests (see TenantSecurityChainTest).
 */
@SpringBootConfiguration
public class SliceTestApplication {
}
