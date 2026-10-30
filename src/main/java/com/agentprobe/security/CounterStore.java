package com.agentprobe.security;

import java.time.Duration;

/** Atomic counter with expiry. Redis in production, in-memory in tests. */
public interface CounterStore {

    /** Increments the counter, sets the expiry on first use, and returns the new value. */
    long increment(String key, Duration ttl);
}