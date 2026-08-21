package io.akka.omnigent.domain;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Registered policy callables, keyed by the {@link PolicySpec#builtin} name — the Java analogue
 * of the source's {@code omnigent.policies.builtins} modules (POLICIES.md "Builtin policies").
 * Only the deterministic, LLM-free callables needed to demonstrate composition are ported
 * (SPEC-001 §1); each mirrors one source builtin's shape rather than its exact parameters.
 */
public final class BuiltinPolicies {

  private BuiltinPolicies() {}

  /** {@code max_tool_calls_per_session} (POLICIES.md "Safety"): DENY once a counter, tracked
   * via {@code StateUpdate.INCREMENT} on {@code call_count}, reaches {@code limit}. */
  public static final String MAX_TOOL_CALLS_PER_SESSION = "max_tool_calls_per_session";

  /** {@code ask_on_os_tools}-shaped: ASK before any call to a configured tool. */
  public static final String ASK_ON_TOOL = "ask_on_tool";

  /** A DENY-only gate on a configured tool name, standing in for {@code block_skills} /
   * a narrow {@code github_policy} write-repo denial. */
  public static final String DENY_TOOL = "deny_tool";

  /** Writes a fixed label value on a matching TOOL_RESULT, standing in for the source's
   * "taint on unsafe read" pattern used by the risk-score / PII builtins. */
  public static final String TAINT_LABEL_ON_RESULT = "taint_label_on_result";

  /** Uppercases string content and returns it as {@code data} — a minimal stand-in for the
   * source's content-transforming builtins (e.g. PII redaction), used to demonstrate that
   * {@code data} chains sequentially across policies (POLICIES.md §4). */
  public static final String UPPERCASE = "upper";

  /** Reverses string content and returns it as {@code data} — paired with {@link #UPPERCASE}
   * in the sequential-chaining conformance test. */
  public static final String REVERSE = "reverse";

  private static final Map<String, BiFunction<PolicyEvent, Map<String, Object>, PolicyResponse>>
      REGISTRY =
          Map.of(
              MAX_TOOL_CALLS_PER_SESSION, BuiltinPolicies::maxToolCallsPerSession,
              ASK_ON_TOOL, BuiltinPolicies::askOnTool,
              DENY_TOOL, BuiltinPolicies::denyTool,
              TAINT_LABEL_ON_RESULT, BuiltinPolicies::taintLabelOnResult,
              UPPERCASE, (event, config) -> new PolicyResponse(
                  PolicyAction.ALLOW, null, null, null, ((String) event.content()).toUpperCase()),
              REVERSE, (event, config) -> new PolicyResponse(
                  PolicyAction.ALLOW, null, null, null,
                  new StringBuilder((String) event.content()).reverse().toString()));

  public static PolicyResponse evaluate(String builtin, PolicyEvent event, Map<String, Object> config) {
    var fn = REGISTRY.get(builtin);
    if (fn == null) {
      throw new IllegalArgumentException("no such builtin policy: " + builtin);
    }
    return fn.apply(event, config);
  }

  private static PolicyResponse maxToolCallsPerSession(PolicyEvent event, Map<String, Object> config) {
    long limit = ((Number) config.getOrDefault("limit", 100)).longValue();
    long current = ((Number) event.sessionState().getOrDefault("call_count", 0)).longValue();
    if (current >= limit) {
      return PolicyResponse.deny("tool-call limit of " + limit + " reached");
    }
    return new PolicyResponse(
        PolicyAction.ALLOW,
        null,
        null,
        List.of(new StateUpdate("call_count", StateUpdateAction.INCREMENT, 1L)),
        null);
  }

  private static PolicyResponse askOnTool(PolicyEvent event, Map<String, Object> config) {
    String tool = (String) config.get("tool_name");
    if (tool != null && tool.equals(event.toolName())) {
      return PolicyResponse.ask("approval required for " + tool);
    }
    return PolicyResponse.allow();
  }

  private static PolicyResponse denyTool(PolicyEvent event, Map<String, Object> config) {
    String tool = (String) config.get("tool_name");
    if (tool != null && tool.equals(event.toolName())) {
      return PolicyResponse.deny(tool + " is blocked");
    }
    return PolicyResponse.allow();
  }

  @SuppressWarnings("unchecked")
  private static PolicyResponse taintLabelOnResult(PolicyEvent event, Map<String, Object> config) {
    String tool = (String) config.get("tool_name");
    if (tool != null && !tool.equals(event.toolName())) {
      return PolicyResponse.allow();
    }
    Map<String, String> labels = (Map<String, String>) (Map<String, ?>) config.get("set_labels");
    return new PolicyResponse(PolicyAction.ALLOW, null, labels, null, null);
  }
}
