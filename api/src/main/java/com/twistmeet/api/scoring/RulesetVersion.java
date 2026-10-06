package com.twistmeet.api.scoring;

/**
 * Versioned ruleset identifier (09: "Inputs are a ruleset version and ordered raw attempt
 * values/statuses"). There is only one ruleset today; the enum exists so a future rule change ships
 * as a new version rather than silently altering V1's behavior for events that already snapshotted
 * it (00 §4: "In-app rules text is versioned and snapshotted into the event").
 */
public enum RulesetVersion {
  V1
}
