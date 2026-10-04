package com.twistmeet.api.scramble;

import org.springframework.stereotype.Service;
import org.worldcubeassociation.tnoodle.scrambles.Puzzle;
import org.worldcubeassociation.tnoodle.scrambles.PuzzleRegistry;

/**
 * Wraps the official WCA scramble program (OD01 in DECISIONS.md — org.worldcubeassociation.tnoodle,
 * GPL-3.0) behind this one class, so the GPL dependency's surface in this codebase is exactly here
 * and nowhere else. {@link PuzzleRegistry#THREE} is TNoodle's 3x3x3 puzzle (random-state scrambling
 * per WCA Regulation 4b3, not random moves — see DECISIONS.md).
 */
@Service
public class ScrambleGenerator {

  public static final String GENERATOR_NAME = "org.worldcubeassociation.tnoodle:lib-scrambles";
  public static final String GENERATOR_VERSION = "0.20.0";

  private final Puzzle puzzle = PuzzleRegistry.THREE.getScrambler();

  /**
   * One WCA-compliant random-state 3x3x3 scramble, in standard notation (e.g. "R U R' U2 F...").
   */
  public String generate() {
    return puzzle.generateScramble();
  }
}
