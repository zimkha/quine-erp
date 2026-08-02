package com.zim.shared.domain;

public interface BusinessRule {

    boolean isBroken();

    String code();

    String message();
}
