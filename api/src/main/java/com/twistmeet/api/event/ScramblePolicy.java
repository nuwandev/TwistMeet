package com.twistmeet.api.event;

/**
 * 00 §7: staff-prepared is the default/recommended policy; self-scramble is casual-phone-only.
 * Stored on the event from M1 so the wizard/default is in place, but no scramble generation,
 * encryption, or reveal logic is implemented until M4 — see route 50 note in TRACEABILITY.md.
 */
public enum ScramblePolicy {
  STAFF_PREPARED,
  SELF_SCRAMBLE
}
