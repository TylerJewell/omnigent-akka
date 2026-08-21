package io.akka.omnigent.domain;

/** SPEC-001 §3 rule 9 — the four session-state mutation ops a policy can request. */
public enum StateUpdateAction {
  SET,
  INCREMENT,
  DELETE,
  APPEND
}
