"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { apiFetch, ApiError } from "@/lib/api";
import { JoinResponse } from "@/lib/types";

export default function JoinForm({ initialCode }: { initialCode?: string }) {
  const router = useRouter();
  const [code, setCode] = useState(initialCode ?? "");
  const [displayName, setDisplayName] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      const response = await apiFetch<JoinResponse>(
        `/api/v1/join/${encodeURIComponent(code.trim().toUpperCase())}`,
        { method: "POST", body: { displayName } },
      );
      router.push(`/e/${response.eventId}`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not join this event");
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="card" style={{ display: "flex", flexDirection: "column", gap: "var(--space-2)" }}>
      <label>
        Event code
        <input
          className="field-input tabular"
          value={code}
          onChange={(e) => setCode(e.target.value)}
          required
          maxLength={16}
          autoCapitalize="characters"
        />
      </label>
      <label>
        Display name
        <input
          className="field-input"
          value={displayName}
          onChange={(e) => setDisplayName(e.target.value)}
          required
          maxLength={32}
          placeholder="Shown to officials, and to other participants if this event is public"
        />
      </label>
      <p style={{ fontSize: 14 }}>
        You don&apos;t need an account to join. We only ask for a display name — no email or
        phone number.
      </p>
      {error && <p className="error-text">{error}</p>}
      <button className="button-primary" type="submit" disabled={busy}>
        Join event
      </button>
    </form>
  );
}
