package io.akka.omnigent;

import io.akka.omnigent.domain.BuiltinPolicies;
import io.akka.omnigent.domain.EvaluationContext;
import io.akka.omnigent.domain.Phase;
import io.akka.omnigent.domain.PhaseSelector;
import io.akka.omnigent.domain.PolicyEngine;
import io.akka.omnigent.domain.PolicySpec;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * Times the same three {@code PolicyEngine.evaluate} scenarios {@code bench/speed_source.py}
 * times against the real source, printed as ns/op so {@code bench/REPORT.md} can put the two
 * side by side. Not an assertion — a measurement, run by hand and transcribed (`mvn test
 * -Dtest=BenchAnswersTest`).
 */
public class BenchAnswersTest {

  private static final int ITERATIONS = 5_000;
  private static final int WARMUP = 200;

  private static long timeIt(Supplier<?> f) {
    for (int i = 0; i < WARMUP; i++) f.get();
    var start = System.nanoTime();
    for (int i = 0; i < ITERATIONS; i++) f.get();
    return (System.nanoTime() - start) / ITERATIONS;
  }

  @Test
  public void printTimings() {
    // 1. Zero policies -> ALLOW.
    var ctx = new EvaluationContext(Phase.TOOL_CALL, "t", Map.of("name", "t"), null);
    var allowNs = timeIt(() -> PolicyEngine.evaluate(List.of(), Map.of(), Map.of(), Map.of(), ctx));

    // 2. DENY short-circuit (second policy, a counter, never runs).
    var noShell =
        new PolicySpec(
            "no_shell",
            List.of(new PhaseSelector(Phase.TOOL_CALL, "run_shell")),
            null,
            null,
            BuiltinPolicies.DENY_TOOL,
            Map.of("tool_name", "run_shell"));
    var counter =
        new PolicySpec(
            "counter",
            List.of(PhaseSelector.of(Phase.TOOL_CALL)),
            null,
            null,
            BuiltinPolicies.MAX_TOOL_CALLS_PER_SESSION,
            Map.of("limit", 1_000_000));
    var policies = List.of(noShell, counter);
    var denyCtx = new EvaluationContext(Phase.TOOL_CALL, "run_shell", Map.of("cmd", "ls"), null);
    var denyNs = timeIt(() -> PolicyEngine.evaluate(policies, Map.of(), Map.of(), Map.of(), denyCtx));

    // 3. ASK (withheld writes).
    var confirm =
        new PolicySpec(
            "confirm_shell",
            List.of(new PhaseSelector(Phase.TOOL_CALL, "run_shell")),
            null,
            null,
            BuiltinPolicies.ASK_ON_TOOL,
            Map.of("tool_name", "run_shell"));
    var askNs = timeIt(() -> PolicyEngine.evaluate(List.of(confirm), Map.of(), Map.of(), Map.of(), denyCtx));

    System.out.println("{");
    System.out.println("  \"allow_zero_policies_ns\": " + allowNs + ",");
    System.out.println("  \"deny_short_circuit_ns\": " + denyNs + ",");
    System.out.println("  \"ask_withholds_writes_ns\": " + askNs);
    System.out.println("}");
  }
}
