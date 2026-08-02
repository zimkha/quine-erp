package com.zim.organization.application.port;

import java.util.UUID;

@FunctionalInterface
public interface EventIdGenerator {

    UUID generate();
}
