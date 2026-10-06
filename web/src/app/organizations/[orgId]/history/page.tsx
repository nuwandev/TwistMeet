"use client";

import { useParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { apiFetch, ApiError, ensureCsrfCookie } from "@/lib/api";
import { EventHistorySummary, EventState } from "@/lib/types";
import { EmptyState, ErrorState, LoadingState } from "@/components/common";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL?.trim() || "http://localhost:8080";

/** 07 S14 Event history and export. */
export default function EventHistoryPage() {
  const { orgId } = useParams<{ orgId: string }>();
  const [events, setEvents] = useState<EventHistorySummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [stateFilter, setStateFilter] = useState<EventState | "">("");
  const [nameFilter, setNameFilter] = useState("");

  const load = useCallback(async () => {
    try {
      const params = new URLSearchParams();
      if (stateFilter) params.set("state", stateFilter);
      if (nameFilter) params.set("q", nameFilter);
      const data = await apiFetch<EventHistorySummary[]>(
        `/api/v1/organizations/${orgId}/events/history?${params.toString()}`,
      );
      setEvents(data);
      setError(null);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to load event history");
    }
  }, [orgId, stateFilter, nameFilter]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- intentional fetch-on-mount/filter-change
    load();
  }, [load]);

  async function exportCsv(eventId: string) {
    await ensureCsrfCookie();
    window.open(
      `${API_BASE_URL}/api/v1/organizations/${orgId}/events/${eventId}/export.csv`,
      "_blank",
    );
  }

  if (error && !events) {
    return (
      <main style={{ maxWidth: 720, margin: "0 auto", padding: "var(--space-4) var(--space-2)" }}>
        <ErrorState message={error} onRetry={load} />
      </main>
    );
  }

  if (!events) {
    return (
      <main style={{ maxWidth: 720, margin: "0 auto", padding: "var(--space-4) var(--space-2)" }}>
        <LoadingState label="Loading event history…" />
      </main>
    );
  }

  return (
    <main style={{ maxWidth: 720, margin: "0 auto", padding: "var(--space-4) var(--space-2)" }}>
      <h1>Event history</h1>
      {error && <ErrorState message={error} onRetry={load} />}

      <div style={{ display: "flex", gap: "var(--space-1)", marginBottom: "var(--space-2)" }}>
        <label className="field-label" htmlFor="history-name">
          Name contains
        </label>
        <input
          id="history-name"
          className="field-input"
          value={nameFilter}
          onChange={(e) => setNameFilter(e.target.value)}
        />
        <label className="field-label" htmlFor="history-state">
          State
        </label>
        <select
          id="history-state"
          value={stateFilter}
          onChange={(e) => setStateFilter(e.target.value as EventState | "")}
        >
          <option value="">Any</option>
          <option value="DRAFT">Draft</option>
          <option value="REGISTRATION_OPEN">Registration open</option>
          <option value="REGISTRATION_LOCKED">Registration locked</option>
          <option value="READY">Ready</option>
          <option value="LIVE">Live</option>
          <option value="COMPLETED">Completed</option>
          <option value="ARCHIVED">Archived</option>
        </select>
      </div>

      {events.length === 0 ? (
        <EmptyState title="No events match these filters." />
      ) : (
        <ul style={{ display: "flex", flexDirection: "column", gap: "var(--space-1)" }}>
          {events.map((e) => (
            <li key={e.eventId} className="card">
              <p>
                <strong>{e.name}</strong> — {e.state}
              </p>
              <p>
                {new Date(e.startsAt).toLocaleDateString()} &middot; {e.entrantCount} entrant(s)
                &middot; {e.roundCount} round(s)
              </p>
              <button onClick={() => exportCsv(e.eventId)}>Download CSV</button>
            </li>
          ))}
        </ul>
      )}
    </main>
  );
}
