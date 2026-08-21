package io.akka.omnigent.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** {@link PolicyEngine#applyOne} — SPEC-001 §3 rule 9, the four {@link StateUpdateAction}s. */
class SessionStateTest {

  @Test
  void setReplaces() {
    Map<String, Object> state = new HashMap<>();
    PolicyEngine.applyOne(state, new StateUpdate("k", StateUpdateAction.SET, "v"));
    assertThat(state).containsEntry("k", "v");
    PolicyEngine.applyOne(state, new StateUpdate("k", StateUpdateAction.SET, "v2"));
    assertThat(state).containsEntry("k", "v2");
  }

  @Test
  void incrementAddsToExistingOrZero() {
    Map<String, Object> state = new HashMap<>();
    PolicyEngine.applyOne(state, new StateUpdate("count", StateUpdateAction.INCREMENT, 1L));
    assertThat(state.get("count")).isEqualTo(1L);
    PolicyEngine.applyOne(state, new StateUpdate("count", StateUpdateAction.INCREMENT, 4L));
    assertThat(state.get("count")).isEqualTo(5L);
  }

  @Test
  void deleteRemovesAndIsNoOpWhenAbsent() {
    Map<String, Object> state = new HashMap<>(Map.of("k", "v"));
    PolicyEngine.applyOne(state, new StateUpdate("k", StateUpdateAction.DELETE, null));
    assertThat(state).doesNotContainKey("k");
    PolicyEngine.applyOne(state, new StateUpdate("k", StateUpdateAction.DELETE, null));
    assertThat(state).doesNotContainKey("k");
  }

  @Test
  void appendCreatesSingleElementListWhenAbsentThenGrows() {
    Map<String, Object> state = new HashMap<>();
    PolicyEngine.applyOne(state, new StateUpdate("items", StateUpdateAction.APPEND, "a"));
    assertThat(state.get("items")).isEqualTo(List.of("a"));
    PolicyEngine.applyOne(state, new StateUpdate("items", StateUpdateAction.APPEND, "b"));
    assertThat(state.get("items")).isEqualTo(List.of("a", "b"));
  }

  @Test
  void appendOnNonListRaises() {
    Map<String, Object> state = new HashMap<>(Map.of("items", "not-a-list"));
    assertThatThrownBy(() -> PolicyEngine.applyOne(state, new StateUpdate("items", StateUpdateAction.APPEND, "x")))
        .isInstanceOf(IllegalStateException.class);
  }
}
