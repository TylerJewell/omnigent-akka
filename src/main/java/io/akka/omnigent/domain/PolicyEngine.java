package io.akka.omnigent.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The composed policy-decision procedure — SPEC-001 §3. A pure function of (policy list, label
 * schema, current label/state snapshot, one event): filter (phase + condition gates), dispatch
 * each matching policy in declaration order, and compose — DENY short-circuits, ASK accumulates
 * and withholds every write, ALLOW applies everything accumulated so far. Mirrors the source's
 * {@code PolicyEngine._evaluate_composed} (omnigent/runtime/policies/engine.py).
 *
 * <p>Deliberately stateless and static: the caller ({@link
 * io.akka.omnigent.application.HarnessSessionEntity}) owns the durable label/state cache and
 * applies the writes this returns — the engine only decides what {@code should} be written,
 * matching the source's own split between the pure {@code Policy.evaluate} and the engine's
 * write-through side effects.
 */
public final class PolicyEngine {

  private PolicyEngine() {}

  public static PolicyResult evaluate(
      List<PolicySpec> policies,
      Map<String, LabelDef> labelDefs,
      Map<String, String> labels,
      Map<String, Object> sessionState,
      EvaluationContext ctx) {

    Map<String, String> accumulatedLabels = new HashMap<>();
    List<StateUpdate> accumulatedState = new ArrayList<>();
    List<String> askPolicies = new ArrayList<>();
    List<String> askReasons = new ArrayList<>();
    Object composedData = null;
    Object currentContent = ctx.content();

    for (PolicySpec policy : policies) {
      if (!policy.matchesPhase(ctx.phase(), ctx.toolName())) {
        continue;
      }
      if (!policy.matchesCondition(labels)) {
        continue;
      }

      PolicyResponse response = dispatch(policy, ctx, labels, sessionState, currentContent);

      Map<String, String> filtered = filterWritable(response.setLabels(), policy.setLabelsWhitelist());
      if (filtered != null) {
        accumulatedLabels.putAll(filtered);
      }
      if (response.stateUpdates() != null) {
        accumulatedState.addAll(response.stateUpdates());
      }

      if (response.result() == PolicyAction.DENY) {
        return new PolicyResult(
            PolicyAction.DENY,
            response.reason(),
            accumulatedLabels.isEmpty() ? null : Map.copyOf(accumulatedLabels),
            accumulatedState.isEmpty() ? null : List.copyOf(accumulatedState),
            null,
            List.of(policy.name()));
      }
      if (response.data() != null) {
        composedData = response.data();
        currentContent = composedData;
      }
      if (response.result() == PolicyAction.ASK) {
        askPolicies.add(policy.name());
        askReasons.add(policy.name() + ": " + (response.reason() == null ? "approval required" : response.reason()));
      }
    }

    if (!askPolicies.isEmpty()) {
      return new PolicyResult(
          PolicyAction.ASK,
          String.join("; ", askReasons),
          accumulatedLabels.isEmpty() ? null : Map.copyOf(accumulatedLabels),
          accumulatedState.isEmpty() ? null : List.copyOf(accumulatedState),
          composedData,
          List.copyOf(askPolicies));
    }

    return new PolicyResult(
        PolicyAction.ALLOW,
        null,
        accumulatedLabels.isEmpty() ? null : Map.copyOf(accumulatedLabels),
        accumulatedState.isEmpty() ? null : List.copyOf(accumulatedState),
        composedData,
        null);
  }

  private static PolicyResponse dispatch(
      PolicySpec policy,
      EvaluationContext ctx,
      Map<String, String> labels,
      Map<String, Object> sessionState,
      Object content) {
    var event =
        new PolicyEvent(ctx.phase(), ctx.toolName(), content, ctx.harness(), labels, sessionState);
    try {
      return BuiltinPolicies.evaluate(policy.builtin(), event, policy.config());
    } catch (RuntimeException exc) {
      // Fail-closed (SPEC-001 §3 rule 10): a broken policy callable becomes a DENY, never
      // a propagated exception.
      return PolicyResponse.deny("policy '" + policy.name() + "' failed: " + exc.getMessage());
    }
  }

  private static Map<String, String> filterWritable(Map<String, String> setLabels, List<String> whitelist) {
    if (setLabels == null || setLabels.isEmpty()) {
      return null;
    }
    if (whitelist == null) {
      return setLabels;
    }
    Map<String, String> filtered = new HashMap<>();
    for (var entry : setLabels.entrySet()) {
      if (whitelist.contains(entry.getKey())) {
        filtered.put(entry.getKey(), entry.getValue());
      }
    }
    return filtered;
  }

  /**
   * Validate label writes against declared {@link LabelDef} schemas — SPEC-001 §3 rule 8.
   * Silent per-key drop: an out-of-enum value is dropped, the rest of the batch still lands.
   */
  public static Map<String, String> filterSchemaValid(
      Map<String, String> setLabels, Map<String, LabelDef> labelDefs) {
    Map<String, String> result = new HashMap<>();
    for (var entry : setLabels.entrySet()) {
      LabelDef def = labelDefs.get(entry.getKey());
      if (def == null || def.allows(entry.getValue())) {
        result.put(entry.getKey(), entry.getValue());
      }
    }
    return result;
  }

  /** Apply one {@link StateUpdate} in place — SPEC-001 §3 rule 9. */
  @SuppressWarnings("unchecked")
  public static void applyOne(Map<String, Object> state, StateUpdate op) {
    switch (op.action()) {
      case SET -> state.put(op.key(), op.value());
      case DELETE -> state.remove(op.key());
      case INCREMENT -> {
        Number current = (Number) state.getOrDefault(op.key(), 0L);
        Number delta = (Number) op.value();
        state.put(op.key(), current.longValue() + delta.longValue());
      }
      case APPEND -> {
        Object existing = state.get(op.key());
        if (existing == null) {
          var list = new ArrayList<>();
          list.add(op.value());
          state.put(op.key(), list);
        } else {
          if (!(existing instanceof List<?>)) {
            throw new IllegalStateException(
                "APPEND on key '" + op.key() + "': expected list, got " + existing.getClass());
          }
          ((List<Object>) existing).add(op.value());
        }
      }
    }
  }
}
