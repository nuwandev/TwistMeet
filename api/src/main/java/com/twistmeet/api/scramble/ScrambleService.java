package com.twistmeet.api.scramble;

import com.twistmeet.api.common.ApiException;
import com.twistmeet.api.common.AuditService;
import com.twistmeet.api.competition.Attempt;
import com.twistmeet.api.competition.AttemptRepository;
import com.twistmeet.api.competition.Round;
import com.twistmeet.api.competition.RoundRepository;
import com.twistmeet.api.event.Event;
import com.twistmeet.api.event.EventRepository;
import com.twistmeet.api.org.TenantAccessService;
import com.twistmeet.api.registration.EventEntrant;
import com.twistmeet.api.registration.GuestAuthResolver;
import com.twistmeet.api.scramble.ScrambleDtos.AssignmentView;
import com.twistmeet.api.scramble.ScrambleDtos.BatchView;
import com.twistmeet.api.scramble.ScrambleDtos.PrintEntry;
import com.twistmeet.api.scramble.ScrambleDtos.RevealView;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 00 §7 scramble policy; 07 S07 scramble preparation station; 08 scramble routes. Orchestrates
 * generation (M4 scope only — never does anything with advancement, publishing, or export).
 */
@Service
public class ScrambleService {

  private final ScrambleBatchRepository batchRepository;
  private final ScrambleSecretRepository secretRepository;
  private final ScrambleAssignmentRepository assignmentRepository;
  private final RoundRepository roundRepository;
  private final AttemptRepository attemptRepository;
  private final EventRepository eventRepository;
  private final TenantAccessService tenantAccessService;
  private final AuditService auditService;
  private final ScrambleGenerator generator;
  private final ScrambleEncryptionService encryptionService;
  private final GuestAuthResolver guestAuthResolver;

  public ScrambleService(
      ScrambleBatchRepository batchRepository,
      ScrambleSecretRepository secretRepository,
      ScrambleAssignmentRepository assignmentRepository,
      RoundRepository roundRepository,
      AttemptRepository attemptRepository,
      EventRepository eventRepository,
      TenantAccessService tenantAccessService,
      AuditService auditService,
      ScrambleGenerator generator,
      ScrambleEncryptionService encryptionService,
      GuestAuthResolver guestAuthResolver) {
    this.batchRepository = batchRepository;
    this.secretRepository = secretRepository;
    this.assignmentRepository = assignmentRepository;
    this.roundRepository = roundRepository;
    this.attemptRepository = attemptRepository;
    this.eventRepository = eventRepository;
    this.tenantAccessService = tenantAccessService;
    this.auditService = auditService;
    this.generator = generator;
    this.encryptionService = encryptionService;
    this.guestAuthResolver = guestAuthResolver;
  }

  @Transactional
  public BatchView createBatch(UUID roundId, UUID actorUserId) {
    Round round = findRoundOrNotFound(roundId);
    Event event = findEventOrNotFound(round.getEventId());
    tenantAccessService.requireOrganizer(event, actorUserId);

    if (batchRepository.findByRoundId(roundId).isPresent()) {
      throw ApiException.conflict(
          "SCRAMBLE_BATCH_EXISTS", "A scramble batch already exists for this round");
    }
    List<Attempt> attempts = attemptRepository.findByRoundId(roundId);
    if (attempts.isEmpty()) {
      throw ApiException.badRequest(
          "ROUND_NOT_PREPARED",
          "Prepare the round (allocate attempts) before generating scrambles");
    }

    // 00 §7: "at least two extra sequences per round or 10% of attempts rounded up, whichever
    // is greater."
    int extraCount = Math.max(2, (int) Math.ceil(attempts.size() * 0.10));

    ScrambleBatch batch =
        batchRepository.save(
            new ScrambleBatch(
                roundId,
                ScrambleGenerator.GENERATOR_NAME,
                ScrambleGenerator.GENERATOR_VERSION,
                round.getRulesetVersion().name(),
                extraCount,
                actorUserId));

    for (Attempt attempt : attempts) {
      ScrambleSecret secret = encryptAndSave(batch.getId(), false);
      ScrambleAssignment assignment =
          assignmentRepository.save(
              new ScrambleAssignment(
                  batch.getId(),
                  secret.getId(),
                  roundId,
                  attempt.getId(),
                  attempt.getEntrantId(),
                  attempt.getAttemptNumber()));
      attempt.setScrambleAssignmentId(assignment.getId());
      attemptRepository.save(attempt);
    }
    for (int i = 0; i < extraCount; i++) {
      encryptAndSave(batch.getId(), true);
    }

    auditService.recordStaffAction(
        event.getOrganizationId(),
        event.getId(),
        actorUserId,
        "SCRAMBLE_BATCH_CREATED",
        "ScrambleBatch",
        batch.getId().toString(),
        null);
    return BatchView.of(batch, attempts.size());
  }

