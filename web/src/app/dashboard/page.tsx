"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { apiFetch, ApiError } from "@/lib/api";
import { EventView, OrganizationView } from "@/lib/types";
import { EmptyState, ErrorState, LoadingState, StatusBadge } from "@/components/common";
import { EventWizard } from "@/components/EventWizard";

export default function DashboardPage() {
  const [orgs, setOrgs] = useState<OrganizationView[] | null>(null);
  const [selectedOrgId, setSelectedOrgId] = useState<string | null>(null);
  const [events, setEvents] = useState<EventView[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function loadOrgs() {
    try {
      const data = await apiFetch<OrganizationView[]>("/api/v1/organizations");
      setOrgs(data);
      if (data.length > 0 && !selectedOrgId) {
        setSelectedOrgId(data[0].id);
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Not signed in");
    }
  }

  async function loadEvents(orgId: string) {
    try {
      const data = await apiFetch<EventView[]>(`/api/v1/organizations/${orgId}/events`);
      setEvents(data);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to load events");
    }
  }

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- intentional fetch-on-mount
    loadOrgs();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (selectedOrgId) {
      // eslint-disable-next-line react-hooks/set-state-in-effect -- intentional fetch-on-change
      loadEvents(selectedOrgId);
    }
  }, [selectedOrgId]);

  async function handleCreateOrg(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    // Capture the element itself (not just e.currentTarget, which React nulls out once the
    // handler yields past its first `await`) so .reset() below is safe to call later.
    const formEl = e.currentTarget;
    const form = new FormData(formEl);
    try {
      const org = await apiFetch<OrganizationView>("/api/v1/organizations", {
        method: "POST",
        body: {
          name: form.get("name"),
          defaultTimezone: form.get("defaultTimezone"),
        },
      });
      formEl.reset();
      await loadOrgs();
      setSelectedOrgId(org.id);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to create organization");
    }
  }

  if (orgs === null && error) {
    return (
      <main style={{ maxWidth: 640, margin: "0 auto", padding: "var(--space-4) var(--space-2)" }}>
        <ErrorState message={error} onRetry={loadOrgs} />
        <Link href="/sign-in">Sign in</Link>
      </main>
    );
  }

  if (orgs === null) {
    return (
      <main style={{ maxWidth: 640, margin: "0 auto", padding: "var(--space-4) var(--space-2)" }}>
        <LoadingState label="Loading your organizations…" />
      </main>
    );
  }

  return (
    <main style={{ maxWidth: 720, margin: "0 auto", padding: "var(--space-4) var(--space-2)" }}>
      <h1>Organization dashboard</h1>

      <section className="card" style={{ marginBottom: "var(--space-3)" }}>
        <h2>Your organizations</h2>
        {orgs && orgs.length > 0 ? (
          <ul>
            {orgs.map((org) => (
              <li key={org.id}>
                <button type="button" onClick={() => setSelectedOrgId(org.id)}>
                  {org.name} {selectedOrgId === org.id && "(selected)"}
                </button>
              </li>
            ))}
          </ul>
        ) : (
          <p>No organizations yet — create your first one below.</p>
        )}
        <form onSubmit={handleCreateOrg} style={{ display: "flex", gap: "var(--space-1)", flexWrap: "wrap" }}>
          <input className="field-input" name="name" placeholder="Organization name" required minLength={2} maxLength={120} />
          <input className="field-input" name="defaultTimezone" placeholder="America/Los_Angeles" required defaultValue="America/Los_Angeles" />
          <button className="button-primary" type="submit">Create organization</button>
        </form>
      </section>

      {selectedOrgId && (
        <section className="card" style={{ marginBottom: "var(--space-3)" }}>
          <h2>Events</h2>
          {events === null ? (
            <LoadingState label="Loading events…" />
          ) : events.length > 0 ? (
            <ul>
              {events.map((event) => (
                <li key={event.id}>
                  <Link href={`/events/${event.id}`}>
                    {event.name} — <StatusBadge tone="neutral">{event.state}</StatusBadge>
                  </Link>
                </li>
              ))}
            </ul>
          ) : (
            <EmptyState title="No events in this organization yet — create your first one below." />
          )}
        </section>
      )}

      {selectedOrgId && (
        <section>
          <h2>Create event</h2>
          <EventWizard
            organizationId={selectedOrgId}
            onCreated={async () => {
              await loadEvents(selectedOrgId);
            }}
          />
        </section>
      )}

      {error && <p className="error-text" style={{ marginTop: "var(--space-2)" }}>{error}</p>}
    </main>
  );
}
