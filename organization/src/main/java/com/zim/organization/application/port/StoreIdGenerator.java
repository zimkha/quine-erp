package com.zim.organization.application.port;

import com.zim.organization.domain.valueobject.StoreId;

@FunctionalInterface
public interface StoreIdGenerator {

    StoreId generate();
}
