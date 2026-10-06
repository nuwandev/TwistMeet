package com.twistmeet.api.support;

import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot 4's {@code RestTemplateBuilder} auto-detects Apache HttpClient5 on the classpath
 * (needed by {@link TestApiClient} to avoid {@code HttpRetryException} on non-2xx streamed POST
 * responses) and builds it with Apache's default cookie management enabled. That default cookie
 * store lives on the one underlying {@code HttpClient}, which every {@link TestApiClient} in a test
 * shares via the single injected {@code TestRestTemplate} bean — so one test user's session cookie
 * can silently leak into another {@link TestApiClient} instance in the same test class, since
 * {@link TestApiClient} is deliberately the only cookie jar these tests want. Disabling Apache's
 * own cookie management here restores that: whatever {@code Cookie} header {@link TestApiClient}
 * sets is the only cookie state involved, matching a real stateless HTTP client.
 */
@TestConfiguration
class StatelessHttpClientTestConfig {

  @Bean
  RestTemplateBuilder restTemplateBuilder() {
    return new RestTemplateBuilder()
        .requestFactoryBuilder(
            ClientHttpRequestFactoryBuilder.httpComponents()
                .withHttpClientCustomizer(HttpClientBuilder::disableCookieManagement));
  }
}
