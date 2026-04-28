package com.talhanation.bannermod.client.military.render;

import com.talhanation.bannermod.util.RuntimeProfilingCounters;

public final class RecruitRenderProfiling {
    private static final String PREFIX = "recruit.render.";
    private static final ThreadLocal<Long> NESTED_RENDER_NANOS = ThreadLocal.withInitial(() -> 0L);

    private RecruitRenderProfiling() {
    }

    public static long start() {
        return System.nanoTime();
    }

    public static void increment(String bucket) {
        RuntimeProfilingCounters.increment(PREFIX + bucket);
    }

    public static void duration(String bucket, long startNanos) {
        long elapsedNanos = Math.max(0L, System.nanoTime() - startNanos);
        RuntimeProfilingCounters.increment(PREFIX + bucket + ".calls");
        RuntimeProfilingCounters.add(PREFIX + bucket + ".nanos", elapsedNanos);
        if (bucket.equals("nameplates") || bucket.startsWith("layers.")) {
            NESTED_RENDER_NANOS.set(NESTED_RENDER_NANOS.get() + elapsedNanos);
        }
    }

    public static void skipped(String bucket) {
        RuntimeProfilingCounters.increment(PREFIX + bucket + ".skipped");
    }

    public static void layerDuration(String layerBucket, long startNanos) {
        duration("layers." + layerBucket, startNanos);
    }

    public static void layerSkipped(String layerBucket) {
        skipped("layers." + layerBucket);
    }

    public static void textureStateSwitch(String bucket) {
        RuntimeProfilingCounters.increment(PREFIX + "texture_state_switches");
        RuntimeProfilingCounters.increment(PREFIX + "texture_state." + bucket);
    }

    public static void beginNormalRender() {
        NESTED_RENDER_NANOS.set(0L);
    }

    public static void endNormalRender(long startNanos) {
        long elapsedNanos = Math.max(0L, System.nanoTime() - startNanos);
        RuntimeProfilingCounters.increment(PREFIX + "normal_total.calls");
        RuntimeProfilingCounters.add(PREFIX + "normal_total.nanos", elapsedNanos);
        RuntimeProfilingCounters.increment(PREFIX + "base_model.calls");
        RuntimeProfilingCounters.add(PREFIX + "base_model.nanos", Math.max(0L, elapsedNanos - NESTED_RENDER_NANOS.get()));
        NESTED_RENDER_NANOS.remove();
    }
}
