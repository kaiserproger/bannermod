package com.talhanation.bannermod.army.command;

import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.events.CommandEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Per-recruit {@link CommandIntentQueue} registry and the advancement logic that pops the
 * head intent when the current one completes.
 *
 * <p>Completion is intent-type specific:</p>
 * <ul>
 *   <li><b>Movement ({@link MovementCommandState#MOVE_TO_POSITION})</b>: recruit is within
 *       {@link #MOVE_TO_POINT_THRESHOLD} blocks of its hold_pos, <em>or</em> the per-intent
 *       time budget elapsed (stuck-recover).</li>
 *   <li><b>Movement (other states, e.g. hold/follow/wander)</b>: never auto-completes. The
 *       player replaces it with a new non-queued command.</li>
 *   <li><b>Face / Attack / StrategicFire / Aggro</b>: one-shot — considered complete as soon
 *       as applied, advancing the queue on the very next tick.</li>
 * </ul>
 *
 * <p>The queue is in-memory only; restart wipes it and keeps the first slice cheap.</p>
 */
public final class CommandIntentQueueRuntime {
    public static final double MOVE_TO_POINT_THRESHOLD = 2.5D;
    public static final long MOVE_TO_POINT_TIMEOUT_TICKS = 20L * 60L;

    private static final CommandIntentQueueRuntime INSTANCE = new CommandIntentQueueRuntime();

    private final Map<UUID, CommandIntentQueue> queues = new HashMap<>();
    private final Map<UUID, UUID> issuerByRecruit = new HashMap<>();
    private final Map<UUID, Long> startedAtByRecruit = new HashMap<>();

    private CommandIntentQueueRuntime() {
    }

    public static CommandIntentQueueRuntime instance() {
        return INSTANCE;
    }

    public synchronized CommandIntentQueue queueFor(UUID recruitUuid) {
        Objects.requireNonNull(recruitUuid, "recruitUuid");
        return queues.computeIfAbsent(recruitUuid, ignored -> new CommandIntentQueue());
    }

    public synchronized void clearFor(UUID recruitUuid) {
        if (recruitUuid == null) return;
        CommandIntentQueue queue = queues.get(recruitUuid);
        if (queue != null) {
            queue.clear();
        }
        issuerByRecruit.remove(recruitUuid);
        startedAtByRecruit.remove(recruitUuid);
    }

    public synchronized int size() {
        return queues.size();
    }

    public synchronized int sizeFor(UUID recruitUuid) {
        if (recruitUuid == null) return 0;
        CommandIntentQueue queue = queues.get(recruitUuid);
        return queue == null ? 0 : queue.size();
    }

    public synchronized Optional<CommandIntent> headFor(UUID recruitUuid) {
        CommandIntentQueue queue = recruitUuid == null ? null : queues.get(recruitUuid);
        return queue == null ? Optional.empty() : queue.head();
    }

    public synchronized Optional<UUID> issuerFor(UUID recruitUuid) {
        return Optional.ofNullable(issuerByRecruit.get(recruitUuid));
    }

    public static boolean canExecuteQueued(CommandIntent intent) {
        return intent instanceof CommandIntent.Movement
                || intent instanceof CommandIntent.Face
                || intent instanceof CommandIntent.Attack
                || intent instanceof CommandIntent.StrategicFire
                || intent instanceof CommandIntent.Aggro;
    }

    /**
     * Append {@code intent} to the queue of every actor. Applies the head immediately if the
     * queue was empty before the append. Returns the number of recruits whose queue was
     * actually modified.
     */
    public synchronized int appendForActors(@Nullable ServerPlayer issuer,
                                            CommandIntent intent,
                                            List<AbstractRecruitEntity> actors,
                                            long gameTime) {
        Objects.requireNonNull(intent, "intent");
        if (!canExecuteQueued(intent)) {
            throw new IllegalArgumentException("Unsupported queued command intent: " + intent.type());
        }
        if (actors == null || actors.isEmpty()) {
            return 0;
        }
        int modified = 0;
        for (AbstractRecruitEntity recruit : actors) {
            if (recruit == null || recruit.getUUID() == null) continue;
            CommandIntentQueue queue = queueFor(recruit.getUUID());
            boolean wasEmpty = queue.isEmpty();
            queue.append(intent);
            if (issuer != null) {
                issuerByRecruit.put(recruit.getUUID(), issuer.getUUID());
            }
            if (wasEmpty) {
                startedAtByRecruit.put(recruit.getUUID(), gameTime);
                applyHead(issuer, recruit, intent);
            }
            modified++;
        }
        return modified;
    }

    /** Drop every queued intent for the given actors and mark them without an active head. */
    public synchronized void clearForActors(List<AbstractRecruitEntity> actors) {
        if (actors == null) return;
        for (AbstractRecruitEntity recruit : actors) {
            if (recruit == null) continue;
            clearFor(recruit.getUUID());
        }
    }

    /**
     * Advance every queue whose head has completed. Called by the server tick loop.
     */
    public synchronized void tick(@Nullable MinecraftServer server, long gameTime) {
        if (server == null) {
            return;
        }
        List<UUID> advanceIds = new ArrayList<>();
        for (Map.Entry<UUID, CommandIntentQueue> e : queues.entrySet()) {
            Optional<CommandIntent> head = e.getValue().head();
            if (head.isEmpty()) {
                continue;
            }
            UUID recruitUuid = e.getKey();
            AbstractRecruitEntity recruit = findRecruit(server, recruitUuid);
            if (recruit == null) {
                continue;
            }
            Long startedAt = startedAtByRecruit.get(recruitUuid);
            long activeTicks = startedAt == null ? 0L : Math.max(0L, gameTime - startedAt);
            if (isComplete(head.get(), recruit, activeTicks)) {
                advanceIds.add(recruitUuid);
            }
        }
        for (UUID recruitUuid : advanceIds) {
            advance(server, recruitUuid, gameTime);
        }
    }

    private void advance(MinecraftServer server, UUID recruitUuid, long gameTime) {
        CommandIntentQueue queue = queues.get(recruitUuid);
        if (queue == null) return;
        queue.popHead();
        if (queue.isEmpty()) {
            issuerByRecruit.remove(recruitUuid);
            startedAtByRecruit.remove(recruitUuid);
            return;
        }
        AbstractRecruitEntity recruit = findRecruit(server, recruitUuid);
        if (recruit == null) return;
        UUID issuerUuid = issuerByRecruit.get(recruitUuid);
        ServerPlayer issuer = issuerUuid == null ? null : server.getPlayerList().getPlayer(issuerUuid);
        startedAtByRecruit.put(recruitUuid, gameTime);
        applyHead(issuer, recruit, queue.head().orElseThrow());
    }

    private static boolean isComplete(CommandIntent intent, AbstractRecruitEntity recruit, long activeTicks) {
        if (intent instanceof CommandIntent.Movement move) {
            if (MovementCommandState.isPointMove(move.movementState()) || move.targetPos() != null) {
                Vec3 target = move.targetPos() != null ? move.targetPos() : recruit.holdPosVec;
                if (target == null) {
                    return activeTicks >= MOVE_TO_POINT_TIMEOUT_TICKS;
                }
                double distSq = recruit.distanceToSqr(target);
                if (distSq <= MOVE_TO_POINT_THRESHOLD * MOVE_TO_POINT_THRESHOLD) {
                    return true;
                }
                return activeTicks >= MOVE_TO_POINT_TIMEOUT_TICKS;
            }
            return false;
        }
        return true;
    }

    private static void applyHead(@Nullable Player issuer, AbstractRecruitEntity recruit, CommandIntent intent) {
        List<AbstractRecruitEntity> single = List.of(recruit);
        if (intent instanceof CommandIntent.Movement move) {
            if (issuer != null) {
                CommandEvents.onMovementCommand(issuer, single, move.movementState(), move.formation(), move.tight(), move.targetPos());
            }
        } else if (intent instanceof CommandIntent.Face face) {
            if (issuer != null) {
                CommandEvents.onFaceCommand(issuer, single, face.formation(), face.tight());
            }
        } else if (intent instanceof CommandIntent.Attack attack) {
            if (issuer != null) {
                CommandEvents.onAttackCommand(issuer, issuer.getUUID(), single, attack.groupUuid());
            }
        } else if (intent instanceof CommandIntent.StrategicFire fire) {
            if (issuer != null) {
                CommandEvents.onStrategicFireCommand(issuer, issuer.getUUID(), recruit, fire.groupUuid(), fire.shouldFire());
            }
        } else if (intent instanceof CommandIntent.Aggro aggro) {
            if (issuer != null) {
                CommandEvents.onAggroCommand(issuer.getUUID(), recruit, aggro.state(), aggro.groupUuid(), aggro.fromGui());
            }
        }
    }

    private static AbstractRecruitEntity findRecruit(MinecraftServer server, UUID recruitUuid) {
        if (server == null || recruitUuid == null) return null;
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(recruitUuid);
            if (AbstractRecruitEntity.class.isInstance(entity)) {
                AbstractRecruitEntity recruit = AbstractRecruitEntity.class.cast(entity);
                if (!recruit.isAlive()) {
                    continue;
                }
                return recruit;
            }
        }
        return null;
    }

    /** Visible for tests. */
    public synchronized void clearAllForTest() {
        queues.clear();
        issuerByRecruit.clear();
        startedAtByRecruit.clear();
    }
}
