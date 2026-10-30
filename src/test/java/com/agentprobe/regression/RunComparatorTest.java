package com.agentprobe.regression;

import com.agentprobe.regression.RunComparator.Comparison;
import com.agentprobe.regression.RunComparator.Row;
import com.agentprobe.regression.RunComparator.RunData;
import com.agentprobe.regression.RunComparator.ScenarioDiff;
import com.agentprobe.regression.RunComparator.Thresholds;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RunComparatorTest {

    private static Row row(String id, boolean passed, long latency) {
        return new Row(id, passed, passed ? 1.0 : 0.0, latency, 10);
    }

    /** n scenarios named s0..s(n-1), each executed once, all with the same result. */
    private static List<Row> scenarios(int n, boolean passed, long latency) {
        List<Row> rows = new ArrayList<Row>();
        for (int i = 0; i < n; i++) {
            rows.add(row("s" + i, passed, latency));
        }
        return rows;
    }

    /** One scenario executed several times: passes passing trials followed by failures failing trials. */
    private static List<Row> trials(String id, int passes, int failures) {
        List<Row> rows = new ArrayList<Row>();
        for (int i = 0; i < passes; i++) {
            rows.add(row(id, true, 100));
        }
        for (int i = 0; i < failures; i++) {
            rows.add(row(id, false, 100));
        }
        return rows;
    }

    private static RunData done(List<Row> rows) {
        return new RunData("COMPLETED", rows);
    }

    private static ScenarioDiff diff(Comparison c, String id) {
        return c.scenarios().stream().filter(d -> d.scenarioId().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void identicalRunsPass() {
        Comparison c = RunComparator.compare(done(scenarios(5, true, 100)), done(scenarios(5, true, 100)),
                RunComparator.defaults());

        assertThat(c.verdict()).isEqualTo("PASS");
        assertThat(c.reasons()).containsExactly("No regressions detected");
        assertThat(c.counts().unchanged()).isEqualTo(5);
    }

    @Test
    void bigPassRateDropFails() {
        List<Row> candidate = scenarios(5, true, 100);
        candidate.set(2, row("s2", false, 100)); // 5/5 -> 4/5 = -20 points

        Comparison c = RunComparator.compare(done(scenarios(5, true, 100)), done(candidate), RunComparator.defaults());

        assertThat(c.verdict()).isEqualTo("FAIL");
        assertThat(diff(c, "s2").status()).isEqualTo("REGRESSED");
        assertThat(c.counts().regressed()).isEqualTo(1);
        assertThat(c.reasons().get(0)).contains("Pass rate dropped");
    }

    @Test
    void oneRegressionInALargeSuiteOnlyWarns() {
        List<Row> candidate = scenarios(20, true, 100);
        candidate.set(7, row("s7", false, 100)); // 20/20 -> 19/20 = -5 points, below the 10 point fail line

        Comparison c = RunComparator.compare(done(scenarios(20, true, 100)), done(candidate),
                RunComparator.defaults());

        assertThat(c.verdict()).isEqualTo("WARN");
        assertThat(c.counts().regressed()).isEqualTo(1);
    }

    @Test
    void repeatedTrialsAbsorbSingleFlakyFailure() {
        // 5 trials: 5/5 -> 4/5 is -20 points. With a 25 point scenario threshold that is noise, not a regression.
        Thresholds relaxed = new Thresholds(0.10, 0.25, 0.50, 0.50, 0.50);

        Comparison c = RunComparator.compare(done(trials("s", 5, 0)), done(trials("s", 4, 1)), relaxed);

        assertThat(diff(c, "s").status()).isEqualTo("UNCHANGED");
        assertThat(diff(c, "s").flaky()).isTrue();
        assertThat(c.counts().flakyInCandidate()).isEqualTo(1);
    }

    @Test
    void passRateDropAcrossTrialsIsARegression() {
        // 3/3 -> 1/3 = -67 points
        Comparison c = RunComparator.compare(done(trials("s", 3, 0)), done(trials("s", 1, 2)),
                RunComparator.defaults());

        assertThat(diff(c, "s").status()).isEqualTo("REGRESSED");
        assertThat(diff(c, "s").passRateDelta()).isCloseTo(-0.6667, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void improvementIsReportedAndPasses() {
        Comparison c = RunComparator.compare(done(trials("s", 0, 3)), done(trials("s", 3, 0)),
                RunComparator.defaults());

        assertThat(diff(c, "s").status()).isEqualTo("IMPROVED");
        assertThat(c.verdict()).isEqualTo("PASS");
        assertThat(c.reasons()).contains("1 scenario(s) improved");
    }

    @Test
    void scoreDropWithoutPassRateChangeIsDegraded() {
        List<Row> base = List.of(new Row("s", true, 1.0, 100, 10));
        List<Row> cand = List.of(new Row("s", true, 0.7, 100, 10));

        Comparison c = RunComparator.compare(done(base), done(cand), RunComparator.defaults());

        assertThat(diff(c, "s").status()).isEqualTo("DEGRADED");
        assertThat(c.verdict()).isEqualTo("WARN");
    }

    @Test
    void incompleteCandidateRunFails() {
        Comparison c = RunComparator.compare(done(scenarios(3, true, 100)),
                new RunData("BUDGET_EXCEEDED", scenarios(3, true, 100)), RunComparator.defaults());

        assertThat(c.verdict()).isEqualTo("FAIL");
        assertThat(c.reasons().get(0)).contains("BUDGET_EXCEEDED");
    }

    @Test
    void emptyCandidateFails() {
        Comparison c = RunComparator.compare(done(scenarios(3, true, 100)), done(new ArrayList<Row>()),
                RunComparator.defaults());
        assertThat(c.verdict()).isEqualTo("FAIL");
    }

    @Test
    void latencyIncreaseWarns() {
        Comparison c = RunComparator.compare(done(scenarios(4, true, 100)), done(scenarios(4, true, 300)),
                RunComparator.defaults());

        assertThat(c.verdict()).isEqualTo("WARN");
        assertThat(c.reasons()).anyMatch(r -> r.contains("p95 latency"));
    }

    @Test
    void differentScenarioSetsWarn() {
        Comparison c = RunComparator.compare(done(scenarios(3, true, 100)), done(scenarios(4, true, 100)),
                RunComparator.defaults());

        assertThat(c.counts().onlyInCandidate()).isEqualTo(1);
        assertThat(diff(c, "s3").status()).isEqualTo("ONLY_IN_CANDIDATE");
        assertThat(c.verdict()).isEqualTo("WARN");
    }

    @Test
    void differentTrialCountsWarn() {
        Comparison c = RunComparator.compare(done(trials("s", 3, 0)), done(trials("s", 1, 0)),
                RunComparator.defaults());

        assertThat(c.reasons()).anyMatch(r -> r.contains("different number of trials"));
        assertThat(c.verdict()).isEqualTo("WARN");
    }

    @Test
    void percentilesUseNearestRank() {
        List<Long> sorted = List.of(10L, 20L, 30L, 40L, 50L, 60L, 70L, 80L, 90L, 100L);
        assertThat(RunComparator.percentile(sorted, 50)).isEqualTo(50);
        assertThat(RunComparator.percentile(sorted, 95)).isEqualTo(100);
        assertThat(RunComparator.percentile(List.of(), 95)).isEqualTo(0);
    }
}