  public List<AssignmentView> listForRound(UUID roundId, UUID actorUserId) {
    Round round = findRoundOrNotFound(roundId);
    Event event = findEventOrNotFound(round.getEventId());
    tenantAccessService.requireScrambleStaff(event, actorUserId);
    return assignmentRepository.findByRoundId(roundId).stream()
        .sorted(Comparator.comparingInt(ScrambleAssignment::getAttemptNumber))
        .map(AssignmentView::of)
        .toList();
  }

  @Transactional
  public RevealView reveal(UUID assignmentId, UUID actorUserId) {
    ScrambleAssignment assignment = findAssignmentOrNotFound(assignmentId);
    Event event = eventForAssignment(assignment);
    tenantAccessService.requireAssignedScrambleStaff(event, actorUserId);
    if (assignment.getState() == ScrambleAssignmentState.VOIDED) {
      throw ApiException.invalidTransition("This scramble assignment has been voided");
    }
    boolean wasAlreadyRevealed = assignment.isRevealed();
    assignment.reveal(actorUserId);
    assignment = assignmentRepository.save(assignment);
    if (!wasAlreadyRevealed) {
      auditService.recordStaffAction(
          event.getOrganizationId(),
          event.getId(),
          actorUserId,
          "SCRAMBLE_REVEALED",
          "ScrambleAssignment",
          assignmentId.toString(),
          null);
    }
    return toRevealView(assignment);
  }

  /** Re-view after an initial reveal; every access is audited (00 §7), not only the first. */
  @Transactional
  public RevealView officialView(UUID assignmentId, UUID actorUserId) {
    ScrambleAssignment assignment = findAssignmentOrNotFound(assignmentId);
    Event event = eventForAssignment(assignment);
    tenantAccessService.requireAssignedScrambleStaff(event, actorUserId);
    if (!assignment.isRevealed()) {
      throw ApiException.scrambleNotAvailable("Reveal this assignment before viewing it");
    }
    auditService.recordStaffAction(
        event.getOrganizationId(),
        event.getId(),
        actorUserId,
        "SCRAMBLE_VIEWED",
        "ScrambleAssignment",
        assignmentId.toString(),
        null);
    return toRevealView(assignment);
  }

  @Transactional
  public AssignmentView markApplied(UUID assignmentId, UUID actorUserId) {
    ScrambleAssignment assignment = findAssignmentOrNotFound(assignmentId);
    Event event = eventForAssignment(assignment);
    tenantAccessService.requireScrambleStaff(event, actorUserId);
    if (assignment.getState() == ScrambleAssignmentState.VOIDED) {
      throw ApiException.invalidTransition("This scramble assignment has been voided");
    }
    if (!assignment.isRevealed()) {
      throw ApiException.invalidTransition("Reveal the scramble before marking it applied");
    }
    boolean already = assignment.getAppliedAt() != null;
    assignment.markApplied(actorUserId);
    assignment = assignmentRepository.save(assignment);
    if (!already) {
      auditService.recordStaffAction(
          event.getOrganizationId(),
          event.getId(),
          actorUserId,
          "SCRAMBLE_APPLIED",
          "ScrambleAssignment",
          assignmentId.toString(),
          null);
    }
    return AssignmentView.of(assignment);
  }

  @Transactional
  public AssignmentView markChecked(UUID assignmentId, UUID actorUserId, boolean independent) {
    ScrambleAssignment assignment = findAssignmentOrNotFound(assignmentId);
    Event event = eventForAssignment(assignment);
    tenantAccessService.requireScrambleStaff(event, actorUserId);
    if (assignment.getState() == ScrambleAssignmentState.VOIDED) {
      throw ApiException.invalidTransition("This scramble assignment has been voided");
    }
    if (assignment.getAppliedAt() == null) {
      throw ApiException.invalidTransition("Mark the scramble applied before marking it checked");
    }
    boolean alreadyChecked = assignment.getCheckedAt() != null;
    boolean alreadySecondChecked = assignment.getSecondCheckedAt() != null;
    assignment.markChecked(actorUserId, independent);
    assignment = assignmentRepository.save(assignment);
    String action =
        !alreadyChecked
            ? "SCRAMBLE_CHECKED"
            : (!alreadySecondChecked && assignment.getSecondCheckedAt() != null
                ? "SCRAMBLE_SECOND_CHECKED"
                : null);
    if (action != null) {
      auditService.recordStaffAction(
          event.getOrganizationId(),
          event.getId(),
          actorUserId,
          action,
          "ScrambleAssignment",
          assignmentId.toString(),
          null);
    }
    return AssignmentView.of(assignment);
  }

