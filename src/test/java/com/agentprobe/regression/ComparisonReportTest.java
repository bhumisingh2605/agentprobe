package com.agentprobe.regression;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ComparisonReportTest {

    @Test
    void reportShowsVerdictLabelsAndOnlyChangedScenarios() {
        RunComparator.Comparison c = RunComparator.compare(
                new RunComparator.RunData("COMPLETED", List.of(
                        new RunComparator.Row("cancel-asks-confirmation", true, 1.0, 100, 10),
                        new RunComparator.Row("greeting", true, 1.0, 100, 10))),
                new RunComparator.RunData("COMPLETED", List.of(
                        new RunComparator.Row("cancel-asks-confirmation", false, 0.0, 100, 10),
                        new RunComparator.Row("greeting", true, 1.0, 100, 10))),
                RunComparator.defaults());

        String md = ComparisonReport.toMarkdown("prompt-v1", "prompt-v2-weak",
                "11111111-aaaa", "22222222-bbbb", c);

        assertThat(md).contains("regression report: FAIL");
        assertThat(md).contains("`prompt-v1`").contains("`prompt-v2-weak`");
        assertThat(md).contains("cancel-asks-confirmation | REGRESSED");
        assertThat(md).doesNotContain("| greeting |");
        assertThat(md).contains("Pass rate");
    }
}