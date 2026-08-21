package io.akka.omnigent.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The composed policy-decision procedure, SPEC-001 §3, against the conformance table. Each test
 * name maps to one conformance-table row; the mirrored source test (question-log) is named in
 * the Javadoc.
 */
class PolicyEngineTest {

  private static final EvaluationContext REQUEST = new EvaluationContext(Phase.REQUEST, null, "hi", null);

  private static PolicySpec fixed(String name, Phase phase, String tool, String builtin, Map<String, Object> config) {
    return new PolicySpec(name, List.of(new PhaseSelector(phase, tool)), null, null, builtin, config);
  }

  /** Mirrors {@code test_evaluate_allows_with_zero_policies}. */
  @Test
  void emptyPolicyListAllowsEveryPhase() {
    for (Phase phase : Phase.values()) {
      var ctx = new EvaluationContext(phase, null, "x", null);
      var result = PolicyEngine.evaluate(List.of(), Map.of(), Map.of(), Map.of(), ctx);
      assertThat(result.action()).isEqualTo(PolicyAction.ALLOW);
      assertThat(result.reason()).isNull();
    }
  }

  /** Mirrors {@code test_tool_call_deny_by_tool_name}. */
  @Test
  void denyShortCircuitsRemainingPolicies() {
    var noShell = fixed("no_shell", Phase.TOOL_CALL, "run_shell", BuiltinPolicies.DENY_TOOL, Map.of("tool_name", "run_shell"));
    var counter = fixed("counter", Phase.TOOL_CALL, null, BuiltinPolicies.MAX_TOOL_CALLS_PER_SESSION, Map.of("limit", 100));
    var policies = List.of(noShell, counter);

    var deny = PolicyEngine.evaluate(
        policies, Map.of(), Map.of(), Map.of(),
        new EvaluationContext(Phase.TOOL_CALL, "run_shell", Map.of("cmd", "ls"), null));
    assertThat(deny.action()).isEqualTo(PolicyAction.DENY);
    assertThat(deny.decidingPolicies()).containsExactly("no_shell");
    // The counter policy never ran, so it wrote no state update.
    assertThat(deny.stateUpdates()).isNull();

    var allow = PolicyEngine.evaluate(
        policies, Map.of(), Map.of(), Map.of(),
        new EvaluationContext(Phase.TOOL_CALL, "web_search", Map.of("q", "x"), null));
    assertThat(allow.action()).isEqualTo(PolicyAction.ALLOW);
  }

  /** Mirrors {@code test_tool_call_ask_withholds_labels}. */
  @Test
  void askWithholdsLabelWrites() {
    var confirmShell =
        fixed(
            "confirm_shell",
            Phase.TOOL_CALL,
            "run_shell",
            BuiltinPolicies.ASK_ON_TOOL,
            Map.of("tool_name", "run_shell"));
    var result = PolicyEngine.evaluate(
        List.of(confirmShell), Map.of(), Map.of(), Map.of(),
        new EvaluationContext(Phase.TOOL_CALL, "run_shell", Map.of("cmd", "ls"), null));
    assertThat(result.action()).isEqualTo(PolicyAction.ASK);
    assertThat(result.decidingPolicies()).containsExactly("confirm_shell");
    // The engine reports what WOULD be written; the entity (not the engine) is what
    // withholds persistence — SPEC-001 §3 rule 5.
    var state = HarnessSessionState.initial();
    assertThat(state.eventsFor(result)).isEmpty();
  }

  /** Mirrors the DENY case in {@code test_engine_session_state.py}: writes accumulated by
   * earlier ALLOWing policies still land alongside a later DENY. */
  @Test
  void denyAppliesAccumulatedWrites() {
    var taint =
        fixed(
            "taint",
            Phase.TOOL_RESULT,
            "web_search",
            BuiltinPolicies.TAINT_LABEL_ON_RESULT,
            Map.of("tool_name", "web_search", "set_labels", Map.of("integrity", "0")));
    var block =
        fixed("block_after", Phase.TOOL_RESULT, "web_search", BuiltinPolicies.DENY_TOOL, Map.of("tool_name", "web_search"));
    var result = PolicyEngine.evaluate(
        List.of(taint, block), Map.of(), Map.of(), Map.of(),
        new EvaluationContext(Phase.TOOL_RESULT, "web_search", "results", null));
    assertThat(result.action()).isEqualTo(PolicyAction.DENY);
    assertThat(result.setLabels()).containsEntry("integrity", "0");
  }

  /** Mirrors {@code test_label_validation.py} (all four cases collapsed into one). */
  @Test
  void labelEnumViolationDropsSilentlyPerKey() {
    var labelDefs = Map.of("integrity", new LabelDef(List.of("0", "1")));
    var filtered = PolicyEngine.filterSchemaValid(Map.of("integrity", "2", "other", "x"), labelDefs);
    assertThat(filtered).containsEntry("other", "x").doesNotContainKey("integrity");

    var valid = PolicyEngine.filterSchemaValid(Map.of("integrity", "1"), labelDefs);
    assertThat(valid).containsEntry("integrity", "1");

    var schemaless = PolicyEngine.filterSchemaValid(Map.of("anything", "123"), Map.of());
    assertThat(schemaless).containsEntry("anything", "123");
  }

  /** Mirrors the sequential-chaining assertion in POLICIES.md §4 (row 6): each transforming
   * policy sees the PREVIOUS policy's output, not the original content. */
  @Test
  void dataTransformsChainAcrossPolicies() {
    var upper = new PolicySpec("upper", List.of(PhaseSelector.of(Phase.REQUEST)), null, null, "upper", Map.of());
    var reverse = new PolicySpec("reverse", List.of(PhaseSelector.of(Phase.REQUEST)), null, null, "reverse", Map.of());
    var result = PolicyEngine.evaluate(List.of(upper, reverse), Map.of(), Map.of(), Map.of(), REQUEST);
    // "hi" -> upper -> "HI" -> reverse -> "IH"
    assertThat(result.data()).isEqualTo("IH");
  }

  /** Mirrors {@code _condition_matches}: a condition key absent from the label snapshot never
   * matches, the same as a mismatched value. */
  @Test
  void conditionOnAbsentLabelNeverFires() {
    var gated =
        new PolicySpec(
            "gated",
            List.of(PhaseSelector.of(Phase.REQUEST)),
            Map.of("cost_control.plan", "restricted"),
            null,
            BuiltinPolicies.DENY_TOOL,
            Map.of());
    var result = PolicyEngine.evaluate(List.of(gated), Map.of(), Map.of(), Map.of(), REQUEST);
    assertThat(result.action()).isEqualTo(PolicyAction.ALLOW);
  }

  /** Mirrors the engine's fail-closed contract (question-log row 7): a broken policy becomes a
   * DENY, never a propagated exception. */
  @Test
  void throwingPolicyFailsClosed() {
    var broken =
        new PolicySpec(
            "broken",
            List.of(PhaseSelector.of(Phase.REQUEST)),
            null,
            null,
            "does-not-exist",
            Map.of());
    var result = PolicyEngine.evaluate(List.of(broken), Map.of(), Map.of(), Map.of(), REQUEST);
    assertThat(result.action()).isEqualTo(PolicyAction.DENY);
    assertThat(result.reason()).contains("broken");
  }
}
