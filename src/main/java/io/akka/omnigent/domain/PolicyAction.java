package io.akka.omnigent.domain;

/** A single policy's or the engine's composed verdict — SPEC-001 §2. */
public enum PolicyAction {
  ALLOW,
  ASK,
  DENY
}
