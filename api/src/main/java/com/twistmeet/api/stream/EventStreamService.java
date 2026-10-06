package com.twistmeet.api.stream;

import com.twistmeet.api.event.Event;
import com.twistmeet.api.event.EventRepository;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 08 "Real-time updates": "Use SSE or WebSocket with authenticated, event-scoped subscriptions.
 * Initial snapshot comes from REST; updates contain {eventId, eventVersion, eventType, resourceId,
 * changedFields, occurredAt} and no scramble text... Public channel emits only published fields."
 * This is a plain in-process pub/sub (one API instance per environment, 00 §10 "Deploy API and web
 * app as one product" — no multi-instance fan-out requirement in V1); a future horizontally-scaled
 * deployment would need a shared broker instead of this in-memory registry (recorded in
 * DECISIONS.md).
 *
 * <p>Callers (round/attempt/advancement/correction/help-request/event services) call {@link
 * #publish} after committing a state change. Never pass scramble notation in {@code changedFields}
 * or any payload value — the contract explicitly forbids scramble text on this channel, and the
 * public copy already only goes out when the event is published.
 */
@Service
public class EventStreamService {

  // Server never closes the connection; the browser's EventSource reconnects with backoff.
  private static final long EMITTER_TIMEOUT_MS = 0L;

  private final EventRepository eventRepository;
  private final ExecutorService sendExecutor =
      Executors.newSingleThreadExecutor(
          r -> {
            Thread t = new Thread(r, "event-stream-sender");
            t.setDaemon(true);
            return t;
          });

  private final Map<UUID, List<SseEmitter>> staffEmittersByEvent = new ConcurrentHashMap<>();
  private final Map<String, List<SseEmitter>> publicEmittersBySlug = new ConcurrentHashMap<>();
  private final Map<UUID, AtomicLong> sequenceByEvent = new ConcurrentHashMap<>();

  public EventStreamService(EventRepository eventRepository) {
    this.eventRepository = eventRepository;
  }

  public SseEmitter subscribeStaff(UUID eventId) {
    SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
    List<SseEmitter> list =
        staffEmittersByEvent.computeIfAbsent(eventId, k -> new CopyOnWriteArrayList<>());
    register(list, emitter);
    return emitter;
  }

  public SseEmitter subscribePublic(String publicSlug) {
    SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
    List<SseEmitter> list =
        publicEmittersBySlug.computeIfAbsent(publicSlug, k -> new CopyOnWriteArrayList<>());
    register(list, emitter);
    return emitter;
  }

  private void register(List<SseEmitter> list, SseEmitter emitter) {
    list.add(emitter);
    emitter.onCompletion(() -> list.remove(emitter));
    emitter.onTimeout(() -> list.remove(emitter));
    emitter.onError(ex -> list.remove(emitter));
    try {
      emitter.send(SseEmitter.event().comment("connected"));
    } catch (IOException e) {
      list.remove(emitter);
      emitter.complete();
    }
  }

  /**
   * Broadcasts a change to the staff channel for {@code eventId}, and — only if that event is
   * currently published — a field-stripped copy to its public channel. {@code resourceId} and
   * {@code changedFields} describe which object changed, never its content; clients always refetch
   * the affected resource over REST rather than trusting values carried on this channel.
   */
  public void publish(UUID eventId, String eventType, UUID resourceId, Set<String> changedFields) {
    long version =
        sequenceByEvent.computeIfAbsent(eventId, k -> new AtomicLong()).incrementAndGet();
    String occurredAt = Instant.now().toString();

    Map<String, Object> staffPayload =
        Map.of(
            "eventId", eventId.toString(),
            "eventVersion", version,
            "eventType", eventType,
            "resourceId", resourceId == null ? "" : resourceId.toString(),
            "changedFields", changedFields,
            "occurredAt", occurredAt);
    broadcast(staffEmittersByEvent.get(eventId), version, staffPayload);

    Event event = eventRepository.findById(eventId).orElse(null);
    if (event != null && event.getPublicSlug() != null && event.getPublishedAt() != null) {
      Map<String, Object> publicPayload =
          Map.of(
              "eventVersion", version,
              "eventType", eventType,
              "occurredAt", occurredAt);
      broadcast(publicEmittersBySlug.get(event.getPublicSlug()), version, publicPayload);
    }
  }

  private void broadcast(List<SseEmitter> emitters, long version, Map<String, Object> payload) {
    if (emitters == null || emitters.isEmpty()) {
      return;
    }
    sendExecutor.execute(
        () -> {
          for (SseEmitter emitter : emitters) {
            try {
              // No SSE `event:` name is set: the envelope's own `eventType` field carries that,
              // and every client subscribes with a single generic onmessage handler that simply
              // refetches over REST, so there is nothing client-side to route by event name.
              emitter.send(SseEmitter.event().id(Long.toString(version)).data(payload));
            } catch (Exception e) {
              emitters.remove(emitter);
              emitter.complete();
            }
          }
        });
  }
}
