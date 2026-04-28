package com.talhanation.bannermod.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdaptiveRuntimeBudgetsTest {
    @BeforeEach
    void setUp() {
        AdaptiveRuntimeBudgets.resetForTests();
    }

    @AfterEach
    void tearDown() {
        AdaptiveRuntimeBudgets.resetForTests();
    }

    @Test
    void overloadedTicksReduceBudgetToFloor() {
        assertEquals(16, AdaptiveRuntimeBudgets.intBudget("test", 16, 4));

        for (int i = 0; i < 8; i++) {
            AdaptiveRuntimeBudgets.recordServerTickNanos(70_000_000L);
            AdaptiveRuntimeBudgets.intBudget("test", 16, 4);
        }

        assertEquals(4, AdaptiveRuntimeBudgets.intBudget("test", 16, 4));
    }

    @Test
    void recoveryWaitsForHysteresisThenRisesGradually() {
        for (int i = 0; i < 8; i++) {
            AdaptiveRuntimeBudgets.recordServerTickNanos(70_000_000L);
            AdaptiveRuntimeBudgets.intBudget("test", 20, 5);
        }
        assertEquals(5, AdaptiveRuntimeBudgets.intBudget("test", 20, 5));

        for (int i = 0; i < 19; i++) {
            AdaptiveRuntimeBudgets.recordServerTickNanos(30_000_000L);
            AdaptiveRuntimeBudgets.intBudget("test", 20, 5);
        }
        assertEquals(5, AdaptiveRuntimeBudgets.intBudget("test", 20, 5));

        AdaptiveRuntimeBudgets.recordServerTickNanos(30_000_000L);
        int firstRecoveryBudget = AdaptiveRuntimeBudgets.intBudget("test", 20, 5);
        assertTrue(firstRecoveryBudget > 5 && firstRecoveryBudget < 20);

        for (int i = 0; i < 10; i++) {
            AdaptiveRuntimeBudgets.recordServerTickNanos(30_000_000L);
            AdaptiveRuntimeBudgets.intBudget("test", 20, 5);
        }
        assertEquals(20, AdaptiveRuntimeBudgets.intBudget("test", 20, 5));
    }

    @Test
    void middlePressureHoldsBudgetUntilRecoveryThresholdIsStable() {
        for (int i = 0; i < 8; i++) {
            AdaptiveRuntimeBudgets.recordServerTickNanos(70_000_000L);
            AdaptiveRuntimeBudgets.intBudget("test", 20, 5);
        }
        assertEquals(5, AdaptiveRuntimeBudgets.intBudget("test", 20, 5));

        for (int i = 0; i < 20; i++) {
            AdaptiveRuntimeBudgets.recordServerTickNanos(48_000_000L);
            assertEquals(5, AdaptiveRuntimeBudgets.intBudget("test", 20, 5));
        }
    }

    @Test
    void formationMapThrottleExpandsUnderPressureAndShrinksAfterRecovery() {
        assertEquals(1, AdaptiveRuntimeBudgets.throttleTicks("throttle", 1, 5));

        for (int i = 0; i < 8; i++) {
            AdaptiveRuntimeBudgets.recordServerTickNanos(70_000_000L);
            AdaptiveRuntimeBudgets.throttleTicks("throttle", 1, 5);
        }
        assertEquals(5, AdaptiveRuntimeBudgets.throttleTicks("throttle", 1, 5));

        for (int i = 0; i < 25; i++) {
            AdaptiveRuntimeBudgets.recordServerTickNanos(30_000_000L);
            AdaptiveRuntimeBudgets.throttleTicks("throttle", 1, 5);
        }
        assertTrue(AdaptiveRuntimeBudgets.throttleTicks("throttle", 1, 5) < 5);
    }
}
