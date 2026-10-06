-- 07 S08 Tournament Control: "time since round start." Distinct from created_at (when the
-- round was configured, back in DRAFT); started_at is set once when the round transitions to
-- LIVE and stays null before that.
ALTER TABLE rounds ADD COLUMN started_at TIMESTAMPTZ;
