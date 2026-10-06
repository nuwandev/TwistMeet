"use client";

import { useState } from "react";
import { apiFetch, ApiError } from "@/lib/api";
import { EventView } from "@/lib/types";

/**
 * 07 S03 Event creation wizard, simplified to the fields the backend's CreateEventRequest
 * actually accepts (puzzle type is always 3x3x3 per 00 §4's default, and round setup happens on
 * the event detail page after the draft exists — see DECISIONS.md). Steps: basics, operations,
 * review. There is no cross-reload draft persistence (the event isn't created until the final
 * step), so "save draft" here means "you can go Back and change anything before creating" rather
 * than a resumable draft across browser sessions — a documented simplification, not a silent gap.
 */
type Step = 0 | 1 | 2;

type WizardState = {
  name: string;
  startsAt: string;
  timezone: string;
  venueLabel: string;
  visibility: "PRIVATE" | "PUBLIC";
  timerMode: "PHYSICAL_JUDGE" | "PHONE_CASUAL";
  scramblePolicy: "STAFF_PREPARED" | "SELF_SCRAMBLE";
};

const INITIAL: WizardState = {
  name: "",
  startsAt: "",
  timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || "America/Los_Angeles",
  venueLabel: "",
  visibility: "PRIVATE",
  timerMode: "PHYSICAL_JUDGE",
  scramblePolicy: "STAFF_PREPARED",
};

