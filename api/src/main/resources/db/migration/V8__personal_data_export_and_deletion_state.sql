-- 04 "`User`: staff identity, authentication settings, deletion state" and "Give organizers
-- export and deletion processes." This is the policy-independent mechanism only — recording
-- that a deletion was requested, and when. It performs no automated deletion: the actual
-- retention period and whether to hard-delete vs. anonymize is an explicit open decision
-- (DATA_RETENTION_DECISIONS.md), and acting on a request is a manual operator action once that
-- policy exists, not something this column triggers on its own.
ALTER TABLE users ADD COLUMN deletion_requested_at TIMESTAMPTZ;
