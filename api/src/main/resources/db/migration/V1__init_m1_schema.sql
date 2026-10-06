-- M1 foundation schema: staff auth, organizations, events, guest registration.
-- Later milestones (rounds, attempts, scrambles, corrections, advancement, public snapshots)
-- add their own tables in later migrations; nothing here is Hibernate auto-DDL (00 §10).

CREATE TABLE users (
    id              UUID PRIMARY KEY,
    email           VARCHAR(320) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    display_name    VARCHAR(120) NOT NULL,
    email_verified  BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE email_verification_tokens (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL REFERENCES users (id),
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE organizations (
    id                UUID PRIMARY KEY,
    name              VARCHAR(120) NOT NULL,
    slug              VARCHAR(140) NOT NULL UNIQUE,
    default_timezone  VARCHAR(60) NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    version           BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE organization_memberships (
    id               UUID PRIMARY KEY,
    organization_id  UUID NOT NULL REFERENCES organizations (id),
    user_id          UUID NOT NULL REFERENCES users (id),
    role             VARCHAR(20) NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_membership_org_user UNIQUE (organization_id, user_id)
);

CREATE INDEX idx_membership_user ON organization_memberships (user_id);

-- Every event row carries its organization_id so a row cannot be attached across
-- organizations by accident (04 "Database invariants"); every query in the application layer
-- additionally goes through TenantAccessService (R58).
CREATE TABLE events (
    id                UUID PRIMARY KEY,
    organization_id   UUID NOT NULL REFERENCES organizations (id),
    name              VARCHAR(80) NOT NULL,
    description       TEXT,
    starts_at         TIMESTAMPTZ NOT NULL,
    timezone          VARCHAR(60) NOT NULL,
    venue_label       VARCHAR(200),
    visibility        VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
    state             VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    puzzle_type       VARCHAR(20) NOT NULL DEFAULT '333',
    timer_mode        VARCHAR(30) NOT NULL DEFAULT 'PHYSICAL_JUDGE',
    scramble_policy   VARCHAR(30) NOT NULL DEFAULT 'STAFF_PREPARED',
    join_code         VARCHAR(16),
    join_code_hash    VARCHAR(64) UNIQUE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    version           BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_events_org ON events (organization_id);

CREATE TABLE event_entrants (
    id            UUID PRIMARY KEY,
    event_id      UUID NOT NULL REFERENCES events (id),
    display_name  VARCHAR(32) NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    joined_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    version       BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_entrants_event ON event_entrants (event_id);

CREATE TABLE guest_credentials (
    id          UUID PRIMARY KEY,
    entrant_id  UUID NOT NULL REFERENCES event_entrants (id),
    event_id    UUID NOT NULL REFERENCES events (id),
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ
);

-- Append-only by application convention (common.AuditEvent never updates/deletes a row).
-- A later milestone may add a database-level trigger/role restriction to enforce this; for M1
-- the guarantee is at the application layer only — tracked as a known gap, not silently assumed.
CREATE TABLE audit_events (
    id               UUID PRIMARY KEY,
    organization_id  UUID,
    event_id         UUID,
    actor_type       VARCHAR(20) NOT NULL,
    actor_id         UUID,
    action           VARCHAR(60) NOT NULL,
    target_type      VARCHAR(60),
    target_id        VARCHAR(100),
    reason           TEXT,
    occurred_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_org ON audit_events (organization_id);
CREATE INDEX idx_audit_event ON audit_events (event_id);
