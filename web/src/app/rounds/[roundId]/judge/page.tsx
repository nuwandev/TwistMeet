"use client";

import { useParams } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { apiFetch, ApiError } from "@/lib/api";
import { AttemptView } from "@/lib/types";
import {
  EmptyState,
  ErrorState,
  LoadingState,
  OfflineState,
  RoleBanner,
  SavedState,
  StatusBadge,
  useOnlineStatus,
} from "@/components/common";

type Draft = { status: string; rawTimeSeconds: string; penalty: string };
type SaveState = "idle" | "saving" | "saved" | "error";

/**
 * 07 S09 Judge entry: enter time, select penalty, save; adjusted result preview; saved receipt
 * with a 10-second undo window (undo re-submits the previous value as a new revision — R40 forbids
 * erasing history, so "undo" here is "revise back," never a silent rewrite); stale/duplicate
 * submissions prompt a reload rather than quietly overwriting. Keyboard: Enter saves only when the
 * draft is valid, Escape discards the unsaved draft for that row back to the attempt's last known
 * value.
 */
export default function JudgeEntryPage() {
  const { roundId } = useParams<{ roundId: string }>();
  const online = useOnlineStatus();
  const [attempts, setAttempts] = useState<AttemptView[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [rowError, setRowError] = useState<Record<string, string>>({});
  const [busyRow, setBusyRow] = useState<string | null>(null);
  const [drafts, setDrafts] = useState<Record<string, Draft>>({});
  const [saveState, setSaveState] = useState<Record<string, SaveState>>({});
  const [undoable, setUndoable] = useState<Record<string, { previous: Draft; expiresAt: number }>>({});
  const timersRef = useRef<Record<string, ReturnType<typeof setTimeout>>>({});

  async function load() {
    try {
      const data = await apiFetch<AttemptView[]>(`/api/v1/rounds/${roundId}/attempts`);
      setAttempts(data.sort((a, b) => a.attemptNumber - b.attemptNumber));
      setError(null);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to load attempts");
    }
  }

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- intentional fetch-on-mount
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [roundId]);

  useEffect(() => {
    const timers = timersRef.current;
    return () => {
      Object.values(timers).forEach(clearTimeout);
    };
  }, []);

  function freshDraft(attempt: AttemptView): Draft {
    return {
      status: attempt.resultStatus === "PENDING" ? "OK" : attempt.resultStatus,
      rawTimeSeconds: attempt.rawTimeMs ? (attempt.rawTimeMs / 1000).toFixed(3) : "",
      penalty: attempt.penalty,
    };
  }

  function draftFor(attempt: AttemptView): Draft {
    return drafts[attempt.id] ?? freshDraft(attempt);
  }

  function isValid(draft: Draft): boolean {
    if (draft.status !== "OK") return true;
    const seconds = parseFloat(draft.rawTimeSeconds);
    return Number.isFinite(seconds) && seconds >= 0.001 && seconds <= 9999.999;
  }

  function discardDraft(attempt: AttemptView) {
    const next = { ...drafts };
    delete next[attempt.id];
    setDrafts(next);
  }

  async function save(attempt: AttemptView, overrideDraft?: Draft) {
    const draft = overrideDraft ?? draftFor(attempt);
    if (!isValid(draft)) {
      setRowError({ ...rowError, [attempt.id]: "Enter a time between 0.001 and 9999.999 seconds." });
      return;
    }
    setBusyRow(attempt.id);
    setSaveState({ ...saveState, [attempt.id]: "saving" });
    setRowError({ ...rowError, [attempt.id]: "" });
    try {
      const rawTimeMs = draft.status === "OK" ? Math.round(parseFloat(draft.rawTimeSeconds) * 1000) : undefined;
      const previousDraft = freshDraft(attempt);
      await apiFetch(`/api/v1/attempts/${attempt.id}/judge-result`, {
        method: "PUT",
        body: {
          status: draft.status,
          rawTimeMs,
          penalty: draft.status === "OK" ? draft.penalty : "NONE",
          expectedVersion: attempt.version,
        },
      });
      setSaveState((s) => ({ ...s, [attempt.id]: "saved" }));
      await load();
      // 07 S09: "immediate saved receipt + undo window 10 seconds (undo creates revision; cannot
      // erase)." Only offer undo when there was a previous recorded value to revert to.
      if (attempt.resultStatus !== "PENDING") {
        if (timersRef.current[attempt.id]) clearTimeout(timersRef.current[attempt.id]);
        setUndoable((u) => ({ ...u, [attempt.id]: { previous: previousDraft, expiresAt: Date.now() + 10000 } }));
        timersRef.current[attempt.id] = setTimeout(() => {
          setUndoable((u) => {
            const next = { ...u };
            delete next[attempt.id];
            return next;
          });
        }, 10000);
      }
    } catch (err) {
      setSaveState((s) => ({ ...s, [attempt.id]: "error" }));
      if (err instanceof ApiError && err.code === "STALE_VERSION") {
        setRowError({
          ...rowError,
          [attempt.id]: "This attempt changed since you loaded it. Refreshing with the latest value.",
        });
      } else {
        setRowError({ ...rowError, [attempt.id]: err instanceof ApiError ? err.message : "Save failed" });
      }
      await load();
    } finally {
      setBusyRow(null);
    }
  }

  async function undo(attempt: AttemptView) {
    const entry = undoable[attempt.id];
    if (!entry) return;
    const next = { ...undoable };
    delete next[attempt.id];
    setUndoable(next);
    await save(attempt, entry.previous);
  }

  if (error && !attempts) {
    return (
      <main style={{ maxWidth: 720, margin: "0 auto", padding: "var(--space-4) var(--space-2)" }}>
        <ErrorState message={error} onRetry={load} />
      </main>
    );
  }

  if (!attempts) {
    return (
      <main style={{ maxWidth: 720, margin: "0 auto", padding: "var(--space-4) var(--space-2)" }}>
        <LoadingState label="Loading attempts…" />
      </main>
    );
  }

  return (
    <main style={{ maxWidth: 760, margin: "0 auto", padding: "var(--space-4) var(--space-2)" }}>
      {attempts[0] && <RoleBanner eventId={attempts[0].eventId} />}
      <h1>Judge entry</h1>
      <p>Enter the physical-timer result for each attempt in this round.</p>
      {!online && <OfflineState onRetry={load} />}
      {error && <ErrorState message={error} onRetry={load} />}

      {attempts.length === 0 ? (
        <EmptyState title="No attempts are assigned to this round yet." />
      ) : (
        <table style={{ width: "100%", borderCollapse: "collapse" }}>
          <caption style={{ textAlign: "left" }}>Attempts, in attempt-number order</caption>
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
              const pending = undoable[attempt.id];
              return (
                <tr key={attempt.id}>
                  <td>
                    #{attempt.attemptNumber} <StatusBadge tone="neutral">{attempt.state}</StatusBadge>
                  </td>
                  <td>
                    <select
                      aria-label={`Status for attempt ${attempt.attemptNumber}`}
                      value={draft.status}
                      disabled={busyRow === attempt.id}
                      onChange={(e) => setDrafts({ ...drafts, [attempt.id]: { ...draft, status: e.target.value } })}
                      onKeyDown={(e) => {
                        if (e.key === "Escape") discardDraft(attempt);
                      }}
                    >
                      <option value="OK">OK</option>
                      <option value="DNF">DNF</option>
                      <option value="DNS">DNS</option>
                    </select>
                  </td>
                  <td>
                    <input
                      aria-label={`Time in seconds for attempt ${attempt.attemptNumber}`}
                      className="field-input"
                      type="number"
                      step="0.001"
                      min="0.001"
                      max="9999.999"
                      disabled={draft.status !== "OK" || busyRow === attempt.id}
                      value={draft.rawTimeSeconds}
                      onChange={(e) =>
                        setDrafts({ ...drafts, [attempt.id]: { ...draft, rawTimeSeconds: e.target.value } })
                      }
                      onKeyDown={(e) => {
                        if (e.key === "Enter" && isValid(draft)) {
                          e.preventDefault();
                          save(attempt);
                        } else if (e.key === "Escape") {
                          discardDraft(attempt);
                        }
                      }}
                    />
                  </td>
                  <td>
                    <select
                      aria-label={`Penalty for attempt ${attempt.attemptNumber}`}
                      value={draft.penalty}
                      disabled={draft.status !== "OK" || busyRow === attempt.id}
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
                    <button
                      className="button-primary"
                      disabled={busyRow === attempt.id || !isValid(draft)}
                      onClick={() => save(attempt)}
                    >
                      Save
                    </button>
                    <SavedState state={saveState[attempt.id] ?? "idle"} />
                    {rowError[attempt.id] && <p className="error-text">{rowError[attempt.id]}</p>}
                    {pending && (
                      <button type="button" onClick={() => undo(attempt)}>
                        Undo
                      </button>
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      )}
    </main>
  );
}
