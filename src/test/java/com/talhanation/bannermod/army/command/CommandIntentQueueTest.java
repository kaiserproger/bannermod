package com.talhanation.bannermod.army.command;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandIntentQueueTest {

    @Test
    void appendAndHead() {
        CommandIntentQueue queue = new CommandIntentQueue();
        assertTrue(queue.isEmpty());

        CommandIntent first = movement(1L);
        queue.append(first);

        assertEquals(1, queue.size());
        assertFalse(queue.isEmpty());
        assertEquals(first, queue.head().orElseThrow());
    }

    @Test
    void appendPreservesFifoOrder() {
        CommandIntentQueue queue = new CommandIntentQueue();
        queue.append(movement(1L));
        queue.append(movement(2L));
        queue.append(movement(3L));

        assertEquals(1L, ((CommandIntent.Movement) queue.popHead().orElseThrow()).issuedAtGameTime());
        assertEquals(2L, ((CommandIntent.Movement) queue.popHead().orElseThrow()).issuedAtGameTime());
        assertEquals(3L, ((CommandIntent.Movement) queue.popHead().orElseThrow()).issuedAtGameTime());
        assertTrue(queue.isEmpty());
    }

    @Test
    void clearRemovesAll() {
        CommandIntentQueue queue = new CommandIntentQueue();
        queue.append(movement(1L));
        queue.append(movement(2L));
        queue.clear();

        assertTrue(queue.isEmpty());
        assertEquals(0, queue.size());
    }

    @Test
    void prependJumpsToFront() {
        CommandIntentQueue queue = new CommandIntentQueue();
        queue.append(movement(1L));
        queue.append(movement(2L));
        queue.prepend(movement(99L));

        assertEquals(99L, ((CommandIntent.Movement) queue.popHead().orElseThrow()).issuedAtGameTime());
        assertEquals(1L, ((CommandIntent.Movement) queue.popHead().orElseThrow()).issuedAtGameTime());
    }

    @Test
    void snapshotReturnsCurrentOrder() {
        CommandIntentQueue queue = new CommandIntentQueue();
        queue.append(movement(1L));
        queue.append(movement(2L));
        assertEquals(2, queue.snapshot().size());
    }

    @Test
    void runtimeTracksSizePerRecruit() {
        CommandIntentQueueRuntime runtime = CommandIntentQueueRuntime.instance();
        runtime.clearAllForTest();
        UUID recruitA = UUID.randomUUID();
        UUID recruitB = UUID.randomUUID();

        runtime.queueFor(recruitA).append(movement(1L));
        runtime.queueFor(recruitA).append(movement(2L));
        runtime.queueFor(recruitB).append(movement(99L));

        assertEquals(2, runtime.sizeFor(recruitA));
        assertEquals(1, runtime.sizeFor(recruitB));
        assertEquals(1L, ((CommandIntent.Movement) runtime.headFor(recruitA).orElseThrow()).issuedAtGameTime());
    }

    @Test
    void runtimeClearForDropsOneRecruitOnly() {
        CommandIntentQueueRuntime runtime = CommandIntentQueueRuntime.instance();
        runtime.clearAllForTest();
        UUID recruitA = UUID.randomUUID();
        UUID recruitB = UUID.randomUUID();
        runtime.queueFor(recruitA).append(movement(1L));
        runtime.queueFor(recruitB).append(movement(2L));

        runtime.clearFor(recruitA);

        assertEquals(0, runtime.sizeFor(recruitA));
        assertEquals(1, runtime.sizeFor(recruitB));
    }

    @ParameterizedTest
    @MethodSource("unsupportedQueuedIntents")
    void runtimeRejectsUnsupportedQueuedIntentsBeforeEnqueue(CommandIntent intent) {
        CommandIntentQueueRuntime runtime = CommandIntentQueueRuntime.instance();
        runtime.clearAllForTest();

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> runtime.appendForActors(null, intent, List.of(), 0L));

        assertTrue(thrown.getMessage().contains(intent.type().name()));
        assertEquals(0, runtime.size());
    }

    private static CommandIntent movement(long tick) {
        return new CommandIntent.Movement(
                tick,
                CommandIntentPriority.NORMAL,
                true,
                6,
                0,
                false,
                null
        );
    }

    private static Stream<CommandIntent> unsupportedQueuedIntents() {
        UUID groupUuid = UUID.randomUUID();
        return Stream.of(
                new CommandIntent.CombatStanceChange(1L, CommandIntentPriority.NORMAL, true, null, groupUuid),
                new CommandIntent.SiegeMachine(1L, CommandIntentPriority.NORMAL, true, UUID.randomUUID(), groupUuid, false)
        );
    }
}
