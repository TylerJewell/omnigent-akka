package io.akka.omnigent.application;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import io.akka.omnigent.domain.EvaluationContext;
import io.akka.omnigent.domain.HarnessSessionEvent;
import io.akka.omnigent.domain.HarnessSessionState;
import io.akka.omnigent.domain.LabelDef;
import io.akka.omnigent.domain.PolicyResult;
import io.akka.omnigent.domain.PolicySpec;
import java.util.List;
import java.util.Map;

/**
 * One harness session's policy set and label/state cache — SPEC-001 §2. The entity id is the
 * session id; two different ids never see each other's state (SPEC-001 §3 rule 11), and the
 * same policy list enforces identically no matter which {@code harness} string an event carries
 * (SPEC-001 §4). This is the multi-harness demonstration surface: several harness backends
 * (standing in for {@code claude-sdk}, {@code codex-native}, {@code cursor}, ...) submit events
 * against independently keyed sessions, each governed by the one shared {@link
 * io.akka.omnigent.domain.PolicyEngine}.
 */
@Component(id = "harness-session")
public class HarnessSessionEntity extends EventSourcedEntity<HarnessSessionState, HarnessSessionEvent> {

  @Override
  public HarnessSessionState emptyState() {
    return HarnessSessionState.initial();
  }

  public record AttachPolicies(List<PolicySpec> policies, Map<String, LabelDef> labelDefs) {}

  public Effect<Done> attachPolicies(AttachPolicies cmd) {
    return effects()
        .persist(new HarnessSessionEvent.PoliciesAttached(cmd.policies(), cmd.labelDefs()))
        .thenReply(state -> Done.getInstance());
  }

  /**
   * Runs one event through the composed policy pipeline (SPEC-001 §3) and, unless the result is
   * ASK (rule 5 — writes withheld pending approval), persists whatever labels/state the pass
   * accumulated before replying.
   */
  public Effect<PolicyResult> evaluate(EvaluationContext ctx) {
    PolicyResult result = currentState().evaluate(ctx);
    List<HarnessSessionEvent> events = currentState().eventsFor(result);
    return switch (events.size()) {
      case 0 -> effects().reply(result);
      case 1 -> effects().persist(events.get(0)).thenReply(state -> result);
      default -> effects().persist(events.get(0), events.get(1)).thenReply(state -> result);
    };
  }

  public ReadOnlyEffect<Map<String, String>> labels() {
    return effects().reply(currentState().labels());
  }

  public ReadOnlyEffect<Map<String, Object>> sessionState() {
    return effects().reply(currentState().sessionState());
  }

  @Override
  public HarnessSessionState applyEvent(HarnessSessionEvent event) {
    return currentState().onEvent(event);
  }
}
