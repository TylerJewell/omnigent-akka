package io.akka.omnigent.domain;

import java.util.Map;

/**
 * The event handed to a builtin policy callable — the Java analogue of the source's {@code
 * event} dict (POLICIES.md "Policy function interface"). Built by {@link PolicyEngine} from the
 * caller's {@link EvaluationContext} plus the session's current label/state hot cache, so a
 * callable never has to re-query anything.
 */
public record PolicyEvent(
    Phase phase,
    String toolName,
    Object content,
    String harness,
    Map<String, String> labels,
    Map<String, Object> sessionState) {}
