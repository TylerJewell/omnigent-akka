package io.akka.omnigent.domain;

import java.util.List;
import java.util.Map;

/**
 * One policy's decision, or the engine's composed decision across a whole pass — SPEC-001 §2.
 * {@code decidingPolicies} is set only on an engine-composed result (a single builtin's own
 * {@link PolicyResponse} carries no such field).
 */
public record PolicyResult(
    PolicyAction action,
    String reason,
    Map<String, String> setLabels,
    List<StateUpdate> stateUpdates,
    Object data,
    List<String> decidingPolicies) {

  public static PolicyResult allow() {
    return new PolicyResult(PolicyAction.ALLOW, null, null, null, null, null);
  }
}
