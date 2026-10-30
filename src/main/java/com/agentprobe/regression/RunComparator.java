package com.agentprobe.regression;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Compares a candidate run to a baseline run, scenario by scenario.
 *
 * Because LLM output is non-deterministic, every scenario can run several times (trials) and we compare
 * pass RATES instead of single pass/fail results. A scenario only counts as REGRESSED when its pass rate
 * drops by at least scenarioPassRateDrop (default 20 points), which keeps noise from raising false alarms.
 *
 * Verdict: FAIL, WARN or PASS, with a list of human-readable reasons.
 */
public final class RunComparator {

    private static final double EPS = 1e-9;

    private RunComparator() {
    }

    /** All values are fractions: 0.10 means 10 percentage points (or +10 percent for latency and tokens). */
    public record Thresholds(double maxPassRateDrop, double scenarioPassRateDrop, double scoreDrop,
                             double latencyIncrease, double tokenIncrease) {
    }

    public static Thresholds defaults() {
        return new Thresholds(0.10, 0.20, 0.15, 0.50, 0.50);
    }

    public record Row(String scenarioId, boolean passed, double score, long latencyMs, Integer tokens) {
    }

    public record RunData(String status, List<Row> rows) {
    }

    public record Summary(int results, int scenarios, double trialsPerScenario, double passRate, double meanScore,
                          long avgLatencyMs, long p50LatencyMs, long p95LatencyMs, long totalTokens) {
    }

    /** status: REGRESSED, IMPROVED, DEGRADED (score dropped, pass rate did not), UNCHANGED,
     *  ONLY_IN_BASELINE, ONLY_IN_CANDIDATE. */
    public record ScenarioDiff(String scenarioId, String status, int baseTrials, int candidateTrials,
                               double basePassRate, double candidatePassRate, double passRateDelta,
                               double baseMeanScore, double candidateMeanScore, double scoreDelta,
                               long baseAvgLatencyMs, long candidateAvgLatencyMs, boolean flaky) {
    }

    public record Counts(int regressed, int improved, int degraded, int unchanged,
                         int onlyInBaseline, int onlyInCandidate, int flakyInCandidate) {
    }

    public record Comparison(String verdict, List<String> reasons, Summary baseline, Summary candidate,
                             Counts counts, double passRateDelta, double meanScoreDelta,
                             List<ScenarioDiff> scenarios) {
    }

    // ------------------------------------------------------------------ aggregation

    private static final class Agg {
        int trials;
        int passes;
        double scoreSum;
        long latSum;
        int latCount;

        void add(Row r) {
            trials++;
            if (r.passed()) {
                passes++;
            }
            scoreSum += r.score();
            if (r.latencyMs() > 0) {
                latSum += r.latencyMs();
                latCount++;
            }
        }

        double passRate() {
            return trials == 0 ? 0.0 : (double) passes / trials;
        }

        double meanScore() {
            return trials == 0 ? 0.0 : scoreSum / trials;
        }

        long avgLatency() {
            return latCount == 0 ? 0 : latSum / latCount;
        }

        boolean flaky() {
            return trials > 1 && passes > 0 && passes < trials;
        }
    }

    private static Map<String, Agg> group(List<Row> rows) {
        Map<String, Agg> map = new TreeMap<String, Agg>();
        for (Row r : rows) {
            map.computeIfAbsent(r.scenarioId(), k -> new Agg()).add(r);
        }
        return map;
    }

    private static Summary summarize(List<Row> rows) {
        Map<String, Agg> groups = group(rows);
        int passes = 0;
        double scoreSum = 0;
        long tokens = 0;
        List<Long> latencies = new ArrayList<Long>();
        for (Row r : rows) {
            if (r.passed()) {
                passes++;
            }
            scoreSum += r.score();
            if (r.tokens() != null) {
                tokens += r.tokens();
            }
            if (r.latencyMs() > 0) {
                latencies.add(r.latencyMs());
            }
        }
        Collections.sort(latencies);
        long latSum = 0;
        for (Long l : latencies) {
            latSum += l;
        }
        int n = rows.size();
        return new Summary(n, groups.size(), groups.isEmpty() ? 0.0 : (double) n / groups.size(),
                n == 0 ? 0.0 : (double) passes / n, n == 0 ? 0.0 : scoreSum / n,
                latencies.isEmpty() ? 0 : latSum / latencies.size(),
                percentile(latencies, 50), percentile(latencies, 95), tokens);
    }

    /** Nearest-rank percentile on a sorted list. */
    static long percentile(List<Long> sorted, int p) {
        if (sorted.isEmpty()) {
            return 0;
        }
        int idx = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        idx = Math.max(0, Math.min(sorted.size() - 1, idx));
        return sorted.get(idx);
    }

    // ------------------------------------------------------------------ comparison

