package com.talhanation.bannermod.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AdaptiveRuntimeBudgets {
    private static final long OVERLOAD_TICK_NANOS = 52_000_000L;
    private static final long RECOVERY_TICK_NANOS = 45_000_000L;
    private static final int RECOVERY_SAMPLES_REQUIRED = 20;

    private static final Map<String, BudgetState> STATES = new ConcurrentHashMap<>();
    private static volatile PressureMode pressureMode = PressureMode.STABLE;
    private static volatile int recoverySamples;

    private AdaptiveRuntimeBudgets() {
    }

    public static void recordServerTickNanos(long tickNanos) {
        if (tickNanos <= 0L) {
            return;
        }
        if (tickNanos >= OVERLOAD_TICK_NANOS) {
            pressureMode = PressureMode.OVERLOADED;
            recoverySamples = 0;
        } else if (tickNanos <= RECOVERY_TICK_NANOS) {
            int samples = Math.min(RECOVERY_SAMPLES_REQUIRED, recoverySamples + 1);
            recoverySamples = samples;
            pressureMode = samples >= RECOVERY_SAMPLES_REQUIRED ? PressureMode.RECOVERING : PressureMode.HOLD;
        } else {
            pressureMode = PressureMode.HOLD;
            recoverySamples = 0;
        }
    }

    public static int intBudget(String key, int ceiling, int floor) {
        if (ceiling <= 0) {
            return 0;
        }
        int clampedFloor = Math.max(0, Math.min(floor, ceiling));
        return (int) longBudget(key, ceiling, clampedFloor);
    }

    public static long longBudget(String key, long ceiling, long floor) {
        if (ceiling <= 0L) {
            return 0L;
        }
        long clampedFloor = Math.max(0L, Math.min(floor, ceiling));
        BudgetState state = STATES.computeIfAbsent(key, ignored -> new BudgetState(ceiling));
        return state.apply(ceiling, clampedFloor, pressureMode);
    }

    public static int throttleTicks(String key, int floor, int ceiling) {
        int clampedFloor = Math.max(1, floor);
        int clampedCeiling = Math.max(clampedFloor, ceiling);
        long inverseBudget = longBudget(key, clampedCeiling, clampedFloor);
        return (int) Math.max(clampedFloor, clampedCeiling - inverseBudget + clampedFloor);
    }

    static void resetForTests() {
        STATES.clear();
        pressureMode = PressureMode.STABLE;
        recoverySamples = 0;
    }

    private enum PressureMode {
        STABLE,
        OVERLOADED,
        HOLD,
        RECOVERING
    }

    private static final class BudgetState {
        private long current;

        private BudgetState(long current) {
            this.current = current;
        }

        private synchronized long apply(long ceiling, long floor, PressureMode mode) {
            current = Math.max(floor, Math.min(current, ceiling));
            if (mode == PressureMode.OVERLOADED) {
                long reduction = Math.max(1L, Math.max(ceiling / 4L, (current - floor) / 2L));
                current = Math.max(floor, current - reduction);
            } else if (mode == PressureMode.RECOVERING) {
                long increase = Math.max(1L, ceiling / 10L);
                current = Math.min(ceiling, current + increase);
            }
            return current;
        }
    }
}
