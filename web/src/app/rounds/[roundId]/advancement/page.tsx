"use client";

import { useParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { apiFetch, ApiError } from "@/lib/api";
import { AdvancementCommitView, AdvancementPreviewView } from "@/lib/types";
import { ConfirmDialog, EmptyState, ErrorState, LoadingState, StatusBadge } from "@/components/common";

/**
 * 07 S11 "Round review and advancement": preview rankings/cutoff/ties, then commit — creating the
 * next round's qualified roster transactionally (AdvancementService on the API side).
 */
export default function AdvancementPage() {
  const { roundId } = useParams<{ roundId: string }>();
  const [preview, setPreview] = useState<AdvancementPreviewView | null>(null);
  const [committed, setCommitted] = useState<AdvancementCommitView | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [confirming, setConfirming] = useState(false);

  const load = useCallback(async () => {
    try {
      const data = await apiFetch<AdvancementPreviewView>(
        `/api/v1/rounds/${roundId}/advancement/preview`,
        { method: "POST" },
      );
      setPreview(data);
      setError(null);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to load advancement preview");
    }
  }, [roundId]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- intentional fetch-on-mount
    load();
  }, [load]);

  async function commit() {
    if (!preview) return;
    setBusy(true);
    try {
      const data = await apiFetch<AdvancementCommitView>(
        `/api/v1/rounds/${roundId}/advancement/commit`,
        { method: "POST", body: { expectedVersion: await currentVersion() } },
      );
      setCommitted(data);
      setConfirming(false);
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not commit advancement");
    } finally {
      setBusy(false);
    }
  }

  async function currentVersion(): Promise<number> {
    const round = await apiFetch<{ version: number }>(`/api/v1/rounds/${roundId}`);
    return round.version;
  }

  if (error && !preview) {
    return (
      <main style={{ maxWidth: 640, margin: "0 auto", padding: "var(--space-4) var(--space-2)" }}>
        <ErrorState message={error} onRetry={load} />
      </main>
    );
  }

  if (!preview) {
    return (
      <main style={{ maxWidth: 640, margin: "0 auto", padding: "var(--space-4) var(--space-2)" }}>
        <LoadingState label="Loading advancement preview…" />
      </main>
    );
  }

  return (
    <main style={{ maxWidth: 640, margin: "0 auto", padding: "var(--space-4) var(--space-2)" }}>
      <h1>Round review and advancement</h1>
      {error && <ErrorState message={error} onRetry={load} />}

      <p>
        Rule: <strong>{preview.advancementRule}</strong>
        {preview.advancementValue != null ? ` (${preview.advancementValue})` : ""}
      </p>
      <p>
        Eligible: {preview.eligibleCount} &middot; Target: {preview.targetCount} &middot; Advancing:{" "}
        {preview.advancing.length}
      </p>
      <p>{preview.tieNote}</p>
      {preview.alreadyCommitted && <StatusBadge tone="good">Already committed</StatusBadge>}
      {!preview.nextRoundId && (
        <p className="error-text">Create the next round before committing advancement.</p>
      )}

      {preview.advancing.length === 0 ? (
        <EmptyState title="No entrants are eligible to advance yet." />
      ) : (
        <ol>
          {preview.advancing.map((a) => (
            <li key={a.entrantId}>
              {a.displayName} (rank {a.rank})
            </li>
          ))}
        </ol>
      )}

      <button
        className="button-primary"
        disabled={busy || !preview.nextRoundId}
        onClick={() => setConfirming(true)}
      >
        Commit advancement
      </button>

      {committed && (
        <p className="status-badge" role="status">
          {committed.idempotentReplay ? "Already committed" : "Committed"}: {committed.advancedCount}{" "}
          entrant(s) advanced.
        </p>
      )}

      <ConfirmDialog
        open={confirming}
        title="Commit advancement"
        summary={
          <p>
            This admits {preview.advancing.length} of {preview.eligibleCount} eligible entrants into
            the next round. This cannot be silently undone — only through a logged admin operation.
          </p>
        }
        confirmLabel="Commit advancement"
        busy={busy}
        onConfirm={commit}
        onCancel={() => setConfirming(false)}
      />
    </main>
  );
}
