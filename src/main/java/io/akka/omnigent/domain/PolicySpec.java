package io.akka.omnigent.domain;

import java.util.List;
import java.util.Map;

/**
 * A declared policy attached to a session — SPEC-001 §2. {@code on} is the phase-selector gate;
 * {@code condition} is the label-condition gate (AND across keys, OR within a list value, an
 * absent label never matches — SPEC-001 §3 rule 2); {@code setLabelsWhitelist}, when non-null,
 * restricts which label keys this policy's writes may touch (unmatched keys are dropped by the
 * engine before accumulation).
 *
 * <p>{@code builtin} names an entry in {@link BuiltinPolicies}; {@code config} is the
 * per-attachment parameter map passed to it, mirroring the source's {@code factory_params}.
 */
public record PolicySpec(
    String name,
    List<PhaseSelector> on,
    Map<String, Object> condition,
    List<String> setLabelsWhitelist,
    String builtin,
    Map<String, Object> config) {

  public boolean matchesPhase(Phase phase, String toolName) {
    // Self-selecting policies (source's `on: null`) are out of scope (SPEC-001 §1) — every
    // builtin here always declares its own selectors, so a null `on` never matches rather
    // than throwing on a malformed attach request.
    if (on == null) {
      return false;
    }
    for (PhaseSelector selector : on) {
      if (selector.matches(phase, toolName)) {
        return true;
      }
    }
    return false;
  }

  public boolean matchesCondition(Map<String, String> labels) {
    if (condition == null) {
      return true;
    }
    for (var entry : condition.entrySet()) {
      String actual = labels.get(entry.getKey());
      if (actual == null) {
        return false;
      }
      Object expected = entry.getValue();
      if (expected instanceof List<?> options) {
        if (!options.contains(actual)) {
          return false;
        }
      } else if (!actual.equals(expected)) {
        return false;
      }
    }
    return true;
  }
}
