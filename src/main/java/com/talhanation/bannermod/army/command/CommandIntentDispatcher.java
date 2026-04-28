package com.talhanation.bannermod.army.command;

import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.events.CommandEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Single server-side entry point for every {@link CommandIntent}.
 *
 * <p>The dispatcher currently forwards each intent to the matching public
 * {@link CommandEvents} method — the legacy service layer is still the source of truth
 * for what each command actually does. The intent → legacy call translation lives here
 * and <em>only</em> here, which is the seam that future slices will extend with:</p>
 * <ul>
 *   <li>Per-recruit command queues (append vs replace via {@link CommandIntent#queueMode()}).</li>
 *   <li>Priority-based preemption.</li>
 *   <li>Uniform logging / audit / replay via {@link CommandIntentLog}.</li>
 *   <li>Cross-intent validation (e.g. "can't move while resting" rules) in one place instead
 *       of scattered across packet handlers.</li>
 * </ul>
 *
 * <p>The dispatcher is stateless and thread-safe.</p>
 */
public final class CommandIntentDispatcher {
    private CommandIntentDispatcher() {
    }

    /**
     * Dispatch one intent on behalf of {@code player} against {@code actors}.
     *
     * <p>{@code actors} is the already-validated list of recruits the selection system
     * resolved on the server side. The dispatcher does not re-validate ownership — that
     * is the packet handler's responsibility.</p>
     *
     * @return the intent as given (for chaining / logging tests)
     */
    public static CommandIntent dispatch(@Nullable ServerPlayer player,
                                         CommandIntent intent,
                                         List<AbstractRecruitEntity> actors) {
        if (intent == null) {
            return null;
        }
        List<AbstractRecruitEntity> safeActors = actors == null ? List.of() : actors;
        safeActors = narrowBySelection(player, safeActors);
        CommandIntentLog.instance().record(player, intent, safeActors.size());

        if (player == null) {
            return intent;
        }
        if (safeActors.isEmpty()) {
            player.sendSystemMessage(Component.literal("Command rejected: no eligible recruits selected."));
            return intent;
        }

        long gameTime = player.getCommandSenderWorld().getGameTime();
        CommandIntentQueueRuntime queueRuntime = CommandIntentQueueRuntime.instance();

        if (intent.queueMode()) {
            if (!CommandIntentQueueRuntime.canExecuteQueued(intent)) {
                player.sendSystemMessage(Component.literal("Command rejected: queued " + intent.type() + " orders are not supported."));
                return intent;
            }
            // Append to each actor's queue. If the queue was empty, the runtime applies
            // the intent immediately so there's no perceived delay for the first waypoint.
            int modified = queueRuntime.appendForActors(player, intent, safeActors, gameTime);
            player.sendSystemMessage(Component.literal("Command queued: " + modified + " recruit(s), "
                    + pendingOrderCount(queueRuntime, safeActors) + " pending order(s)."));
            return intent;
        }

        // Non-queued: wipe any pending plan for these actors and apply the intent right now.
        int replaced = actorsWithPendingOrders(queueRuntime, safeActors);
        queueRuntime.clearForActors(safeActors);
        applyIntentDirectly(player, intent, safeActors);
        String suffix = replaced > 0 ? ", replaced queued orders for " + replaced + " recruit(s)." : ", immediate order.";
        player.sendSystemMessage(Component.literal("Command accepted: " + safeActors.size() + " recruit(s)" + suffix));
        return intent;
    }

    private static int actorsWithPendingOrders(CommandIntentQueueRuntime queueRuntime, List<AbstractRecruitEntity> actors) {
        int count = 0;
        for (AbstractRecruitEntity actor : actors) {
            if (actor != null && queueRuntime.sizeFor(actor.getUUID()) > 0) {
                count++;
            }
        }
        return count;
    }

    private static int pendingOrderCount(CommandIntentQueueRuntime queueRuntime, List<AbstractRecruitEntity> actors) {
        int count = 0;
        for (AbstractRecruitEntity actor : actors) {
            if (actor != null) {
                count += queueRuntime.sizeFor(actor.getUUID());
            }
        }
        return count;
    }

    /**
     * If the player has a non-empty selection, keep only actors whose UUID is in the
     * selection set. Otherwise pass the list through untouched. Selection acts as a
     * filter, not an expander — callers are still responsible for the initial resolve
     * (e.g. by group UUID and owner match).
     */
    static List<AbstractRecruitEntity> narrowBySelection(@Nullable ServerPlayer player,
                                                         List<AbstractRecruitEntity> actors) {
        if (player == null || actors == null || actors.isEmpty()) {
            return actors == null ? List.of() : actors;
        }
        java.util.Set<java.util.UUID> selection = RecruitSelectionRegistry.instance().get(player.getUUID());
        if (selection.isEmpty()) {
            return actors;
        }
        java.util.List<AbstractRecruitEntity> narrowed = new java.util.ArrayList<>(actors.size());
        for (AbstractRecruitEntity recruit : actors) {
            if (selection.contains(recruit.getUUID())) {
                narrowed.add(recruit);
            }
        }
        return narrowed;
    }

    static void applyIntentDirectly(ServerPlayer player,
                                    CommandIntent intent,
                                    List<AbstractRecruitEntity> safeActors) {
        if (intent instanceof CommandIntent.Movement move) {
            CommandEvents.onMovementCommand(
                    player, safeActors, move.movementState(), move.formation(), move.tight(), move.targetPos());
        } else if (intent instanceof CommandIntent.Face face) {
            CommandEvents.onFaceCommand(
                    player, safeActors, face.formation(), face.tight());
        } else if (intent instanceof CommandIntent.Attack attack) {
            CommandEvents.onAttackCommand(
                    player, player.getUUID(), safeActors, attack.groupUuid());
        } else if (intent instanceof CommandIntent.StrategicFire fire) {
            for (AbstractRecruitEntity recruit : safeActors) {
                CommandEvents.onStrategicFireCommand(
                        player, player.getUUID(), recruit, fire.groupUuid(), fire.shouldFire());
            }
        } else if (intent instanceof CommandIntent.Aggro aggro) {
            for (AbstractRecruitEntity recruit : safeActors) {
                CommandEvents.onAggroCommand(
                        player.getUUID(), recruit, aggro.state(), aggro.groupUuid(), aggro.fromGui());
            }
        } else if (intent instanceof CommandIntent.CombatStanceChange stanceChange) {
            for (AbstractRecruitEntity recruit : safeActors) {
                CommandEvents.onCombatStanceCommand(
                        player.getUUID(), recruit, stanceChange.stance(), stanceChange.groupUuid());
            }
        } else if (intent instanceof CommandIntent.SiegeMachine siegeMachine) {
            for (AbstractRecruitEntity recruit : safeActors) {
                CommandEvents.onMountButton(
                        player.getUUID(), recruit, siegeMachine.returnToKnownMount() ? null : siegeMachine.mountUuid(), siegeMachine.groupUuid());
            }
        }
    }
}
