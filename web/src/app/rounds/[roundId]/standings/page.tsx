"use client";

import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import { apiFetch, ApiError } from "@/lib/api";
import { StandingsView } from "@/lib/types";

/** 07 S12-adjacent authorized standings view (public/published standings are a later milestone). */
export default function StandingsPage() {
  const { roundId } = useParams<{ roundId: string }>();
  const [standings, setStandings] = useState<StandingsView | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    apiFetch<StandingsView>(`/api/v1/rounds/${roundId}/standings`)
      .then(setStandings)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load standings"));
  }, [roundId]);

  if (error) {
    return (
      <main style={{ maxWidth: 640, margin: "0 auto", padding: "var(--space-4) var(--space-2)" }}>
        <p className="error-text">{error}</p>
      </main>
    );
  }

  if (!standings) {
    return (
      <main style={{ maxWidth: 640, margin: "0 auto", padding: "var(--space-4) var(--space-2)" }}>
        <p>Loading…</p>
      </main>
    );
  }

  return (
    <main style={{ maxWidth: 640, margin: "0 auto", padding: "var(--space-4) var(--space-2)" }}>
      <h1>Standings</h1>
      <p className="status-badge">{standings.provisional ? "Provisional" : "Final"}</p>
      <table style={{ width: "100%", borderCollapse: "collapse" }}>
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
    </main>
  );
}
