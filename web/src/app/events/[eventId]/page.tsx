"use client";

import { useEffect, useState } from "react";
import { apiFetch, ApiError } from "@/lib/api";
import { EntrantView, EventView } from "@/lib/types";

export default function EventDetailPage({ params }: { params: { eventId: string } }) {
  const { eventId } = params;
  const [event, setEvent] = useState<EventView | null>(null);
  const [entrants, setEntrants] = useState<EntrantView[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function load() {
    try {
      const data = await apiFetch<EventView>(`/api/v1/events/${eventId}`);
      setEvent(data);
      const roster = await apiFetch<EntrantView[]>(`/api/v1/events/${eventId}/entrants`);
      setEntrants(roster);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to load event");
    }
  }

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [eventId]);

  async function runAction(path: string) {
    setBusy(true);
    setError(null);
    try {
      await apiFetch<EventView>(`/api/v1/events/${eventId}${path}`, { method: "POST" });
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Action failed");
    } finally {
      setBusy(false);
    }
  }

  if (error && !event) {
    return (
      <main style={{ maxWidth: 640, margin: "0 auto", padding: "var(--space-4) var(--space-2)" }}>
        <p className="error-text">{error}</p>
      </main>
    );
  }

  if (!event) {
    return (
      <main style={{ maxWidth: 640, margin: "0 auto", padding: "var(--space-4) var(--space-2)" }}>
        <p>Loading…</p>
      </main>
    );
  }

  const joinUrl =
    event.joinCode && typeof window !== "undefined"
      ? `${window.location.origin}/join/${event.joinCode}`
      : null;

  return (
    <main style={{ maxWidth: 720, margin: "0 auto", padding: "var(--space-4) var(--space-2)" }}>
      <h1>{event.name}</h1>
      <p>
        <span className="status-badge">{event.state}</span>{" "}
        <span className="status-badge">{event.timerMode === "PHYSICAL_JUDGE" ? "Judge recorded · physical timer" : "Self-timed · device/browser timing"}</span>
      </p>

      <section className="card" style={{ marginBottom: "var(--space-3)" }}>
        <h2>Registration</h2>
        {event.joinCode && (
          <p>
            Join code: <strong className="tabular">{event.joinCode}</strong>
            {joinUrl && (
              <>
                {" "}
                — <a href={joinUrl}>{joinUrl}</a>
              </>
            )}
          </p>
        )}
        <div style={{ display: "flex", gap: "var(--space-1)", flexWrap: "wrap" }}>
          <button className="button-primary" disabled={busy || event.state !== "DRAFT"} onClick={() => runAction("/registration/open")}>
            Open registration
          </button>
          <button className="button-primary" disabled={busy || event.state !== "REGISTRATION_OPEN"} onClick={() => runAction("/registration/lock")}>
            Lock registration
          </button>
          <button className="button-primary" disabled={busy || event.state !== "REGISTRATION_LOCKED"} onClick={() => runAction("/registration/reopen")}>
            Reopen registration
          </button>
          <button className="button-primary" disabled={busy} onClick={() => runAction("/join-codes/rotate")}>
            Rotate join code
          </button>
        </div>
      </section>

      <section className="card">
        <h2>Roster ({entrants?.length ?? 0})</h2>
        {entrants && entrants.length > 0 ? (
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <caption style={{ textAlign: "left", marginBottom: "var(--space-1)" }}>
              Entrants who have joined this event
            </caption>
            <thead>
              <tr>
                <th style={{ textAlign: "left" }}>Display name</th>
                <th style={{ textAlign: "left" }}>Status</th>
                <th style={{ textAlign: "left" }}>Joined</th>
              </tr>
            </thead>
            <tbody>
              {entrants.map((entrant) => (
                <tr key={entrant.id}>
                  <td>{entrant.displayName}</td>
                  <td>{entrant.status}</td>
                  <td>{new Date(entrant.joinedAt).toLocaleString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
        ) : (
          <p>No one has joined yet.</p>
        )}
      </section>

      {error && <p className="error-text" style={{ marginTop: "var(--space-2)" }}>{error}</p>}
    </main>
  );
}
