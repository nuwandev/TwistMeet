package com.twistmeet.api.common;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ApiErrorResponse> handleApiException(
      ApiException ex, HttpServletRequest request) {
    String requestId = requestId(request);
    return ResponseEntity.status(ex.getStatus())
        .body(ApiErrorResponse.of(ex.getCode(), ex.getMessage(), requestId));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiErrorResponse> handleValidation(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    Map<String, String> fieldErrors = new LinkedHashMap<>();
    ex.getBindingResult()
        .getFieldErrors()
        .forEach(fe -> fieldErrors.put(fe.getField(), fe.getDefaultMessage()));
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(
            ApiErrorResponse.withFieldErrors(
                "VALIDATION_ERROR", "Request failed validation", fieldErrors, requestId(request)));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiErrorResponse> handleUnexpected(
      Exception ex, HttpServletRequest request) {
    log.error("Unexpected error handling {} {}", request.getMethod(), request.getRequestURI(), ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ApiErrorResponse.of("INTERNAL_ERROR", "Unexpected error", requestId(request)));
  }

  private String requestId(HttpServletRequest request) {
    Object existing = request.getAttribute("requestId");
    if (existing != null) {
      return existing.toString();
    }
    return UUID.randomUUID().toString();
  }
}
