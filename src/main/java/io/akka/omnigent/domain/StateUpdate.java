package io.akka.omnigent.domain;

/**
 * One mutation to apply to a session's per-turn state map — SPEC-001 §2, §3 rule 9. Applied in
 * list order; {@code INCREMENT} requires the existing value (if any) to be numeric, {@code
 * APPEND} requires it (if any) to be a list.
 */
public record StateUpdate(String key, StateUpdateAction action, Object value) {}
