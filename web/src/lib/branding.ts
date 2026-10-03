/**
 * Product name/branding is explicitly unresolved (DECISIONS.md OD02 — "TwistMeet" is a
 * provisional working name, not a cleared brand). Every user-visible rendering of the product
 * name must go through this single value, driven by an environment variable, so a future rename
 * never requires touching page/component code — only this default (or the env var) changes.
 */
export const APP_NAME =
  process.env.NEXT_PUBLIC_APP_NAME?.trim() || "TwistMeet (working name)";
