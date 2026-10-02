package com.zim.quine;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

// Web slices must not load OrganizationConfiguration (JPA), which
// QuineApplication imports.
@SpringBootConfiguration
@EnableAutoConfiguration
public class SliceTestApplication {
}
