"use client";

import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import { apiFetch, ApiError } from "@/lib/api";
import { CorrectionView } from "@/lib/types";

/** 07 S10 Correction queue: staff-only, oldest first, decide with a reason. */
export default function CorrectionQueuePage() {
  const { eventId } = useParams<{ eventId: string }>();
  const [corrections, setCorrections] = useState<CorrectionView[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [reasons, setReasons] = useState<Record<string, string>>({});

  async function load() {
    try {
      const data = await apiFetch<CorrectionView[]>(`/api/v1/events/${eventId}/corrections`);
      setCorrections(data.sort((a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime()));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to load corrections");
    }
  }

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- intentional fetch-on-mount
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [eventId]);

  async function decide(correction: CorrectionView, action: string) {
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
    }
  }

  if (!corrections) {
    return (
      <main style={{ maxWidth: 720, margin: "0 auto", padding: "var(--space-4) var(--space-2)" }}>
        <p>{error ?? "Loading…"}</p>
      </main>
    );
  }

  const pending = corrections.filter((c) => c.state === "PENDING");

  return (
    <main style={{ maxWidth: 720, margin: "0 auto", padding: "var(--space-4) var(--space-2)" }}>
      <h1>Correction queue</h1>
      {error && <p className="error-text">{error}</p>}
      {pending.length === 0 ? (
        <p>No pending correction requests.</p>
      ) : (
        <ul style={{ display: "flex", flexDirection: "column", gap: "var(--space-2)" }}>
          {pending.map((correction) => (
            <li key={correction.id} className="card">
              <p>
                <strong>{correction.category}</strong> — attempt {correction.attemptId}
              </p>
              {correction.note && <p>{correction.note}</p>}
              <p>Requested {new Date(correction.createdAt).toLocaleString()}</p>
              <input
                className="field-input"
                placeholder="Decision reason (required)"
                value={reasons[correction.id] ?? ""}
                onChange={(e) => setReasons({ ...reasons, [correction.id]: e.target.value })}
              />
              <div style={{ display: "flex", gap: "var(--space-1)", marginTop: "var(--space-1)" }}>
                <button disabled={busy} onClick={() => decide(correction, "ACCEPT_NO_RETRY")}>
                  Accept, no replacement
                </button>
                <button disabled={busy} onClick={() => decide(correction, "ACCEPT_RETRY")}>
                  Accept, grant replacement
                </button>
                <button disabled={busy} onClick={() => decide(correction, "REJECT")}>
                  Reject
                </button>
                <button disabled={busy} onClick={() => decide(correction, "NEED_INFO")}>
                  Ask for details
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </main>
  );
}
