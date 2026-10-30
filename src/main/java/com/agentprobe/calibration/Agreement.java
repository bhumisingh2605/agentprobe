package com.agentprobe.calibration;

import java.util.ArrayList;
import java.util.List;

/** Compares the LLM judge with human labels: percent agreement, Cohen's kappa and score agreement. */
public final class Agreement {

    private Agreement() {
    }

    /** Scores are 1 to 5. */
    public record Pair(boolean humanPassed, boolean judgePassed, int humanScore, int judgeScore) {
    }

    public record Report(
            int n,
            int bothPass,
            int humanPassJudgeFail,
            int humanFailJudgePass,
            int bothFail,
            double percentAgreement,
            double cohensKappa,
            String kappaInterpretation,
            double exactScoreAgreement,
            double within1ScoreAgreement,
            double meanAbsScoreError,
            double judgePassRateMinusHuman,
            List<String> warnings) {
    }

    public static Report compute(List<Pair> pairs) {
        List<String> warnings = new ArrayList<String>();
        int n = pairs.size();
        if (n == 0) {
            warnings.add("No labeled results yet. Label judged scenario results first.");
            return new Report(0, 0, 0, 0, 0, 0, 0, "n/a", 0, 0, 0, 0, warnings);
        }
        int a = 0; // both pass
        int b = 0; // human pass, judge fail
        int c = 0; // human fail, judge pass
        int d = 0; // both fail
        int exact = 0;
        int within1 = 0;
        double absErr = 0;
        for (Pair p : pairs) {
            if (p.humanPassed() && p.judgePassed()) {
                a++;
            } else if (p.humanPassed()) {
                b++;
            } else if (p.judgePassed()) {
                c++;
            } else {
                d++;
            }
            int diff = Math.abs(p.humanScore() - p.judgeScore());
            if (diff == 0) {
                exact++;
            }
            if (diff <= 1) {
                within1++;
            }
            absErr += diff;
        }
        double po = (double) (a + d) / n;
        double humanPass = (double) (a + b) / n;
        double judgePass = (double) (a + c) / n;
        double pe = humanPass * judgePass + (1 - humanPass) * (1 - judgePass);
        double kappa;
        if (1 - pe < 1e-12) {
            kappa = po >= 1 - 1e-12 ? 1.0 : 0.0; // only one class present in both raters
            warnings.add("Only one outcome (all pass or all fail) was labeled, so kappa is not meaningful. "
                    + "Label a mix of passing and failing answers.");
        } else {
            kappa = (po - pe) / (1 - pe);
        }
        if (n < 30) {
            warnings.add("Only " + n + " labels. Aim for at least 50 before quoting agreement numbers.");
        }
        return new Report(n, a, b, c, d, po, kappa, interpret(kappa),
                (double) exact / n, (double) within1 / n, absErr / n, judgePass - humanPass, warnings);
    }

    /** Landis and Koch bands. */
    static String interpret(double k) {
        if (k < 0) return "poor (worse than chance)";
        if (k <= 0.20) return "slight";
        if (k <= 0.40) return "fair";
        if (k <= 0.60) return "moderate";
        if (k <= 0.80) return "substantial";
        return "almost perfect";
    }
}