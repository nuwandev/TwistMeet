-- Replaces the single-step register-then-verify flow with double opt-in: no User row (and
-- therefore no password) exists until the mailbox owner clicks the verification link and
-- chooses the password themselves. See DECISIONS.md "M1 follow-up: email verification lifecycle"
-- for why (closes both the pre-verification-login enumeration gap and the attacker-chosen-
-- password mailbox hijack risk). email_verification_tokens is superseded by pending_registrations
-- and dropped; nothing in a dev/pilot-stage schema depended on its history surviving.
DROP TABLE email_verification_tokens;

CREATE TABLE pending_registrations (
    id            UUID PRIMARY KEY,
    email         VARCHAR(320) NOT NULL UNIQUE,
    display_name  VARCHAR(120) NOT NULL,
    token_hash    VARCHAR(64) NOT NULL UNIQUE,
    expires_at    TIMESTAMPTZ NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
