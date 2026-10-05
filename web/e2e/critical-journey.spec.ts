import { test, expect, type Page } from "@playwright/test";
import fs from "node:fs";

/**
 * Golden-path critical journey through the real, running TwistMeet app:
 * organizer registers -> verifies email -> sets password -> creates an
 * organization -> creates an event -> opens registration -> a guest joins ->
 * organizer creates and runs a round -> judge records a result -> Tournament
 * Control reflects it -> round is closed -> standings show the result.
 *
 * The only non-UI step is reading the verification email from the mail
 * catcher's log file (there is no UI for that), everything else drives the
 * real app in a real browser against the real running API.
 */

// Points at whatever file a local SMTP catcher (listening on the port the API's dev mail config
// sends to, default 1025) appends captured messages to. See README "Running the E2E test" for
// the catcher this was developed against; no catcher is started by this spec itself.
const MAIL_LOG = process.env.MAIL_LOG_PATH ?? "/tmp/twistmeet-captured-mail.log";

/**
 * The mail catcher logs the raw MIME message, and SmtpMailService sends
 * plain text with Content-Transfer-Encoding: quoted-printable, which can
 * split the verification URL across soft line breaks ("=\n") and escape
 * "=" itself as "=3D". Decode that before pattern-matching the URL out.
 */
function decodeQuotedPrintable(text: string): string {
  return text
    .replace(/=\r?\n/g, "")
    .replace(/=([0-9A-Fa-f]{2})/g, (_, hex) => String.fromCharCode(parseInt(hex, 16)));
}

/**
 * Polls the mail-catcher log file for the most recent message addressed to
 * `email` and extracts the /verify-email?token=... URL from its body.
 */