export function EventWizard({
  organizationId,
  onCreated,
}: {
  organizationId: string;
  onCreated: (event: EventView) => void;
}) {
  const [step, setStep] = useState<Step>(0);
  const [state, setState] = useState<WizardState>(INITIAL);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  function update<K extends keyof WizardState>(key: K, value: WizardState[K]) {
    setState((s) => ({ ...s, [key]: value }));
  }

  function basicsValid() {
    return state.name.trim().length >= 3 && state.startsAt.length > 0 && state.timezone.trim().length > 0;
  }

  function operationsValid() {
    // Mirrors the server rule (00 §7): self-scramble only in casual phone-timer mode.
    return state.scramblePolicy !== "SELF_SCRAMBLE" || state.timerMode === "PHONE_CASUAL";
  }

  async function handleCreate() {
    setBusy(true);
    setError(null);
    try {
      const event = await apiFetch<EventView>(`/api/v1/organizations/${organizationId}/events`, {
        method: "POST",
        body: {
          name: state.name,
          description: "",
          startsAt: new Date(state.startsAt).toISOString(),
          timezone: state.timezone,
          venueLabel: state.venueLabel || null,
          visibility: state.visibility,
          timerMode: state.timerMode,
          scramblePolicy: state.scramblePolicy,
        },
      });
      setState(INITIAL);
      setStep(0);
      onCreated(event);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to create event");
    } finally {
      setBusy(false);
    }
  }

  const steps = ["Basics", "Operations", "Review"];

  return (
    <div className="card" aria-label="Create event wizard">
      <nav aria-label="Wizard progress">
        <ol style={{ display: "flex", gap: "var(--space-2)", listStyle: "none", padding: 0 }}>
          {steps.map((label, i) => (
            <li key={label}>
              <span
                className="status-badge"
                aria-current={step === i ? "step" : undefined}
                style={{ fontWeight: step === i ? 700 : 400 }}
              >
                {i + 1}. {label}
              </span>
            </li>
          ))}
        </ol>
      </nav>

      {step === 0 && (
        <fieldset style={{ border: "none", padding: 0, display: "flex", flexDirection: "column", gap: "var(--space-1)" }}>
          <legend>Event basics</legend>
          <label className="field-label" htmlFor="wizard-name">
            Event name (3–80 characters)
          </label>
          <input
            id="wizard-name"
            className="field-input"
            value={state.name}
            minLength={3}
            maxLength={80}
            onChange={(e) => update("name", e.target.value)}
          />
          <label className="field-label" htmlFor="wizard-starts-at">
            Date and time
          </label>
          <input
            id="wizard-starts-at"
            className="field-input"
            type="datetime-local"
            value={state.startsAt}
            onChange={(e) => update("startsAt", e.target.value)}
          />
          <label className="field-label" htmlFor="wizard-timezone">
            Timezone (IANA, e.g. America/Los_Angeles)
          </label>
          <input
            id="wizard-timezone"
            className="field-input"
            value={state.timezone}
            onChange={(e) => update("timezone", e.target.value)}
          />
          <label className="field-label" htmlFor="wizard-venue">
            Venue (optional)
          </label>
          <input
            id="wizard-venue"
            className="field-input"
            value={state.venueLabel}
            onChange={(e) => update("venueLabel", e.target.value)}
          />
          <label className="field-label" htmlFor="wizard-visibility">
            Visibility
          </label>
          <select
            id="wizard-visibility"
            className="field-input"
            value={state.visibility}
            onChange={(e) => update("visibility", e.target.value as WizardState["visibility"])}
          >
            <option value="PRIVATE">Private / unlisted</option>
            <option value="PUBLIC">Public</option>
          </select>
          {!basicsValid() && <p className="error-text">Name (3+ chars), date/time, and timezone are required.</p>}
        </fieldset>
      )}

      {step === 1 && (
        <fieldset style={{ border: "none", padding: 0, display: "flex", flexDirection: "column", gap: "var(--space-1)" }}>
          <legend>Operations</legend>
          <label className="field-label" htmlFor="wizard-timer-mode">
            Timer mode
          </label>
          <select
            id="wizard-timer-mode"
            className="field-input"
            value={state.timerMode}
            onChange={(e) => update("timerMode", e.target.value as WizardState["timerMode"])}
          >
            <option value="PHYSICAL_JUDGE">Judge recorded · physical timer (default, in-person)</option>
            <option value="PHONE_CASUAL">Self-timed · device/browser timing (casual)</option>
          </select>
          <label className="field-label" htmlFor="wizard-scramble-policy">
            Scramble policy
          </label>
          <select
            id="wizard-scramble-policy"
            className="field-input"
            value={state.scramblePolicy}
            onChange={(e) => update("scramblePolicy", e.target.value as WizardState["scramblePolicy"])}
          >
            <option value="STAFF_PREPARED">Staff-prepared (recommended for judged events)</option>
            <option value="SELF_SCRAMBLE">Competitor self-scramble (casual phone mode only)</option>
          </select>
          {!operationsValid() && (
            <p className="error-text">Self-scramble requires self-timed (casual phone) mode.</p>
          )}
        </fieldset>
      )}

      {step === 2 && (
        <div>
          <h3>Review</h3>
          <dl>
            <dt>Name</dt>
            <dd>{state.name}</dd>
            <dt>Starts</dt>
            <dd>{state.startsAt} ({state.timezone})</dd>
            <dt>Venue</dt>
            <dd>{state.venueLabel || "—"}</dd>
            <dt>Visibility</dt>
            <dd>{state.visibility}</dd>
            <dt>Timer mode</dt>
            <dd>{state.timerMode === "PHYSICAL_JUDGE" ? "Judge recorded · physical timer" : "Self-timed · device/browser timing"}</dd>
            <dt>Scramble policy</dt>
            <dd>{state.scramblePolicy}</dd>
          </dl>
          <p>This creates a draft event. Registration stays closed until you open it from the event page.</p>
        </div>
      )}

      {error && <p className="error-text">{error}</p>}

      <div style={{ display: "flex", gap: "var(--space-1)", marginTop: "var(--space-2)" }}>
        {step > 0 && (
          <button type="button" disabled={busy} onClick={() => setStep((s) => (s - 1) as Step)}>
            Back
          </button>
        )}
        {step < 2 && (
          <button
            type="button"
            className="button-primary"
            disabled={(step === 0 && !basicsValid()) || (step === 1 && !operationsValid())}
            onClick={() => setStep((s) => (s + 1) as Step)}
          >
            Continue
          </button>
        )}
        {step === 2 && (
          <button type="button" className="button-primary" disabled={busy} onClick={handleCreate}>
            {busy ? "Creating…" : "Create draft event"}
          </button>
        )}
      </div>
    </div>
  );
}
