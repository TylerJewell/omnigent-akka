package io.akka.omnigent.domain;

/**
 * The enforcement point an event occurred at — SPEC-001 §2. Mirrors the source's four
 * non-advisory phases ({@code omnigent.spec.types.Phase}); the LLM-request/response advisory
 * phases are out of scope (SPEC-001 §1).
 */
public enum Phase {
  REQUEST,
  RESPONSE,
  TOOL_CALL,
  TOOL_RESULT
}
