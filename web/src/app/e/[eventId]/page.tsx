"use client";

import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import { apiFetch, ApiError } from "@/lib/api";
import { AttemptView, CorrectionCategory, CorrectionView, EntrantView, ScrambleRevealView } from "@/lib/types";
import {
  ErrorState,
  LiveAnnouncer,
  LoadingState,
  OfflineState,
  StatusBadge,
  useOnlineStatus,
} from "@/components/common";
import { TwistyGuide } from "@/components/TwistyGuide";

/**
 * 00 §7.2 self-scramble mode: shown only once this attempt is "unlocked" (the server re-checks
 * that independently of anything this component does). A 403 here just means either this event
 * doesn't use self-scramble, or this attempt isn't unlocked yet — both render nothing, since a
 * judge-mode or not-yet-current attempt simply has no self-scramble to show.
 */
function SelfScrambleReveal({ attemptId }: { attemptId: string }) {
  const [reveal, setReveal] = useState<ScrambleRevealView | null>(null);

  useEffect(() => {
    let cancelled = false;
    apiFetch<ScrambleRevealView>(`/api/v1/attempts/${attemptId}/current-scramble`)
      .then((data) => {
        if (!cancelled) setReveal(data);
      })
      .catch(() => {
        /* not self-scramble mode, or not unlocked yet — nothing to show */
      });
    return () => {
      cancelled = true;
    };
  }, [attemptId]);

  if (!reveal) return null;
  return (
    <div className="card" style={{ marginBottom: "var(--space-1)" }}>
      <p className="status-badge">Self-scrambled · apply this before starting your timer</p>
      <p className="tabular">{reveal.notation}</p>
      <TwistyGuide alg={reveal.notation} label="Your scramble" />
    </div>
  );
}

type CorrectionDraft = { category: CorrectionCategory; note: string };
type Confirmation = { attemptId: string; correctionId: string };

/**
 * Competitor waiting room (P03) plus attempt status (P04 phone / P05 judge) and correction
 * request (P06) — folded into one page since M2 has no groups/stations yet.
 *
 * P05 distinguishes a general "Request judge/help" action (available any time during an
 * attempt, before a result exists) from the "correction-request" action (only after a judge/
 * self-timed result is recorded, to contest it). Only the latter is implemented: the API's
 * correction-request endpoint requires the attempt to already have a non-PENDING result (it
 * exists to contest a result, not to page staff), and a general help-ticket system has no
 * backing data model in 00/08. Documented as a gap in DECISIONS.md rather than faked here.
 */
