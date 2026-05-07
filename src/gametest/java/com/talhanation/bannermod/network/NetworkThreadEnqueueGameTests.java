package com.talhanation.bannermod.network;

import com.talhanation.bannermod.BannerModDedicatedServerGameTestSupport;
import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.network.compat.BannerModNetworkContext;
import com.talhanation.bannermod.network.messages.military.MessageMovement;
import com.talhanation.bannermod.network.messages.military.MessageUpkeepPos;
import com.talhanation.bannermod.network.throttle.PacketRateLimitConfig;
import com.talhanation.bannermod.network.throttle.PacketRateLimiter;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * ENQUEUE-001 acceptance gametest — drives the production
 * {@code BannerModMessage#executeServerSide(BannerModNetworkContext)} wrap
 * with 1000 concurrent dispatches and asserts that every body runs on the
 * main server thread without throwing.
 *
 * <p>Logical contract:
 * <ol>
 *   <li>Construct an {@link IPayloadContext} whose {@code enqueueWork}
 *       defers to the {@link MinecraftServer} main-thread executor.</li>
 *   <li>Wrap that context in a {@link BannerModNetworkContext} so it walks
 *       the same code path NeoForge takes in production.</li>
 *   <li>From multiple worker threads, dispatch 1000 real
 *       {@link MessageUpkeepPos} packets — a representative serverbound
 *       handler whose body performs an entity-index lookup
 *       ({@code level.getEntitiesOfClass}) that is unsafe off the main
 *       thread.</li>
 *   <li>Wait until all 1000 enqueued bodies have completed.</li>
 *   <li>Assert: zero captured exceptions, every captured runner-thread
 *       name equals the main server thread name, completion count = 1000.</li>
 * </ol>
 *
 * <p>Why this proves the acceptance: capturing the runner thread name
 * inside each body is the discriminator. If the wrap was missing or routed
 * synchronously (e.g. {@code enqueueWork(r){r.run();}}), the captured
 * thread set would include the worker thread names instead of the single
 * main-server thread, and the assertion would fail. With the wrap in place
 * and correctly deferring to {@code server.execute}, every body runs on
 * the main thread sequentially — no CME possible by construction.
 */
@GameTestHolder(BannerModMain.MOD_ID)
public class NetworkThreadEnqueueGameTests {

    private static final int WORKER_COUNT = 4;
    private static final int DISPATCHES_PER_WORKER = 250;
    private static final int TOTAL_DISPATCHES = WORKER_COUNT * DISPATCHES_PER_WORKER;
    private static final UUID SENDER_UUID = UUID.fromString("00000000-0000-0000-0000-0000feed0001");
    private static final UUID MOVEMENT_SENDER_UUID = UUID.fromString("00000000-0000-0000-0000-0000feed0002");
    private static final UUID MOVEMENT_GROUP_UUID = UUID.fromString("00000000-0000-0000-0000-0000feed1002");

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty", timeoutTicks = 1200)
    public static void thousandConcurrentDispatchesAllRunOnMainThreadWithNoException(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        helper.assertTrue(server != null, "Gametest must run inside a real MinecraftServer context");

        ServerPlayer sender = (ServerPlayer) BannerModDedicatedServerGameTestSupport
                .createFakeServerPlayer(level, SENDER_UUID, "enqueue-test-sender");
        BlockPos targetPos = helper.absolutePos(new BlockPos(2, 2, 2));

        AtomicInteger completed = new AtomicInteger();
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
        List<String> runnerThreadNames = Collections.synchronizedList(new ArrayList<>());

        // Custom IPayloadContext: enqueueWork defers to the server's main
        // thread executor. The captured wrapper records the runner thread
        // name and any exception so the assertions can prove the deferral
        // happened.
        IPayloadContext deferringContext = new MainThreadDeferringContext(
                sender,
                server,
                completed,
                errors,
                runnerThreadNames
        );
        BannerModNetworkContext bannerCtx = new BannerModNetworkContext(deferringContext);

        // Spawn worker threads. Each fires off DISPATCHES_PER_WORKER
        // executeServerSide calls, hitting the wrap from a non-main thread.
        Thread[] workers = new Thread[WORKER_COUNT];
        for (int t = 0; t < WORKER_COUNT; t++) {
            workers[t] = new Thread(() -> {
                for (int i = 0; i < DISPATCHES_PER_WORKER; i++) {
                    MessageUpkeepPos msg = new MessageUpkeepPos(SENDER_UUID, null, targetPos);
                    msg.executeServerSide(bannerCtx);
                }
            }, "enqueue-test-worker-" + t);
            workers[t].setDaemon(true);
            workers[t].start();
        }
        for (Thread w : workers) {
            try {
                w.join(10_000L);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                helper.fail("Worker thread join interrupted");
            }
            helper.assertTrue(!w.isAlive(), "Worker thread did not finish dispatching within 10s");
        }

        // Workers have queued all 1000 runnables onto the server main-thread
        // executor. Drain by ticking until completion + assertions hold.
        String mainThreadName = server.getRunningThread().getName();
        helper.succeedWhen(() -> {
            helper.assertTrue(
                    errors.isEmpty(),
                    "Expected zero body exceptions; first was: "
                            + (errors.isEmpty() ? "<none>" : errors.get(0).toString())
            );
            int done = completed.get();
            helper.assertTrue(
                    done == TOTAL_DISPATCHES,
                    "Expected " + TOTAL_DISPATCHES + " bodies to complete; got " + done
            );
            // The discriminator: every recorded runner thread name must equal
            // the server main-thread name, proving the wrap deferred.
            List<String> snapshot;
            synchronized (runnerThreadNames) {
                snapshot = new ArrayList<>(runnerThreadNames);
            }
            boolean allOnMain = snapshot.stream().allMatch(name -> name.equals(mainThreadName));
            helper.assertTrue(
                    allOnMain,
                    "Every body must run on the main server thread (" + mainThreadName + "); observed distinct threads: "
                            + snapshot.stream().distinct().sorted().toList()
            );
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty", timeoutTicks = 1200)
    public static void thousandConcurrentMovementPacketsDoNotTouchEntityCollectionsOffThread(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        helper.assertTrue(server != null, "Gametest must run inside a real MinecraftServer context");

        ServerPlayer sender = (ServerPlayer) BannerModDedicatedServerGameTestSupport
                .createFakeServerPlayer(level, MOVEMENT_SENDER_UUID, "movement-enqueue-test-sender");

        AtomicInteger completed = new AtomicInteger();
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
        List<String> runnerThreadNames = Collections.synchronizedList(new ArrayList<>());

        IPayloadContext deferringContext = new MainThreadDeferringContext(
                sender,
                server,
                completed,
                errors,
                runnerThreadNames
        );
        BannerModNetworkContext bannerCtx = new BannerModNetworkContext(deferringContext);

        PacketRateLimiter limiter = PacketRateLimiter.shared();
        limiter.setCooldownSource(packetClass -> packetClass == MessageMovement.class ? 0L : -1L);
        limiter.clearState();

        try {
            Thread[] workers = new Thread[WORKER_COUNT];
            for (int t = 0; t < WORKER_COUNT; t++) {
                workers[t] = new Thread(() -> {
                    for (int i = 0; i < DISPATCHES_PER_WORKER; i++) {
                        try {
                            MessageMovement msg = new MessageMovement(
                                    MOVEMENT_SENDER_UUID,
                                    6,
                                    MOVEMENT_GROUP_UUID,
                                    0,
                                    false
                            );
                            msg.executeServerSide(bannerCtx);
                        } catch (Throwable t1) {
                            errors.add(t1);
                        }
                    }
                }, "movement-enqueue-test-worker-" + t);
                workers[t].setDaemon(true);
                workers[t].start();
            }
            for (Thread w : workers) {
                try {
                    w.join(10_000L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    helper.fail("Worker thread join interrupted");
                }
                helper.assertTrue(!w.isAlive(), "Worker thread did not finish dispatching within 10s");
            }
        } finally {
            PacketRateLimitConfig.install();
            limiter.clearState();
        }

        String mainThreadName = server.getRunningThread().getName();
        helper.succeedWhen(() -> {
            helper.assertTrue(
                    errors.isEmpty(),
                    "Expected zero movement packet exceptions; first was: "
                            + (errors.isEmpty() ? "<none>" : errors.get(0).toString())
            );
            int done = completed.get();
            helper.assertTrue(
                    done == TOTAL_DISPATCHES,
                    "Expected " + TOTAL_DISPATCHES + " movement packet bodies to complete; got " + done
            );
            List<String> snapshot;
            synchronized (runnerThreadNames) {
                snapshot = new ArrayList<>(runnerThreadNames);
            }
            boolean allOnMain = snapshot.stream().allMatch(name -> name.equals(mainThreadName));
            helper.assertTrue(
                    allOnMain,
                    "Every movement body must run on the main server thread (" + mainThreadName
                            + "); observed distinct threads: "
                            + snapshot.stream().distinct().sorted().toList()
            );
        });
    }

    /**
     * Test-only {@link IPayloadContext} whose {@code enqueueWork} defers to
     * the server main-thread executor. Records each runnable's runner thread
     * name and any thrown exception so the assertions can prove the wrap.
     */
    private record MainThreadDeferringContext(
            ServerPlayer sender,
            MinecraftServer server,
            AtomicInteger completed,
            List<Throwable> errors,
            List<String> runnerThreadNames
    ) implements IPayloadContext {

        @Override
        public ICommonPacketListener listener() {
            return null;
        }

        @Override
        public Player player() {
            return sender;
        }

        @Override
        public CompletableFuture<Void> enqueueWork(Runnable runnable) {
            Runnable instrumented = () -> {
                try {
                    runnerThreadNames.add(Thread.currentThread().getName());
                    runnable.run();
                    completed.incrementAndGet();
                } catch (Throwable t) {
                    errors.add(t);
                }
            };
            if (server.isSameThread()) {
                instrumented.run();
                return CompletableFuture.completedFuture(null);
            }
            CompletableFuture<Void> done = new CompletableFuture<>();
            server.execute(() -> {
                instrumented.run();
                done.complete(null);
            });
            return done;
        }

        @Override
        public <T> CompletableFuture<T> enqueueWork(Supplier<T> supplier) {
            // Not exercised by these tests; route the same way for safety.
            if (server.isSameThread()) {
                return CompletableFuture.completedFuture(supplier.get());
            }
            CompletableFuture<T> done = new CompletableFuture<>();
            server.execute(() -> done.complete(supplier.get()));
            return done;
        }

        @Override
        public PacketFlow flow() {
            return PacketFlow.SERVERBOUND;
        }

        @Override
        public void handle(CustomPacketPayload payload) {
        }

        @Override
        public void finishCurrentTask(ConfigurationTask.Type type) {
        }

        @Override
        public void handle(Packet<?> packet) {
            if (packet instanceof ClientboundCustomPayloadPacket || packet instanceof ServerboundCustomPayloadPacket) {
                return;
            }
            IPayloadContext.super.handle(packet);
        }
    }
}
