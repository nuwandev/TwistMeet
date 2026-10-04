"use client";

import { useParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { apiFetch, ApiError } from "@/lib/api";
import { StandingsView } from "@/lib/types";
import { EmptyState, ErrorState, LoadingState, OfflineState, StatusBadge, useOnlineStatus } from "@/components/common";

/** 07 S12-adjacent authorized standings view (public/published standings are a later milestone). */
export default function StandingsPage() {
  const { roundId } = useParams<{ roundId: string }>();
  const online = useOnlineStatus();
  const [standings, setStandings] = useState<StandingsView | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const data = await apiFetch<StandingsView>(`/api/v1/rounds/${roundId}/standings`);
      setStandings(data);
      setError(null);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to load standings");
    }
  }, [roundId]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- intentional fetch-on-mount
    load();
  }, [load]);

  if (error && !standings) {
    return (
      <main style={{ maxWidth: 640, margin: "0 auto", padding: "var(--space-4) var(--space-2)" }}>
        <ErrorState message={error} onRetry={load} />
      </main>
    );
  }

  if (!standings) {
    return (
      <main style={{ maxWidth: 640, margin: "0 auto", padding: "var(--space-4) var(--space-2)" }}>
        <LoadingState label="Loading standings…" />
      </main>
    );
  }

  return (
    <main style={{ maxWidth: 640, margin: "0 auto", padding: "var(--space-4) var(--space-2)" }}>
      <h1>Standings</h1>
      <StatusBadge tone={standings.provisional ? "warn" : "good"}>
        {standings.provisional ? "Provisional" : "Final"}
      </StatusBadge>
      {!online && <OfflineState onRetry={load} />}
      {error && <ErrorState message={error} onRetry={load} />}

      {standings.standings.length === 0 ? (
        <EmptyState title="No results yet. Standings appear once attempts have outcomes." />
      ) : (
        <table style={{ width: "100%", borderCollapse: "collapse" }}>
          <caption style={{ textAlign: "left" }}>
            Round standings, {standings.provisional ? "provisional" : "final"}
          </caption>
          <thead>
            <tr>
              <th style={{ textAlign: "left" }}>Rank</th>
              <th style={{ textAlign: "left" }}>Entrant</th>
              <th style={{ textAlign: "left" }}>Result</th>
              <th style={{ textAlign: "left" }}>Best single</th>
            </tr>
          </thead>
          <tbody>
            {standings.standings.map((row) => (
              <tr key={row.entrantId}>
                <td>{row.rank}</td>
                <td>{row.displayName}</td>
                <td className="tabular">
                  {row.outcome === "OK" && row.displayMs != null ? (row.displayMs / 1000).toFixed(2) : "DNF"}
                </td>
                <td className="tabular">
                  {row.bestValidSingleMs != null ? (row.bestValidSingleMs / 1000).toFixed(2) : "—"}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </main>
  );
}
