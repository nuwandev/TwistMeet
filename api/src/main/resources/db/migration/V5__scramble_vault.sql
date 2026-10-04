-- M4 scramble controls: versioned generation, encrypted vault storage, per-attempt assignment,
-- controlled extras, role-scoped reveal, applied/checked audit. See DECISIONS.md "OD01
-- resolution" for the generator choice (org.worldcubeassociation.tnoodle, the official WCA
-- scramble program) and the encryption-at-rest approach (00 §7 / 04 / 08 DB invariants).

-- One batch per round. Extras count and generator/ruleset version are recorded for audit and
-- reproducibility, never the notation itself (that lives only in scramble_secrets, encrypted).
CREATE TABLE scramble_batches (
    id                UUID PRIMARY KEY,
    round_id          UUID NOT NULL REFERENCES rounds (id),
    puzzle_type       VARCHAR(20) NOT NULL DEFAULT '3x3x3',
    generator_name    VARCHAR(80) NOT NULL,
    generator_version VARCHAR(40) NOT NULL,
    ruleset_version   VARCHAR(10) NOT NULL,
    extra_count       INTEGER NOT NULL,
    created_by        UUID NOT NULL REFERENCES users (id),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_scramble_batch_round UNIQUE (round_id)
);

-- 08 DB invariant: "Scramble notation encrypted separately from ordinary event data; encryption
-- keys managed outside DB; reveal metadata audit has no notation content." This table holds only
-- the encrypted payload (AES-GCM ciphertext + nonce) and is never joined into a response DTO
-- directly — ScrambleService decrypts on demand for an authorized caller only.
CREATE TABLE scramble_secrets (
    id          UUID PRIMARY KEY,
    batch_id    UUID NOT NULL REFERENCES scramble_batches (id),
    ciphertext  TEXT NOT NULL,
    nonce       VARCHAR(40) NOT NULL,
    is_extra    BOOLEAN NOT NULL DEFAULT FALSE,
    consumed    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_scramble_secrets_batch ON scramble_secrets (batch_id);

-- One assignment per attempt slot (00 §7/02 "attempt N receives the appropriate prepared
-- puzzle"); a spoiled/voided assignment is never reused — a fresh row with a fresh (extra)
-- secret is created instead, so the full assignment history for an attempt stays intact and
-- auditable rather than being overwritten in place.
CREATE TABLE scramble_assignments (
    id                  UUID PRIMARY KEY,
    batch_id            UUID NOT NULL REFERENCES scramble_batches (id),
    secret_id           UUID NOT NULL REFERENCES scramble_secrets (id),
    round_id            UUID NOT NULL REFERENCES rounds (id),
    attempt_id          UUID NOT NULL REFERENCES attempts (id),
    entrant_id          UUID NOT NULL REFERENCES event_entrants (id),
    attempt_number      INTEGER NOT NULL,
    state               VARCHAR(20) NOT NULL DEFAULT 'ASSIGNED',
    revealed_at         TIMESTAMPTZ,
    -- No FK: in self-scramble mode the "revealer" is the competitor (an event_entrants id, not
    -- a users id) — same no-FK pattern as audit_events.actor_id, which has the identical
    -- staff-or-guest-actor shape.
    revealed_by         UUID,
    applied_at          TIMESTAMPTZ,
    applied_by          UUID REFERENCES users (id),
    checked_at          TIMESTAMPTZ,
    checked_by          UUID REFERENCES users (id),
    second_checked_at   TIMESTAMPTZ,
    second_checked_by   UUID REFERENCES users (id),
    voided_at           TIMESTAMPTZ,
    voided_by           UUID REFERENCES users (id),
    void_reason         VARCHAR(500),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    version             BIGINT NOT NULL DEFAULT 0,
    -- Unique scramble-secret use (08 DB invariant: "unique scramble assignment use") — a secret
    -- can back at most one *live* assignment, enforced per-secret since a spoiled assignment's
    -- secret is marked consumed and never reassigned.
    CONSTRAINT uq_scramble_assignment_secret UNIQUE (secret_id)
);

CREATE INDEX idx_scramble_assignments_round ON scramble_assignments (round_id);
CREATE INDEX idx_scramble_assignments_attempt ON scramble_assignments (attempt_id);

-- Only one *live* (non-voided) assignment per attempt at a time — application code enforces this
-- by always voiding the old row before inserting a replacement; this partial index catches any
-- bug that would otherwise let two live assignments exist for the same attempt.
CREATE UNIQUE INDEX uq_scramble_assignment_live_attempt
    ON scramble_assignments (attempt_id)
    WHERE state <> 'VOIDED';
