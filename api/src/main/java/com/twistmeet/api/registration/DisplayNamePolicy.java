package com.twistmeet.api.registration;

import com.twistmeet.api.common.ApiException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Minimal display-name hygiene for guest join (07 P02: "trimmed, 1-32 Unicode grapheme clusters, no
 * control chars/markup"). The full configurable profanity/reserved-word filter is DECISIONS.md OD08
 * (unresolved) — this only rejects control characters and markup-like characters as a safety floor,
 * it is not a content policy.
 */
public final class DisplayNamePolicy {

  private DisplayNamePolicy() {}

  public static String normalize(String rawDisplayName) {
    String trimmed = rawDisplayName == null ? "" : rawDisplayName.trim();
    if (trimmed.isEmpty() || trimmed.length() > 32) {
      throw ApiException.badRequest("INVALID_DISPLAY_NAME", "Display name must be 1-32 characters");
    }
    for (int i = 0; i < trimmed.length(); i++) {
      char c = trimmed.charAt(i);
      if (Character.isISOControl(c) || c == '<' || c == '>') {
        throw ApiException.badRequest(
            "INVALID_DISPLAY_NAME", "Display name contains disallowed characters");
      }
    }
    return trimmed;
  }

  /**
   * 07 P02: "Duplicate display name shows an inline suggestion `Nuwan (2)`... permits deliberate
   * reuse after disambiguation." Shared by guest join and staff manual-add, which must behave
   * identically (08: "Manual add creates event guest with same limited data as QR join" — S05).
   */
  public static String disambiguate(List<EventEntrant> existingEntrants, String displayName) {
    Set<String> taken =
        existingEntrants.stream().map(EventEntrant::getDisplayName).collect(Collectors.toSet());
    if (!taken.contains(displayName)) {
      return displayName;
    }
    int suffix = 2;
    String candidate;
    do {
      candidate = displayName + " (" + suffix + ")";
      suffix++;
    } while (taken.contains(candidate));
    return candidate;
  }
}
