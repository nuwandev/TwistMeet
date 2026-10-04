"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import { apiFetch, ApiError } from "@/lib/api";
import { EntrantView, EventView, RoundFormat, RoundView } from "@/lib/types";
import {
  ConfirmDialog,
  EmptyState,
  ErrorState,
  LoadingState,
  OfflineState,
  RoleBanner,
  StatusBadge,
  useOnlineStatus,
} from "@/components/common";

type PendingConfirm =
  | { kind: "remove"; entrantId: string; name: string }
  | { kind: "withdraw"; entrantId: string; name: string }
  | { kind: "open-registration" }
  | null;

export default function EventDetailPage() {
  const { eventId } = useParams<{ eventId: string }>();
  const online = useOnlineStatus();
  const [event, setEvent] = useState<EventView | null>(null);
  const [entrants, setEntrants] = useState<EntrantView[] | null>(null);
  const [rounds, setRounds] = useState<RoundView[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [confirm, setConfirm] = useState<PendingConfirm>(null);

  async function load() {
    try {
      const data = await apiFetch<EventView>(`/api/v1/events/${eventId}`);
      setEvent(data);
      const roster = await apiFetch<EntrantView[]>(`/api/v1/events/${eventId}/entrants`);
      setEntrants(roster);
      const roundList = await apiFetch<RoundView[]>(`/api/v1/events/${eventId}/rounds`);
      setRounds(roundList);
      setError(null);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to load event");
    }
  }

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- intentional fetch-on-mount
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [eventId]);

  async function runAction(path: string, method: string = "POST", body?: unknown) {
    setBusy(true);
    setError(null);
    try {
      await apiFetch(path, { method, body });
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Action failed");
    } finally {
      setBusy(false);
    }
  }

  async function handleAddEntrant(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const formEl = e.currentTarget;
    const form = new FormData(formEl);
    await runAction(`/api/v1/events/${eventId}/entrants`, "POST", { displayName: form.get("displayName") });
    formEl.reset();
  }

  async function handleCreateRound(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const formEl = e.currentTarget;
    const form = new FormData(formEl);
    const nextOrder = (rounds?.length ?? 0) + 1;
    await runAction(`/api/v1/events/${eventId}/rounds`, "POST", {
      order: nextOrder,
      name: form.get("name"),
      format: form.get("format") as RoundFormat,
      advancementRule: "EVERYONE",
    });
    formEl.reset();
  }

  if (error && !event) {
    return (
      <main style={{ maxWidth: 640, margin: "0 auto", padding: "var(--space-4) var(--space-2)" }}>
        <ErrorState message={error} onRetry={load} />
      </main>
    );
  }

  if (!event) {
    return (
      <main style={{ maxWidth: 640, margin: "0 auto", padding: "var(--space-4) var(--space-2)" }}>
        <LoadingState label="Loading event…" />
      </main>
    );
  }

  const joinUrl =
    event.joinCode && typeof window !== "undefined"
      ? `${window.location.origin}/join/${event.joinCode}`
      : null;
  const liveRoundExists = rounds?.some((r) => r.state === "LIVE") ?? false;

  return (
    <main style={{ maxWidth: 720, margin: "0 auto", padding: "var(--space-4) var(--space-2)" }}>
      <RoleBanner eventId={eventId} />
      <h1>{event.name}</h1>
      <p>
        <StatusBadge tone="neutral">{event.state}</StatusBadge>{" "}
        <StatusBadge tone="neutral">
          {event.timerMode === "PHYSICAL_JUDGE" ? "Judge recorded · physical timer" : "Self-timed · device/browser timing"}
        </StatusBadge>
        {liveRoundExists && (
          <>
            {" "}
            <Link href={`/events/${eventId}/control`}>Tournament Control</Link>
          </>
        )}
      </p>
      {!online && <OfflineState onRetry={load} />}
      {error && <ErrorState message={error} onRetry={load} />}

      <section className="card" style={{ marginBottom: "var(--space-3)" }}>
        <h2>Registration</h2>
        {event.joinCode && (
          <p>
            Join code: <strong className="tabular">{event.joinCode}</strong>
            {joinUrl && (
              <>
                {" "}
                — <a href={joinUrl}>{joinUrl}</a>
              </>
            )}
          </p>
        )}
        <div style={{ display: "flex", gap: "var(--space-1)", flexWrap: "wrap" }}>
          <button className="button-primary" disabled={busy || event.state !== "DRAFT"} onClick={() => setConfirm({ kind: "open-registration" })}>
            Open registration
          </button>
          <button className="button-primary" disabled={busy || event.state !== "REGISTRATION_OPEN"} onClick={() => runAction(`/api/v1/events/${eventId}/registration/lock`)}>
            Lock registration
          </button>
          <button className="button-primary" disabled={busy || event.state !== "REGISTRATION_LOCKED"} onClick={() => runAction(`/api/v1/events/${eventId}/registration/reopen`)}>
            Reopen registration
          </button>
          <button className="button-primary" disabled={busy} onClick={() => runAction(`/api/v1/events/${eventId}/join-codes/rotate`)}>
            Rotate join code
          </button>
        </div>
      </section>

      <section className="card" style={{ marginBottom: "var(--space-3)" }}>
        <h2>Roster ({entrants?.length ?? 0})</h2>
        {entrants && entrants.length > 0 ? (
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <caption style={{ textAlign: "left", marginBottom: "var(--space-1)" }}>
              Entrants who have joined this event
            </caption>
            <thead>
              <tr>
                <th style={{ textAlign: "left" }}>Display name</th>
                <th style={{ textAlign: "left" }}>Status</th>
                <th style={{ textAlign: "left" }}>Check-in</th>
                <th style={{ textAlign: "left" }}>Joined</th>
                <th style={{ textAlign: "left" }}>Actions</th>
              </tr>
            </thead>
            <tbody>
              {entrants.map((entrant) => (
                <tr key={entrant.id}>
                  <td>{entrant.displayName}</td>
                  <td>{entrant.status}</td>
                  <td>{entrant.checkInState}</td>
                  <td>{new Date(entrant.joinedAt).toLocaleString()}</td>
                  <td style={{ display: "flex", gap: "var(--space-1)" }}>
                    {entrant.checkInState === "NOT_CHECKED_IN" && (
                      <button
                        type="button"
                        disabled={busy}
                        onClick={() => runAction(`/api/v1/events/${eventId}/entrants/${entrant.id}/check-in`)}
                      >
                        Check in
                      </button>
                    )}
                    {entrant.status === "ACTIVE" && (
                      <>
                        <button
                          type="button"
                          disabled={busy}
                          onClick={() => setConfirm({ kind: "withdraw", entrantId: entrant.id, name: entrant.displayName })}
                        >
                          Withdraw
                        </button>
                        <button
                          type="button"
                          disabled={busy}
                          onClick={() => setConfirm({ kind: "remove", entrantId: entrant.id, name: entrant.displayName })}
                        >
                          Remove
                        </button>
                      </>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        ) : (
          <EmptyState title="No one has joined yet." />
        )}
        <form onSubmit={handleAddEntrant} style={{ display: "flex", gap: "var(--space-1)", marginTop: "var(--space-2)" }}>
          <input className="field-input" name="displayName" placeholder="Display name" required minLength={1} maxLength={32} />
          <button className="button-primary" type="submit" disabled={busy}>
            Add entrant
          </button>
        </form>
      </section>

      <section className="card">
        <h2>Rounds</h2>
        {rounds && rounds.length > 0 ? (
          <ul style={{ display: "flex", flexDirection: "column", gap: "var(--space-2)" }}>
            {rounds.map((round) => (
              <li key={round.id} className="card">
                <p>
                  <strong>
                    {round.order}. {round.name}
                  </strong>{" "}
                  <StatusBadge tone="neutral">{round.format}</StatusBadge>{" "}
                  <StatusBadge tone={round.state === "LIVE" ? "good" : "neutral"}>{round.state}</StatusBadge>
                  {round.paused && <StatusBadge tone="warn">PAUSED</StatusBadge>}
                </p>
                <div style={{ display: "flex", gap: "var(--space-1)", flexWrap: "wrap" }}>
                  {round.state === "DRAFT" && (
                    <button disabled={busy} onClick={() => runAction(`/api/v1/rounds/${round.id}/prepare`)}>
                      Prepare (freeze roster)
                    </button>
                  )}
                  {round.state === "PREPARING" && (
                    <button disabled={busy} onClick={() => runAction(`/api/v1/rounds/${round.id}/ready`)}>
                      Mark ready
                    </button>
                  )}
                  {round.state === "READY" && (
                    <button disabled={busy} onClick={() => runAction(`/api/v1/rounds/${round.id}/start`)}>
                      Start round
                    </button>
                  )}
                  {(round.state === "PREPARING" || round.state === "READY" || round.state === "LIVE") && (
                    <Link href={`/rounds/${round.id}/scramble`}>Scramble station</Link>
                  )}
                  {round.state === "LIVE" && (
                    <Link href={`/events/${eventId}/control`}>Tournament Control</Link>
                  )}
                  {round.state === "REVIEW" && (
                    <>
                      <Link href={`/rounds/${round.id}/standings`}>Standings</Link>
                      <Link href={`/events/${eventId}/corrections`}>Correction queue</Link>
                      <button disabled={busy} onClick={() => runAction(`/api/v1/rounds/${round.id}/close`)}>
                        Close round
                      </button>
                    </>
                  )}
                  {round.state === "CLOSED" && <Link href={`/rounds/${round.id}/standings`}>Final standings</Link>}
                </div>
              </li>
            ))}
          </ul>
        ) : (
          <EmptyState title="No rounds configured yet." />
        )}
        <form onSubmit={handleCreateRound} style={{ display: "flex", gap: "var(--space-1)", marginTop: "var(--space-2)", flexWrap: "wrap" }}>
          <input className="field-input" name="name" placeholder="Round name" required minLength={1} maxLength={80} defaultValue="Final" />
          <select className="field-input" name="format" defaultValue="AO5">
            <option value="BO1">Best of 1</option>
            <option value="BO2">Best of 2</option>
            <option value="BO3">Best of 3</option>
            <option value="MO3">Mean of 3</option>
            <option value="AO5">Average of 5</option>
          </select>
          <button className="button-primary" type="submit" disabled={busy}>
            Add round
          </button>
        </form>
      </section>

      <ConfirmDialog
        open={confirm?.kind === "open-registration"}
        title="Open registration"
        summary="Competitors will be able to join with the event's join code once registration opens. You can lock registration again later."
        confirmLabel="Open registration"
        busy={busy}
        onConfirm={() => {
          setConfirm(null);
          runAction(`/api/v1/events/${eventId}/registration/open`);
        }}
        onCancel={() => setConfirm(null)}
      />
      <ConfirmDialog
        open={confirm?.kind === "withdraw"}
        title="Withdraw entrant"
        summary={confirm?.kind === "withdraw" ? `${confirm.name} will be marked withdrawn. Their existing results and audit history are preserved.` : null}
        confirmLabel="Withdraw entrant"
        busy={busy}
        onConfirm={() => {
          if (confirm?.kind === "withdraw") {
            const { entrantId } = confirm;
            setConfirm(null);
            runAction(`/api/v1/events/${eventId}/entrants/${entrantId}/withdraw`);
          }
        }}
        onCancel={() => setConfirm(null)}
      />
      <ConfirmDialog
        open={confirm?.kind === "remove"}
        title="Remove entrant"
        summary={confirm?.kind === "remove" ? `${confirm.name} will be permanently removed from the roster. This only works before any attempt exists for them — if any exist, withdraw instead.` : null}
        confirmLabel="Remove entrant"
        busy={busy}
        onConfirm={() => {
          if (confirm?.kind === "remove") {
            const { entrantId } = confirm;
            setConfirm(null);
            runAction(`/api/v1/events/${eventId}/entrants/${entrantId}`, "DELETE");
          }
        }}
        onCancel={() => setConfirm(null)}
      />
    </main>
  );
}
