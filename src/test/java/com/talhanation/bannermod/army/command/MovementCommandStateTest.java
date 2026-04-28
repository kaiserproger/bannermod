package com.talhanation.bannermod.army.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MovementCommandStateTest {

    @Test
    void formationTargetStatesMatchMovementFormationRuntimeBranches() {
        assertTrue(MovementCommandState.usesFormationTarget(MovementCommandState.HOLD_POSITION));
        assertTrue(MovementCommandState.usesFormationTarget(MovementCommandState.HOLD_OWNER_POSITION));
        assertTrue(MovementCommandState.usesFormationTarget(MovementCommandState.MOVE_TO_POSITION));
        assertTrue(MovementCommandState.usesFormationTarget(MovementCommandState.FORWARD));
        assertTrue(MovementCommandState.usesFormationTarget(MovementCommandState.BACKWARD));

        assertFalse(MovementCommandState.usesFormationTarget(MovementCommandState.WANDER));
        assertFalse(MovementCommandState.usesFormationTarget(MovementCommandState.FOLLOW));
        assertFalse(MovementCommandState.usesFormationTarget(MovementCommandState.BACK_TO_POSITION));
        assertFalse(MovementCommandState.usesFormationTarget(MovementCommandState.PROTECT));
    }

    @Test
    void pointMoveStateMatchesQueuedCompletionRuntimeBranch() {
        assertTrue(MovementCommandState.isPointMove(MovementCommandState.MOVE_TO_POSITION));
        assertFalse(MovementCommandState.isPointMove(MovementCommandState.HOLD_POSITION));
        assertFalse(MovementCommandState.isPointMove(MovementCommandState.FORWARD));
    }
}
