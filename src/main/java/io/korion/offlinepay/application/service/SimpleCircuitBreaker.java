package io.korion.offlinepay.application.service;

public final class SimpleCircuitBreaker {

    private final int failureThreshold;
    private final long resetTimeoutMs;
    private int consecutiveFailures;
    private long openedAtEpochMs;
    private boolean open;

    public SimpleCircuitBreaker(int failureThreshold, long resetTimeoutMs) {
        this.failureThreshold = Math.max(1, failureThreshold);
        this.resetTimeoutMs = Math.max(1000L, resetTimeoutMs);
    }

    public synchronized void assertCallable() {
        if (!open) {
            return;
        }
        if (System.currentTimeMillis() - openedAtEpochMs >= resetTimeoutMs) {
            open = false;
            consecutiveFailures = 0;
            openedAtEpochMs = 0L;
            return;
        }
        throw new IllegalStateException("circuit is open");
    }

    public synchronized boolean onSuccess() {
        boolean recovered = open || consecutiveFailures > 0;
        open = false;
        consecutiveFailures = 0;
        openedAtEpochMs = 0L;
        return recovered;
    }

    public synchronized boolean onFailure() {
        consecutiveFailures++;
        if (!open && consecutiveFailures >= failureThreshold) {
            open = true;
            openedAtEpochMs = System.currentTimeMillis();
            return true;
        }
        return false;
    }

    public synchronized boolean isOpen() {
        return open;
    }
}
