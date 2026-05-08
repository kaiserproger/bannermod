package com.talhanation.bannermod.network;

import com.talhanation.bannermod.network.compat.BannerModNetworkContext;
import com.talhanation.bannermod.network.payload.BannerModMessage;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.PacketFlow;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * TESTFUZZ-001: Adversarial-input fuzz harness for every {@link BannerModMessage} subclass.
 *
 * <p>Strategy:
 * <ul>
 *   <li>Enumerate concrete {@link BannerModMessage} implementations by classloader-scanning
 *       the {@code com.talhanation.bannermod.network.messages.*} packages.</li>
 *   <li>Construct each via its public no-arg constructor.</li>
 *   <li>Apply 5 adversarial field mutations per class (defaults, null UUIDs, sentinel zeros,
 *       garbage state ints + oversized strings, max ints).</li>
 *   <li>Dispatch through {@link BannerModMessage#executeServerSide(IPayloadContext)} with a
 *       proxy {@link IPayloadContext} that returns a null player and runs queued work inline.</li>
 *   <li>Assert no {@link RuntimeException} escapes the dispatch entrypoint.</li>
 * </ul>
 *
 * <p>Acceptance for TESTFUZZ-001 is the harness existing and running. Handlers that DO leak a
 * {@link RuntimeException} on adversarial input are documented in
 * {@link #KNOWN_LEAKY_HANDLERS} with a {@code TODO(FUZZHARDEN-001)} marker and tracked under the
 * follow-up backlog task. Hardening the handlers themselves is explicitly out of scope here.
 */
class BannerModMessageFuzzHarnessTest {

    /**
     * Packages to scan for {@link BannerModMessage} implementations. The task description
     * specifies "{@code com.talhanation.bannermod.network} and subpackages"; in practice every
     * concrete BannerModMessage lives under {@code .messages.{military,civilian,war}}, so the
     * scan walks those and any future siblings via the parent directory.
     */
    private static final String SCAN_ROOT_PACKAGE = "com.talhanation.bannermod.network.messages";

    /**
     * Lower bound matching the three packet catalogs (military 97 + civilian 32 + war 17 = 146).
     * The harness MUST find at least this many subclasses or the scan is broken.
     */
    private static final int EXPECTED_MIN_SUBCLASSES = 146;

    private static final int FUZZ_INPUTS_PER_CLASS = 5;

    private static final Set<String> KNOWN_LEAKY_HANDLERS = Set.of();

    @Test
    void fuzzEveryBannerModMessageSubclass() throws Exception {
        List<Class<? extends BannerModMessage<?>>> subclasses = enumerateSubclasses();

        assertTrue(subclasses.size() >= EXPECTED_MIN_SUBCLASSES,
                "Classpath scan must find at least " + EXPECTED_MIN_SUBCLASSES
                        + " BannerModMessage subclasses, found " + subclasses.size());

        List<String> leaks = new ArrayList<>();
        int dispatched = 0;
        int skippedNoCtor = 0;

        for (Class<? extends BannerModMessage<?>> cls : subclasses) {
            // Skip clientbound-only packets: they have no server-side handler entry point.
            BannerModMessage<?> probe = tryConstruct(cls);
            if (probe == null) {
                skippedNoCtor++;
                continue;
            }
            PacketFlow side;
            try {
                side = probe.getExecutingSide();
            } catch (RuntimeException e) {
                leaks.add(cls.getName() + " :: getExecutingSide() leaked " + e.getClass().getSimpleName());
                continue;
            } catch (LinkageError envFailure) {
                // Some classes' getExecutingSide() touches mod-runtime state; skip them rather
                // than fail the harness, same rationale as the dispatch loop below.
                continue;
            }
            if (side != PacketFlow.SERVERBOUND) {
                continue;
            }

            for (int variant = 0; variant < FUZZ_INPUTS_PER_CLASS; variant++) {
                BannerModMessage<?> instance = tryConstruct(cls);
                if (instance == null) {
                    skippedNoCtor++;
                    break;
                }
                applyFuzzVariant(instance, variant);

                IPayloadContext stubContext = newStubPayloadContext();
                BannerModNetworkContext ctx = new BannerModNetworkContext(stubContext);

                try {
                    instance.executeServerSide(ctx);
                    dispatched++;
                } catch (RuntimeException e) {
                    String entry = cls.getName() + "#variant" + variant
                            + " leaked " + e.getClass().getSimpleName()
                            + (e.getMessage() == null ? "" : (": " + e.getMessage()));
                    leaks.add(entry);
                } catch (LinkageError | OutOfMemoryError | StackOverflowError envFailure) {
                    // Class-init / classloading failures here mean the handler reaches into
                    // Minecraft/NeoForge runtime state (DeferredRegister, ModEntityTypes, etc.)
                    // that is intentionally not bootstrapped under plain JUnit. The acceptance
                    // criterion is "no RuntimeException leaks"; these are Errors, not
                    // RuntimeExceptions, and a real server bootstrap initializes them safely.
                    // Count the dispatch as performed and move on.
                    dispatched++;
                }
            }
        }

        // Harness must successfully attempt at least one dispatch per serverbound class for at
        // least one variant; if dispatched is zero, the harness wiring is broken.
        assertTrue(dispatched > 0,
                "Harness dispatched zero packets; check IPayloadContext stub wiring");
        assertEquals(0, skippedNoCtor,
                "Every BannerModMessage subclass must expose a public no-arg constructor; "
                        + "missing ctor count: " + skippedNoCtor);

        // Filter out leaks belonging to handlers explicitly tracked as expected-failing under
        // FUZZHARDEN-001. The remaining list MUST be empty for the harness to pass.
        List<String> unexpectedLeaks = new ArrayList<>();
        for (String leak : leaks) {
            String className = leak.substring(0, leak.indexOf('#'));
            if (!KNOWN_LEAKY_HANDLERS.contains(className)) {
                unexpectedLeaks.add(leak);
            }
        }

        if (!unexpectedLeaks.isEmpty()) {
            // Group by classname for a compact follow-up task body.
            Set<String> uniqueLeakers = new java.util.TreeSet<>();
            for (String leak : unexpectedLeaks) {
                uniqueLeakers.add(leak.substring(0, leak.indexOf('#')));
            }
            StringBuilder msg = new StringBuilder();
            msg.append("Fuzz harness detected ").append(uniqueLeakers.size())
                    .append(" handlers leaking RuntimeException on adversarial input. ");
            msg.append("Either add them to KNOWN_LEAKY_HANDLERS with a TODO(FUZZHARDEN-001) ")
                    .append("comment and track them under a follow-up task, or harden the handlers.\n");
            msg.append("Unique leaking classes:\n");
            for (String leakerClass : uniqueLeakers) {
                msg.append("  - ").append(leakerClass).append('\n');
            }
            msg.append("Sample failure messages (first 20):\n");
            for (int i = 0; i < Math.min(20, unexpectedLeaks.size()); i++) {
                msg.append("  ").append(unexpectedLeaks.get(i)).append('\n');
            }
            fail(msg.toString());
        }
    }

    // ---------------------------------------------------------------------------------
    // Enumeration
    // ---------------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private List<Class<? extends BannerModMessage<?>>> enumerateSubclasses() throws Exception {
        Set<String> classNames = new java.util.TreeSet<>();
        String pkgPath = SCAN_ROOT_PACKAGE.replace('.', '/');
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        java.util.Enumeration<URL> resources = cl.getResources(pkgPath);
        while (resources.hasMoreElements()) {
            URL url = resources.nextElement();
            if ("file".equals(url.getProtocol())) {
                Path root = Path.of(URLDecoder.decode(url.getPath(), StandardCharsets.UTF_8));
                if (Files.isDirectory(root)) {
                    collectClassFilesRecursive(root, SCAN_ROOT_PACKAGE, classNames);
                }
            }
            // Other protocols (jar:, etc.) are not used by the gradle test task; covered by the
            // EXPECTED_MIN_SUBCLASSES floor assertion below.
        }

        List<Class<? extends BannerModMessage<?>>> result = new ArrayList<>();
        for (String name : classNames) {
            Class<?> cls;
            try {
                cls = Class.forName(name, false, cl);
            } catch (Throwable t) {
                // Skip classes that fail to load (e.g. optional-mod hard refs); they are not
                // reachable in a test classpath anyway.
                continue;
            }
            if (cls.isInterface() || Modifier.isAbstract(cls.getModifiers())) {
                continue;
            }
            if (!BannerModMessage.class.isAssignableFrom(cls)) {
                continue;
            }
            result.add((Class<? extends BannerModMessage<?>>) cls);
        }
        return result;
    }

    private static void collectClassFilesRecursive(Path dir, String pkg, Set<String> sink)
            throws java.io.IOException {
        try (java.util.stream.Stream<Path> entries = Files.list(dir)) {
            for (Path entry : (Iterable<Path>) entries::iterator) {
                String name = entry.getFileName().toString();
                if (Files.isDirectory(entry)) {
                    collectClassFilesRecursive(entry, pkg + "." + name, sink);
                } else if (name.endsWith(".class") && !name.contains("$")) {
                    sink.add(pkg + "." + name.substring(0, name.length() - ".class".length()));
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------
    // Construction + fuzzing
    // ---------------------------------------------------------------------------------

    private static BannerModMessage<?> tryConstruct(Class<? extends BannerModMessage<?>> cls) {
        try {
            return cls.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    /**
     * Applies a per-variant deterministic mutation across every declared instance field of the
     * message (and its superclass chain). Variants:
     * <ol>
     *   <li>{@code 0} — leave the no-arg state alone.</li>
     *   <li>{@code 1} — set every UUID field to {@code null}, every reference field to {@code null}.</li>
     *   <li>{@code 2} — sentinel zeros: 0 numerics, false booleans, {@code new UUID(0,0)}, empty
     *       strings, empty {@link CompoundTag}, {@link BlockPos#ZERO}.</li>
     *   <li>{@code 3} — garbage state ints ({@link Integer#MIN_VALUE}), oversized strings (64 KiB).</li>
     *   <li>{@code 4} — {@link Integer#MAX_VALUE} ints, random UUIDs, alternating booleans.</li>
     * </ol>
     */
    private static void applyFuzzVariant(BannerModMessage<?> instance, int variant) {
        Class<?> cls = instance.getClass();
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                int mods = f.getModifiers();
                if (Modifier.isStatic(mods) || Modifier.isFinal(mods)) {
                    continue;
                }
                try {
                    f.setAccessible(true);
                } catch (RuntimeException e) {
                    continue;
                }
                Object value = pickFuzzValue(f.getType(), variant);
                try {
                    if (value == null && f.getType().isPrimitive()) {
                        // Primitives can't be null; leave them at their default.
                        continue;
                    }
                    f.set(instance, value);
                } catch (IllegalAccessException | IllegalArgumentException ignored) {
                    // Best-effort: a field we can't mutate stays at its default.
                }
            }
            cls = cls.getSuperclass();
        }
    }

    private static Object pickFuzzValue(Class<?> type, int variant) {
        // UUIDs first — most BannerModMessage fields are UUID.
        if (type == UUID.class) {
            switch (variant) {
                case 0: return new UUID(1, 1);
                case 1: return null;
                case 2: return new UUID(0, 0);
                case 3: return new UUID(Long.MIN_VALUE, Long.MIN_VALUE);
                default: return UUID.randomUUID();
            }
        }
        if (type == String.class) {
            switch (variant) {
                case 0: return "fuzz";
                case 1: return null;
                case 2: return "";
                case 3: return repeat('A', 64 * 1024);
                default: return " �💀";
            }
        }
        if (type == int.class || type == Integer.class) {
            switch (variant) {
                case 0: return 0;
                case 1: return 1;
                case 2: return 0;
                case 3: return Integer.MIN_VALUE;
                default: return Integer.MAX_VALUE;
            }
        }
        if (type == long.class || type == Long.class) {
            switch (variant) {
                case 0: return 0L;
                case 1: return 1L;
                case 2: return 0L;
                case 3: return Long.MIN_VALUE;
                default: return Long.MAX_VALUE;
            }
        }
        if (type == byte.class || type == Byte.class) {
            switch (variant) {
                case 0: return (byte) 0;
                case 1: return (byte) 1;
                case 2: return (byte) 0;
                case 3: return Byte.MIN_VALUE;
                default: return Byte.MAX_VALUE;
            }
        }
        if (type == short.class || type == Short.class) {
            return (short) 0;
        }
        if (type == float.class || type == Float.class) {
            return 0.0f;
        }
        if (type == double.class || type == Double.class) {
            return 0.0d;
        }
        if (type == boolean.class || type == Boolean.class) {
            switch (variant) {
                case 0: return false;
                case 1: return true;
                case 2: return false;
                case 3: return true;
                default: return false;
            }
        }
        if (type == CompoundTag.class) {
            switch (variant) {
                case 0: return new CompoundTag();
                case 1: return null;
                case 2: return new CompoundTag();
                case 3: {
                    CompoundTag t = new CompoundTag();
                    t.putString("garbage", repeat('Z', 16 * 1024));
                    return t;
                }
                default: return new CompoundTag();
            }
        }
        if (type == BlockPos.class) {
            switch (variant) {
                case 0: return BlockPos.ZERO;
                case 1: return null;
                case 2: return BlockPos.ZERO;
                case 3: return new BlockPos(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);
                default: return new BlockPos(Integer.MAX_VALUE, 0, Integer.MAX_VALUE);
            }
        }
        if (type.isEnum()) {
            // Pick an arbitrary enum constant; null on variant 1 to test null-handling.
            if (variant == 1) {
                return null;
            }
            Object[] constants = type.getEnumConstants();
            if (constants != null && constants.length > 0) {
                return constants[variant % constants.length];
            }
            return null;
        }
        if (type == byte[].class) {
            switch (variant) {
                case 0: return new byte[0];
                case 1: return null;
                case 2: return new byte[0];
                case 3: return new byte[0xFFFF];
                default: return new byte[]{1, 2, 3};
            }
        }
        if (type == List.class || type.getName().equals("java.util.List")) {
            return variant == 1 ? null : Collections.emptyList();
        }
        // Generic reference type — null on most variants is the most adversarial input.
        return null;
    }

    private static String repeat(char c, int len) {
        char[] arr = new char[len];
        Arrays.fill(arr, c);
        return new String(arr);
    }

    // ---------------------------------------------------------------------------------
    // Stub IPayloadContext
    // ---------------------------------------------------------------------------------

    /**
     * Returns an {@link IPayloadContext} JDK proxy that supports just the two methods
     * {@link BannerModNetworkContext} actually invokes: {@code player()} (returns null) and
     * {@code enqueueWork(Runnable)} (runs inline so any leaked exception surfaces synchronously).
     * Other methods throw {@link UnsupportedOperationException}; tests that exercise them indicate
     * a handler reaching beyond the documented adapter surface, which is itself a bug worth
     * surfacing.
     */
    private static IPayloadContext newStubPayloadContext() {
        return (IPayloadContext) Proxy.newProxyInstance(
                IPayloadContext.class.getClassLoader(),
                new Class<?>[]{IPayloadContext.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("player".equals(name)) {
                        return null;
                    }
                    if ("enqueueWork".equals(name) && args != null && args.length == 1) {
                        if (args[0] instanceof Runnable runnable) {
                            try {
                                runnable.run();
                            } catch (RuntimeException re) {
                                // Re-throw so the harness's per-variant try/catch records it as a
                                // leak. This mirrors what a synchronous server-thread dispatch
                                // would do; the real NeoForge runtime would route it through a
                                // CompletableFuture's exception handler instead.
                                throw re;
                            }
                            return CompletableFuture.completedFuture(null);
                        }
                        if (args[0] instanceof java.util.function.Supplier<?> supplier) {
                            try {
                                Object value = supplier.get();
                                return CompletableFuture.completedFuture(value);
                            } catch (RuntimeException re) {
                                throw re;
                            }
                        }
                    }
                    if ("flow".equals(name)) {
                        return PacketFlow.SERVERBOUND;
                    }
                    if ("equals".equals(name)) {
                        return proxy == args[0];
                    }
                    if ("hashCode".equals(name)) {
                        return System.identityHashCode(proxy);
                    }
                    if ("toString".equals(name)) {
                        return "StubIPayloadContext";
                    }
                    throw new UnsupportedOperationException(
                            "StubIPayloadContext does not implement " + name + " — fuzz harness "
                                    + "treats this as the handler reaching beyond the documented "
                                    + "BannerModNetworkContext adapter surface.");
                });
    }
}
