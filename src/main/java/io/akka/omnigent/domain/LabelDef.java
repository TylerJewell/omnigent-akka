package io.akka.omnigent.domain;

import java.util.List;

/**
 * Optional schema for one label key — SPEC-001 §2, §3 rule 8. A {@code null} {@code values} list
 * means the key is unschemaed and any write lands; a non-null list means a write outside it is
 * dropped silently at apply time.
 */
public record LabelDef(List<String> values) {

  public boolean allows(String value) {
    return values == null || values.contains(value);
  }
}