    public static Comparison compare(RunData base, RunData cand, Thresholds t) {
        Map<String, Agg> b = group(base.rows());
        Map<String, Agg> c = group(cand.rows());
        Set<String> ids = new TreeSet<String>(b.keySet());
        ids.addAll(c.keySet());

        List<ScenarioDiff> diffs = new ArrayList<ScenarioDiff>();
        List<String> regressedIds = new ArrayList<String>();
        List<String> degradedIds = new ArrayList<String>();
        int regressed = 0, improved = 0, degraded = 0, unchanged = 0, onlyBase = 0, onlyCand = 0, flaky = 0;

        for (String id : ids) {
            Agg ba = b.get(id);
            Agg ca = c.get(id);
            if (ca != null && ca.flaky()) {
                flaky++;
            }
            if (ba == null) {
                onlyCand++;
                diffs.add(new ScenarioDiff(id, "ONLY_IN_CANDIDATE", 0, ca.trials, 0, ca.passRate(), 0,
                        0, ca.meanScore(), 0, 0, ca.avgLatency(), ca.flaky()));
                continue;
            }
            if (ca == null) {
                onlyBase++;
                diffs.add(new ScenarioDiff(id, "ONLY_IN_BASELINE", ba.trials, 0, ba.passRate(), 0, 0,
                        ba.meanScore(), 0, 0, ba.avgLatency(), 0, false));
                continue;
            }
            double passDelta = ca.passRate() - ba.passRate();
            double scoreDelta = ca.meanScore() - ba.meanScore();
            String status;
            if (passDelta <= -t.scenarioPassRateDrop() + EPS && passDelta < -EPS) {
                status = "REGRESSED";
                regressed++;
                regressedIds.add(id);
            } else if (passDelta >= t.scenarioPassRateDrop() - EPS && passDelta > EPS) {
                status = "IMPROVED";
                improved++;
            } else if (scoreDelta <= -t.scoreDrop() + EPS && scoreDelta < -EPS) {
                status = "DEGRADED";
                degraded++;
                degradedIds.add(id);
            } else {
                status = "UNCHANGED";
                unchanged++;
            }
            diffs.add(new ScenarioDiff(id, status, ba.trials, ca.trials, ba.passRate(), ca.passRate(), passDelta,
                    ba.meanScore(), ca.meanScore(), scoreDelta, ba.avgLatency(), ca.avgLatency(), ca.flaky()));
        }

        Summary bs = summarize(base.rows());
        Summary cs = summarize(cand.rows());
        double passRateDelta = cs.passRate() - bs.passRate();
        double meanScoreDelta = cs.meanScore() - bs.meanScore();

        Verdict v = new Verdict();
        if (!"COMPLETED".equals(cand.status())) {
            v.fail("Candidate run ended with status " + cand.status() + ", so the comparison is not reliable");
        }
        if (!"COMPLETED".equals(base.status())) {
            v.warn("Baseline run ended with status " + base.status());
        }
        if (bs.results() == 0 || cs.results() == 0) {
            v.fail("A run has no results to compare");
        } else {
            double drop = bs.passRate() - cs.passRate();
            if (drop > EPS && drop >= t.maxPassRateDrop() - EPS) {
                v.fail("Pass rate dropped by " + points(drop) + " points (" + pct(bs.passRate())
                        + " to " + pct(cs.passRate()) + ")");
            }
            if (regressed > 0) {
                v.warn(regressed + " scenario(s) regressed: " + regressedIds);
            }
            if (degraded > 0) {
                v.warn(degraded + " scenario(s) got lower quality scores while still passing: " + degradedIds);
            }
            if (-meanScoreDelta >= t.scoreDrop() - EPS && meanScoreDelta < -EPS) {
                v.warn("Mean score dropped from " + two(bs.meanScore()) + " to " + two(cs.meanScore()));
            }
            if (bs.p95LatencyMs() > 0 && cs.p95LatencyMs() > bs.p95LatencyMs() * (1 + t.latencyIncrease())) {
                v.warn("p95 latency increased from " + bs.p95LatencyMs() + " ms to " + cs.p95LatencyMs() + " ms");
            }
            double baseTokensPerRun = (double) bs.totalTokens() / bs.results();
            double candTokensPerRun = (double) cs.totalTokens() / cs.results();
            if (baseTokensPerRun > 0 && candTokensPerRun > baseTokensPerRun * (1 + t.tokenIncrease())) {
                v.warn("Tokens per execution increased from " + Math.round(baseTokensPerRun)
                        + " to " + Math.round(candTokensPerRun));
            }
            if (Math.abs(bs.trialsPerScenario() - cs.trialsPerScenario()) > EPS) {
                v.warn("Runs used a different number of trials per scenario (" + two(bs.trialsPerScenario())
                        + " vs " + two(cs.trialsPerScenario()) + "), so pass rates are less comparable");
            }
            if (onlyBase > 0 || onlyCand > 0) {
                v.warn("The scenario sets differ (" + onlyBase + " only in baseline, " + onlyCand
                        + " only in candidate)");
            }
        }
        if (v.level == 0) {
            v.reasons.add("No regressions detected");
            if (improved > 0) {
                v.reasons.add(improved + " scenario(s) improved");
            }
        }

        Counts counts = new Counts(regressed, improved, degraded, unchanged, onlyBase, onlyCand, flaky);
        return new Comparison(v.name(), v.reasons, bs, cs, counts, passRateDelta, meanScoreDelta, diffs);
    }

    // ------------------------------------------------------------------ helpers

    private static final class Verdict {
        int level; // 0 PASS, 1 WARN, 2 FAIL
        final List<String> reasons = new ArrayList<String>();

        void warn(String reason) {
            level = Math.max(level, 1);
            reasons.add(reason);
        }

        void fail(String reason) {
            level = 2;
            reasons.add(reason);
        }

        String name() {
            return level == 2 ? "FAIL" : level == 1 ? "WARN" : "PASS";
        }
    }

    private static String pct(double fraction) {
        return String.format(Locale.ROOT, "%.0f%%", fraction * 100);
    }

    private static String points(double fraction) {
        return String.format(Locale.ROOT, "%.0f", fraction * 100);
    }

    private static String two(double d) {
        return String.format(Locale.ROOT, "%.2f", d);
    }
}