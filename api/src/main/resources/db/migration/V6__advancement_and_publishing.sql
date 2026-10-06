-- M5: advancement preview/commit, publishing and public display, event history, CSV export.
-- See DECISIONS.md "M5 implementation decisions" for the resolved-ambiguity notes referenced
-- below (publicSlug-vs-token identifier, publish gating, next-round roster model).

-- Advancement commit (09 "Advancement"; 08 "creates next-round slots transactionally"). A round
-- can be committed at most once; repeating the same commit call is idempotent (ScrambleService's
-- sibling, AdvancementService, checks this column before mutating anything).
ALTER TABLE rounds ADD COLUMN advancement_committed_at TIMESTAMPTZ;
ALTER TABLE rounds ADD COLUMN advancement_committed_by UUID REFERENCES users (id);
ALTER TABLE rounds ADD COLUMN advancement_committed_count INTEGER;

-- The roster an advancement commit admits into the NEXT round. Absent any row for a round
-- (e.g. a round's own first-ever round, or any round nobody ever committed advancement into),
-- RoundService.prepare() falls back to every active event entrant — the exact M1-M4 behavior —
-- so this is purely additive and only takes effect once advancement has actually been committed.
CREATE TABLE round_qualified_entrants (
    round_id   UUID NOT NULL REFERENCES rounds (id),
    entrant_id UUID NOT NULL REFERENCES event_entrants (id),
    PRIMARY KEY (round_id, entrant_id)
);

-- Publishing (08 `/events/{eventId}/publish`, `/unpublish`; `publicSlug` on the Event DTO; 08 DB
-- invariant "unique public slug"). `published_at` null means hidden/unpublished. Unpublish
-- rotates `public_slug` to a fresh value rather than merely clearing `published_at`, so a
-- previously shared link stops working immediately — the resolution of 07 S13's "Hide by
-- revoking token" against 08's "publicSlug" naming (DECISIONS.md).
ALTER TABLE events ADD COLUMN published_at TIMESTAMPTZ;
ALTER TABLE events ADD COLUMN public_slug VARCHAR(32) UNIQUE;
