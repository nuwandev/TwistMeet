import Link from "next/link";
import { APP_NAME } from "@/lib/branding";

export default function HomePage() {
  return (
    <main style={{ maxWidth: 640, margin: "0 auto", padding: "var(--space-4) var(--space-2)" }}>
      <h1>{APP_NAME}</h1>
      <p>
        Run a small, unofficial speedcubing event: guest registration by QR or code, judge-entered
        results, and clear live standings. This is an early build (Milestone 1) — organizer
        tooling and competitor screens beyond registration are not built yet.
      </p>
      <div style={{ display: "flex", gap: "var(--space-2)", marginTop: "var(--space-3)" }}>
        <Link className="button-primary" href="/sign-in" style={{ display: "inline-flex", alignItems: "center", textDecoration: "none" }}>
          Staff sign in
        </Link>
        <Link href="/join" style={{ display: "inline-flex", alignItems: "center" }}>
          Have a join code?
        </Link>
      </div>
    </main>
  );
}
