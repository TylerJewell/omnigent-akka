package io.akka.omnigent.domain;

import java.util.List;
import java.util.Map;

/**
 * What a single builtin policy callable returns — the Java analogue of the source's {@code
 * PolicyResponse} dict (POLICIES.md "Writing custom policies"). {@code null} means "abstain":
 * the engine treats an abstaining policy as though it never ran (question-log evidence:
 * {@code on: null} self-selection in the source; this port's builtins always match their
 * declared {@link PhaseSelector}s instead, so abstention here is reserved for a builtin that
 * matched the phase/tool but chooses not to opine — kept for API symmetry with the source).
 */
public record PolicyResponse(
    PolicyAction result,
    String reason,
    Map<String, String> setLabels,
    List<StateUpdate> stateUpdates,
    Object data) {

  public static PolicyResponse allow() {
    return new PolicyResponse(PolicyAction.ALLOW, null, null, null, null);
  }

  public static PolicyResponse deny(String reason) {
    return new PolicyResponse(PolicyAction.DENY, reason, null, null, null);
  }

  public static PolicyResponse ask(String reason) {
    return new PolicyResponse(PolicyAction.ASK, reason, null, null, null);
  }
}
