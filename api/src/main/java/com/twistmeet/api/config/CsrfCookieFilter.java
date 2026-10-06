package com.twistmeet.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Forces the deferred CSRF token to resolve (and therefore save its cookie) on every request.
 *
 * <p>{@code CsrfFilter} only stores a {@code Supplier<CsrfToken>} on the request attribute; with a
 * plain JSON API there is no view layer that ever calls {@code CsrfToken#getToken()}, so the {@code
 * XSRF-TOKEN} cookie would otherwise never be issued. This is the pattern Spring Security itself
 * documents for single-page-app cookie/header CSRF (the official Angular/SPA CSRF guide).
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
    if (csrfToken != null) {
      csrfToken.getToken();
    }
    filterChain.doFilter(request, response);
  }
}
