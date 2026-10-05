export type UserView = {
  id: string;
  email: string;
  displayName: string;
  emailVerified: boolean;
  createdAt: string;
};

// Deliberately generic — identical whether or not the email was already registered.
export type RegistrationAccepted = {
  email: string;
  message: string;
};

export type OrganizationView = {
  id: string;
  name: string;
  slug: string;
  defaultTimezone: string;
  createdAt: string;
  version: number;
};

export type EventState =
  | "DRAFT"
  | "REGISTRATION_OPEN"
  | "REGISTRATION_LOCKED"
  | "READY"
  | "LIVE"
  | "COMPLETED"
  | "ARCHIVED";

export type EventView = {
  id: string;
  organizationId: string;
  name: string;
  description: string | null;
  startsAt: string;
  timezone: string;
  venueLabel: string | null;
  visibility: "PRIVATE" | "PUBLIC";
  state: EventState;
  puzzleType: string;
  timerMode: "PHYSICAL_JUDGE" | "PHONE_CASUAL";
  scramblePolicy: "STAFF_PREPARED" | "SELF_SCRAMBLE";
  joinCode: string | null;
  rulesetSnapshot: string | null;
  publishedAt: string | null;
  publicSlug: string | null;
  createdAt: string;
  version: number;
};

export type EntrantView = {
  id: string;
  displayName: string;
  status: "ACTIVE" | "WITHDRAWN";
  checkInState: "NOT_CHECKED_IN" | "CHECKED_IN";
  joinedAt: string;
  version: number;
};

export type MyRole = "OWNER" | "ORGANIZER" | "JUDGE";

export type JoinResponse = {
  eventId: string;
  eventName: string;
  entrant: EntrantView;
};

export type RoundFormat = "BO1" | "BO2" | "BO3" | "MO3" | "AO5";
export type RoundState = "DRAFT" | "PREPARING" | "READY" | "LIVE" | "REVIEW" | "CLOSED";
export type AdvancementRule = "EVERYONE" | "TOP_N" | "TOP_PERCENT";

export type RoundView = {
  id: string;
  eventId: string;
  order: number;
  name: string;
  format: RoundFormat;
  attemptCount: number;
  advancementRule: AdvancementRule;
  advancementValue: number | null;
  tiePolicy: "SHARED_RANK" | "TIE_BREAK_ATTEMPT";
  state: RoundState;
  paused: boolean;
  rulesetVersion: string;
  createdAt: string;
  startedAt: string | null;
  version: number;
};

export type ResultSource = "JUDGE" | "SELF_TIMED";
export type AttemptState =
  | "PENDING"
  | "RUNNING"
  | "STOPPED"
  | "SUBMITTED"
  | "ACCEPTED"
  | "VOIDED";
export type ResultStatus = "PENDING" | "OK" | "DNF" | "DNS" | "VOID";
export type Penalty = "NONE" | "PLUS_TWO";

export type AttemptView = {
  id: string;
  eventId: string;
  roundId: string;
  entrantId: string;
  attemptNumber: number;
  resultSource: ResultSource;
  state: AttemptState;
  rawTimeMs: number | null;
  penalty: Penalty;
  adjustedTimeMs: number | null;
  resultStatus: ResultStatus;
  startedAt: string | null;
  stoppedAt: string | null;
  submittedAt: string | null;
  version: number;
};

export type ResultRevisionView = {
  id: string;
  previousRawTimeMs: number | null;
  previousPenalty: Penalty | null;
  previousResultStatus: ResultStatus | null;
  newRawTimeMs: number | null;
  newPenalty: Penalty;
  newResultStatus: ResultStatus;
  note: string | null;
  createdAt: string;
};

export type CorrectionCategory = "TIMER_OR_ENTRY_ISSUE" | "SCRAMBLE_CONCERN" | "INTERRUPTION" | "OTHER";
export type CorrectionState = "PENDING" | "DECIDED";
export type CorrectionDecision = "ACCEPT_NO_RETRY" | "ACCEPT_RETRY" | "REJECT" | "NEED_INFO";

export type CorrectionView = {
  id: string;
  attemptId: string;
  requestedBy: string;
  category: CorrectionCategory;
  note: string | null;
  state: CorrectionState;
  decision: CorrectionDecision | null;
  decidedBy: string | null;
  decisionReason: string | null;
  createdAt: string;
  decidedAt: string | null;
  version: number;
};

export type EntrantStanding = {
  entrantId: string;
  displayName: string;
  rank: number;
  outcome: "OK" | "DNF" | "NO_RESULT";
  displayMs: number | null;
  bestValidSingleMs: number | null;
  discardedAttemptNumbers: number[];
};

export type ScrambleAssignmentState = "ASSIGNED" | "REVEALED" | "APPLIED" | "CHECKED" | "VOIDED";

export type ScrambleBatchView = {
  id: string;
  roundId: string;
  puzzleType: string;
  generatorName: string;
  generatorVersion: string;
  rulesetVersion: string;
  attemptCount: number;
  extraCount: number;
  createdAt: string;
};

export type ScrambleAssignmentView = {
  id: string;
  roundId: string;
  attemptId: string;
  attemptNumber: number;
  state: ScrambleAssignmentState;
  revealedAt: string | null;
  appliedAt: string | null;
  appliedBy: string | null;
  checkedAt: string | null;
  checkedBy: string | null;
  secondCheckedAt: string | null;
  secondCheckedBy: string | null;
  version: number;
};

/** Only ever returned by reveal/official-view/current-scramble/print — never by a list endpoint. */
export type ScrambleRevealView = {
  assignmentId: string;
  notation: string;
  puzzleType: string;
  roundId: string;
  attemptNumber: number;
  revealedAt: string;
};

export type StandingsView = {
  roundId: string;
  provisional: boolean;
  rulesetVersion: string;
  standings: EntrantStanding[];
};

export type AdvancedEntrant = { entrantId: string; displayName: string; rank: number };

export type AdvancementPreviewView = {
  roundId: string;
  nextRoundId: string | null;
  advancementRule: AdvancementRule;
  advancementValue: number | null;
  eligibleCount: number;
  targetCount: number;
  advancing: AdvancedEntrant[];
  tieNote: string;
  alreadyCommitted: boolean;
  tieBreakRequired: boolean;
  tiedPendingResolution: AdvancedEntrant[];
};

export type AdvancementCommitView = {
  roundId: string;
  nextRoundId: string | null;
  eligibleCount: number;
  advancedCount: number;
  advancing: AdvancedEntrant[];
  tieNote: string;
  idempotentReplay: boolean;
};

export type EventHistorySummary = {
  eventId: string;
  name: string;
  state: EventState;
  startsAt: string;
  entrantCount: number;
  roundCount: number;
};

export type PublicRoundSummary = { roundId: string; order: number; name: string; state: string };

export type PublicEventView = {
  eventId: string;
  name: string;
  venueLabel: string | null;
  startsAt: string;
  timezone: string;
  rounds: PublicRoundSummary[];
};

export type PublicEntrantStanding = {
  displayName: string;
  rank: number;
  outcome: string;
  displayMs: number | null;
  bestValidSingleMs: number | null;
  completedAttempts: number;
  totalAttempts: number;
};

export type HelpRequestView = {
  id: string;
  attemptId: string;
  entrantId: string;
  state: "PENDING" | "RESOLVED";
  createdAt: string;
  resolvedAt: string | null;
  resolvedBy: string | null;
};

export type PublicStandingsView = {
  roundId: string;
  provisional: boolean;
  standings: PublicEntrantStanding[];
};
