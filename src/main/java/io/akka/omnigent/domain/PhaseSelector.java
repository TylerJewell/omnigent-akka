package io.akka.omnigent.domain;

/**
 * Which phase (and, optionally, which single tool name) a policy fires on — SPEC-001 §2. A
 * {@code null} {@code toolName} matches every tool on that phase.
 */
public record PhaseSelector(Phase phase, String toolName) {

  public static PhaseSelector of(Phase phase) {
    return new PhaseSelector(phase, null);
  }

  public boolean matches(Phase actualPhase, String actualToolName) {
    if (phase != actualPhase) {
      return false;
    }
    return toolName == null || toolName.equals(actualToolName);
  }
}
