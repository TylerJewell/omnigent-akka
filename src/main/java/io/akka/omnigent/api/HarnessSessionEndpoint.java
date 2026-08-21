package io.akka.omnigent.api;

import akka.Done;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpException;
import io.akka.omnigent.application.HarnessSessionEntity;
import io.akka.omnigent.domain.EvaluationContext;
import io.akka.omnigent.domain.LabelDef;
import io.akka.omnigent.domain.Phase;
import io.akka.omnigent.domain.PolicyResult;
import io.akka.omnigent.domain.PolicySpec;
import java.util.List;
import java.util.Map;

/**
 * Attach policies to a harness session and submit enforcement events against it — SPEC-001 §2.
 * This is the port's own reachable surface: the source has no single HTTP endpoint either — the
 * same composed decision is reached from inside the workflow loop at four different enforcement
 * sites (POLICIES.md §5), which this endpoint's {@code /evaluate} route stands in for.
 */
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
@HttpEndpoint("/sessions")
public class HarnessSessionEndpoint {

  private final ComponentClient componentClient;

  public HarnessSessionEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public record AttachPoliciesRequest(List<PolicySpec> policies, Map<String, LabelDef> labelDefs) {}

  @Post("/{sessionId}/policies")
  public Done attachPolicies(String sessionId, AttachPoliciesRequest request) {
    if (request.policies() == null || request.policies().isEmpty()) {
      throw HttpException.badRequest("policies must not be empty");
    }
    for (var policy : request.policies()) {
      if (policy.on() == null || policy.on().isEmpty()) {
        throw HttpException.badRequest("policy '" + policy.name() + "' must declare at least one phase selector");
      }
    }
    var labelDefs = request.labelDefs() == null ? Map.<String, LabelDef>of() : request.labelDefs();
    return entity(sessionId)
        .method(HarnessSessionEntity::attachPolicies)
        .invoke(new HarnessSessionEntity.AttachPolicies(request.policies(), labelDefs));
  }

  public record EvaluateRequest(Phase phase, String toolName, Object content, String harness) {}

  @Post("/{sessionId}/evaluate")
  public PolicyResult evaluate(String sessionId, EvaluateRequest request) {
    if (request.phase() == null) {
      throw HttpException.badRequest("phase is required");
    }
    var ctx = new EvaluationContext(request.phase(), request.toolName(), request.content(), request.harness());
    return entity(sessionId).method(HarnessSessionEntity::evaluate).invoke(ctx);
  }

  @Get("/{sessionId}/labels")
  public Map<String, String> labels(String sessionId) {
    return entity(sessionId).method(HarnessSessionEntity::labels).invoke();
  }

  @Get("/{sessionId}/state")
  public Map<String, Object> sessionState(String sessionId) {
    return entity(sessionId).method(HarnessSessionEntity::sessionState).invoke();
  }

  private akka.javasdk.client.EventSourcedEntityClient entity(String sessionId) {
    return componentClient.forEventSourcedEntity(sessionId);
  }
}
