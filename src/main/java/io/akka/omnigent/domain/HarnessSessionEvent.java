package io.akka.omnigent.domain;

import akka.javasdk.annotations.TypeName;
import java.util.List;
import java.util.Map;

/** Events for {@link io.akka.omnigent.application.HarnessSessionEntity} — SPEC-001 §2. */
public sealed interface HarnessSessionEvent {

  @TypeName("policies-attached")
  record PoliciesAttached(List<PolicySpec> policies, Map<String, LabelDef> labelDefs)
      implements HarnessSessionEvent {}

  @TypeName("labels-written")
  record LabelsWritten(Map<String, String> labels) implements HarnessSessionEvent {}

  @TypeName("session-state-updated")
  record SessionStateUpdated(List<StateUpdate> updates) implements HarnessSessionEvent {}
}
