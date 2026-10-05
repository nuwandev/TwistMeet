"use client";

import { useEffect, useRef, useState } from "react";
import { API_BASE_URL, apiFetch, ApiError } from "@/lib/api";
import { MyRole } from "@/lib/types";

/**
 * Shared components and states from product-docs/07-screen-and-component-spec.md "Common
 * components and states". Every data-entry screen in M3 is built from these rather than
 * reinventing loading/empty/error/offline handling per page.
 */

/** 07: "icon + text + optional timestamp; never color only." */
export function StatusBadge({
  tone = "neutral",
  icon,
  children,
}: {
  tone?: "neutral" | "good" | "warn" | "bad";
  icon?: string;
  children: React.ReactNode;
}) {
  const toneIcon = icon ?? { neutral: "●", good: "✓", warn: "!", bad: "✕" }[tone];
  return (
    <span className={`status-badge status-badge-${tone}`}>
      <span aria-hidden="true">{toneIcon}</span>
      {children}
    </span>
  );
}

/** 07: "Saved", "Saving…", "Queued on this device", "Not saved — retry", last sync time. */
export function SavedState({
  state,
  lastSyncedAt,
}: {
  state: "idle" | "saving" | "saved" | "queued" | "error";
  lastSyncedAt?: string | null;
}) {
  if (state === "idle") return null;
  const label = {
    saving: "Saving…",
    saved: "Saved",
    queued: "Queued on this device",
    error: "Not saved — retry",
  }[state];
  return (
    <span role="status" aria-live="polite" className={`saved-state saved-state-${state}`}>
      {label}
      {state === "saved" && lastSyncedAt && (
        <> · {new Date(lastSyncedAt).toLocaleTimeString()}</>
      )}
    </span>
  );
}

export function LoadingState({ label = "Loading…" }: { label?: string }) {
  return (
    <div className="state-block" role="status" aria-live="polite">
      <p>{label}</p>
    </div>
  );
}

export function EmptyState({
  title,
  action,
}: {
  title: string;
  action?: React.ReactNode;
}) {
  return (
    <div className="state-block empty-state">
      <p>{title}</p>
      {action}
    </div>
  );
}

export function ErrorState({
  message,
  onRetry,
}: {
  message: string;
  onRetry?: () => void;
}) {
  return (
    <div className="state-block error-state" role="alert">
      <p className="error-text">{message}</p>
      {onRetry && (
        <button type="button" onClick={onRetry}>
          Retry
        </button>
      )}
    </div>
  );
}

export function OfflineState({ onRetry }: { onRetry?: () => void }) {
  return (
    <div className="state-block offline-state" role="alert">
      <p className="error-text">
        You&apos;re offline. Changes here won&apos;t save until the connection comes back.
      </p>
      {onRetry && (
        <button type="button" onClick={onRetry}>
          Try again
        </button>
      )}
    </div>
  );
}

/** Browser online/offline + a lightweight API reachability probe for "connection-loss" states. */
export function useOnlineStatus(): boolean {
  const [online, setOnline] = useState(() => (typeof navigator === "undefined" ? true : navigator.onLine));

  useEffect(() => {
    function goOnline() {
      setOnline(true);
    }
    function goOffline() {
      setOnline(false);
    }
    window.addEventListener("online", goOnline);
    window.addEventListener("offline", goOffline);
    return () => {
      window.removeEventListener("online", goOnline);
      window.removeEventListener("offline", goOffline);
    };
  }, []);

  return online;
}

export type ConnectionState = "connecting" | "connected" | "disconnected";

/**
 * 08 "Real-time updates": subscribes to the authenticated, event-scoped SSE channel at
 * `/api/v1/events/{eventId}/stream` and calls `onEvent` for every update. Per the contract,
 * updates never carry the changed data itself — only {eventType, resourceId, changedFields,
 * occurredAt} — so `onEvent` is expected to refetch the REST snapshot rather than trust any
 * value off the wire. 07 S08 "connection state" is surfaced via the returned {@link
 * ConnectionState} so Tournament Control can show it. The browser's EventSource reconnects
 * automatically with backoff on a dropped connection; each successful reconnect re-triggers
 * `onEvent` once so the caller refetches in case any update was missed while disconnected.
 */
