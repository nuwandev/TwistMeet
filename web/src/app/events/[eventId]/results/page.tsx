"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useCallback, useEffect, useMemo, useState } from "react";
import { API_BASE_URL, apiFetch, ApiError, ensureCsrfCookie } from "@/lib/api";
import {
  AttemptView,
  EventView,
  ResultRevisionView,
  RoundFormat,
  RoundView,
  StandingsView,
} from "@/lib/types";
import {
  ConfirmDialog,
  EmptyState,
  ErrorState,
  LoadingState,
  LiveAnnouncer,
  OfflineState,
  RoleBanner,
  StatusBadge,
  useEventStream,
  useOnlineStatus,
} from "@/components/common";

type Tab = "live" | "published" | "revisions";

const TABS: { id: Tab; label: string }[] = [
  { id: "live", label: "Live / Unpublished" },
  { id: "published", label: "Published" },
  { id: "revisions", label: "Revisions" },
];

/** BO1/BO2/BO3 report the single best attempt; MO3/AO5 report a mean — 07 S12 "best/average." */
function resultLabel(format: RoundFormat): string {
  return format === "MO3" || format === "AO5" ? "Average" : "Best";
}

function formatMs(ms: number | null): string {
  return ms == null ? "—" : (ms / 1000).toFixed(2) + "s";
}

/**
 * 07 S12 Results and publication. Tabs: live/unpublished, published, revisions. Ranking table
 * with best/average, attempts, result source label. Organizer can publish, unpublish, export
 * CSV, amend eligible metadata (here: an entrant's display name — the one piece of per-entrant
 * metadata that's editable without touching a scored result). Public name masking option. Shows
 * last updated. Revisions tab lists actor, time, reason and before/after values — staff-only, so
 * nothing here needs hiding from a public viewer (that boundary is enforced server-side: this
 * screen's data never reaches `/public/**`).
 */