export default function WaitingRoomPage() {
  const { eventId } = useParams<{ eventId: string }>();
  const online = useOnlineStatus();
  const [entrant, setEntrant] = useState<EntrantView | null>(null);
  const [attempts, setAttempts] = useState<AttemptView[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [rawSeconds, setRawSeconds] = useState("");
  const [timeError, setTimeError] = useState<string | null>(null);
  const [correctionDrafts, setCorrectionDrafts] = useState<Record<string, CorrectionDraft>>({});
  const [confirmation, setConfirmation] = useState<Confirmation | null>(null);
  const [announcement, setAnnouncement] = useState("");
  const [exportError, setExportError] = useState<string | null>(null);

  async function handleExportMyData() {
    try {
      const data = await apiFetch<unknown>(`/api/v1/guest/events/${eventId}/me/export`);
      const blob = new Blob([JSON.stringify(data, null, 2)], { type: "application/json" });
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = "twistmeet-my-data.json";
      a.click();
      URL.revokeObjectURL(url);
      setExportError(null);
    } catch (err) {
      setExportError(err instanceof ApiError ? err.message : "Export failed");
    }
  }

  async function load() {
    try {
      const me = await apiFetch<EntrantView>(`/api/v1/guest/events/${eventId}/me`);
      setEntrant(me);
      const own = await apiFetch<AttemptView[]>(`/api/v1/guest/events/${eventId}/me/attempts`);
      setAttempts(own.sort((a, b) => a.attemptNumber - b.attemptNumber));
      setError(null);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not load your status");
    }
  }

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- intentional fetch-on-mount
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [eventId]);

  function correctionDraftFor(attemptId: string): CorrectionDraft {
    return correctionDrafts[attemptId] ?? { category: "OTHER", note: "" };
  }

  async function startAttempt(attempt: AttemptView) {
    setBusy(true);
    setError(null);
    try {
      await apiFetch(`/api/v1/attempts/${attempt.id}/start`, { method: "POST" });
      setAnnouncement(`Attempt ${attempt.attemptNumber} timer started.`);
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not start");
    } finally {
      setBusy(false);
    }
  }

  async function requestHelp(attempt: AttemptView) {
    setBusy(true);
    setError(null);
    try {
      await apiFetch(`/api/v1/attempts/${attempt.id}/help-requests`, { method: "POST" });
      setAnnouncement(`Help requested for attempt ${attempt.attemptNumber}. Staff have been notified.`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not request help");
    } finally {
      setBusy(false);
    }
  }

  async function stopAttempt(attempt: AttemptView) {
    setBusy(true);
    setError(null);
    try {
      await apiFetch(`/api/v1/attempts/${attempt.id}/stop`, { method: "POST" });
      setAnnouncement(`Attempt ${attempt.attemptNumber} timer stopped. Enter your time to submit.`);
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not stop");
    } finally {
      setBusy(false);
    }
  }

  async function submitAttempt(attempt: AttemptView) {
    const seconds = parseFloat(rawSeconds);
    if (!Number.isFinite(seconds) || seconds < 0.001 || seconds > 9999.999) {
      setTimeError("Enter a time between 0.001 and 9999.999 seconds.");
      return;
    }
    setTimeError(null);
    setBusy(true);
    setError(null);
    try {
      await apiFetch(`/api/v1/attempts/${attempt.id}/submit`, {
        method: "POST",
        body: {
          status: "OK",
          rawTimeMs: Math.round(seconds * 1000),
          clientBuild: "web-1",
        },
      });
      setRawSeconds("");
      setAnnouncement(`Attempt ${attempt.attemptNumber} recorded as self-timed.`);
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not submit");
    } finally {
      setBusy(false);
    }
  }

  async function requestCorrection(attempt: AttemptView) {
    const draft = correctionDraftFor(attempt.id);
    setBusy(true);
    setError(null);
    try {
      const correction = await apiFetch<CorrectionView>(
        `/api/v1/attempts/${attempt.id}/correction-requests`,
        { method: "POST", body: { category: draft.category, note: draft.note } },
      );
      setCorrectionDrafts({ ...correctionDrafts, [attempt.id]: { category: "OTHER", note: "" } });
      setConfirmation({ attemptId: attempt.id, correctionId: correction.id });
      setAnnouncement(`Correction request ${correction.id} sent, status ${correction.state}.`);
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
        <ErrorState message={error} onRetry={load} />
        <p>Your join session may have expired. Ask the organizer for the event code.</p>
      </main>
    );
  }

  if (!entrant) {
    return (
      <main style={{ maxWidth: 420, margin: "0 auto", padding: "var(--space-4) var(--space-2)" }}>
        <LoadingState label="Loading your status…" />
      </main>
    );
  }

  const isWithdrawn = entrant.status === "WITHDRAWN";

  return (
    <main style={{ maxWidth: 480, margin: "0 auto", padding: "var(--space-4) var(--space-2)" }}>
      <LiveAnnouncer message={announcement} />
      <h1>You&apos;re in, {entrant.displayName}</h1>
      <StatusBadge tone={isWithdrawn ? "bad" : "neutral"}>{entrant.status}</StatusBadge>{" "}
      <StatusBadge tone={entrant.checkInState === "CHECKED_IN" ? "good" : "warn"}>
        {entrant.checkInState === "CHECKED_IN" ? "Checked in" : "Not checked in"}
      </StatusBadge>
      <div style={{ marginTop: "var(--space-1)" }}>
        <button type="button" onClick={handleExportMyData}>
          Export my data
        </button>
      </div>
      {exportError && <p className="error-text">{exportError}</p>}

      {!online && <OfflineState onRetry={load} />}
      {error && <ErrorState message={error} onRetry={load} />}

      {entrant.checkInState === "NOT_CHECKED_IN" && !isWithdrawn && (
        <p>Ask event staff to check you in when you arrive.</p>
      )}

      {isWithdrawn ? (
        <p>You have withdrawn from this event. If this was a mistake, ask an organizer.</p>
      ) : !attempts || attempts.length === 0 ? (
        <p>The next round isn&apos;t open yet. Wait here — this page updates once it is.</p>
      ) : (
        <ul style={{ display: "flex", flexDirection: "column", gap: "var(--space-2)" }}>
          {attempts.map((attempt) => {
            const correctionDraft = correctionDraftFor(attempt.id);
            return (
              <li key={attempt.id} className="card">
                <p>
                  Attempt #{attempt.attemptNumber} —{" "}
                  <StatusBadge tone="neutral">
                    {attempt.resultSource === "JUDGE" ? "Judge recorded · physical timer" : "CASUAL SELF-TIMED"}
                  </StatusBadge>
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
                      <>
                        <SelfScrambleReveal attemptId={attempt.id} />
                        <button className="button-primary" disabled={busy} onClick={() => startAttempt(attempt)}>
                          Ready — start timer
                        </button>
                      </>
                    )}
                    {attempt.state === "RUNNING" && (
                      <button className="button-primary" disabled={busy} onClick={() => stopAttempt(attempt)}>
                        Stop timer
                      </button>
                    )}
                    {attempt.state === "STOPPED" && (
                      <div>
                        <label className="field-label" htmlFor={`time-${attempt.id}`}>
                          Time, in seconds
                        </label>
                        <div style={{ display: "flex", gap: "var(--space-1)" }}>
                          <input
                            id={`time-${attempt.id}`}
                            className="field-input"
                            type="number"
                            step="0.001"
                            min="0.001"
                            max="9999.999"
                            value={rawSeconds}
                            onChange={(e) => setRawSeconds(e.target.value)}
                          />
                          <button className="button-primary" disabled={busy} onClick={() => submitAttempt(attempt)}>
                            Submit time
                          </button>
                        </div>
                        {timeError && <p className="error-text">{timeError}</p>}
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

                {attempt.resultStatus === "PENDING" && attempt.state !== "VOIDED" && (
                  <button disabled={busy} onClick={() => requestHelp(attempt)}>
                    Request judge / help
                  </button>
                )}

                {attempt.resultStatus !== "PENDING" && attempt.state !== "VOIDED" && (
                  <div style={{ marginTop: "var(--space-1)" }}>
                    {confirmation?.attemptId === attempt.id ? (
                      <p className="status-badge">
                        Request {confirmation.correctionId} sent — pending staff decision.
                      </p>
                    ) : (
                      <>
                        <label className="field-label" htmlFor={`category-${attempt.id}`}>
                          What happened?
                        </label>
                        <select
                          id={`category-${attempt.id}`}
                          className="field-input"
                          value={correctionDraft.category}
                          onChange={(e) =>
                            setCorrectionDrafts({
                              ...correctionDrafts,
                              [attempt.id]: { ...correctionDraft, category: e.target.value as CorrectionCategory },
                            })
                          }
                        >
                          <option value="TIMER_OR_ENTRY_ISSUE">Timer or entry issue</option>
                          <option value="SCRAMBLE_CONCERN">Scramble concern</option>
                          <option value="INTERRUPTION">Interruption</option>
                          <option value="OTHER">Other</option>
                        </select>
                        <label className="field-label" htmlFor={`note-${attempt.id}`}>
                          Note (optional, up to 500 characters)
                        </label>
                        <input
                          id={`note-${attempt.id}`}
                          className="field-input"
                          maxLength={500}
                          value={correctionDraft.note}
                          onChange={(e) =>
                            setCorrectionDrafts({
                              ...correctionDrafts,
                              [attempt.id]: { ...correctionDraft, note: e.target.value },
                            })
                          }
                        />
                        <p>This pauses round close/advancement review but does not guarantee a replacement.</p>
                        <button disabled={busy} onClick={() => requestCorrection(attempt)}>
                          Request correction
                        </button>
                      </>
                    )}
                  </div>
                )}
              </li>
            );
          })}
        </ul>
      )}
    </main>
  );
}
