"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { apiFetch, ApiError } from "@/lib/api";
import { RegistrationAccepted, UserView } from "@/lib/types";

export default function SignInPage() {
  const router = useRouter();
  const [mode, setMode] = useState<"login" | "register">("login");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      if (mode === "register") {
        // The response is deliberately generic (same for a brand-new or already-registered
        // email — see DECISIONS.md) and doesn't tell us whether this created an account, so we
        // always just continue straight to login with the same credentials.
        await apiFetch<RegistrationAccepted>("/api/v1/auth/register", {
          method: "POST",
          body: { email, password, displayName },
        });
      }
      await apiFetch<UserView>("/api/v1/auth/login", {
        method: "POST",
        body: { email, password },
      });
      router.push("/dashboard");
    } catch (err) {
      const message = err instanceof ApiError ? err.message : "Something went wrong";
      setError(message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <main style={{ maxWidth: 420, margin: "0 auto", padding: "var(--space-4) var(--space-2)" }}>
      <h1>Staff sign in</h1>
      <div style={{ display: "flex", gap: "var(--space-2)", marginBottom: "var(--space-2)" }}>
        <button type="button" onClick={() => setMode("login")} disabled={mode === "login"}>
          Sign in
        </button>
        <button type="button" onClick={() => setMode("register")} disabled={mode === "register"}>
          Create account
        </button>
      </div>
      <form onSubmit={handleSubmit} className="card" style={{ display: "flex", flexDirection: "column", gap: "var(--space-2)" }}>
        {mode === "register" && (
          <label>
            Display name
            <input
              className="field-input"
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
              required
              maxLength={120}
            />
          </label>
        )}
        <label>
          Email
          <input
            className="field-input"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
          />
        </label>
        <label>
          Password
          <input
            className="field-input"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            minLength={10}
          />
        </label>
        {error && <p className="error-text">{error}</p>}
        <button className="button-primary" type="submit" disabled={busy}>
          {mode === "register" ? "Create account and sign in" : "Sign in"}
        </button>
      </form>
    </main>
  );
}
