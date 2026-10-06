-- V1 completeness pass: public name masking (07 S12) and competitor help/request-judge (00 §5
-- "Judge: assigned event attempt entry/status and help escalation"; 07 P05 "Request judge/help").

ALTER TABLE events ADD COLUMN public_name_mask BOOLEAN NOT NULL DEFAULT FALSE;

-- A help/escalation ticket, distinct from a correction request: raised any time during an
-- attempt, before a result exists, with no category/decision workflow — just "a judge is
-- wanted here," resolved by staff. Correction requests (corrections table, M2) remain the
-- separate, heavier flow for contesting an already-recorded result.
CREATE TABLE help_requests (
    id          UUID PRIMARY KEY,
    attempt_id  UUID NOT NULL REFERENCES attempts (id),
    event_id    UUID NOT NULL REFERENCES events (id),
    entrant_id  UUID NOT NULL REFERENCES event_entrants (id),
    state       VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at TIMESTAMPTZ,
    resolved_by UUID REFERENCES users (id)
);

CREATE INDEX idx_help_requests_event ON help_requests (event_id);
