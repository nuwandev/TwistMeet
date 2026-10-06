package com.twistmeet.api.scoring;

/**
 * One entrant's position in a ranked round: {@code rank} uses competition ranking (1, 2, 2, 4 —
 * tied entrants share a rank and the next distinct rank skips ahead by the number of entrants tied
 * above it).
 */
public record RankedEntry<T>(T id, int rank, RoundResult result) {}
