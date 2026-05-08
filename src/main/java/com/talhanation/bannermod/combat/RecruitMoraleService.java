package com.talhanation.bannermod.combat;

import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Drives {@link MoralePolicy} on a per-recruit basis.
 *
 * <p>The service stores one {@link Entry} per recruit UUID with the last-evaluated state and
 * a {@code routEndTick} (the game-tick at which the rout window closes). An entry is created
 * lazily on the first evaluation and left in place — recruits removed from the world have
 * their entries reaped by {@link #invalidate(UUID)}, called from the existing entity-leave
 * hook in {@link com.talhanation.bannermod.combat.runtime.RecruitCombatRuntime}.</p>
 *
 * <p>Transitions into {@link MoraleState#ROUTED} fire two side effects:
 * <ol>
 *   <li>The recruit's {@code FLEEING} sync flag is set so the existing {@link
 *       com.talhanation.bannermod.entity.military.AbstractRecruitEntity#setFleeing(boolean)}
 *       state is shared with clients (used today by render-side cues).</li>
 *   <li>The owner (if online) receives a chat message naming the rout reason tokens emitted
 *       by the policy. This is the player-facing "see why" half of COMBAT-001 acceptance.</li>
 * </ol>
 *
 * <p>The {@link RecruitMoraleRoutGoal} reads {@link #isRouted(AbstractRecruitEntity, long)}
 * to decide whether to override combat with disengagement; consult the goal for the in-world
 * effect of a rout. {@link MoralePolicy} stays the single source of truth for state
 * resolution — the service only owns the timeline + side-effect plumbing.</p>
 */
public final class RecruitMoraleService {

    /** Re-evaluate every 40 ticks (2 seconds) to keep the policy off the hot per-tick path. */
    public static final int EVALUATION_INTERVAL_TICKS = 40;

    /** Once a squad routs, it disengages for 200 ticks (10 seconds) before re-evaluation. */
    public static final long ROUT_WINDOW_TICKS = 200L;

    /**
     * Sliding-window length (ticks) used by the suppression accumulator. A damage event
     * older than {@code gameTime - SUPPRESSION_WINDOW_TICKS} is treated as no longer
     * contributing to the SUSTAINED_FIRE token. 60 ticks (3 seconds) matches the gameplay
     * intent of "sustained fire" — multiple hits within a short burst, not a slow trickle.
     */
    public static final long SUPPRESSION_WINDOW_TICKS = 60L;

    private static final Map<UUID, Entry> STATE = new ConcurrentHashMap<>();

    private RecruitMoraleService() {
    }

    /**
     * Per-recruit timeline entry. {@code lastState} is the previous evaluation result so the
     * service can detect transitions; {@code routEndTick} is the absolute game-tick at which
     * the rout window expires (0 means "not currently routed").
     *
     * <p>{@code damageWindowStartTick} / {@code damageEventsInWindow} drive the suppression
     * accumulator: the window auto-resets when a new event arrives more than
     * {@link #SUPPRESSION_WINDOW_TICKS} after the window started, so a slow trickle of one hit
     * every few seconds never trips SUSTAINED_FIRE.</p>
     */
    public static final class Entry {
        public MoraleState lastState = MoraleState.STEADY;
        public long routEndTick;
        public long lastEvaluationTick = Long.MIN_VALUE;
        public long damageWindowStartTick = Long.MIN_VALUE;
        public int damageEventsInWindow;
    }

    public static Entry stateFor(UUID recruitUuid) {
        return STATE.computeIfAbsent(recruitUuid, k -> new Entry());
    }

    /** True if the recruit's rout window has not yet expired at {@code gameTime}. */
    public static boolean isRouted(AbstractRecruitEntity recruit, long gameTime) {
        if (recruit == null) return false;
        Entry entry = STATE.get(recruit.getUUID());
        return entry != null && entry.routEndTick > gameTime;
    }

    /**
     * Last evaluated {@link MoraleState} for the recruit, or {@link MoraleState#STEADY} if no
     * entry exists yet (no evaluation tick has run for this recruit). The damage path reads
     * this to apply {@link MoralePolicy#attackMultiplierFor(MoraleState)} on outgoing hits.
     */
    public static MoraleState lastStateOf(AbstractRecruitEntity recruit) {
        if (recruit == null) return MoraleState.STEADY;
        Entry entry = STATE.get(recruit.getUUID());
        return entry == null ? MoraleState.STEADY : entry.lastState;
    }

    /** Outgoing-damage multiplier the recruit damage path should apply this swing. */
    public static double attackMultiplierFor(AbstractRecruitEntity recruit) {
        return MoralePolicy.attackMultiplierFor(lastStateOf(recruit));
    }

    /**
     * Record one damage-taken event against the recruit's suppression window. Idempotent
     * within a single tick (callers must not re-record the same hit). The window auto-resets
     * when a new event arrives more than {@link #SUPPRESSION_WINDOW_TICKS} after
     * {@code damageWindowStartTick}; otherwise the event count is incremented.
     */
    public static void recordDamageTaken(UUID recruitUuid, long gameTime) {
        if (recruitUuid == null) return;
        Entry entry = stateFor(recruitUuid);
        // Open a fresh window on the first event (the Long.MIN_VALUE sentinel would
        // otherwise overflow under subtraction), or whenever the previous window has
        // expired. Otherwise accumulate inside the still-open window.
        if (entry.damageEventsInWindow == 0
                || gameTime - entry.damageWindowStartTick > SUPPRESSION_WINDOW_TICKS) {
            entry.damageWindowStartTick = gameTime;
            entry.damageEventsInWindow = 1;
        } else {
            entry.damageEventsInWindow++;
        }
    }

    /** Convenience overload used from the entity hurt() hook. */
    public static void recordDamageTaken(AbstractRecruitEntity recruit, long gameTime) {
        if (recruit != null) {
            recordDamageTaken(recruit.getUUID(), gameTime);
        }
    }

    /**
     * Number of damage events recorded within the still-open suppression window at
     * {@code gameTime}. Returns 0 once the window has expired so a stale window never
     * keeps tripping SUSTAINED_FIRE after the actor walks out of the line of fire.
     */
    public static int recentDamageEventCount(UUID recruitUuid, long gameTime) {
        if (recruitUuid == null) return 0;
        Entry entry = STATE.get(recruitUuid);
        if (entry == null || entry.damageEventsInWindow == 0) return 0;
        if (gameTime - entry.damageWindowStartTick > SUPPRESSION_WINDOW_TICKS) {
            return 0;
        }
        return entry.damageEventsInWindow;
    }

    /** Drop the recruit's entry. Called from the entity-leave hook. */
    public static void invalidate(UUID recruitUuid) {
        if (recruitUuid != null) {
            STATE.remove(recruitUuid);
        }
    }

    /** Reset everything — used by tests and by the dedicated test harness. */
    public static void resetForTests() {
        STATE.clear();
    }

    /**
     * Run one evaluation step for {@code recruit} at {@code gameTime}. The interval gate is
     * applied here, so callers can call this every tick from the recruit AI loop without
     * adding their own throttle.
     *
     * <p>Returns the most recently observed {@link MoraleAssessment} regardless of whether
     * a fresh evaluation actually ran this call — callers can use it to drive UI / logging
     * even on idle ticks.</p>
     */
    public static MoraleAssessment tick(AbstractRecruitEntity recruit, ServerLevel level, long gameTime) {
        Entry entry = stateFor(recruit.getUUID());
        if (gameTime - entry.lastEvaluationTick < EVALUATION_INTERVAL_TICKS) {
            return new MoraleAssessment(entry.lastState, java.util.List.of());
        }
        entry.lastEvaluationTick = gameTime;
        MoraleSnapshot snapshot = RecruitMoraleSampler.sample(recruit, level);
        MoraleAssessment assessment = MoralePolicy.evaluate(snapshot);
        applyTransition(recruit, entry, assessment, gameTime);
        return assessment;
    }

    private static void applyTransition(AbstractRecruitEntity recruit,
                                        Entry entry,
                                        MoraleAssessment assessment,
                                        long gameTime) {
        MoraleState previous = entry.lastState;
        MoraleState next = assessment.state();
        entry.lastState = next;
        if (next == MoraleState.ROUTED) {
            entry.routEndTick = gameTime + ROUT_WINDOW_TICKS;
            recruit.setFleeing(true);
            if (previous != MoraleState.ROUTED) {
                broadcastRoutToOwner(recruit, assessment);
            }
        } else if (entry.routEndTick <= gameTime) {
            // Window has expired and the new state is not ROUTED again — clear the visual
            // cue so the recruit returns to its normal stance.
            if (recruit.getFleeing()) {
                recruit.setFleeing(false);
            }
        }
    }

    private static void broadcastRoutToOwner(AbstractRecruitEntity recruit, MoraleAssessment assessment) {
        UUID ownerUuid = recruit.getOwnerUUID();
        if (ownerUuid == null) return;
        if (!(recruit.level() instanceof ServerLevel serverLevel)) return;
        Player owner = serverLevel.getPlayerByUUID(ownerUuid);
        if (owner == null) return;
        String reasonText = String.join(", ", assessment.reasons());
        Component name = recruit.getName();
        owner.sendSystemMessage(Component.translatable(
                "bannermod.morale.routed",
                name,
                reasonText
        ));
    }
}