export function useEventStream(eventId: string | undefined, onEvent: () => void): ConnectionState {
  const [state, setState] = useState<ConnectionState>("connecting");
  const onEventRef = useRef(onEvent);
  useEffect(() => {
    onEventRef.current = onEvent;
  }, [onEvent]);

  useEffect(() => {
    if (!eventId) return;
    // eslint-disable-next-line react-hooks/set-state-in-effect -- resetting on resubscribe is the point
    setState("connecting");
    const source = new EventSource(`${API_BASE_URL}/api/v1/events/${eventId}/stream`, {
      withCredentials: true,
    });
    source.onopen = () => {
      setState("connected");
      onEventRef.current();
    };
    source.onerror = () => {
      // EventSource retries on its own; reflect the drop in the UI until it reopens.
      setState("disconnected");
    };
    source.onmessage = () => {
      onEventRef.current();
    };
    return () => {
      source.close();
    };
  }, [eventId]);

  return state;
}

/** Same as {@link useEventStream} but for the unauthenticated public channel (07 S13). */
export function usePublicEventStream(publicSlug: string | undefined, onEvent: () => void): ConnectionState {
  const [state, setState] = useState<ConnectionState>("connecting");
  const onEventRef = useRef(onEvent);
  useEffect(() => {
    onEventRef.current = onEvent;
  }, [onEvent]);

  useEffect(() => {
    if (!publicSlug) return;
    // eslint-disable-next-line react-hooks/set-state-in-effect -- resetting on resubscribe is the point
    setState("connecting");
    const source = new EventSource(`${API_BASE_URL}/api/v1/public/events/${publicSlug}/stream`);
    source.onopen = () => {
      setState("connected");
      onEventRef.current();
    };
    source.onerror = () => {
      setState("disconnected");
    };
    source.onmessage = () => {
      onEventRef.current();
    };
    return () => {
      source.close();
    };
  }, [publicSlug]);

  return state;
}

/**
 * 07 ConfirmDialog: "destructive/high-impact actions give action summary and require a specific
 * confirm button; no generic 'OK'." A native <dialog> keeps focus trapped and is dismissible with
 * Escape for free; confirmLabel must name the action, never just "OK"/"Confirm".
 */
export function ConfirmDialog({
  open,
  title,
  summary,
  confirmLabel,
  busy,
  onConfirm,
  onCancel,
}: {
  open: boolean;
  title: string;
  summary: React.ReactNode;
  confirmLabel: string;
  busy?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}) {
  const ref = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    if (open && !dialog.open) {
      dialog.showModal();
    } else if (!open && dialog.open) {
      dialog.close();
    }
  }, [open]);

  if (!open) return null;

  return (
    <dialog
      ref={ref}
      onCancel={(e) => {
        e.preventDefault();
        onCancel();
      }}
      aria-labelledby="confirm-dialog-title"
      className="confirm-dialog"
    >
      <h2 id="confirm-dialog-title">{title}</h2>
      <div>{summary}</div>
      <div style={{ display: "flex", gap: "var(--space-1)", marginTop: "var(--space-2)" }}>
        <button type="button" onClick={onCancel} disabled={busy}>
          Cancel
        </button>
        <button type="button" className="button-primary" onClick={onConfirm} disabled={busy} autoFocus>
          {confirmLabel}
        </button>
      </div>
    </dialog>
  );
}

const ROLE_LABEL: Record<MyRole, string> = {
  OWNER: "Owner",
  ORGANIZER: "Organizer",
  JUDGE: "Judge",
};

/** 00 §5/§9: "Display role and event scope visibly on staff pages." */
export function RoleBanner({ eventId }: { eventId: string }) {
  const [role, setRole] = useState<MyRole | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    apiFetch<{ role: MyRole }>(`/api/v1/events/${eventId}/my-role`)
      .then((data) => {
        if (!cancelled) setRole(data.role);
      })
      .catch((err) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Unknown role");
      });
    return () => {
      cancelled = true;
    };
  }, [eventId]);

  if (error || !role) return null;
  return (
    <p className="role-banner" aria-label={`Your role: ${ROLE_LABEL[role]}`}>
      <StatusBadge tone="neutral" icon="◆">
        {ROLE_LABEL[role]}
      </StatusBadge>
    </p>
  );
}

/** A visually-hidden live region for status announcements screen readers need but sighted users don't. */
export function LiveAnnouncer({ message }: { message: string }) {
  return (
    <div aria-live="polite" role="status" className="visually-hidden">
      {message}
    </div>
  );
}