async function waitForVerificationUrl(email: string): Promise<string> {
  const deadline = Date.now() + 20_000;
  while (Date.now() < deadline) {
    if (fs.existsSync(MAIL_LOG)) {
      const contents = fs.readFileSync(MAIL_LOG, "utf8");
      const blocks = contents.split("=== MESSAGE ===").slice(1);
      // Walk from the most recently appended block backwards so a re-run
      // (same mailbox, new token) always picks up the latest link.
      for (let i = blocks.length - 1; i >= 0; i--) {
        const block = blocks[i];
        const toLine = block.split("\n").find((line) => line.includes("to="));
        if (toLine && toLine.includes(email)) {
          const decoded = decodeQuotedPrintable(block);
          const match = decoded.match(/https?:\/\/\S*\/verify-email\?token=\S+/);
          if (match) {
            return match[0].trim();
          }
        }
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
  throw new Error(`Timed out waiting for verification email to ${email}`);
}

test("organizer and competitor golden path", async ({ page, browser }) => {
  const uniqueSuffix = Date.now();
  const organizerEmail = `e2e-${uniqueSuffix}@example.com`;
  const organizerPassword = "correct-horse-battery";

  // ---- 1. Organizer registers ----
  await page.goto("/sign-in");
  await page.getByRole("button", { name: "Create account" }).click();
  await page.getByLabel("Display name").fill("E2E Organizer");
  await page.getByLabel("Email").fill(organizerEmail);
  await page.getByRole("button", { name: "Send verification link" }).click();
  await expect(page.getByRole("heading", { name: "Check your email" })).toBeVisible();

  // ---- 2. Read the verification email from the mail catcher ----
  const verifyUrl = await waitForVerificationUrl(organizerEmail);

  // ---- 3. Set password and land on dashboard ----
  await page.goto(verifyUrl);
  await page.getByLabel("Password").fill(organizerPassword);
  await page.getByRole("button", { name: "Set password and continue" }).click();
  await page.waitForURL("**/dashboard");
  await expect(page.getByRole("heading", { name: "Organization dashboard" })).toBeVisible();

  // ---- 4. Create an organization ----
  const orgName = `E2E Club ${uniqueSuffix}`;
  await page.getByPlaceholder("Organization name").fill(orgName);
  await page.getByRole("button", { name: "Create organization" }).click();
  await expect(page.getByRole("button", { name: new RegExp(`${orgName}.*\\(selected\\)`) })).toBeVisible();

  // ---- 5. Create an event (PHYSICAL_JUDGE timer mode) ----
  const eventName = `E2E Meetup ${uniqueSuffix}`;
  await page.locator("#wizard-name").fill(eventName);
  await page.locator("#wizard-starts-at").fill("2027-06-01T10:00");
  const continueBtn = page.getByRole("button", { name: "Continue" });
  await expect(continueBtn).toBeEnabled();
  await continueBtn.click();

  await expect(page.locator("#wizard-timer-mode")).toBeVisible();
  await page.locator("#wizard-timer-mode").selectOption("PHYSICAL_JUDGE");
  await page.getByRole("button", { name: "Continue" }).click();

  await expect(page.getByRole("heading", { name: "Review" })).toBeVisible();
  await page.getByRole("button", { name: "Create draft event" }).click();

  await page.getByRole("link", { name: eventName }).click();
  await page.waitForURL("**/events/*");
  await expect(page.getByRole("heading", { name: eventName })).toBeVisible();
  await expect(page.getByText("Owner")).toBeVisible();

  // ---- 6. Open registration to get a join code ----
  await page.getByRole("button", { name: "Open registration", exact: true }).click();
  const openRegDialog = page.locator("dialog[open]");
  await expect(openRegDialog).toBeVisible();
  await openRegDialog.getByRole("button", { name: "Open registration", exact: true }).click();
  await expect(openRegDialog).toBeHidden();

  const joinCodeLocator = page.locator("strong.tabular");
  await expect(joinCodeLocator).toBeVisible();
  const joinCode = (await joinCodeLocator.textContent())?.trim();
  if (!joinCode) throw new Error("Could not read join code after opening registration");

  // ---- 7. Guest joins in a separate browser context (no shared cookies) ----
  const guestContext = await browser.newContext();
  const guestPage: Page = await guestContext.newPage();
  const guestName = `E2E Guest ${uniqueSuffix}`;
  await guestPage.goto(`/join/${joinCode}`);
  await guestPage.getByLabel("Display name").fill(guestName);
  await guestPage.getByRole("button", { name: "Join event" }).click();
  await guestPage.waitForURL("**/e/*");

  // ---- 8. Organizer creates a BO1 round and runs it through to LIVE ----
  await page.reload();
  await expect(page.getByRole("heading", { name: "Rounds" })).toBeVisible();
  await page.locator('select[name="format"]').selectOption("BO1");
  await page.getByRole("button", { name: "Add round" }).click();
  await expect(page.getByRole("button", { name: "Prepare (freeze roster)" })).toBeVisible();

  await page.getByRole("button", { name: "Prepare (freeze roster)" }).click();
  await expect(page.getByRole("button", { name: "Mark ready" })).toBeVisible();
  await page.getByRole("button", { name: "Mark ready" }).click();
  await expect(page.getByRole("button", { name: "Start round" })).toBeVisible();
  await page.getByRole("button", { name: "Start round" }).click();
  await expect(page.getByText("LIVE")).toBeVisible();

  // ---- 9. Judge records a result for the guest's attempt ----
  const judgeLink = page.getByRole("link", { name: "Tournament Control" }).first();
  await expect(judgeLink).toBeVisible();

  // Navigate straight to Tournament Control, then to judge entry from there,
  // since that's how an organizer would get there during a live round.
  await judgeLink.click();
  await page.waitForURL("**/control");
  await expect(page.getByRole("heading", { name: "Tournament Control" })).toBeVisible();

  // Connection-state badge: one of "Live" / "Connecting…" / "Reconnecting…".
  const connectionBadge = page.locator(".status-badge", {
    hasText: /Live|Connecting…|Reconnecting…/,
  });
  await expect(connectionBadge).toBeVisible();

  await expect(page.getByText(/0 \/ 1 attempts complete/)).toBeVisible();

  await page.getByRole("link", { name: "Open judge entry" }).click();
  await page.waitForURL("**/judge");
  await expect(page.getByRole("heading", { name: "Judge entry" })).toBeVisible();

  const timeInput = page.getByLabel(/Time in seconds for attempt 1/);
  await timeInput.fill("12.34");
  await page.getByRole("button", { name: "Save" }).first().click();
  await expect(page.getByText("Saved", { exact: false }).first()).toBeVisible();

  // ---- 10. Verify Tournament Control reflects the completed attempt ----
  await page.goBack();
  await page.waitForURL("**/control");
  await expect(page.getByText(/1 \/ 1 attempts complete/)).toBeVisible();
  const connectionBadgeAfter = page.locator(".status-badge", {
    hasText: /Live|Connecting…|Reconnecting…/,
  });
  await expect(connectionBadgeAfter).toBeVisible();

  // ---- 11. Review and close the round, then check standings ----
  const closeBtn = page.getByRole("button", { name: "Close round" });
  await expect(closeBtn).toBeEnabled();
  await closeBtn.click();
  const closeDialog = page.locator("dialog[open]");
  await expect(closeDialog).toBeVisible();
  await closeDialog.getByRole("button", { name: "Close round" }).click();
  await expect(closeDialog).toBeHidden();

  // The event page now shows the round as CLOSED with a standings link.
  await page.goto(page.url().replace(/\/events\/([a-f0-9-]+)\/control/, "/events/$1"));
  await expect(page.getByText("CLOSED")).toBeVisible();
  await page.getByRole("link", { name: "Final standings" }).click();
  await page.waitForURL("**/standings");

  await expect(page.getByRole("heading", { name: "Standings" })).toBeVisible();
  await expect(page.locator(".status-badge", { hasText: "Final" })).toBeVisible();
  await expect(page.getByRole("cell", { name: guestName })).toBeVisible();
  await expect(page.getByText("12.34", { exact: false }).first()).toBeVisible();

  await guestContext.close();
});