  @Transactional
  public AssignmentView spoil(UUID assignmentId, UUID actorUserId, String reason) {
    ScrambleAssignment assignment = findAssignmentOrNotFound(assignmentId);
    Event event = eventForAssignment(assignment);
    tenantAccessService.requireOrganizerOrJudge(event, actorUserId);
    if (assignment.getState() == ScrambleAssignmentState.VOIDED) {
      throw ApiException.invalidTransition("This scramble assignment has already been voided");
    }

    ScrambleSecret oldSecret = findSecretOrNotFound(assignment.getSecretId());
    oldSecret.markConsumed();
    secretRepository.save(oldSecret);
    assignment.voidAssignment(actorUserId, reason);
    assignment = assignmentRepository.save(assignment);

    assignReplacementSecret(
        assignment.getBatchId(),
        assignment.getRoundId(),
        assignment.getAttemptId(),
        assignment.getEntrantId(),
        assignment.getAttemptNumber());

    auditService.recordStaffAction(
        event.getOrganizationId(),
        event.getId(),
        actorUserId,
        "SCRAMBLE_SPOILED",
        "ScrambleAssignment",
        assignmentId.toString(),
        reason);
    return AssignmentView.of(assignment);
  }

  /**
   * Called by {@code CorrectionService} after it creates a brand-new replacement {@link Attempt}
   * (ACCEPT_RETRY). A no-op if the round has no scramble batch yet — not every event uses the M4
   * scramble module, and a replacement attempt without one is simply unassigned, same as before
   * this milestone.
   */
  @Transactional
  public void assignScrambleIfBatchExists(Attempt replacementAttempt) {
    batchRepository
        .findByRoundId(replacementAttempt.getRoundId())
        .ifPresent(
            batch ->
                assignReplacementSecret(
                    batch.getId(),
                    replacementAttempt.getRoundId(),
                    replacementAttempt.getId(),
                    replacementAttempt.getEntrantId(),
                    replacementAttempt.getAttemptNumber()));
  }

  private void assignReplacementSecret(
      UUID batchId, UUID roundId, UUID attemptId, UUID entrantId, int attemptNumber) {
    ScrambleSecret extra =
        secretRepository
            .findFirstByBatchIdAndExtraTrueAndConsumedFalse(batchId)
            .orElseThrow(
                () ->
                    ApiException.conflict(
                        "NO_EXTRA_SCRAMBLES_AVAILABLE",
                        "No unused extra scramble is available for a replacement"));
    extra.markConsumed();
    secretRepository.save(extra);
    ScrambleAssignment assignment =
        assignmentRepository.save(
            new ScrambleAssignment(
                batchId, extra.getId(), roundId, attemptId, entrantId, attemptNumber));
    attemptRepository
        .findById(attemptId)
        .ifPresent(
            attempt -> {
              attempt.setScrambleAssignmentId(assignment.getId());
              attemptRepository.save(attempt);
            });
  }

  /** 00 §7 "print only for officials." Printing exposes the notation, so it also reveals. */
  @Transactional
  public List<PrintEntry> print(UUID roundId, UUID actorUserId) {
    Round round = findRoundOrNotFound(roundId);
    Event event = findEventOrNotFound(round.getEventId());
    tenantAccessService.requireAssignedScrambleStaff(event, actorUserId);
    List<ScrambleAssignment> assignments =
        assignmentRepository.findByRoundId(roundId).stream()
            .filter(a -> a.getState() != ScrambleAssignmentState.VOIDED)
            .sorted(Comparator.comparingInt(ScrambleAssignment::getAttemptNumber))
            .toList();
    List<PrintEntry> entries = new java.util.ArrayList<>();
    boolean anyNewlyRevealed = false;
    for (ScrambleAssignment assignment : assignments) {
      boolean wasRevealed = assignment.isRevealed();
      assignment.reveal(actorUserId);
      if (!wasRevealed) {
        anyNewlyRevealed = true;
        assignmentRepository.save(assignment);
      }
      ScrambleSecret secret = findSecretOrNotFound(assignment.getSecretId());
      String notation = encryptionService.decrypt(secret.getCiphertext(), secret.getNonce());
      entries.add(new PrintEntry(assignment.getAttemptNumber(), notation));
    }
    if (anyNewlyRevealed) {
      auditService.recordStaffAction(
          event.getOrganizationId(),
          event.getId(),
          actorUserId,
          "SCRAMBLE_BATCH_PRINTED",
          "Round",
          roundId.toString(),
          null);
    }
    return entries;
  }

