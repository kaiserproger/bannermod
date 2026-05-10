package com.talhanation.bannermod.settlement.goal;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

/**
 * Runtime record of one active {@link ResidentGoal} execution.
 * Kept mutable for cheap per-tick advance without allocations.
 */
public final class ResidentTask {

    private final ResourceLocation goalId;
    private final long startGameTime;
    private final int maxTicks;
    private long lastAdvancedGameTime;
    private int elapsedTicks;
    private boolean done;
    @Nullable
    private ResidentStopReason stopReason;

    public ResidentTask(ResourceLocation goalId, long startGameTime, int maxTicks) {
        if (goalId == null) {
            throw new IllegalArgumentException("goalId must not be null");
        }
        if (maxTicks < 0) {
            throw new IllegalArgumentException("maxTicks must be non-negative");
        }
        this.goalId = goalId;
        this.startGameTime = startGameTime;
        this.maxTicks = maxTicks;
        this.lastAdvancedGameTime = startGameTime;
        this.elapsedTicks = 0;
        this.done = false;
        this.stopReason = null;
    }

    public ResourceLocation goalId() {
        return this.goalId;
    }

    public long startGameTime() {
        return this.startGameTime;
    }

    public int maxTicks() {
        return this.maxTicks;
    }

    public int elapsedTicks() {
        return this.elapsedTicks;
    }

    public boolean isDone() {
        return this.done;
    }

    @Nullable
    public ResidentStopReason stopReason() {
        return this.stopReason;
    }

    void syncElapsed(long gameTime) {
        if (this.done) {
            return;
        }
        long normalizedGameTime = Math.max(this.lastAdvancedGameTime, gameTime);
        long delta = Math.max(0L, normalizedGameTime - this.lastAdvancedGameTime);
        this.lastAdvancedGameTime = normalizedGameTime;
        this.elapsedTicks = (int) Math.min(Integer.MAX_VALUE, (long) this.elapsedTicks + delta);
    }

    boolean finishTimedOutIfExpired() {
        if (this.done || this.maxTicks <= 0 || this.elapsedTicks < this.maxTicks) {
            return false;
        }
        this.done = true;
        this.stopReason = ResidentStopReason.TIMED_OUT;
        return true;
    }

    /** Advance by elapsed game time since the last scheduler heartbeat. */
    void advance(long gameTime) {
        this.syncElapsed(gameTime);
        if (this.done) {
            return;
        }
        this.finishTimedOutIfExpired();
    }

    /** Mark finished with the given reason. Idempotent: first call wins. */
    void finish(ResidentStopReason reason) {
        if (this.done) {
            return;
        }
        this.done = true;
        this.stopReason = reason;
    }
}
