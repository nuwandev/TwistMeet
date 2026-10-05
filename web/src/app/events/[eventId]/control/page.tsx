"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { apiFetch, ApiError } from "@/lib/api";
import { AttemptView, CorrectionView, EventView, RoundView } from "@/lib/types";
import {
  ConfirmDialog,
  EmptyState,
  ErrorState,
  LiveAnnouncer,
  LoadingState,
  OfflineState,
  RoleBanner,
  StatusBadge,
  useEventStream,
  useOnlineStatus,
} from "@/components/common";

/**
 * 07 S08 Tournament Control. Composed entirely from existing M2 reads (round, round attempts,
 * event corrections) rather than a new aggregate endpoint — reuse per this milestone's
 * instruction to add only the APIs the specs require. Organizer-scoped: a judge with no
 * organization membership can judge/correct via their own dedicated pages, but this screen's
 * "pause/close" actions and event-wide view are Organizer-only per 00 §5, so it calls the
 * organizer-only `GET /events/{eventId}` directly rather than trying to work around that.
 */
type Filter = "all" | "needs-attention" | "in-progress" | "finished";

export default function TournamentControlPage() {
  const { eventId } = useParams<{ eventId: string }>();
  const online = useOnlineStatus();
  const [event, setEvent] = useState<EventView | null>(null);
  const [rounds, setRounds] = useState<RoundView[] | null>(null);
  const [attempts, setAttempts] = useState<AttemptView[] | null>(null);
  const [corrections, setCorrections] = useState<CorrectionView[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [filter, setFilter] = useState<Filter>("all");
  const [busy, setBusy] = useState(false);
  const [confirmAction, setConfirmAction] = useState<null | "pause" | "close">(null);
  const [announcement, setAnnouncement] = useState("");
  const previousCompleteRef = useRef<number | null>(null);
  const previousHelpRef = useRef<number | null>(null);

  const liveRound = useMemo(() => rounds?.find((r) => r.state === "LIVE") ?? null, [rounds]);

  const load = useCallback(async () => {
    try {
      const ev = await apiFetch<EventView>(`/api/v1/events/${eventId}`);
      setEvent(ev);
      const roundList = await apiFetch<RoundView[]>(`/api/v1/events/${eventId}/rounds`);
      setRounds(roundList);
      const live = roundList.find((r) => r.state === "LIVE");
      if (live) {
        const roundAttempts = await apiFetch<AttemptView[]>(`/api/v1/rounds/${live.id}/attempts`);
        roundAttempts.sort((a, b) => a.attemptNumber - b.attemptNumber);
        setAttempts(roundAttempts);
        const pending = await apiFetch<CorrectionView[]>(
          `/api/v1/events/${eventId}/corrections?state=PENDING`,
        );
        const liveAttemptIds = new Set(roundAttempts.map((a) => a.id));
        const liveCorrections = pending.filter((c) => liveAttemptIds.has(c.attemptId));
        setCorrections(liveCorrections);

        // 07 S08's progress summary and connection state update live over SSE with no page
        // reload; a sighted user sees the number change, but a screen reader needs an explicit
        // announcement (same aria-live pattern e/[eventId] already uses for the competitor side).
        const completeNow = roundAttempts.filter((a) => a.resultStatus !== "PENDING").length;
        if (previousCompleteRef.current !== null && completeNow > previousCompleteRef.current) {
          setAnnouncement(`${completeNow} of ${roundAttempts.length} attempts complete.`);
        }
        previousCompleteRef.current = completeNow;
        if (previousHelpRef.current !== null && liveCorrections.length > previousHelpRef.current) {
          setAnnouncement(
            `${liveCorrections.length} correction${liveCorrections.length === 1 ? "" : "s"} pending.`,
          );
        }
        previousHelpRef.current = liveCorrections.length;
      } else {
        setAttempts([]);
        setCorrections([]);
      }
      setError(null);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to load Tournament Control");
    }
  }, [eventId]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- intentional fetch-on-mount
    load();
  }, [load]);

  const connection = useEventStream(eventId, load);

  async function runAction(path: string) {
    setBusy(true);
    try {
      await apiFetch(path, { method: "POST" });
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Action failed");
    } finally {
      setBusy(false);
      setConfirmAction(null);
    }
  }

  async function closeLiveRound(roundId: string) {
    setBusy(true);
    try {
      // S08 "close round when eligible" is one action in this UI; the round lifecycle (00 §6)
      // still requires REVIEW before CLOSED, so this performs both transitions in sequence.
      await apiFetch(`/api/v1/rounds/${roundId}/review`, { method: "POST" });
      await apiFetch(`/api/v1/rounds/${roundId}/close`, { method: "POST" });
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not close the round");
    } finally {
      setBusy(false);
      setConfirmAction(null);
    }
  }

  if (error && !event) {
    return (
      <main style={{ maxWidth: 960, margin: "0 auto", padding: "var(--space-4) var(--space-2)" }}>
        <ErrorState message={error} onRetry={load} />
      </main>
    );
  }

  if (!event || !rounds) {
    return (
      <main style={{ maxWidth: 960, margin: "0 auto", padding: "var(--space-4) var(--space-2)" }}>
        <LoadingState label="Loading Tournament Control…" />
      </main>
    );
  }

  if (!liveRound) {
    return (
      <main style={{ maxWidth: 960, margin: "0 auto", padding: "var(--space-4) var(--space-2)" }}>
        <RoleBanner eventId={eventId} />
        <h1>Tournament Control</h1>
        <EmptyState
          title="No round is currently live."
          action={<Link href={`/events/${eventId}`}>Go to event setup</Link>}
        />
      </main>
    );
  }

  const complete = (attempts ?? []).filter((a) => a.resultStatus !== "PENDING").length;
  const expected = (attempts ?? []).length;
  const helpRequested = corrections?.length ?? 0;

  function attemptRowFilter(attempt: AttemptView): boolean {
    if (filter === "all") return true;
    if (filter === "needs-attention")
      return attempt.state === "VOIDED" || corrections?.some((c) => c.attemptId === attempt.id) === true;
    if (filter === "in-progress")
      return attempt.resultStatus === "PENDING" && attempt.state !== "VOIDED";
    if (filter === "finished") return attempt.resultStatus !== "PENDING";
    return true;
  }

  return (
    <main style={{ maxWidth: 960, margin: "0 auto", padding: "var(--space-4) var(--space-2)" }}>
      <RoleBanner eventId={eventId} />
      <LiveAnnouncer message={announcement} />
      <h1>Tournament Control</h1>
      <p>
        <strong>{event.name}</strong> — round <strong>{liveRound.name}</strong>{" "}
        <StatusBadge tone="good">{liveRound.state}</StatusBadge>
        {liveRound.paused && <StatusBadge tone="warn">PAUSED</StatusBadge>}{" "}
        <StatusBadge tone="neutral">
          {event.timerMode === "PHYSICAL_JUDGE" ? "Judge recorded · physical timer" : "Self-timed · device/browser timing"}
        </StatusBadge>{" "}
        <StatusBadge tone={connection === "connected" ? "good" : connection === "connecting" ? "neutral" : "warn"}>
          {connection === "connected"
            ? "Live"
            : connection === "connecting"
              ? "Connecting…"
              : "Reconnecting…"}
        </StatusBadge>
      </p>
      {liveRound.startedAt && (
        <p>Started {new Date(liveRound.startedAt).toLocaleTimeString()}</p>
      )}
      {!online && <OfflineState onRetry={load} />}
      {error && <ErrorState message={error} onRetry={load} />}

      <section className="card" style={{ marginBottom: "var(--space-3)" }}>
        <h2>Progress</h2>
        <p>
          {complete} / {expected} attempts complete · {helpRequested} correction
          {helpRequested === 1 ? "" : "s"} pending
        </p>
        <div style={{ display: "flex", gap: "var(--space-1)", flexWrap: "wrap" }}>
          <button className="button-primary" disabled={busy} onClick={() => setConfirmAction("pause")}>
            {liveRound.paused ? "Resume new starts" : "Pause new starts"}
          </button>
          <Link href={`/rounds/${liveRound.id}/judge`}>Open judge entry</Link>
          <Link href={`/rounds/${liveRound.id}/scramble`}>Scramble station</Link>
          <Link href={`/rounds/${liveRound.id}/print-sheet`}>Print offline score sheet</Link>
          <Link href={`/events/${eventId}/corrections`}>Resolve requests ({helpRequested})</Link>
          <button
            className="button-primary"
            disabled={busy || complete < expected}
            onClick={() => setConfirmAction("close")}
            title={complete < expected ? "All attempts must be resolved first" : undefined}
          >
            Close round
          </button>
        </div>
      </section>

      <section className="card">
        <h2>Entrants</h2>
        <div role="group" aria-label="Filter" style={{ marginBottom: "var(--space-1)", display: "flex", gap: "var(--space-1)" }}>
          {(["all", "needs-attention", "in-progress", "finished"] as Filter[]).map((f) => (
            <button
              key={f}
              type="button"
              aria-pressed={filter === f}
              onClick={() => setFilter(f)}
              style={{ fontWeight: filter === f ? 700 : 400 }}
            >
              {f === "all" ? "All" : f === "needs-attention" ? "Needs attention" : f === "in-progress" ? "In progress" : "Finished"}
            </button>
          ))}
        </div>
        {attempts && attempts.filter(attemptRowFilter).length > 0 ? (
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <caption style={{ textAlign: "left" }}>Attempts in the live round</caption>
            <thead>
              <tr>
                <th style={{ textAlign: "left" }}>Attempt</th>
                <th style={{ textAlign: "left" }}>State</th>
                <th style={{ textAlign: "left" }}>Result</th>
                <th style={{ textAlign: "left" }}>Action</th>
              </tr>
            </thead>
            <tbody>
              {attempts.filter(attemptRowFilter).map((attempt) => (
                <tr key={attempt.id}>
                  <td>#{attempt.attemptNumber}</td>
                  <td>
                    <StatusBadge tone={attempt.state === "VOIDED" ? "bad" : "neutral"}>
                      {attempt.state}
                    </StatusBadge>
                  </td>
                  <td className="tabular">
                    {attempt.resultStatus === "PENDING"
                      ? "—"
                      : attempt.resultStatus === "OK"
                        ? `${((attempt.adjustedTimeMs ?? 0) / 1000).toFixed(2)}s`
                        : attempt.resultStatus}
                  </td>
                  <td>
                    <Link href={`/rounds/${liveRound.id}/judge`}>Judge entry</Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        ) : (
          <EmptyState title="No attempts match this filter." />
        )}
      </section>

      <ConfirmDialog
        open={confirmAction === "pause"}
        title={liveRound.paused ? "Resume new starts" : "Pause new starts"}
        summary={
          liveRound.paused
            ? "Competitors will be able to start new self-timed attempts again."
            : "Competitors will not be able to start new self-timed attempts until you resume. In-progress attempts are not affected."
        }
        confirmLabel={liveRound.paused ? "Resume new starts" : "Pause new starts"}
        busy={busy}
        onConfirm={() => runAction(`/api/v1/rounds/${liveRound.id}/pause`)}
        onCancel={() => setConfirmAction(null)}
      />
      <ConfirmDialog
        open={confirmAction === "close"}
        title="Close round"
        summary={`This closes "${liveRound.name}" and finalizes standings for this round. ${complete} of ${expected} attempts are resolved.`}
        confirmLabel="Close round"
        busy={busy}
        onConfirm={() => closeLiveRound(liveRound.id)}
        onCancel={() => setConfirmAction(null)}
      />
    </main>
  );
}