  /**
   * Self-scramble mode only (00 §7.2): the competitor's own current attempt, once "unlocked" —
   * defined here as this attempt being the lowest-numbered one for that entrant still PENDING (i.e.
   * it's their turn and they haven't solved it yet). No future attempt's sequence is ever reachable
   * through this method, regardless of what assignment ID is requested.
   */
  @Transactional
  public RevealView currentScrambleForGuest(UUID attemptId, HttpServletRequest request) {
    Attempt attempt =
        attemptRepository
            .findById(attemptId)
            .orElseThrow(() -> ApiException.notFound("Attempt not found"));
    Event event = findEventOrNotFound(attempt.getEventId());
    EventEntrant entrant = guestAuthResolver.requireEntrantForEvent(request, event.getId());
    if (!entrant.getId().equals(attempt.getEntrantId())) {
      throw ApiException.notFound("Attempt not found");
    }
    if (event.getScramblePolicy() != com.twistmeet.api.event.ScramblePolicy.SELF_SCRAMBLE) {
      throw ApiException.scrambleNotAvailable("This event does not use self-scramble mode");
    }
    boolean isUnlocked =
        attemptRepository.findByRoundIdAndEntrantId(attempt.getRoundId(), entrant.getId()).stream()
            .filter(a -> a.getResultStatus() == com.twistmeet.api.competition.ResultStatus.PENDING)
            .min(Comparator.comparingInt(Attempt::getAttemptNumber))
            .map(a -> a.getId().equals(attemptId))
            .orElse(false);
    if (!isUnlocked) {
      throw ApiException.scrambleNotAvailable(
          "This attempt's scramble is not unlocked yet — finish your current attempt first");
    }
    ScrambleAssignment assignment =
        assignmentRepository
            .findByAttemptIdAndStateNot(attemptId, ScrambleAssignmentState.VOIDED)
            .orElseThrow(
                () -> ApiException.scrambleNotAvailable("No scramble has been generated yet"));
    boolean wasRevealed = assignment.isRevealed();
    assignment.reveal(entrant.getId());
    assignment = assignmentRepository.save(assignment);
    if (!wasRevealed) {
      auditService.recordGuestAction(
          event.getId(),
          entrant.getId(),
          "SCRAMBLE_SELF_REVEALED",
          "ScrambleAssignment",
          assignment.getId().toString());
    }
    return toRevealView(assignment);
  }

  private RevealView toRevealView(ScrambleAssignment assignment) {
    ScrambleSecret secret = findSecretOrNotFound(assignment.getSecretId());
    String notation = encryptionService.decrypt(secret.getCiphertext(), secret.getNonce());
    return new RevealView(
        assignment.getId(),
        notation,
        "3x3x3",
        assignment.getRoundId(),
        assignment.getAttemptNumber(),
        assignment.getRevealedAt());
  }

  private ScrambleSecret encryptAndSave(UUID batchId, boolean extra) {
    ScrambleEncryptionService.Encrypted encrypted = encryptionService.encrypt(generator.generate());
    return secretRepository.save(
        new ScrambleSecret(batchId, encrypted.ciphertextBase64(), encrypted.nonceBase64(), extra));
  }

  private Round findRoundOrNotFound(UUID roundId) {
    return roundRepository
        .findById(roundId)
        .orElseThrow(() -> ApiException.notFound("Round not found"));
  }

  private Event findEventOrNotFound(UUID eventId) {
    return eventRepository
        .findById(eventId)
        .orElseThrow(() -> ApiException.notFound("Event not found"));
  }

  private ScrambleAssignment findAssignmentOrNotFound(UUID id) {
    return assignmentRepository
        .findById(id)
        .orElseThrow(() -> ApiException.notFound("Scramble assignment not found"));
  }

  private ScrambleSecret findSecretOrNotFound(UUID id) {
    return secretRepository
        .findById(id)
        .orElseThrow(() -> ApiException.notFound("Scramble secret not found"));
  }

  private Event eventForAssignment(ScrambleAssignment assignment) {
    Round round = findRoundOrNotFound(assignment.getRoundId());
    return findEventOrNotFound(round.getEventId());
  }
}
