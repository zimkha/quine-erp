package com.zim.shared.domain;

import java.util.Objects;

public class DomainException extends RuntimeException {
    private final String code;

    protected DomainException(String code, String message) {
        super(Objects.requireNonNull(message, "Message cannot be null"));

        this.code = Objects.requireNonNull(
                code,
                "Domain exception code cannot be null"
        );
    }

    protected DomainException(
            String code,
            String message,
            Throwable cause
    ) {
        super(
                Objects.requireNonNull(message, "Message cannot be null"),
                cause
        );

        this.code = Objects.requireNonNull(
                code,
                "Domain exception code cannot be null"
        );
    }

    public final String code() {
        return code;
    }
}
