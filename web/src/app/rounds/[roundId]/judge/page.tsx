"use client";

import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import { apiFetch, ApiError } from "@/lib/api";
import { AttemptView } from "@/lib/types";

/** 07 S09 Judge entry: enter time, select penalty, save; shows adjusted result preview. */
export default function JudgeEntryPage() {
  const { roundId } = useParams<{ roundId: string }>();
  const [attempts, setAttempts] = useState<AttemptView[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [drafts, setDrafts] = useState<Record<string, { status: string; rawTimeSeconds: string; penalty: string }>>({});

  async function load() {
    try {
      const data = await apiFetch<AttemptView[]>(`/api/v1/rounds/${roundId}/attempts`);
      setAttempts(data.sort((a, b) => a.attemptNumber - b.attemptNumber));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to load attempts");
    }
  }

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- intentional fetch-on-mount
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [roundId]);

  function draftFor(attempt: AttemptView) {
    return (
      drafts[attempt.id] ?? {
        status: attempt.resultStatus === "PENDING" ? "OK" : attempt.resultStatus,
        rawTimeSeconds: attempt.rawTimeMs ? (attempt.rawTimeMs / 1000).toFixed(3) : "",
        penalty: attempt.penalty,
      }
    );
  }

  async function save(attempt: AttemptView) {
    const draft = draftFor(attempt);
    setBusy(true);
    setError(null);
    try {
      const rawTimeMs = draft.status === "OK" ? Math.round(parseFloat(draft.rawTimeSeconds) * 1000) : undefined;
      await apiFetch(`/api/v1/attempts/${attempt.id}/judge-result`, {
        method: "PUT",
        body: {
          status: draft.status,
          rawTimeMs,
          penalty: draft.status === "OK" ? draft.penalty : "NONE",
          expectedVersion: attempt.version,
        },
      });
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Save failed — attempt may have changed, reloading");
      await load();
    } finally {
      setBusy(false);
    }
  }

  if (!attempts) {
    return (
      <main style={{ maxWidth: 640, margin: "0 auto", padding: "var(--space-4) var(--space-2)" }}>
        <p>{error ?? "Loading…"}</p>
      </main>
    );
  }

  return (
    <main style={{ maxWidth: 720, margin: "0 auto", padding: "var(--space-4) var(--space-2)" }}>
      <h1>Judge entry</h1>
      <p>Context: round {roundId}. Enter the physical-timer result for each attempt.</p>
      {error && <p className="error-text">{error}</p>}
      <table style={{ width: "100%", borderCollapse: "collapse" }}>
        <thead>
          <tr>
            <th style={{ textAlign: "left" }}>Attempt</th>
            <th style={{ textAlign: "left" }}>Status</th>
            <th style={{ textAlign: "left" }}>Time (s)</th>
            <th style={{ textAlign: "left" }}>Penalty</th>
            <th style={{ textAlign: "left" }}>Adjusted</th>
            <th style={{ textAlign: "left" }}>Save</th>
          </tr>
        </thead>
        <tbody>
          {attempts.map((attempt) => {
            const draft = draftFor(attempt);
            return (
              <tr key={attempt.id}>
                <td>
                  #{attempt.attemptNumber} <span className="status-badge">{attempt.state}</span>
                </td>
                <td>
                  <select
                    value={draft.status}
                    onChange={(e) => setDrafts({ ...drafts, [attempt.id]: { ...draft, status: e.target.value } })}
                  >
                    <option value="OK">OK</option>
                    <option value="DNF">DNF</option>
                    <option value="DNS">DNS</option>
                  </select>
                </td>
                <td>
                  <input
                    className="field-input"
                    type="number"
                    step="0.001"
                    min="0.001"
                    max="9999.999"
                    disabled={draft.status !== "OK"}
                    value={draft.rawTimeSeconds}
                    onChange={(e) =>
                      setDrafts({ ...drafts, [attempt.id]: { ...draft, rawTimeSeconds: e.target.value } })
                    }
                  />
                </td>
                <td>
                  <select
                    value={draft.penalty}
                    disabled={draft.status !== "OK"}
                    onChange={(e) => setDrafts({ ...drafts, [attempt.id]: { ...draft, penalty: e.target.value } })}
                  >
                    <option value="NONE">None</option>
                    <option value="PLUS_TWO">+2</option>
                  </select>
                </td>
                <td className="tabular">
                  {attempt.adjustedTimeMs != null ? (attempt.adjustedTimeMs / 1000).toFixed(2) : "—"}
                </td>
                <td>
                  <button className="button-primary" disabled={busy} onClick={() => save(attempt)}>
                    Save
                  </button>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </main>
  );
}
