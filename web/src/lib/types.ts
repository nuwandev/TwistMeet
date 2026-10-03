export type UserView = {
  id: string;
  email: string;
  displayName: string;
  emailVerified: boolean;
  createdAt: string;
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
  createdAt: string;
  version: number;
};

export type EntrantView = {
  id: string;
  displayName: string;
  status: "ACTIVE" | "WITHDRAWN";
  joinedAt: string;
};

export type JoinResponse = {
  eventId: string;
  eventName: string;
  entrant: EntrantView;
};
