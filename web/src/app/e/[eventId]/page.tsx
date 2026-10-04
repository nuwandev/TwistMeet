"use client";

import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import { apiFetch, ApiError } from "@/lib/api";
import { AttemptView, EntrantView } from "@/lib/types";

/**
 * Competitor waiting room (P03) plus attempt status (P04 phone / P05 judge) and correction
 * request (P06) — folded into one page since M2 has no groups/stations yet.
 */
export default function WaitingRoomPage() {
  const { eventId } = useParams<{ eventId: string }>();
  const [entrant, setEntrant] = useState<EntrantView | null>(null);
  const [attempts, setAttempts] = useState<AttemptView[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [rawSeconds, setRawSeconds] = useState("");
  const [correctionNote, setCorrectionNote] = useState<Record<string, string>>({});

  async function load() {
    try {
      const me = await apiFetch<EntrantView>(`/api/v1/guest/events/${eventId}/me`);
      setEntrant(me);
      const own = await apiFetch<AttemptView[]>(`/api/v1/guest/events/${eventId}/me/attempts`);
      setAttempts(own.sort((a, b) => a.attemptNumber - b.attemptNumber));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not load your status");
    }
  }

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- intentional fetch-on-mount
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [eventId]);

  async function startAttempt(attempt: AttemptView) {
    setBusy(true);
    setError(null);
    try {
      await apiFetch(`/api/v1/attempts/${attempt.id}/start`, { method: "POST" });
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not start");
    } finally {
      setBusy(false);
    }
  }

  async function stopAttempt(attempt: AttemptView) {
    setBusy(true);
    setError(null);
    try {
      await apiFetch(`/api/v1/attempts/${attempt.id}/stop`, { method: "POST" });
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not stop");
    } finally {
      setBusy(false);
    }
  }

  async function submitAttempt(attempt: AttemptView) {
    setBusy(true);
    setError(null);
    try {
      await apiFetch(`/api/v1/attempts/${attempt.id}/submit`, {
        method: "POST",
        body: {
          status: "OK",
          rawTimeMs: Math.round(parseFloat(rawSeconds) * 1000),
          clientBuild: "web-1",
        },
      });
      setRawSeconds("");
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not submit");
    } finally {
      setBusy(false);
    }
  }

  async function requestCorrection(attempt: AttemptView) {
    setBusy(true);
    setError(null);
    try {
      await apiFetch(`/api/v1/attempts/${attempt.id}/correction-requests`, {
        method: "POST",
        body: { category: "OTHER", note: correctionNote[attempt.id] ?? "" },
      });
      setCorrectionNote({ ...correctionNote, [attempt.id]: "" });
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not send the correction request");
    } finally {
      setBusy(false);
    }
  }

  if (error && !entrant) {
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
    <main style={{ maxWidth: 480, margin: "0 auto", padding: "var(--space-4) var(--space-2)" }}>
      <h1>You&apos;re in, {entrant.displayName}</h1>
      <p className="status-badge">{entrant.status}</p>
      {error && <p className="error-text">{error}</p>}

      {!attempts || attempts.length === 0 ? (
        <p>Wait here for the organizer to open the next round.</p>
      ) : (
        <ul style={{ display: "flex", flexDirection: "column", gap: "var(--space-2)" }}>
          {attempts.map((attempt) => (
            <li key={attempt.id} className="card">
              <p>
                Attempt #{attempt.attemptNumber} —{" "}
                <span className="status-badge">
                  {attempt.resultSource === "JUDGE" ? "Judge recorded · physical timer" : "CASUAL SELF-TIMED"}
                </span>
              </p>

              {attempt.resultSource === "JUDGE" ? (
                <>
                  <p>A judge records your physical-timer result.</p>
                  {attempt.resultStatus === "PENDING" ? (
                    <p>Waiting for the judge to enter your result.</p>
                  ) : (
                    <p>
                      Result: <strong>{attempt.resultStatus}</strong>
                      {attempt.adjustedTimeMs != null && ` — ${(attempt.adjustedTimeMs / 1000).toFixed(2)}s`}
                    </p>
                  )}
                </>
              ) : (
                <>
                  {attempt.state === "PENDING" && (
                    <button className="button-primary" disabled={busy} onClick={() => startAttempt(attempt)}>
                      Ready — start timer
                    </button>
                  )}
                  {attempt.state === "RUNNING" && (
                    <button className="button-primary" disabled={busy} onClick={() => stopAttempt(attempt)}>
                      Stop timer
                    </button>
                  )}
                  {attempt.state === "STOPPED" && (
                    <div style={{ display: "flex", gap: "var(--space-1)" }}>
                      <input
                        className="field-input"
                        type="number"
                        step="0.001"
                        placeholder="Seconds"
                        value={rawSeconds}
                        onChange={(e) => setRawSeconds(e.target.value)}
                      />
                      <button className="button-primary" disabled={busy} onClick={() => submitAttempt(attempt)}>
                        Submit time
                      </button>
                    </div>
                  )}
                  {(attempt.state === "SUBMITTED" || attempt.state === "ACCEPTED") && (
                    <p>
                      Recorded as self-timed:{" "}
                      {attempt.adjustedTimeMs != null ? `${(attempt.adjustedTimeMs / 1000).toFixed(2)}s` : attempt.resultStatus}
                    </p>
                  )}
                </>
              )}

              {attempt.resultStatus !== "PENDING" && attempt.state !== "VOIDED" && (
                <div style={{ marginTop: "var(--space-1)" }}>
                  <input
                    className="field-input"
                    placeholder="Request a correction (optional note)"
                    value={correctionNote[attempt.id] ?? ""}
                    onChange={(e) => setCorrectionNote({ ...correctionNote, [attempt.id]: e.target.value })}
                  />
                  <button disabled={busy} onClick={() => requestCorrection(attempt)}>
                    Request correction
                  </button>
                </div>
              )}
            </li>
          ))}
        </ul>
      )}
    </main>
  );
}
