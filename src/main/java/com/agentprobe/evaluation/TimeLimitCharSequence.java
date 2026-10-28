package com.agentprobe.evaluation;

/** Throws if a regex runs longer than the allowed time (catastrophic backtracking protection). */
final class TimeLimitedCharSequence implements CharSequence {

    private final CharSequence inner;
    private final long deadlineNanos;

    TimeLimitedCharSequence(CharSequence inner, long limitMillis) {
        this.inner = inner;
        this.deadlineNanos = System.nanoTime() + limitMillis * 1_000_000L;
    }

    @Override
    public char charAt(int index) {
        if (System.nanoTime() > deadlineNanos) {
            throw new IllegalStateException("regex time limit exceeded");
        }
        return inner.charAt(index);
    }

    @Override
    public int length() {
        return inner.length();
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return new TimeLimitedCharSequence(inner.subSequence(start, end),
                Math.max(1, (deadlineNanos - System.nanoTime()) / 1_000_000L));
    }

    @Override
    public String toString() {
        return inner.toString();
    }
}