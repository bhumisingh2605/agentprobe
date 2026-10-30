package com.agentprobe.regression;

import com.agentprobe.regression.RunComparator.Comparison;
import com.agentprobe.regression.RunComparator.ScenarioDiff;
import com.agentprobe.regression.RunComparator.Summary;

import java.util.Locale;

public final class ComparisonReport {

    private ComparisonReport() {
    }

    public static String toMarkdown(String baseLabel, String candidateLabel, String baseRunId,
                                    String candidateRunId, Comparison c) {
        StringBuilder sb = new StringBuilder();
        sb.append("## AgentProbe regression report: ").append(c.verdict()).append("\n\n");
        sb.append("- Baseline: `").append(baseLabel).append("` (run ").append(shortId(baseRunId)).append(")\n");
        sb.append("- Candidate: `").append(candidateLabel).append("` (run ").append(shortId(candidateRunId))
                .append(")\n\n");

        Summary b = c.baseline();
        Summary n = c.candidate();
        sb.append("| Metric | Baseline | Candidate | Change |\n|---|---|---|---|\n");
        row(sb, "Pass rate", pct(b.passRate()), pct(n.passRate()), signedPoints(n.passRate() - b.passRate()));
        row(sb, "Mean score", two(b.meanScore()), two(n.meanScore()), signed(n.meanScore() - b.meanScore()));
        row(sb, "Avg latency", b.avgLatencyMs() + " ms", n.avgLatencyMs() + " ms",
                signedLong(n.avgLatencyMs() - b.avgLatencyMs()) + " ms");
        row(sb, "p95 latency", b.p95LatencyMs() + " ms", n.p95LatencyMs() + " ms",
                signedLong(n.p95LatencyMs() - b.p95LatencyMs()) + " ms");
        row(sb, "Agent tokens", String.valueOf(b.totalTokens()), String.valueOf(n.totalTokens()),
                signedLong(n.totalTokens() - b.totalTokens()));
        row(sb, "Executions", String.valueOf(b.results()), String.valueOf(n.results()), "");

        sb.append("\n### Reasons\n");
        for (String r : c.reasons()) {
            sb.append("- ").append(r).append('\n');
        }

        sb.append("\n### Scenario changes\n");
        boolean any = false;
        StringBuilder rows = new StringBuilder();
        for (ScenarioDiff d : c.scenarios()) {
            if ("UNCHANGED".equals(d.status())) {
                continue;
            }
            any = true;
            rows.append("| ").append(d.scenarioId()).append(" | ").append(d.status()).append(" | ")
                    .append(pct(d.basePassRate())).append(" | ").append(pct(d.candidatePassRate())).append(" |\n");
        }
        if (any) {
            sb.append("| Scenario | Status | Baseline pass rate | Candidate pass rate |\n|---|---|---|---|\n")
                    .append(rows);
        } else {
            sb.append("No scenario changed.\n");
        }
        if (c.counts().flakyInCandidate() > 0) {
            sb.append("\n").append(c.counts().flakyInCandidate())
                    .append(" scenario(s) were flaky in the candidate (passed in some trials, failed in others).\n");
        }
        return sb.toString();
    }

    private static void row(StringBuilder sb, String name, String a, String b, String change) {
        sb.append("| ").append(name).append(" | ").append(a).append(" | ").append(b).append(" | ")
                .append(change).append(" |\n");
    }

    private static String shortId(String id) {
        return id == null || id.length() < 8 ? String.valueOf(id) : id.substring(0, 8);
    }

    private static String pct(double f) {
        return String.format(Locale.ROOT, "%.1f%%", f * 100);
    }

    private static String signedPoints(double f) {
        return String.format(Locale.ROOT, "%+.1f pts", f * 100);
    }

    private static String two(double d) {
        return String.format(Locale.ROOT, "%.2f", d);
    }

    private static String signed(double d) {
        return String.format(Locale.ROOT, "%+.2f", d);
    }

    private static String signedLong(long l) {
        return (l >= 0 ? "+" : "") + l;
    }
}