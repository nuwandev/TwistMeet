"use client";

import { useParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { apiFetch, ApiError } from "@/lib/api";
import { AttemptView, EntrantView, RoundView } from "@/lib/types";
import { ErrorState, LoadingState } from "@/components/common";

/**
 * 00 §2 item 12 "offline paper fallback" / 12 "Printed fallback score sheet is usable; staff
 * rehearsed it." A blank, printable roster + attempt grid for a round, generated from the same
 * data the digital Tournament Control reads — for staff to print and carry if the venue network,
 * device, or the app itself goes down mid-round. Cells show an already-recorded result (so staff
 * can reconcile), or stay blank for manual entry when nothing has been recorded yet. Entering
 * results from a filled-in sheet back into the system afterward uses the existing judge-entry
 * screen per attempt — this page has no write path of its own, by design: a page meant to work
 * when the system is unavailable has nothing to save to.
 */
export default function PrintSheetPage() {
  const { roundId } = useParams<{ roundId: string }>();
  const [round, setRound] = useState<RoundView | null>(null);
  const [attempts, setAttempts] = useState<AttemptView[] | null>(null);
  const [entrants, setEntrants] = useState<Map<string, string> | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const r = await apiFetch<RoundView>(`/api/v1/rounds/${roundId}`);
      setRound(r);
      const [attemptList, entrantList] = await Promise.all([
        apiFetch<AttemptView[]>(`/api/v1/rounds/${roundId}/attempts`),
        apiFetch<EntrantView[]>(`/api/v1/events/${r.eventId}/entrants`),
      ]);
      setAttempts(attemptList.sort((a, b) => a.attemptNumber - b.attemptNumber));
      setEntrants(new Map(entrantList.map((e) => [e.id, e.displayName])));
      setError(null);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to load this round's print sheet");
    }
  }, [roundId]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- intentional fetch-on-mount
    load();
  }, [load]);

  if (error && !round) {
    return (
      <main style={{ maxWidth: 900, margin: "0 auto", padding: "var(--space-4) var(--space-2)" }}>
        <ErrorState message={error} onRetry={load} />
      </main>
    );
  }

  if (!round || !attempts || !entrants) {
    return (
      <main style={{ maxWidth: 900, margin: "0 auto", padding: "var(--space-4) var(--space-2)" }}>
        <LoadingState label="Loading print sheet…" />
      </main>
    );
  }

  const byEntrant = new Map<string, AttemptView[]>();
  for (const attempt of attempts) {
    const list = byEntrant.get(attempt.entrantId) ?? [];
    list.push(attempt);
    byEntrant.set(attempt.entrantId, list);
  }
  const entrantIds = Array.from(byEntrant.keys()).sort((a, b) =>
    (entrants.get(a) ?? "").localeCompare(entrants.get(b) ?? ""),
  );
  const maxAttemptNumber = Math.max(1, ...attempts.map((a) => a.attemptNumber));

  return (
    <main className="print-sheet" style={{ maxWidth: 900, margin: "0 auto", padding: "var(--space-4) var(--space-2)" }}>
      <div className="no-print" style={{ marginBottom: "var(--space-2)" }}>
        <button onClick={() => window.print()}>Print this sheet</button>
        <p>
          Print and keep this with the event&apos;s offline procedure before the round starts. If the
          system goes down mid-round, record times here and re-enter them in judge entry once it
          is back up.
        </p>
      </div>
      <h1>{round.name} — offline score sheet</h1>
      <p>
        Format {round.format} &middot; {entrantIds.length} entrant(s) &middot; printed{" "}
        {new Date().toLocaleString()}
      </p>
      {entrantIds.length === 0 ? (
        <p>No attempts are prepared for this round yet.</p>
      ) : (
        <table style={{ width: "100%", borderCollapse: "collapse" }}>
          <caption style={{ textAlign: "left" }}>
            One row per entrant, one column per attempt. Leave blank cells for staff to fill in.
          </caption>
          <thead>
            <tr>
              <th style={{ textAlign: "left", border: "1px solid #000", padding: 4 }}>Entrant</th>
              {Array.from({ length: maxAttemptNumber }, (_, i) => i + 1).map((n) => (
                <th key={n} style={{ border: "1px solid #000", padding: 4 }}>
                  Attempt {n}
                </th>
              ))}
              <th style={{ border: "1px solid #000", padding: 4 }}>Notes</th>
            </tr>
          </thead>
          <tbody>
            {entrantIds.map((entrantId) => {
              const attemptsByNumber = new Map(
                (byEntrant.get(entrantId) ?? []).map((a) => [a.attemptNumber, a]),
              );
              return (
                <tr key={entrantId}>
                  <td style={{ border: "1px solid #000", padding: 4 }}>{entrants.get(entrantId)}</td>
                  {Array.from({ length: maxAttemptNumber }, (_, i) => i + 1).map((n) => {
                    const a = attemptsByNumber.get(n);
                    const recorded =
                      a && a.resultStatus !== "PENDING"
                        ? a.resultStatus === "OK"
                          ? `${((a.adjustedTimeMs ?? 0) / 1000).toFixed(2)}s`
                          : a.resultStatus
                        : "";
                    return (
                      <td
                        key={n}
                        style={{ border: "1px solid #000", padding: 4, minWidth: 64, height: 28 }}
                      >
                        {recorded}
                      </td>
                    );
                  })}
                  <td style={{ border: "1px solid #000", padding: 4, minWidth: 120 }} />
                </tr>
              );
            })}
          </tbody>
        </table>
      )}
      <style jsx global>{`
        @media print {
          .no-print {
            display: none;
          }
        }
      `}</style>
    </main>
  );
}