export default function ResultsPublicationPage() {
  const { eventId } = useParams<{ eventId: string }>();
  const online = useOnlineStatus();
  const [event, setEvent] = useState<EventView | null>(null);
  const [rounds, setRounds] = useState<RoundView[] | null>(null);
  const [selectedRoundId, setSelectedRoundId] = useState<string | null>(null);
  const [standings, setStandings] = useState<StandingsView | null>(null);
  const [attempts, setAttempts] = useState<AttemptView[] | null>(null);
  const [revisions, setRevisions] = useState<ResultRevisionView[] | null>(null);
  const [tab, setTab] = useState<Tab>("live");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [confirmUnpublish, setConfirmUnpublish] = useState(false);
  const [announcement, setAnnouncement] = useState("");
  const [renaming, setRenaming] = useState<{ entrantId: string; value: string } | null>(null);

  const loadEventAndRounds = useCallback(async () => {
    const ev = await apiFetch<EventView>(`/api/v1/events/${eventId}`);
    setEvent(ev);
    const roundList = await apiFetch<RoundView[]>(`/api/v1/events/${eventId}/rounds`);
    setRounds(roundList);
    return roundList;
  }, [eventId]);

  const loadRoundData = useCallback(async (roundId: string) => {
    const [standingsData, attemptsData] = await Promise.all([
      apiFetch<StandingsView>(`/api/v1/rounds/${roundId}/standings`),
      apiFetch<AttemptView[]>(`/api/v1/rounds/${roundId}/attempts`),
    ]);
    setStandings(standingsData);
    setAttempts(attemptsData);
  }, []);

  const loadRevisions = useCallback(async (roundId: string) => {
    const data = await apiFetch<ResultRevisionView[]>(`/api/v1/rounds/${roundId}/revisions`);
    setRevisions(data);
  }, []);

  const load = useCallback(async () => {
    try {
      const roundList = await loadEventAndRounds();
      const activeRoundId =
        selectedRoundId ??
        roundList.find((r) => r.state === "LIVE")?.id ??
        roundList[roundList.length - 1]?.id ??
        null;
      setSelectedRoundId(activeRoundId);
      if (activeRoundId) {
        await loadRoundData(activeRoundId);
        if (tab === "revisions") {
          await loadRevisions(activeRoundId);
        }
      }
      setError(null);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to load results");
    }
  }, [loadEventAndRounds, loadRoundData, loadRevisions, selectedRoundId, tab]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- intentional fetch-on-mount
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [eventId]);

  useEffect(() => {
    if (!selectedRoundId) return;
    // eslint-disable-next-line react-hooks/set-state-in-effect -- re-fetch when round/tab changes
    loadRoundData(selectedRoundId).catch((err) => {
      setError(err instanceof ApiError ? err.message : "Failed to load round results");
    });
    if (tab === "revisions") {
      loadRevisions(selectedRoundId).catch((err) => {
        setError(err instanceof ApiError ? err.message : "Failed to load revisions");
      });
    }
  }, [selectedRoundId, tab, loadRoundData, loadRevisions]);

  const onStreamEvent = useCallback(() => {
    setAnnouncement("Results updated.");
    load();
  }, [load]);
  const connection = useEventStream(eventId, onStreamEvent);

  const selectedRound = useMemo(
    () => rounds?.find((r) => r.id === selectedRoundId) ?? null,
    [rounds, selectedRoundId],
  );

  const attemptsByEntrant = useMemo(() => {
    const map = new Map<string, AttemptView[]>();
    for (const a of attempts ?? []) {
      const list = map.get(a.entrantId) ?? [];
      list.push(a);
      map.set(a.entrantId, list);
    }
    for (const list of map.values()) {
      list.sort((a, b) => a.attemptNumber - b.attemptNumber);
    }
    return map;
  }, [attempts]);

  const lastUpdated = useMemo(() => {
    if (revisions && revisions.length > 0) return revisions[0].createdAt;
    if (selectedRound?.startedAt) return selectedRound.startedAt;
    return null;
  }, [revisions, selectedRound]);

  async function runAction(path: string, method: string, body?: unknown) {
    setBusy(true);
    setError(null);
    try {
      await apiFetch(path, { method, body });
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Action failed");
    } finally {
      setBusy(false);
      setConfirmUnpublish(false);
    }
  }

  async function exportCsv() {
    if (!event) return;
    await ensureCsrfCookie();
    window.open(
      `${API_BASE_URL}/api/v1/organizations/${event.organizationId}/events/${eventId}/export.csv`,
      "_blank",
    );
  }

  async function saveRename() {
    if (!renaming) return;
    setBusy(true);
    try {
      await apiFetch(`/api/v1/events/${eventId}/entrants/${renaming.entrantId}`, {
        method: "PATCH",
        body: { displayName: renaming.value },
      });
      setRenaming(null);
      if (selectedRoundId) await loadRoundData(selectedRoundId);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not rename entrant");
    } finally {
      setBusy(false);
    }
  }

  if (error && !event) {
    return (
      <main style={{ maxWidth: 900, margin: "0 auto", padding: "var(--space-4) var(--space-2)" }}>
        <ErrorState message={error} onRetry={load} />
      </main>
    );
  }

  if (!event || !rounds) {
    return (
      <main style={{ maxWidth: 900, margin: "0 auto", padding: "var(--space-4) var(--space-2)" }}>
        <LoadingState label="Loading results…" />
      </main>
    );
  }

  const publicUrl =
    event.publishedAt && event.publicSlug && typeof window !== "undefined"
      ? `${window.location.origin}/public/${event.publicSlug}`
      : null;

  function renderRankingTable(masked: boolean) {
    if (!standings || !selectedRound) {
      return <LoadingState label="Loading standings…" />;
    }
    if (standings.standings.length === 0) {
      return <EmptyState title="No results yet for this round." />;
    }
    return (
      <table style={{ width: "100%", borderCollapse: "collapse" }}>
        <caption style={{ textAlign: "left" }}>
          {selectedRound.name} standings, {standings.provisional ? "provisional" : "final"}
        </caption>
        <thead>
          <tr>
            <th style={{ textAlign: "left" }}>Rank</th>
            <th style={{ textAlign: "left" }}>Entrant</th>
            <th style={{ textAlign: "left" }}>{resultLabel(selectedRound.format)}</th>
            <th style={{ textAlign: "left" }}>Best single</th>
            <th style={{ textAlign: "left" }}>Attempts (source)</th>
            {!masked && <th style={{ textAlign: "left" }}>Amend</th>}
          </tr>
        </thead>
        <tbody>
          {standings.standings.map((row) => {
            const displayName = masked ? `Competitor ${row.rank}` : row.displayName;
            const entrantAttempts = attemptsByEntrant.get(row.entrantId) ?? [];
            return (
              <tr key={row.entrantId}>
                <td className="tabular">{row.rank}</td>
                <td>
                  {renaming?.entrantId === row.entrantId ? (
                    <span style={{ display: "flex", gap: "var(--space-1)" }}>
                      <input
                        className="field-input"
                        aria-label={`New display name for ${row.displayName}`}
                        value={renaming.value}
                        onChange={(e) => setRenaming({ entrantId: row.entrantId, value: e.target.value })}
                      />
                      <button disabled={busy} onClick={saveRename}>
                        Save
                      </button>
                      <button disabled={busy} onClick={() => setRenaming(null)}>
                        Cancel
                      </button>
                    </span>
                  ) : (
                    displayName
                  )}
                </td>
                <td className="tabular">
                  {row.outcome === "OK" ? formatMs(row.displayMs) : row.outcome}
                </td>
                <td className="tabular">{formatMs(row.bestValidSingleMs)}</td>
                <td>
                  {entrantAttempts.length === 0
                    ? "—"
                    : entrantAttempts
                        .map(
                          (a) =>
                            `#${a.attemptNumber} ${
                              a.resultStatus === "PENDING" ? "pending" : formatMs(a.adjustedTimeMs)
                            } (${a.resultSource === "JUDGE" ? "judge" : "self-timed"})`,
                        )
                        .join(", ")}
                </td>
                {!masked && (
                  <td>
                    {renaming?.entrantId !== row.entrantId && (
                      <button
                        disabled={busy}
                        onClick={() => setRenaming({ entrantId: row.entrantId, value: row.displayName })}
                      >
                        Edit name
                      </button>
                    )}
                  </td>
                )}
              </tr>
            );
          })}
        </tbody>
      </table>
    );
  }

  return (
    <main style={{ maxWidth: 960, margin: "0 auto", padding: "var(--space-4) var(--space-2)" }}>
      <RoleBanner eventId={eventId} />
      <LiveAnnouncer message={announcement} />
      <h1>Results and publication</h1>
      <p>
        <strong>{event.name}</strong>{" "}
        <StatusBadge tone={connection === "connected" ? "good" : connection === "connecting" ? "neutral" : "warn"}>
          {connection === "connected" ? "Live" : connection === "connecting" ? "Connecting…" : "Reconnecting…"}
        </StatusBadge>
      </p>
      {!online && <OfflineState onRetry={load} />}
      {error && <ErrorState message={error} onRetry={load} />}

      <section className="card" style={{ marginBottom: "var(--space-3)" }}>
        <label className="field-label" htmlFor="results-round-select">
          Round
        </label>
        <select
          id="results-round-select"
          className="field-input"
          value={selectedRoundId ?? ""}
          onChange={(e) => setSelectedRoundId(e.target.value)}
        >
          {rounds.map((r) => (
            <option key={r.id} value={r.id}>
              {r.name} ({r.state})
            </option>
          ))}
        </select>
        <p>
          Last updated:{" "}
          {lastUpdated ? new Date(lastUpdated).toLocaleString() : "no results recorded yet"}
        </p>
      </section>

      <section className="card" style={{ marginBottom: "var(--space-3)" }}>
        <h2>Publishing</h2>
        <p>
          <StatusBadge tone={event.publishedAt ? "good" : "neutral"}>
            {event.publishedAt ? "Published" : "Not published"}
          </StatusBadge>
        </p>
        {publicUrl && (
          <p>
            Public link: <a href={publicUrl}>{publicUrl}</a>
          </p>
        )}
        <div style={{ display: "flex", gap: "var(--space-1)", flexWrap: "wrap" }}>
          <button
            className="button-primary"
            disabled={busy}
            onClick={() => runAction(`/api/v1/events/${eventId}/publish`, "POST")}
          >
            Publish
          </button>
          <button disabled={busy} onClick={() => setConfirmUnpublish(true)}>
            Unpublish (hide / revoke link)
          </button>
          <button disabled={busy} onClick={exportCsv}>
            Export CSV
          </button>
        </div>
        <label style={{ display: "flex", alignItems: "center", gap: "var(--space-1)", marginTop: "var(--space-2)" }}>
          <input
            type="checkbox"
            checked={event.publicNameMask}
            disabled={busy}
            onChange={(e) =>
              runAction(`/api/v1/events/${eventId}/public-name-mask`, "POST", {
                masked: e.target.checked,
              })
            }
          />
          Mask competitor names on the public display (show &quot;Competitor N&quot; instead)
        </label>
      </section>

      <div role="tablist" aria-label="Results view" style={{ display: "flex", gap: "var(--space-1)", marginBottom: "var(--space-2)" }}>
        {TABS.map((t) => (
          <button
            key={t.id}
            role="tab"
            id={`tab-${t.id}`}
            aria-selected={tab === t.id}
            aria-controls={`panel-${t.id}`}
            tabIndex={tab === t.id ? 0 : -1}
            style={{ fontWeight: tab === t.id ? 700 : 400 }}
            onClick={() => setTab(t.id)}
          >
            {t.label}
          </button>
        ))}
      </div>

      {tab === "live" && (
        <section
          id="panel-live"
          role="tabpanel"
          aria-labelledby="tab-live"
          className="card"
        >
          <h2>Live / unpublished results</h2>
          <p>The current working view — always real names, regardless of publish state.</p>
          {renderRankingTable(false)}
        </section>
      )}

      {tab === "published" && (
        <section
          id="panel-published"
          role="tabpanel"
          aria-labelledby="tab-published"
          className="card"
        >
          <h2>Published view</h2>
          {event.publishedAt ? (
            <>
              <p>
                What the public page actually shows right now (no separate published snapshot
                exists — this always reflects the live data for a published event, per
                DECISIONS.md).
              </p>
              {renderRankingTable(event.publicNameMask)}
            </>
          ) : (
            <EmptyState
              title="This event is not published yet."
              action={
                <button className="button-primary" disabled={busy} onClick={() => runAction(`/api/v1/events/${eventId}/publish`, "POST")}>
                  Publish
                </button>
              }
            />
          )}
        </section>
      )}

      {tab === "revisions" && (
        <section
          id="panel-revisions"
          role="tabpanel"
          aria-labelledby="tab-revisions"
          className="card"
        >
          <h2>Revisions</h2>
          {!revisions ? (
            <LoadingState label="Loading revisions…" />
          ) : revisions.length === 0 ? (
            <EmptyState title="No revisions recorded for this round yet." />
          ) : (
            <table style={{ width: "100%", borderCollapse: "collapse" }}>
              <caption style={{ textAlign: "left" }}>
                Every change to a recorded result, newest first
              </caption>
              <thead>
                <tr>
                  <th style={{ textAlign: "left" }}>Time</th>
                  <th style={{ textAlign: "left" }}>Entrant</th>
                  <th style={{ textAlign: "left" }}>Attempt</th>
                  <th style={{ textAlign: "left" }}>Actor</th>
                  <th style={{ textAlign: "left" }}>Before</th>
                  <th style={{ textAlign: "left" }}>After</th>
                  <th style={{ textAlign: "left" }}>Reason</th>
                </tr>
              </thead>
              <tbody>
                {revisions.map((r) => (
                  <tr key={r.id}>
                    <td className="tabular">{new Date(r.createdAt).toLocaleString()}</td>
                    <td>{r.entrantDisplayName}</td>
                    <td className="tabular">#{r.attemptNumber}</td>
                    <td>{r.actorDisplayName ?? "—"}</td>
                    <td className="tabular">
                      {r.previousResultStatus ?? "PENDING"}
                      {r.previousRawTimeMs != null ? ` ${formatMs(r.previousRawTimeMs)}` : ""}
                      {r.previousPenalty === "PLUS_TWO" ? " +2" : ""}
                    </td>
                    <td className="tabular">
                      {r.newResultStatus}
                      {r.newRawTimeMs != null ? ` ${formatMs(r.newRawTimeMs)}` : ""}
                      {r.newPenalty === "PLUS_TWO" ? " +2" : ""}
                    </td>
                    <td>{r.note ?? "—"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </section>
      )}

      <p style={{ marginTop: "var(--space-3)" }}>
        <Link href={`/events/${eventId}`}>Back to event setup</Link>
      </p>

      <ConfirmDialog
        open={confirmUnpublish}
        title="Unpublish event"
        summary={
          <p>
            This revokes the current public link; a previously shared link will stop working
            immediately. You can publish again later, which issues a new link.
          </p>
        }
        confirmLabel="Unpublish"
        busy={busy}
        onConfirm={() => runAction(`/api/v1/events/${eventId}/unpublish`, "POST")}
        onCancel={() => setConfirmUnpublish(false)}
      />
    </main>
  );
}
