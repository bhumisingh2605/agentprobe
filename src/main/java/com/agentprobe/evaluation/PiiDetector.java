package com.agentprobe.evaluation;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finds and redacts personal data. Patterns are deliberately simple and linear (no nested quantifiers). */
public final class PiiDetector {

    public static final Set<String> TYPES = Set.of("email", "phone", "card", "ssn");

    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]{1,64}@[A-Za-z0-9.-]{1,255}\\.[A-Za-z]{2,24}");
    private static final Pattern CARD = Pattern.compile("(?<!\\d)(?:\\d[ -]?){13,19}(?!\\d)");
    private static final Pattern SSN = Pattern.compile("(?<!\\d)\\d{3}-\\d{2}-\\d{4}(?!\\d)");
    private static final Pattern PHONE_US = Pattern.compile("(?<!\\d)(?:\\+?\\d{1,3}[\\s.-]?)?\\(?\\d{3}\\)?[\\s.-]?\\d{3}[\\s.-]?\\d{4}(?!\\d)");
    private static final Pattern PHONE_IN = Pattern.compile("(?<!\\d)(?:\\+91[\\s-]?)?[6-9]\\d{9}(?!\\d)");

    private PiiDetector() {
    }

    public record Finding(String type, String match) {
    }

    public static List<Finding> find(String text, Set<String> types) {
        List<Finding> out = new ArrayList<Finding>();
        if (text == null || text.isEmpty()) {
            return out;
        }
        if (types.contains("email")) {
            collect(EMAIL, text, "email", false, out);
        }
        if (types.contains("card")) {
            collect(CARD, text, "card", true, out);
        }
        if (types.contains("ssn")) {
            collect(SSN, text, "ssn", false, out);
        }
        if (types.contains("phone")) {
            collect(PHONE_US, text, "phone", false, out);
            collect(PHONE_IN, text, "phone", false, out);
        }
        return out;
    }

    public static String redact(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String t = replace(CARD, text, "[REDACTED_CARD]", true);
        t = replace(SSN, t, "[REDACTED_SSN]", false);
        t = replace(EMAIL, t, "[REDACTED_EMAIL]", false);
        t = replace(PHONE_IN, t, "[REDACTED_PHONE]", false);
        t = replace(PHONE_US, t, "[REDACTED_PHONE]", false);
        return t;
    }

    private static void collect(Pattern p, String text, String type, boolean luhnCheck, List<Finding> out) {
        Matcher m = p.matcher(text);
        while (m.find()) {
            String match = m.group();
            if (luhnCheck && !luhn(match.replaceAll("\\D", ""))) {
                continue;
            }
            out.add(new Finding(type, match.trim()));
        }
    }

    private static String replace(Pattern p, String text, String replacement, boolean luhnCheck) {
        Matcher m = p.matcher(text);
        StringBuilder sb = new StringBuilder();
        int last = 0;
        while (m.find()) {
            String match = m.group();
            if (luhnCheck && !luhn(match.replaceAll("\\D", ""))) {
                continue;
            }
            sb.append(text, last, m.start()).append(replacement);
            last = m.end();
        }
        return sb.append(text.substring(last)).toString();
    }

    static boolean luhn(String digits) {
        if (digits.length() < 13 || digits.length() > 19) {
            return false;
        }
        int sum = 0;
        boolean dbl = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (dbl) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            dbl = !dbl;
        }
        return sum % 10 == 0;
    }
}