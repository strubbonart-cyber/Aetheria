package dev.strubbonart.skyblockqol.features.timer;

/**
 * Client-side countdown timer state.
 *
 * Derived from the original Aetheria timer implementation.
 * Copyright (C) 2026 Hamlock and Contributors.
 * Licensed under the Aetheria Mod License (AML-1.0).
 */
public final class TimerManager {
    private static final TimerManager INSTANCE = new TimerManager();

    private long endTimeMs = -1L;
    private long pausedRemainingMs = -1L;
    private long totalDurationMs;
    private boolean expiryReported;

    private TimerManager() {
    }

    public static TimerManager getInstance() {
        return INSTANCE;
    }

    public synchronized boolean isRunning() {
        return endTimeMs > 0 && pausedRemainingMs < 0;
    }

    public synchronized boolean isActive() {
        return endTimeMs > 0 || pausedRemainingMs >= 0;
    }

    public synchronized boolean isPaused() {
        return pausedRemainingMs >= 0;
    }

    public synchronized long getTotalDurationMs() {
        return totalDurationMs;
    }

    public synchronized void start(long durationMs) {
        if (durationMs <= 0) {
            throw new IllegalArgumentException("Timer duration must be positive");
        }
        totalDurationMs = durationMs;
        endTimeMs = System.currentTimeMillis() + durationMs;
        pausedRemainingMs = -1L;
        expiryReported = false;
    }

    public synchronized void cancel() {
        endTimeMs = -1L;
        pausedRemainingMs = -1L;
        expiryReported = false;
    }

    public synchronized void pause() {
        if (!isRunning()) return;
        pausedRemainingMs = getRemainingMs();
        endTimeMs = -1L;
    }

    public synchronized void resume() {
        if (!isPaused()) return;
        endTimeMs = System.currentTimeMillis() + pausedRemainingMs;
        pausedRemainingMs = -1L;
    }

    public synchronized void addTime(long millis) {
        if (millis <= 0 || !isActive()) return;
        if (isPaused()) {
            pausedRemainingMs = Math.addExact(pausedRemainingMs, millis);
        } else {
            endTimeMs = Math.addExact(endTimeMs, millis);
            expiryReported = false;
        }
        totalDurationMs = Math.max(totalDurationMs, getRemainingMs());
    }

    public synchronized long getRemainingMs() {
        if (isPaused()) return pausedRemainingMs;
        if (endTimeMs < 0) return 0L;
        return Math.max(0L, endTimeMs - System.currentTimeMillis());
    }

    /**
     * Returns true exactly once when a running timer reaches zero.
     */
    public synchronized boolean pollExpired() {
        if (!isActive() || isPaused() || expiryReported || getRemainingMs() > 0) {
            return false;
        }
        expiryReported = true;
        return true;
    }
}
