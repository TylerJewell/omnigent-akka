package io.akka.omnigent.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One session's durable policy set plus its label/state hot cache — SPEC-001 §2
 * ({@code HarnessSession}). Distinct entities (distinct session ids) never share this state,
 * regardless of which {@code harness} string is stamped on the events they evaluate
 * (SPEC-001 §3 rule 11) — isolation is per-entity, which Akka already guarantees.
 */
public record HarnessSessionState(
    List<PolicySpec> policies, Map<String, LabelDef> labelDefs, Map<String, String> labels,
    Map<String, Object> sessionState) {

  public static HarnessSessionState initial() {
    return new HarnessSessionState(List.of(), Map.of(), Map.of(), Map.of());
  }

  public HarnessSessionState onEvent(HarnessSessionEvent event) {
    return switch (event) {
      case HarnessSessionEvent.PoliciesAttached e ->
          new HarnessSessionState(e.policies(), e.labelDefs(), labels, sessionState);
      case HarnessSessionEvent.LabelsWritten e -> {
        Map<String, String> merged = new HashMap<>(labels);
        merged.putAll(PolicyEngine.filterSchemaValid(e.labels(), labelDefs));
        yield new HarnessSessionState(policies, labelDefs, Map.copyOf(merged), sessionState);
      }
      case HarnessSessionEvent.SessionStateUpdated e -> {
        Map<String, Object> merged = new HashMap<>(sessionState);
        for (StateUpdate op : e.updates()) {
          PolicyEngine.applyOne(merged, op);
        }
        yield new HarnessSessionState(policies, labelDefs, labels, Map.copyOf(merged));
      }
    };
  }

  public PolicyResult evaluate(EvaluationContext ctx) {
    return PolicyEngine.evaluate(policies, labelDefs, labels, sessionState, ctx);
  }

  /** Every event this evaluation's result should be persisted as — empty on ASK
   * (SPEC-001 §3 rule 5: withheld pending approval) and on a result with nothing to write. */
  public List<HarnessSessionEvent> eventsFor(PolicyResult result) {
    List<HarnessSessionEvent> events = new ArrayList<>();
    if (result.action() == PolicyAction.ASK) {
      return events;
    }
    if (result.stateUpdates() != null && !result.stateUpdates().isEmpty()) {
      events.add(new HarnessSessionEvent.SessionStateUpdated(result.stateUpdates()));
    }
    if (result.setLabels() != null && !result.setLabels().isEmpty()) {
      events.add(new HarnessSessionEvent.LabelsWritten(result.setLabels()));
    }
    return events;
  }
}
