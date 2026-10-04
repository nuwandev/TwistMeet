"use client";

import { useParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { apiFetch, ApiError } from "@/lib/api";
import { PublicEventView, PublicStandingsView } from "@/lib/types";
import { EmptyState, ErrorState, LoadingState, OfflineState, StatusBadge, useOnlineStatus } from "@/components/common";

/**
 * 07 S13 Public display: fullscreen scoreboard, no auth. Only published events/rounds are ever
 * reachable here — an unpublished or hidden event 404s like any other inaccessible object.
 */
export default function PublicEventPage() {
  const { slug } = useParams<{ slug: string }>();
  const online = useOnlineStatus();
  const [event, setEvent] = useState<PublicEventView | null>(null);
  const [selectedRoundId, setSelectedRoundId] = useState<string | null>(null);
  const [standings, setStandings] = useState<PublicStandingsView | null>(null);
  const [error, setError] = useState<string | null>(null);

  const loadEvent = useCallback(async () => {
    try {
      const data = await apiFetch<PublicEventView>(`/api/v1/public/events/${slug}`);
      setEvent(data);
      setError(null);
      if (data.rounds.length > 0) {
        setSelectedRoundId((current) => current ?? data.rounds[data.rounds.length - 1].roundId);
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "This event is not available.");
    }
  }, [slug]);

  const loadStandings = useCallback(async () => {
    if (!selectedRoundId) return;
    try {
      const data = await apiFetch<PublicStandingsView>(
        `/api/v1/public/events/${slug}/standings?roundId=${selectedRoundId}`,
      );
      setStandings(data);
    } catch {
      setStandings(null);
    }
  }, [slug, selectedRoundId]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- intentional fetch-on-mount
    loadEvent();
  }, [loadEvent]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- re-fetch when round selection changes
    loadStandings();
  }, [loadStandings]);

  if (error && !event) {
    return (
      <main style={{ maxWidth: 720, margin: "0 auto", padding: "var(--space-4) var(--space-2)" }}>
        <ErrorState message={error} onRetry={loadEvent} />
      </main>
    );
  }

  if (!event) {
    return (
      <main style={{ maxWidth: 720, margin: "0 auto", padding: "var(--space-4) var(--space-2)" }}>
        <LoadingState label="Loading event…" />
      </main>
    );
  }

  return (
    <main style={{ maxWidth: 720, margin: "0 auto", padding: "var(--space-4) var(--space-2)" }}>
      {!online && <OfflineState onRetry={loadStandings} />}
      <h1>{event.name}</h1>
      {event.venueLabel && <p>{event.venueLabel}</p>}

      {event.rounds.length === 0 ? (
        <EmptyState title="No rounds are available to show yet." />
      ) : (
        <>
          <select
            aria-label="Round"
            value={selectedRoundId ?? ""}
            onChange={(e) => setSelectedRoundId(e.target.value)}
          >
            {event.rounds.map((r) => (
              <option key={r.roundId} value={r.roundId}>
                {r.name}
              </option>
            ))}
          </select>

          {!standings ? (
            <LoadingState label="Loading standings…" />
          ) : (
            <>
              <StatusBadge tone={standings.provisional ? "warn" : "good"}>
                {standings.provisional ? "Provisional" : "Final"}
              </StatusBadge>
              {standings.standings.length === 0 ? (
                <EmptyState title="No results yet." />
              ) : (
                <table style={{ width: "100%", borderCollapse: "collapse" }}>
                  <thead>
                    <tr>
                      <th style={{ textAlign: "left" }}>Rank</th>
                      <th style={{ textAlign: "left" }}>Competitor</th>
                      <th style={{ textAlign: "left" }}>Result</th>
                      <th style={{ textAlign: "left" }}>Best</th>
                      <th style={{ textAlign: "left" }}>Progress</th>
                    </tr>
                  </thead>
                  <tbody>
                    {standings.standings.map((row) => (
                      <tr key={row.rank + row.displayName}>
                        <td>{row.rank}</td>
                        <td>{row.displayName}</td>
                        <td className="tabular">
                          {row.outcome === "OK" && row.displayMs != null
                            ? (row.displayMs / 1000).toFixed(2)
                            : "DNF"}
                        </td>
                        <td className="tabular">
                          {row.bestValidSingleMs != null ? (row.bestValidSingleMs / 1000).toFixed(2) : "—"}
                        </td>
                        <td className="tabular">
                          {row.completedAttempts}/{row.totalAttempts}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </>
          )}
        </>
      )}
    </main>
  );
}
