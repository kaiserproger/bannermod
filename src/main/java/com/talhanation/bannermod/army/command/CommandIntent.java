package com.talhanation.bannermod.army.command;

import com.talhanation.bannermod.ai.military.CombatStance;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * First-class army-command data object.
 *
 * <p>Why a sealed interface:</p>
 * <ul>
 *   <li>Each verb is a record variant — type-safe dispatching via pattern matching in the
 *       {@link CommandIntentDispatcher}.</li>
 *   <li>Every variant carries shared command meta fields ({@link #queueMode()},
 *       {@link #priority()}, {@link #issuedAtGameTime()}) so queueing and logging are
 *       uniform across command kinds.</li>
 *   <li>Records are comparable and serialisable by shape — good enough for an in-memory
 *       ring-buffer log and future replay/undo.</li>
 * </ul>
 *
 * <p>This layer sits <em>between</em> network packets and the legacy
 * {@code CommandEvents} / {@code MovementFormationCommandService} services. Packets still
 * define the wire protocol; the intent is the server-side contract for "what the player
 * asked for". Callers translate a packet → {@link CommandIntent} → dispatcher → legacy
 * service call.</p>
 */
public sealed interface CommandIntent {

    /** Monotonic server tick when the player issued the command. */
    long issuedAtGameTime();

    /** Higher = more urgent. */
    int priority();

    /** Append to the unit's command queue ({@code true}) or replace it ({@code false}). */
    boolean queueMode();

    /** Type enum — convenient for logging and switch statements that don't want pattern matching. */
    CommandIntentType type();

    /**
     * Move the selected recruits. Mirrors {@code onMovementCommand(player, recruits, state, formation, tight)}.
     *
     * <p>{@link #movementState()} uses {@link MovementCommandState}: 0=wander, 1=follow,
     * 2=hold current position or regroup formation, 3=back to saved position,
     * 4=hold owner position or move formation to owner, 5=protect, 6=move to position,
     * 7=formation/single forward, 8=formation/single backward.
     * {@link #formation()} uses the existing formation enum (0=none, 1=line, 2=line-up,
     * 3=square, 4=triangle, 5=circle, 6=movement). {@link #targetPos()} is optional — only
     * meaningful for move-to-position and formation-building variants.</p>
     */
    record Movement(
            long issuedAtGameTime,
            int priority,
            boolean queueMode,
            int movementState,
            int formation,
            boolean tight,
            @Nullable Vec3 targetPos
    ) implements CommandIntent {
        @Override public CommandIntentType type() { return CommandIntentType.MOVEMENT; }
    }

    /** Rotate the formation without moving. Mirrors {@code onFaceCommand}. */
    record Face(
            long issuedAtGameTime,
            int priority,
            boolean queueMode,
            int formation,
            boolean tight
    ) implements CommandIntent {
        @Override public CommandIntentType type() { return CommandIntentType.FACE; }
    }

    /** Attack aggression toggle. Mirrors {@code onAttackCommand} + current group scope. */
    record Attack(
            long issuedAtGameTime,
            int priority,
            boolean queueMode,
            UUID groupUuid
    ) implements CommandIntent {
        @Override public CommandIntentType type() { return CommandIntentType.ATTACK; }
    }

    /** Strategic-fire toggle for ranged recruits. Mirrors {@code onStrategicFireCommand}. */
    record StrategicFire(
            long issuedAtGameTime,
            int priority,
            boolean queueMode,
            UUID groupUuid,
            boolean shouldFire
    ) implements CommandIntent {
        @Override public CommandIntentType type() { return CommandIntentType.STRATEGIC_FIRE; }
    }

    /** Aggression state change for a single recruit. Mirrors {@code onAggroCommand}. */
    record Aggro(
            long issuedAtGameTime,
            int priority,
            boolean queueMode,
            int state,
            UUID groupUuid,
            boolean fromGui
    ) implements CommandIntent {
        @Override public CommandIntentType type() { return CommandIntentType.AGGRO; }
    }

    /** Combat stance change for a recruit group. Mirrors {@code onCombatStanceCommand}. */
    record CombatStanceChange(
            long issuedAtGameTime,
            int priority,
            boolean queueMode,
            CombatStance stance,
            UUID groupUuid
    ) implements CommandIntent {
        @Override public CommandIntentType type() { return CommandIntentType.COMBAT_STANCE; }
    }

    record SiegeMachine(
            long issuedAtGameTime,
            int priority,
            boolean queueMode,
            UUID mountUuid,
            UUID groupUuid,
            boolean returnToKnownMount
    ) implements CommandIntent {
        @Override public CommandIntentType type() { return CommandIntentType.SIEGE_MACHINE; }
    }
}
