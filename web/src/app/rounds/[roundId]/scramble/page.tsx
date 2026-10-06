"use client";

import { useParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { apiFetch, ApiError } from "@/lib/api";
import { ScrambleAssignmentView, ScrambleRevealView } from "@/lib/types";
import {
  ConfirmDialog,
  EmptyState,
  ErrorState,
  LoadingState,
  StatusBadge,
} from "@/components/common";
import { TwistyGuide } from "@/components/TwistyGuide";

/**
 * 07 S07 Scramble preparation station. Batch/metadata actions (generate, list, mark-applied/
 * checked) are organizer-wide (`TenantAccessService.requireScrambleStaff`); actually revealing
 * notation (reveal/official-view/print) requires an explicit Scrambler/Judge assignment on this
 * event per 00 §7 ("revealed only to assigned scrambler/judge") —
 * `TenantAccessService.requireAssignedScrambleStaff`, stricter than organization membership
 * alone. An Organizer who hits "not assigned" sees a one-click self-assign affordance below,
 * since that's the documented, intentional path to gain reveal access, not a dead end. Never
 * shown to competitor roles, and the `ScrambleVisibilityBanner` warns staff themselves not to let
 * a competitor see this screen over their shoulder.
 */
export default function ScramblePreparationStationPage() {
  const { roundId } = useParams<{ roundId: string }>();
  const [assignments, setAssignments] = useState<ScrambleAssignmentView[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [needsAssignment, setNeedsAssignment] = useState(false);
  const [busy, setBusy] = useState(false);
  const [revealed, setRevealed] = useState<Record<string, ScrambleRevealView>>({});
  const [spoilTarget, setSpoilTarget] = useState<ScrambleAssignmentView | null>(null);
  const [spoilReason, setSpoilReason] = useState("");
  const [printing, setPrinting] = useState<{ attemptNumber: number; notation: string }[] | null>(
    null,
  );

  const load = useCallback(async () => {
    try {
      const data = await apiFetch<ScrambleAssignmentView[]>(
        `/api/v1/rounds/${roundId}/scramble-assignments`,
      );
      setAssignments(data.sort((a, b) => a.attemptNumber - b.attemptNumber));
      setError(null);
    } catch (err) {
      if (err instanceof ApiError && err.code === "NOT_FOUND") {
        setAssignments([]);
        setError(null);
      } else {
        setError(err instanceof ApiError ? err.message : "Failed to load scramble assignments");
      }
    }
  }, [roundId]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- intentional fetch-on-mount
    load();
  }, [load]);

  async function generateBatch() {
    setBusy(true);
    setError(null);
    try {
      await apiFetch(`/api/v1/rounds/${roundId}/scramble-batches`, { method: "POST" });
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not generate the scramble batch");
    } finally {
      setBusy(false);
    }
  }

  async function reveal(assignment: ScrambleAssignmentView) {
    setBusy(true);
    setError(null);
    try {
      const view = await apiFetch<ScrambleRevealView>(
        `/api/v1/scramble-assignments/${assignment.id}/reveal`,
        { method: "POST" },
      );
      setRevealed({ ...revealed, [assignment.id]: view });
      setNeedsAssignment(false);
      await load();
    } catch (err) {
      if (err instanceof ApiError && err.code === "FORBIDDEN") {
        setNeedsAssignment(true);
        setError("You are not assigned Scrambler/Judge for this event yet.");
      } else {
        setError(err instanceof ApiError ? err.message : "Could not reveal this scramble");
      }
    } finally {
      setBusy(false);
    }
  }

  async function assignMyselfAsScrambler() {
    setBusy(true);
    try {
      const round = await apiFetch<{ eventId: string }>(`/api/v1/rounds/${roundId}`);
      const me = await apiFetch<{ id: string }>(`/api/v1/me`);
      await apiFetch(`/api/v1/events/${round.eventId}/staff-assignments`, {
        method: "POST",
        body: { userId: me.id, role: "SCRAMBLER" },
      });
      setNeedsAssignment(false);
      setError(null);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not self-assign");
    } finally {
      setBusy(false);
    }
  }

  async function markApplied(assignment: ScrambleAssignmentView) {
    setBusy(true);
    try {
      await apiFetch(`/api/v1/scramble-assignments/${assignment.id}/mark-applied`, {
        method: "POST",
      });
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not mark applied");
    } finally {
      setBusy(false);
    }
  }

  async function markChecked(assignment: ScrambleAssignmentView, independent: boolean) {
    setBusy(true);
    try {
      await apiFetch(
        `/api/v1/scramble-assignments/${assignment.id}/mark-checked?independent=${independent}`,
        { method: "POST" },
      );
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not mark checked");
    } finally {
      setBusy(false);
    }
  }

  async function confirmSpoil() {
    if (!spoilTarget) return;
    setBusy(true);
    try {
      await apiFetch(`/api/v1/scramble-assignments/${spoilTarget.id}/spoil`, {
        method: "POST",
        body: { reason: spoilReason },
      });
      setSpoilTarget(null);
      setSpoilReason("");
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not spoil this scramble");
    } finally {
      setBusy(false);
    }
  }

  async function printBatch() {
    setBusy(true);
    try {
      const entries = await apiFetch<{ attemptNumber: number; notation: string }[]>(
        `/api/v1/rounds/${roundId}/scramble-assignments/print`,
      );
      setPrinting(entries.sort((a, b) => a.attemptNumber - b.attemptNumber));
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not prepare the print view");
    } finally {
      setBusy(false);
    }
  }

  if (error && !assignments) {
    return (
      <main style={{ maxWidth: 820, margin: "0 auto", padding: "var(--space-4) var(--space-2)" }}>
        <ErrorState message={error} onRetry={load} />
      </main>
    );
  }

  if (!assignments) {
    return (
      <main style={{ maxWidth: 820, margin: "0 auto", padding: "var(--space-4) var(--space-2)" }}>
        <LoadingState label="Loading scramble assignments…" />
      </main>
    );
  }

  if (printing) {
    return (
      <main style={{ maxWidth: 480, margin: "0 auto", padding: "var(--space-4) var(--space-2)" }}>
        <p className="status-badge">Officials only — do not post or screen-share this sheet</p>
        <h1>Scramble sheet</h1>
        <ol>
          {printing.map((entry) => (
            <li key={entry.attemptNumber} className="tabular">
              Attempt {entry.attemptNumber}: {entry.notation}
            </li>
          ))}
        </ol>
        <button type="button" onClick={() => window.print()}>
          Print
        </button>
        <button type="button" onClick={() => setPrinting(null)}>
          Back
        </button>
      </main>
    );
  }

  return (
    <main style={{ maxWidth: 820, margin: "0 auto", padding: "var(--space-4) var(--space-2)" }}>
      <p className="status-badge" role="alert">
        Officials only — keep this screen away from competitors
      </p>
      <h1>Scramble preparation station</h1>
      {error && <ErrorState message={error} onRetry={load} />}
      {needsAssignment && (
        <button className="button-primary" disabled={busy} onClick={assignMyselfAsScrambler}>
          Assign myself as Scrambler for this event
        </button>
      )}

      {assignments.length === 0 ? (
        <EmptyState
          title="No scramble batch has been generated for this round yet."
          action={
            <button className="button-primary" disabled={busy} onClick={generateBatch}>
              Generate scramble batch
            </button>
          }
        />
      ) : (
        <>
          <button disabled={busy} onClick={printBatch}>
            Print current batch
          </button>
          <ul style={{ display: "flex", flexDirection: "column", gap: "var(--space-2)", marginTop: "var(--space-2)" }}>
            {assignments.map((assignment) => {
              const reveal_ = revealed[assignment.id];
              return (
                <li key={assignment.id} className="card">
                  <p>
                    <strong>Attempt #{assignment.attemptNumber}</strong>{" "}
                    <StatusBadge tone={assignment.state === "VOIDED" ? "bad" : "neutral"}>
                      {assignment.state}
                    </StatusBadge>
                  </p>

                  {!reveal_ ? (
                    assignment.state !== "VOIDED" && (
                      <button className="button-primary" disabled={busy} onClick={() => reveal(assignment)}>
                        Reveal scramble
                      </button>
                    )
                  ) : (
                    <>
                      <p className="tabular">Notation: {reveal_.notation}</p>
                      <TwistyGuide alg={reveal_.notation} label={`Attempt ${assignment.attemptNumber} scramble`} />
                      <div style={{ display: "flex", gap: "var(--space-1)", marginTop: "var(--space-1)", flexWrap: "wrap" }}>
                        <button disabled={busy || !!assignment.appliedAt} onClick={() => markApplied(assignment)}>
                          Mark applied
                        </button>
                        <button
                          disabled={busy || !assignment.appliedAt || !!assignment.checkedAt}
                          onClick={() => markChecked(assignment, false)}
                        >
                          Mark checked
                        </button>
                        <button
                          disabled={busy || !assignment.checkedAt || !!assignment.secondCheckedAt}
                          onClick={() => markChecked(assignment, true)}
                        >
                          Independent second check
                        </button>
                        <button disabled={busy} onClick={() => setSpoilTarget(assignment)}>
                          Scramble mistake
                        </button>
                      </div>
                    </>
                  )}
                </li>
              );
            })}
          </ul>
        </>
      )}

      <ConfirmDialog
        open={spoilTarget !== null}
        title="Scramble mistake"
        summary={
          <>
            <p>
              This voids attempt {spoilTarget?.attemptNumber}&apos;s current scramble assignment and
              assigns an unused extra. The mistaken scramble is never reused.
            </p>
            <label className="field-label" htmlFor="spoil-reason">
              Reason (required)
            </label>
            <input
              id="spoil-reason"
              className="field-input"
              value={spoilReason}
              onChange={(e) => setSpoilReason(e.target.value)}
            />
          </>
        }
        confirmLabel="Void and reassign"
        busy={busy}
        onConfirm={confirmSpoil}
        onCancel={() => {
          setSpoilTarget(null);
          setSpoilReason("");
        }}
      />
    </main>
  );
}
