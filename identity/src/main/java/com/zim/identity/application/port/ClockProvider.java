package com.zim.identity.application.port;

import java.time.Instant;

@FunctionalInterface
public interface ClockProvider {

  Instant now();
}
