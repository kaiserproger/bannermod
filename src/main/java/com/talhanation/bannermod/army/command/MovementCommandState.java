package com.talhanation.bannermod.army.command;

public final class MovementCommandState {
    public static final int WANDER = 0;
    public static final int FOLLOW = 1;
    /** Hold current position without formation; regroup around median with formation. */
    public static final int HOLD_POSITION = 2;
    public static final int BACK_TO_POSITION = 3;
    /** Hold the owner position without formation; move formation to owner with formation. */
    public static final int HOLD_OWNER_POSITION = 4;
    public static final int PROTECT = 5;
    public static final int MOVE_TO_POSITION = 6;
    public static final int FORWARD = 7;
    public static final int BACKWARD = 8;

    private MovementCommandState() {
    }

    public static boolean usesFormationTarget(int state) {
        return state == HOLD_POSITION
                || state == HOLD_OWNER_POSITION
                || state == MOVE_TO_POSITION
                || state == FORWARD
                || state == BACKWARD;
    }

    public static boolean isPointMove(int state) {
        return state == MOVE_TO_POSITION;
    }
}
