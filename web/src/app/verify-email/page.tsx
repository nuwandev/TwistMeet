"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import { apiFetch, ApiError } from "@/lib/api";
import { UserView } from "@/lib/types";

function VerifyEmailForm() {
  const router = useRouter();
  const token = useSearchParams().get("token") ?? "";
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      // Setting the password here — not at registration — is the point: whoever is filling in
      // this form is the one who ends up controlling the account, regardless of who requested
      // the registration that sent them this link. See DECISIONS.md.
      await apiFetch<UserView>("/api/v1/auth/email/verify", {
        method: "POST",
        body: { token, password },
      });
      router.push("/dashboard");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Something went wrong");
    } finally {
      setBusy(false);
    }
  }

  if (!token) {
    return <p className="error-text">This link is missing its verification code.</p>;
  }

  return (
    <form onSubmit={handleSubmit} className="card" style={{ display: "flex", flexDirection: "column", gap: "var(--space-2)" }}>
      <p>Confirm this is you by choosing a password. This link only works once.</p>
      <label>
        Password
        <input
          className="field-input"
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
          minLength={10}
          autoFocus
        />
      </label>
      {error && <p className="error-text">{error}</p>}
      <button className="button-primary" type="submit" disabled={busy}>
        Set password and continue
      </button>
    </form>
  );
}

export default function VerifyEmailPage() {
  return (
    <main style={{ maxWidth: 420, margin: "0 auto", padding: "var(--space-4) var(--space-2)" }}>
      <h1>Confirm your email</h1>
      <Suspense fallback={<p>Loading…</p>}>
        <VerifyEmailForm />
      </Suspense>
    </main>
  );
}
