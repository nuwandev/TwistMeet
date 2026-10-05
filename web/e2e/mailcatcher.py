#!/usr/bin/env python3
"""Minimal dev-only SMTP catcher for e2e/critical-journey.spec.ts.

The API's dev mail config (application.yml) sends to localhost:1025 with no real mailbox behind
it. This appends every message it receives to a plain-text log the spec polls for the
verification link (see critical-journey.spec.ts's MAIL_LOG/waitForVerificationUrl) — a stand-in
for reading a real inbox, same idea as CapturingMailService on the API test side, just for a
browser-driven E2E run against the real running dev server instead of the Spring test context.

Uses the standard library's `smtpd` module, deprecated since Python 3.12 in favor of `aiosmtpd`
but still present and sufficient for this narrow, local-only use; swap to `aiosmtpd` if/when
`smtpd` is actually removed from the Python version this is run with.

Usage: python3 e2e/mailcatcher.py
Writes to: $MAIL_LOG_PATH, or /tmp/twistmeet-captured-mail.log if unset — set the same path for
the catcher and the Playwright run (`MAIL_LOG_PATH=... npx playwright test`).
"""

import asyncore
import os
import smtpd

LOGFILE = os.environ.get("MAIL_LOG_PATH", "/tmp/twistmeet-captured-mail.log")


class LoggingServer(smtpd.SMTPServer):
    def process_message(self, peer, mailfrom, rcpttos, data, **kwargs):
        with open(LOGFILE, "a") as f:
            f.write("=== MESSAGE ===\n")
            f.write(f"from={mailfrom} to={rcpttos}\n")
            if isinstance(data, bytes):
                data = data.decode("utf-8", errors="replace")
            f.write(data)
            f.write("\n=== END ===\n")
            f.flush()


if __name__ == "__main__":
    server = LoggingServer(("127.0.0.1", 1025), None)
    print(f"mail catcher listening on 127.0.0.1:1025, logging to {LOGFILE}", flush=True)
    asyncore.loop()
