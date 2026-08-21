package io.akka.omnigent;

import static org.assertj.core.api.Assertions.assertThat;

import akka.Done;
import akka.javasdk.testkit.TestKitSupport;
import io.akka.omnigent.api.HarnessSessionEndpoint.AttachPoliciesRequest;
import io.akka.omnigent.api.HarnessSessionEndpoint.EvaluateRequest;
import io.akka.omnigent.domain.BuiltinPolicies;
import io.akka.omnigent.domain.Phase;
import io.akka.omnigent.domain.PhaseSelector;
import io.akka.omnigent.domain.PolicyAction;
import io.akka.omnigent.domain.PolicyResult;
import io.akka.omnigent.domain.PolicySpec;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The HTTP surface, end to end against a running runtime — SPEC-001 §5. Demonstrates the
 * multi-harness slice: two independently keyed sessions, each attached with the identical
 * policy list, submit events tagged with different {@code harness} values and never observe
 * each other's state — SPEC-001 §3 rule 11 and §4.
 */
public class HarnessSessionEndpointIntegrationTest extends TestKitSupport {

  private String newSessionId() {
    return "session-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private Done attach(String sessionId, List<PolicySpec> policies) {
    return httpClient
        .POST("/sessions/" + sessionId + "/policies")
        .withRequestBody(new AttachPoliciesRequest(policies, Map.of()))
        .responseBodyAs(Done.class)
        .invoke()
        .body();
  }

  private PolicyResult evaluate(String sessionId, Phase phase, String toolName, Object content, String harness) {
    return httpClient
        .POST("/sessions/" + sessionId + "/evaluate")
        .withRequestBody(new EvaluateRequest(phase, toolName, content, harness))
        .responseBodyAs(PolicyResult.class)
        .invoke()
        .body();
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> sessionState(String sessionId) {
    return httpClient.GET("/sessions/" + sessionId + "/state").responseBodyAs(Map.class).invoke().body();
  }

  @Test
  void limitDeniesOnceReached() {
    var sessionId = newSessionId();
    var limit =
        new PolicySpec(
            "limit",
            List.of(PhaseSelector.of(Phase.TOOL_CALL)),
            null,
            null,
            BuiltinPolicies.MAX_TOOL_CALLS_PER_SESSION,
            Map.of("limit", 2));
    attach(sessionId, List.of(limit));

    var first = evaluate(sessionId, Phase.TOOL_CALL, "web_search", Map.of("q", "a"), "claude-sdk");
    assertThat(first.action()).isEqualTo(PolicyAction.ALLOW);
    var second = evaluate(sessionId, Phase.TOOL_CALL, "web_search", Map.of("q", "b"), "claude-sdk");
    assertThat(second.action()).isEqualTo(PolicyAction.ALLOW);
    var third = evaluate(sessionId, Phase.TOOL_CALL, "web_search", Map.of("q", "c"), "claude-sdk");
    assertThat(third.action()).isEqualTo(PolicyAction.DENY);
  }

  /** Two sessions, identical policy configuration, never share state — the isolation half of
   * the multi-harness slice. */
  @Test
  void sessionsAreIsolated() {
    var sessionA = newSessionId();
    var sessionB = newSessionId();
    var limit =
        new PolicySpec(
            "limit",
            List.of(PhaseSelector.of(Phase.TOOL_CALL)),
            null,
            null,
            BuiltinPolicies.MAX_TOOL_CALLS_PER_SESSION,
            Map.of("limit", 1));
    attach(sessionA, List.of(limit));
    attach(sessionB, List.of(limit));

    // Exhaust session A's budget.
    assertThat(evaluate(sessionA, Phase.TOOL_CALL, "t", Map.of(), "claude-sdk").action())
        .isEqualTo(PolicyAction.ALLOW);
    assertThat(evaluate(sessionA, Phase.TOOL_CALL, "t", Map.of(), "claude-sdk").action())
        .isEqualTo(PolicyAction.DENY);

    // Session B, same policy config, is untouched.
    assertThat(evaluate(sessionB, Phase.TOOL_CALL, "t", Map.of(), "codex-native").action())
        .isEqualTo(PolicyAction.ALLOW);
  }

  /** The same policy set enforces identically no matter which harness string an event carries
   * — SPEC-001 §4: harness identity is metadata, not a routing key. */
  @Test
  void harnessIdentityDoesNotAffectComposition() {
    var sessionId = newSessionId();
    var noShell =
        new PolicySpec(
            "no_shell",
            List.of(new PhaseSelector(Phase.TOOL_CALL, "run_shell")),
            null,
            null,
            BuiltinPolicies.DENY_TOOL,
            Map.of("tool_name", "run_shell"));
    attach(sessionId, List.of(noShell));

    var fromClaude = evaluate(sessionId, Phase.TOOL_CALL, "run_shell", Map.of("cmd", "ls"), "claude-sdk");
    assertThat(fromClaude.action()).isEqualTo(PolicyAction.DENY);

    var sessionId2 = newSessionId();
    attach(sessionId2, List.of(noShell));
    var fromCodex = evaluate(sessionId2, Phase.TOOL_CALL, "run_shell", Map.of("cmd", "ls"), "codex-native");
    assertThat(fromCodex.action()).isEqualTo(PolicyAction.DENY);
    assertThat(fromCodex.reason()).isEqualTo(fromClaude.reason());
  }
}
