package com.zim.organization.application.port;

import java.time.Instant;

@FunctionalInterface
public interface ClockProvider {

    Instant now();
}
