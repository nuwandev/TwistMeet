package com.twistmeet.api.common;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Minimal in-memory fixed-window rate limiter.
 *
 * <p>This is intentionally simple for M1: it is per-instance (not shared across replicas) and has
 * no persistence. It satisfies the M1 requirement that login and join-code attempts are throttled
 * (00 §7, 08 §API conventions, 12 "throttling prevents enumeration") but is not the
 * production-scale implementation — a shared store (the stack decision defers Redis until needed;
 * see DECISIONS.md) should replace this before multi-instance deployment.
 */
@Component
public class SimpleRateLimiter {

  private record Window(Instant windowStart, int count) {}

  private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

  public boolean tryAcquire(String key, int maxAttempts, Duration window) {
    Instant now = Instant.now();
    Window updated =
        windows.compute(
            key,
            (k, existing) -> {
              if (existing == null || existing.windowStart().plus(window).isBefore(now)) {
                return new Window(now, 1);
              }
              return new Window(existing.windowStart(), existing.count() + 1);
            });
    return updated.count() <= maxAttempts;
  }
}
