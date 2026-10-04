package com.twistmeet.api.support;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * A tiny cookie-jar-aware HTTP client for integration tests. Real browsers and the Next.js web
 * client read the {@code XSRF-TOKEN} cookie and echo it back as {@code X-XSRF-TOKEN} (Spring
 * Security's double-submit CSRF pattern); this client does the same thing explicitly so tests
 * exercise the real CSRF, session-cookie, and guest-credential-cookie behavior end to end rather
 * than disabling any of it.
 */
public class TestApiClient {

  private final TestRestTemplate restTemplate;
  private final ObjectMapper objectMapper = JsonMapper.builder().build();
  private final Map<String, String> cookieJar = new LinkedHashMap<>();

  public TestApiClient(TestRestTemplate restTemplate) {
    this.restTemplate = restTemplate;
  }

  public ResponseEntity<String> get(String path) {
    return exchange(HttpMethod.GET, path, null);
  }

  public ResponseEntity<String> post(String path, Object body) {
    return exchange(HttpMethod.POST, path, body);
  }

  public ResponseEntity<String> patch(String path, Object body) {
    return exchange(HttpMethod.PATCH, path, body);
  }

  public ResponseEntity<String> put(String path, Object body) {
    return exchange(HttpMethod.PUT, path, body);
  }

  public ResponseEntity<String> delete(String path) {
    return exchange(HttpMethod.DELETE, path, null);
  }

  public ResponseEntity<String> exchange(HttpMethod method, String path, Object body) {
    // A GET first ensures a CSRF cookie exists before any mutating call needs to echo it.
    if (method != HttpMethod.GET && !cookieJar.containsKey("XSRF-TOKEN")) {
      get("/api/v1/me");
    }
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    if (!cookieJar.isEmpty()) {
      headers.set("Cookie", cookieHeader());
    }
    if (cookieJar.containsKey("XSRF-TOKEN")) {
      headers.set("X-XSRF-TOKEN", cookieJar.get("XSRF-TOKEN"));
    }
    String json;
    try {
      json = body == null ? null : objectMapper.writeValueAsString(body);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    ResponseEntity<String> response =
        restTemplate.exchange(path, method, new HttpEntity<>(json, headers), String.class);
    captureCookies(response);
    return response;
  }

  public void clearCookies() {
    cookieJar.clear();
  }

  public boolean is2xx(ResponseEntity<?> response) {
    return response.getStatusCode().is2xxSuccessful();
  }

  public HttpStatus statusOf(ResponseEntity<?> response) {
    return HttpStatus.valueOf(response.getStatusCode().value());
  }

  private void captureCookies(ResponseEntity<String> response) {
    List<String> setCookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
    if (setCookies == null) {
      return;
    }
    for (String setCookie : setCookies) {
      String[] parts = setCookie.split(";", 2)[0].split("=", 2);
      if (parts.length == 2) {
        cookieJar.put(parts[0].trim(), parts[1].trim());
      }
    }
  }

  private String cookieHeader() {
    StringBuilder sb = new StringBuilder();
    cookieJar.forEach(
        (k, v) -> {
          if (sb.length() > 0) {
            sb.append("; ");
          }
          sb.append(k).append('=').append(v);
        });
    return sb.toString();
  }
}
