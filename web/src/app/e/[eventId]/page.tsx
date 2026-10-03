"use client";

import { useEffect, useState } from "react";
import { apiFetch, ApiError } from "@/lib/api";
import { EntrantView } from "@/lib/types";

export default function WaitingRoomPage({ params }: { params: { eventId: string } }) {
  const { eventId } = params;
  const [entrant, setEntrant] = useState<EntrantView | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    apiFetch<EntrantView>(`/api/v1/guest/events/${eventId}/me`)
      .then(setEntrant)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Could not load your status"));
  }, [eventId]);

  if (error) {
    return (
      <main style={{ maxWidth: 420, margin: "0 auto", padding: "var(--space-4) var(--space-2)" }}>
        <p className="error-text">{error}</p>
        <p>Your join session may have expired. Ask the organizer for the event code.</p>
      </main>
    );
  }

  if (!entrant) {
    return (
      <main style={{ maxWidth: 420, margin: "0 auto", padding: "var(--space-4) var(--space-2)" }}>
        <p>Loading…</p>
      </main>
    );
  }

  return (
    <main style={{ maxWidth: 420, margin: "0 auto", padding: "var(--space-4) var(--space-2)" }}>
      <h1>You&apos;re in, {entrant.displayName}</h1>
      <p className="status-badge">{entrant.status}</p>
      <p>
        Wait here for the organizer to open the next round. This build does not yet show
        groups/stations or run attempts — that is a later milestone.
      </p>
    </main>
  );
}
