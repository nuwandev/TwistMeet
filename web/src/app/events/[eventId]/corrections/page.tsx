"use client";

import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import { apiFetch, ApiError } from "@/lib/api";
import { CorrectionView, HelpRequestView, MyRole } from "@/lib/types";
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

type PendingDecision = { correction: CorrectionView; action: string } | null;

const ACTION_LABEL: Record<string, string> = {
  ACCEPT_NO_RETRY: "Accept, no replacement",
  ACCEPT_RETRY: "Accept, grant replacement",
  REJECT: "Reject",
  NEED_INFO: "Ask for details",
};

/** 07 S10 Correction queue: staff-only, oldest first, decide with a required reason. */
export default function CorrectionQueuePage() {
  const { eventId } = useParams<{ eventId: string }>();
  const online = useOnlineStatus();
  const [corrections, setCorrections] = useState<CorrectionView[] | null>(null);
  const [helpRequests, setHelpRequests] = useState<HelpRequestView[]>([]);
  const [role, setRole] = useState<MyRole | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [reasons, setReasons] = useState<Record<string, string>>({});
  const [reasonErrors, setReasonErrors] = useState<Record<string, string>>({});
  const [pendingDecision, setPendingDecision] = useState<PendingDecision>(null);

  async function load() {
    try {
      const data = await apiFetch<CorrectionView[]>(`/api/v1/events/${eventId}/corrections`);
      setCorrections(data.sort((a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime()));
      setError(null);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to load corrections");
    }
  }

  async function loadHelpRequests() {
    try {
      const data = await apiFetch<HelpRequestView[]>(
        `/api/v1/events/${eventId}/help-requests?state=PENDING`,
      );
      setHelpRequests(data);
    } catch {
      setHelpRequests([]);
    }
  }

  async function resolveHelpRequest(id: string) {
    setBusy(true);
    try {
      await apiFetch(`/api/v1/help-requests/${id}/resolve`, { method: "POST" });
      await loadHelpRequests();
    } finally {
      setBusy(false);
    }
  }

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- intentional fetch-on-mount
    load();
    loadHelpRequests();
    apiFetch<{ role: MyRole }>(`/api/v1/events/${eventId}/my-role`)
      .then((r) => setRole(r.role))
      .catch(() => setRole(null));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [eventId]);

  function requestDecision(correction: CorrectionView, action: string) {
    const reason = reasons[correction.id]?.trim() ?? "";
    if (!reason) {
      setReasonErrors({ ...reasonErrors, [correction.id]: "A decision reason is required." });
      return;
    }
    setReasonErrors({ ...reasonErrors, [correction.id]: "" });
    setPendingDecision({ correction, action });
  }

  async function confirmDecision() {
    if (!pendingDecision) return;
    const { correction, action } = pendingDecision;
    setBusy(true);
    setError(null);
    try {
      await apiFetch(`/api/v1/corrections/${correction.id}/decision`, {
        method: "POST",
        body: { action, reason: reasons[correction.id] ?? "", expectedVersion: correction.version },
      });
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Decision failed");
    } finally {
      setBusy(false);
      setPendingDecision(null);
    }
  }

  if (error && !corrections) {
    return (
      <main style={{ maxWidth: 720, margin: "0 auto", padding: "var(--space-4) var(--space-2)" }}>
        <ErrorState message={error} onRetry={load} />
      </main>
    );
  }

  if (!corrections) {
    return (
      <main style={{ maxWidth: 720, margin: "0 auto", padding: "var(--space-4) var(--space-2)" }}>
        <LoadingState label="Loading corrections…" />
      </main>
    );
  }

  const pending = corrections.filter((c) => c.state === "PENDING");
  const decided = corrections.filter((c) => c.state === "DECIDED");
  const canDecide = role === "OWNER" || role === "ORGANIZER";

  return (
    <main style={{ maxWidth: 720, margin: "0 auto", padding: "var(--space-4) var(--space-2)" }}>
      <RoleBanner eventId={eventId} />
      <h1>Correction queue</h1>
      {!online && <OfflineState onRetry={load} />}
      {error && <ErrorState message={error} onRetry={load} />}
      {!canDecide && role && (
        <p className="status-badge">Only organizers can decide correction requests. You can still view them.</p>
      )}

      <section className="card" style={{ marginBottom: "var(--space-3)" }}>
        <h2>Help requests ({helpRequests.length})</h2>
        {helpRequests.length === 0 ? (
          <EmptyState title="No one is currently requesting help." />
        ) : (
          <ul style={{ display: "flex", flexDirection: "column", gap: "var(--space-1)" }}>
            {helpRequests.map((h) => (
              <li key={h.id} className="card">
                <p>
                  Attempt help requested at {new Date(h.createdAt).toLocaleTimeString()}
                </p>
                <button disabled={busy} onClick={() => resolveHelpRequest(h.id)}>
                  Mark resolved
                </button>
              </li>
            ))}
          </ul>
        )}
      </section>

      {pending.length === 0 ? (
        <EmptyState title="No pending correction requests." />
      ) : (
        <ul style={{ display: "flex", flexDirection: "column", gap: "var(--space-2)" }}>
          {pending.map((correction) => (
            <li key={correction.id} className="card">
              <p>
                <strong>{correction.category}</strong> — attempt {correction.attemptId}
              </p>
              {correction.note && <p>{correction.note}</p>}
              <p>Requested {new Date(correction.createdAt).toLocaleString()}</p>
              {canDecide && (
                <>
                  <label className="field-label" htmlFor={`reason-${correction.id}`}>
                    Decision reason (required)
                  </label>
                  <input
                    id={`reason-${correction.id}`}
                    className="field-input"
                    value={reasons[correction.id] ?? ""}
                    onChange={(e) => setReasons({ ...reasons, [correction.id]: e.target.value })}
                  />
                  {reasonErrors[correction.id] && <p className="error-text">{reasonErrors[correction.id]}</p>}
                  <div style={{ display: "flex", gap: "var(--space-1)", marginTop: "var(--space-1)", flexWrap: "wrap" }}>
                    {Object.entries(ACTION_LABEL).map(([action, label]) => (
                      <button key={action} disabled={busy} onClick={() => requestDecision(correction, action)}>
                        {label}
                      </button>
                    ))}
                  </div>
                </>
              )}
            </li>
          ))}
        </ul>
      )}

      {decided.length > 0 && (
        <section style={{ marginTop: "var(--space-3)" }}>
          <h2>Recent decisions</h2>
          <ul style={{ display: "flex", flexDirection: "column", gap: "var(--space-1)" }}>
            {decided.slice(-10).reverse().map((correction) => (
              <li key={correction.id} className="card">
                <p>
                  Attempt {correction.attemptId} —{" "}
                  <StatusBadge tone="neutral">{correction.decision}</StatusBadge>
                </p>
                {correction.decisionReason && <p>Reason: {correction.decisionReason}</p>}
                <p>
                  Decided {correction.decidedAt && new Date(correction.decidedAt).toLocaleString()}
                </p>
              </li>
            ))}
          </ul>
        </section>
      )}

      <ConfirmDialog
        open={pendingDecision !== null}
        title={pendingDecision ? ACTION_LABEL[pendingDecision.action] : ""}
        summary={
          pendingDecision && (
            <>
              <p>
                This decides the correction request for attempt {pendingDecision.correction.attemptId}.
              </p>
              {pendingDecision.action === "ACCEPT_NO_RETRY" && <p>The attempt will be voided with no replacement.</p>}
              {pendingDecision.action === "ACCEPT_RETRY" && <p>The attempt will be voided and a replacement attempt created.</p>}
              {pendingDecision.action === "REJECT" && <p>The original result is preserved unchanged.</p>}
              {pendingDecision.action === "NEED_INFO" && <p>The original result is preserved unchanged; the competitor is asked for more detail.</p>}
            </>
          )
        }
        confirmLabel={pendingDecision ? ACTION_LABEL[pendingDecision.action] : "Confirm"}
        busy={busy}
        onConfirm={confirmDecision}
        onCancel={() => setPendingDecision(null)}
      />
    </main>
  );
}
