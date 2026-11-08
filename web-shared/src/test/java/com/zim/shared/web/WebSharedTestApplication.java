package com.zim.shared.web;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

/**
 * Anchor for the web slices of this module. It is not in the test-jar (see
 * the jar plugin configuration), so it cannot leak into a consumer's scan.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
public class WebSharedTestApplication {
}
