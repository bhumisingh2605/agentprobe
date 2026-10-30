package com.agentprobe.security;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Fixed-window limiter, one window per minute. Fails open: if Redis is down, requests are allowed. */
@Component
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    public record Decision(boolean allowed, int limit, long remaining, long retryAfterSeconds) {
    }

    private final CounterStore store;
    private final boolean enabled;
    private final int apiLimit;
    private final int authLimit;
    private final int runsLimit;
    private final int anonLimit;
    private final boolean trustForwardedFor;

    public RateLimiter(CounterStore store,
                       @Value("${agentprobe.ratelimit.enabled:true}") boolean enabled,
                       @Value("${agentprobe.ratelimit.api-per-minute:240}") int apiLimit,
                       @Value("${agentprobe.ratelimit.auth-per-minute:20}") int authLimit,
                       @Value("${agentprobe.ratelimit.runs-per-minute:10}") int runsLimit,
                       @Value("${agentprobe.ratelimit.anon-per-minute:60}") int anonLimit,
                       @Value("${agentprobe.ratelimit.trust-forwarded-for:false}") boolean trustForwardedFor) {
        this.store = store;
        this.enabled = enabled;
        this.apiLimit = apiLimit;
        this.authLimit = authLimit;
        this.runsLimit = runsLimit;
        this.anonLimit = anonLimit;
        this.trustForwardedFor = trustForwardedFor;
    }

    public Decision check(String bucket, String subject) {
        return check(bucket, subject, System.currentTimeMillis());
    }

    public Decision check(String bucket, String subject, long nowMillis) {
        int limit = limitFor(bucket);
        if (!enabled) {
            return new Decision(true, limit, limit, 0);
        }
        long minute = nowMillis / 60_000L;
        long retryAfter = 60 - (nowMillis / 1000L) % 60;
        try {
            long count = store.increment("rl:" + bucket + ":" + subject + ":" + minute, Duration.ofMinutes(2));
            boolean allowed = count <= limit;
            return new Decision(allowed, limit, Math.max(0, limit - count), allowed ? 0 : retryAfter);
        } catch (RuntimeException e) {
            log.warn("Rate limiter unavailable, allowing request: {}", e.getMessage());
            return new Decision(true, limit, limit, 0);
        }
    }

    int limitFor(String bucket) {
        switch (bucket) {
            case "auth": return authLimit;
            case "runs": return runsLimit;
            case "anon": return anonLimit;
            default: return apiLimit;
        }
    }

    /** Behind a trusted reverse proxy (nginx) use the first X-Forwarded-For entry. */
    public String clientIp(HttpServletRequest req) {
        if (trustForwardedFor) {
            String xff = req.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                String first = xff.split(",")[0].trim();
                if (!first.isEmpty() && first.length() <= 64) {
                    return first;
                }
            }
        }
        return req.getRemoteAddr();
    }
}