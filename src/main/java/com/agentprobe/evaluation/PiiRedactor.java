package com.agentprobe.evaluation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Redacts personal data in stored transcripts when agentprobe.privacy.redact-transcripts=true. */
@Component
public class PiiRedactor {

    private final boolean enabled;

    public PiiRedactor(@Value("${agentprobe.privacy.redact-transcripts:false}") boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String apply(String text) {
        return enabled ? PiiDetector.redact(text) : text;
    }
}