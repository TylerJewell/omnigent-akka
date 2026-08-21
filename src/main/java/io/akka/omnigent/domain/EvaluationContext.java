package io.akka.omnigent.domain;

/**
 * Everything a caller supplies for one enforcement call — SPEC-001 §2. Filled by the caller
 * (the HTTP endpoint, standing in for the source's workflow) before dispatching to {@link
 * PolicyEngine#evaluate}; the engine injects the label/state hot cache itself.
 */
public record EvaluationContext(Phase phase, String toolName, Object content, String harness) {}
