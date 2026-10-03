package com.twistmeet.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.twistmeet.api.common.ApiErrorResponse;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.SessionManagementConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Staff authentication uses a server-side session behind a secure, HttpOnly, SameSite cookie (08
 * §API conventions default). CSRF protection is applied via the double-submit cookie pattern for
 * every mutating request except the credential-bootstrap endpoints (register/login/guest join),
 * which do not ride an existing session cookie and are instead protected by rate limiting. Guest
 * access (join codes, guest credential) is handled inside controllers, not Spring Security
 * principals, so those paths are left "permitAll" at this layer and authorized explicitly.
 */
@Configuration
public class SecurityConfig {

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource(Environment env) {
    // The web app is a separate origin (Next.js dev server / later a separate deployed host),
    // so cookie-based auth requires explicit CORS with credentials allowed. Only the configured
    // web origin is allowed — never "*", which is incompatible with allowCredentials anyway.
    String webBaseUrl = env.getProperty("twistmeet.web.base-url", "http://localhost:3000");
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(List.of(webBaseUrl));
    configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN"));
    configuration.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", configuration);
    return source;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http, ObjectMapper objectMapper, CorsConfigurationSource corsConfigurationSource)
      throws Exception {
    http.cors(cors -> cors.configurationSource(corsConfigurationSource))
        .csrf(
            csrf ->
                csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                    .ignoringRequestMatchers(
                        "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/join/**"))
        .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
        .sessionManagement(
            (SessionManagementConfigurer<HttpSecurity> sm) ->
                sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/api/v1/auth/register",
                        "/api/v1/auth/login",
                        "/api/v1/auth/email/verify",
                        "/api/v1/join/**",
                        "/api/v1/guest/**",
                        "/api/v1/public/**",
                        "/actuator/health")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            eh ->
                eh.authenticationEntryPoint(
                        (request, response, authException) -> {
                          response.setStatus(401);
                          response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                          objectMapper.writeValue(
                              response.getWriter(),
                              ApiErrorResponse.of(
                                  "UNAUTHENTICATED", "Sign-in required", requestId(request)));
                        })
                    .accessDeniedHandler(
                        (request, response, accessDeniedException) -> {
                          response.setStatus(403);
                          response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                          objectMapper.writeValue(
                              response.getWriter(),
                              ApiErrorResponse.of(
                                  "FORBIDDEN", "Not permitted", requestId(request)));
                        }))
        .logout(
            logout ->
                logout
                    .logoutUrl("/api/v1/auth/logout")
                    .logoutSuccessHandler(
                        (request, response, authentication) -> response.setStatus(204)));

    return http.build();
  }

  private String requestId(jakarta.servlet.http.HttpServletRequest request) {
    Object v = request.getAttribute("requestId");
    return v == null ? "unknown" : v.toString();
  }
}
