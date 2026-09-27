package com.zim.shared.tenant;

/**
 * The tenant of the current request could not be established.
 *
 * <p>Not a {@code DomainException}: this is a failure to identify the
 * caller, not a rejection by the domain. The message is deliberately
 * generic so that nothing about the request is revealed to the client.
 */
public final class TenantNotResolvedException extends RuntimeException {

  public static final String CODE = "TENANT_NOT_RESOLVED";

  static final String MESSAGE =
      "The tenant of the current request could not be resolved";

  public TenantNotResolvedException() {
    super(MESSAGE);
  }

  public String code() {
    return CODE;
  }
}
