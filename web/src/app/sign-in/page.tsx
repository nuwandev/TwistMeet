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
  const [registered, setRegistered] = useState(false);

  async function handleLogin(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      await apiFetch<UserView>("/api/v1/auth/login", { method: "POST", body: { email, password } });
      router.push("/dashboard");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Something went wrong");
    } finally {
      setBusy(false);
    }
  }

  async function handleRegister(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      // No password here, and no auto-login: an account does not exist yet, and this response
      // is deliberately identical whether or not the email is already registered (see
      // DECISIONS.md). The password is set on the link in the email, not here.
      await apiFetch<RegistrationAccepted>("/api/v1/auth/register", {
        method: "POST",
        body: { email, displayName },
      });
      setRegistered(true);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Something went wrong");
    } finally {
      setBusy(false);
    }
  }

  if (registered) {
    return (
      <main style={{ maxWidth: 420, margin: "0 auto", padding: "var(--space-4) var(--space-2)" }}>
        <h1>Check your email</h1>
        <p>
          If <strong>{email}</strong> can be registered, we&apos;ve sent a link to confirm it and
          set your password. Nobody — including whoever submitted this form, if that wasn&apos;t
          you — can access an account at this address until that link is used.
        </p>
      </main>
    );
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

      {mode === "login" ? (
        <form onSubmit={handleLogin} className="card" style={{ display: "flex", flexDirection: "column", gap: "var(--space-2)" }}>
          <label>
            Email
            <input className="field-input" type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
          </label>
          <label>
            Password
            <input
              className="field-input"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
          </label>
          {error && <p className="error-text">{error}</p>}
          <button className="button-primary" type="submit" disabled={busy}>
            Sign in
          </button>
        </form>
      ) : (
        <form onSubmit={handleRegister} className="card" style={{ display: "flex", flexDirection: "column", gap: "var(--space-2)" }}>
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
          <label>
            Email
            <input className="field-input" type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
          </label>
          <p style={{ fontSize: 14 }}>
            You&apos;ll choose your password after confirming this email address — we&apos;ll
            send a link.
          </p>
          {error && <p className="error-text">{error}</p>}
          <button className="button-primary" type="submit" disabled={busy}>
            Send verification link
          </button>
        </form>
      )}
    </main>
  );
}
