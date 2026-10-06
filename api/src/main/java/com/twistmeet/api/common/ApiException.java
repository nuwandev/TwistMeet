package com.twistmeet.api.common;

import org.springframework.http.HttpStatus;

/**
 * Base class for API errors that map to the stable error codes in {@code 08-data-api-contract.md}.
 */
public class ApiException extends RuntimeException {

  private final HttpStatus status;
  private final String code;

  public ApiException(HttpStatus status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  public HttpStatus getStatus() {
    return status;
  }

  public String getCode() {
    return code;
  }

  public static ApiException notFound(String message) {
    // 08: "Return 404 for inaccessible objects to reduce enumeration." Used for both
    // "does not exist" and "exists but caller has no access" — the two are indistinguishable
    // to the caller by design.
    return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
  }

  public static ApiException forbidden(String message) {
    return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
  }

  public static ApiException invalidTransition(String message) {
    return new ApiException(HttpStatus.CONFLICT, "INVALID_TRANSITION", message);
  }

  public static ApiException joinCodeInvalid(String message) {
    return new ApiException(HttpStatus.NOT_FOUND, "JOIN_CODE_INVALID", message);
  }

  public static ApiException rateLimited(String message) {
    return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", message);
  }

  public static ApiException conflict(String code, String message) {
    return new ApiException(HttpStatus.CONFLICT, code, message);
  }

  public static ApiException badRequest(String code, String message) {
    return new ApiException(HttpStatus.BAD_REQUEST, code, message);
  }

  public static ApiException scrambleNotAvailable(String message) {
    return new ApiException(HttpStatus.FORBIDDEN, "SCRAMBLE_NOT_AVAILABLE", message);
  }
}
