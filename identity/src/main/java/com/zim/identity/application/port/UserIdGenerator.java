package com.zim.identity.application.port;

import com.zim.identity.domain.valueobject.UserId;

@FunctionalInterface
public interface UserIdGenerator {

  UserId generate();
}
