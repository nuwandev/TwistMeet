-- M2 competition engine: rulesets, rounds, attempts, result revisions, corrections,
-- event-scoped staff (judge) assignments. Scrambles, advancement commit, publishing and export
-- remain out of scope for this migration (later milestones).

-- 00 §4: "In-app rules text is versioned and snapshotted into the event when registration
-- opens." A JSON/text snapshot taken once; editing after that requires the versioned-change
-- flow described there (not built this milestone — see DECISIONS.md).
ALTER TABLE events ADD COLUMN ruleset_snapshot TEXT;

-- 08 Entrant resource includes checkInState alongside status.
ALTER TABLE event_entrants ADD COLUMN check_in_state VARCHAR(20) NOT NULL DEFAULT 'NOT_CHECKED_IN';

-- Event-scoped Judge assignment (00 §5: "Judge... event-scoped" roles, distinct from the
-- organization-wide Owner/Organizer roles). A judge need not be an organization member at all;
-- this is a minimal addition (not one of the 08-enumerated routes) documented in DECISIONS.md,
-- the same way RosterController's read-only roster list was for M1.
CREATE TABLE event_staff_assignments (
    id          UUID PRIMARY KEY,
    event_id    UUID NOT NULL REFERENCES events (id),
    user_id     UUID NOT NULL REFERENCES users (id),
    role        VARCHAR(20) NOT NULL,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_event_staff_assignment UNIQUE (event_id, user_id, role)
);

CREATE INDEX idx_event_staff_event ON event_staff_assignments (event_id);

CREATE TABLE rounds (
    id                 UUID PRIMARY KEY,
    event_id           UUID NOT NULL REFERENCES events (id),
    round_order         INTEGER NOT NULL,
    name               VARCHAR(80) NOT NULL,
    format             VARCHAR(10) NOT NULL,
    advancement_rule   VARCHAR(20) NOT NULL DEFAULT 'EVERYONE',
    advancement_value  INTEGER,
    tie_policy         VARCHAR(30) NOT NULL DEFAULT 'SHARED_RANK',
    state              VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    paused             BOOLEAN NOT NULL DEFAULT FALSE,
    ruleset_version    VARCHAR(10) NOT NULL DEFAULT 'V1',
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    version            BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_round_event_order UNIQUE (event_id, round_order)
);

CREATE INDEX idx_rounds_event ON rounds (event_id);

-- Unique (round_id, entrant_id, attempt_number) per 08 database invariants. scramble_assignment_id
-- is deliberately nullable and unused in M2 (scramble generation is a later milestone) — every
-- attempt is created and judged/self-timed without one.
CREATE TABLE attempts (
    id                     UUID PRIMARY KEY,
    event_id               UUID NOT NULL REFERENCES events (id),
    round_id               UUID NOT NULL REFERENCES rounds (id),
    entrant_id             UUID NOT NULL REFERENCES event_entrants (id),
    attempt_number         INTEGER NOT NULL,
    scramble_assignment_id UUID,
    result_source          VARCHAR(20) NOT NULL,
    state                  VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    raw_time_ms            BIGINT,
    penalty                VARCHAR(10) NOT NULL DEFAULT 'NONE',
    adjusted_time_ms       BIGINT,
    result_status          VARCHAR(10) NOT NULL DEFAULT 'PENDING',
    started_at             TIMESTAMPTZ,
    stopped_at             TIMESTAMPTZ,
    submitted_at           TIMESTAMPTZ,
    client_build           VARCHAR(200),
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    version                BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_attempt_round_entrant_number UNIQUE (round_id, entrant_id, attempt_number)
);

CREATE INDEX idx_attempts_round ON attempts (round_id);
CREATE INDEX idx_attempts_entrant ON attempts (entrant_id);

-- Append-only by application convention, same as audit_events (common.AuditEvent) — see that
-- table's comment; the same known-gap note applies (enforced at the application layer only).
CREATE TABLE result_revisions (
    id                     UUID PRIMARY KEY,
    attempt_id             UUID NOT NULL REFERENCES attempts (id),
    actor_user_id          UUID REFERENCES users (id),
    previous_raw_time_ms   BIGINT,
    previous_penalty       VARCHAR(10),
    previous_result_status VARCHAR(10),
    new_raw_time_ms        BIGINT,
    new_penalty            VARCHAR(10) NOT NULL,
    new_result_status      VARCHAR(10) NOT NULL,
    note                   TEXT,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_result_revisions_attempt ON result_revisions (attempt_id);

CREATE TABLE corrections (
    id               UUID PRIMARY KEY,
    attempt_id       UUID NOT NULL REFERENCES attempts (id),
    requested_by     UUID NOT NULL REFERENCES event_entrants (id),
    category         VARCHAR(30) NOT NULL,
    note             VARCHAR(500),
    state            VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    decision         VARCHAR(20),
    decided_by       UUID REFERENCES users (id),
    decision_reason  TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    decided_at       TIMESTAMPTZ,
    version          BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_corrections_attempt ON corrections (attempt_id);
