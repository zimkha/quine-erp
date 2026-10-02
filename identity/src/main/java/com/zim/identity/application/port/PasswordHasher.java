package com.zim.identity.application.port;

import com.zim.identity.domain.valueobject.PasswordHash;
import com.zim.identity.domain.valueobject.PlainPassword;

@FunctionalInterface
public interface PasswordHasher {

  PasswordHash hash(PlainPassword password);
}
