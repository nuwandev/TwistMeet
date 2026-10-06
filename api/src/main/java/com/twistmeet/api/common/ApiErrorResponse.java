package com.twistmeet.api.common;

import java.util.Map;

/**
 * Error body shape fixed by {@code 08-data-api-contract.md}: {code, message, fieldErrors?,
 * requestId}.
 */
public record ApiErrorResponse(
    String code, String message, Map<String, String> fieldErrors, String requestId) {

  public static ApiErrorResponse of(String code, String message, String requestId) {
    return new ApiErrorResponse(code, message, null, requestId);
  }

  public static ApiErrorResponse withFieldErrors(
      String code, String message, Map<String, String> fieldErrors, String requestId) {
    return new ApiErrorResponse(code, message, fieldErrors, requestId);
  }
}
