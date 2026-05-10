package com.talhanation.bannermod.client.military.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.util.RuntimeProfilingCounters;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@EventBusSubscriber(modid = BannerModMain.MOD_ID, value = Dist.CLIENT)
public final class RecruitCrowdRenderEvents {
    private static final double IMPOSTOR_QUERY_RADIUS = 96.0D;
    private static final double IMPOSTOR_QUERY_RADIUS_SQR = IMPOSTOR_QUERY_RADIUS * IMPOSTOR_QUERY_RADIUS;
    private static final double IMPOSTOR_QUERY_CACHE_CAMERA_DRIFT_SQR = 16.0D;
    private static final int IMPOSTOR_QUERY_CACHE_TICKS = 2;
    private static final float BODY_WIDTH = 0.58F;
    private static final float HALF_WIDTH = BODY_WIDTH * 0.5F;

    private static long lastImpostorQueryTick = Long.MIN_VALUE;
    private static ResourceKey<Level> lastImpostorQueryDimension;
    private static Vec3 lastImpostorQueryCameraPos;
    private static List<AbstractRecruitEntity> cachedImpostorQuery = List.of();
    private static Set<Integer> cachedImpostorQueryIds = Set.of();

    private RecruitCrowdRenderEvents() {
    }

    @SubscribeEvent
    public static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        if (event.getEntity() instanceof AbstractRecruitEntity recruit
                && RecruitRenderLod.shouldUseCrowdImpostor(recruit)
                && isCachedImpostorCandidate(recruit)) {
            RuntimeProfilingCounters.increment("recruit.render.normal_skipped_for_impostor");
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onClientLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        clearCachedQuery();
    }

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        clearCachedQuery();
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || !RecruitRenderLod.isCrowdedNearCamera()) {
            return;
        }

        Camera camera = event.getCamera();
        Vec3 cameraPos = camera.getPosition();
        List<AbstractRecruitEntity> recruits = crowdQuery(minecraft, cameraPos);
        if (recruits.isEmpty()) {
            return;
        }
        RuntimeProfilingCounters.add("recruit.render.crowd_query_results", recruits.size());

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        Set<RenderType> usedRenderTypes = new HashSet<>();
        int rendered = 0;
        int candidates = 0;
        int frustumCulled = 0;
        int rangeCulled = 0;
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        long startNanos = System.nanoTime();

        for (AbstractRecruitEntity recruit : recruits) {
            if (recruit.isRemoved()) {
                continue;
            }
            if (recruit.distanceToSqr(cameraPos) > IMPOSTOR_QUERY_RADIUS_SQR) {
                rangeCulled++;
                continue;
            }
            if (!RecruitRenderLod.shouldUseCrowdImpostor(recruit)) {
                continue;
            }
            candidates++;
            if (event.getFrustum() != null && !event.getFrustum().isVisible(recruit.getBoundingBox())) {
                frustumCulled++;
                continue;
            }
            RenderType renderType = RecruitHumanRenderer.crowdRenderType(recruit);
            VertexConsumer consumer = bufferSource.getBuffer(renderType);
            usedRenderTypes.add(renderType);
            renderImpostor(recruit, partialTick, camera, cameraPos, poseStack, consumer, minecraft);
            rendered++;
        }

        for (RenderType renderType : usedRenderTypes) {
            bufferSource.endBatch(renderType);
        }
        if (rendered > 0) {
            RuntimeProfilingCounters.add("recruit.render.crowd_impostors", rendered);
            RuntimeProfilingCounters.add("recruit.render.crowd_impostor_nanos", System.nanoTime() - startNanos);
        }
        if (candidates > 0) {
            RuntimeProfilingCounters.add("recruit.render.crowd_impostor_candidates", candidates);
        }
        if (frustumCulled > 0) {
            RuntimeProfilingCounters.add("recruit.render.crowd_impostor_frustum_culled", frustumCulled);
        }
        if (rangeCulled > 0) {
            RuntimeProfilingCounters.add("recruit.render.crowd_impostor_range_culled", rangeCulled);
        }
    }

    private static List<AbstractRecruitEntity> crowdQuery(Minecraft minecraft, Vec3 cameraPos) {
        if (minecraft.level == null) {
            clearCachedQuery();
            return List.of();
        }

        Level level = minecraft.level;
        long gameTime = level.getGameTime();
        ResourceKey<Level> dimension = level.dimension();
        if (isCachedQueryValid(dimension, gameTime, cameraPos)) {
            RuntimeProfilingCounters.increment("recruit.render.crowd_query_cache_hits");
            return cachedImpostorQuery;
        }

        RuntimeProfilingCounters.increment("recruit.render.crowd_query_cache_misses");
        long startNanos = System.nanoTime();
        List<AbstractRecruitEntity> recruits = level.getEntitiesOfClass(
                AbstractRecruitEntity.class,
                AABB.ofSize(cameraPos, IMPOSTOR_QUERY_RADIUS * 2.0D, IMPOSTOR_QUERY_RADIUS * 2.0D, IMPOSTOR_QUERY_RADIUS * 2.0D),
                recruit -> recruit.distanceToSqr(cameraPos) <= IMPOSTOR_QUERY_RADIUS_SQR
        );
        RuntimeProfilingCounters.add("recruit.render.crowd_query_nanos", System.nanoTime() - startNanos);
        lastImpostorQueryTick = gameTime;
        lastImpostorQueryDimension = dimension;
        lastImpostorQueryCameraPos = cameraPos;
        cachedImpostorQuery = recruits;
        cachedImpostorQueryIds = queryIds(recruits);
        return cachedImpostorQuery;
    }

    private static boolean isCachedImpostorCandidate(AbstractRecruitEntity recruit) {
        if (recruit.isRemoved()) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Vec3 cameraPos = cameraPosition(minecraft);
        if (minecraft.level == null || cameraPos == null || recruit.distanceToSqr(cameraPos) > IMPOSTOR_QUERY_RADIUS_SQR) {
            return false;
        }
        crowdQuery(minecraft, cameraPos);
        return cachedImpostorQueryIds.contains(recruit.getId());
    }

    private static Set<Integer> queryIds(List<AbstractRecruitEntity> recruits) {
        Set<Integer> ids = new HashSet<>();
        for (AbstractRecruitEntity recruit : recruits) {
            ids.add(recruit.getId());
        }
        return ids;
    }

    private static void clearCachedQuery() {
        lastImpostorQueryTick = Long.MIN_VALUE;
        lastImpostorQueryDimension = null;
        lastImpostorQueryCameraPos = null;
        cachedImpostorQuery = List.of();
        cachedImpostorQueryIds = Set.of();
    }

    private static Vec3 cameraPosition(Minecraft minecraft) {
        if (minecraft.gameRenderer == null) {
            return null;
        }
        return minecraft.gameRenderer.getMainCamera().getPosition();
    }

    private static boolean isCachedQueryValid(ResourceKey<Level> dimension, long gameTime, Vec3 cameraPos) {
        return dimension.equals(lastImpostorQueryDimension)
                && gameTime >= lastImpostorQueryTick
                && gameTime - lastImpostorQueryTick < IMPOSTOR_QUERY_CACHE_TICKS
                && lastImpostorQueryCameraPos != null
                && lastImpostorQueryCameraPos.distanceToSqr(cameraPos) <= IMPOSTOR_QUERY_CACHE_CAMERA_DRIFT_SQR;
    }

    private static void renderImpostor(AbstractRecruitEntity recruit,
                                       float partialTick,
                                       Camera camera,
                                       Vec3 cameraPos,
                                       PoseStack poseStack,
                                       VertexConsumer consumer,
                                       Minecraft minecraft) {
        double x = lerp(partialTick, recruit.xOld, recruit.getX()) - cameraPos.x;
        double y = lerp(partialTick, recruit.yOld, recruit.getY()) - cameraPos.y;
        double z = lerp(partialTick, recruit.zOld, recruit.getZ()) - cameraPos.z;
        int light = LevelRenderer.getLightColor(minecraft.level, BlockPos.containing(recruit.getEyePosition(partialTick)));

        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.mulPose(camera.rotation());
        poseStack.scale(-1.0F, 1.0F, 1.0F);

        PoseStack.Pose pose = poseStack.last();
        addQuad(pose, consumer, -0.20F, 1.22F, 0.20F, 1.62F, 8.0F / 64.0F, 8.0F / 64.0F, 16.0F / 64.0F, 16.0F / 64.0F, light);
        addQuad(pose, consumer, -HALF_WIDTH, 0.72F, HALF_WIDTH, 1.22F, 20.0F / 64.0F, 20.0F / 64.0F, 28.0F / 64.0F, 32.0F / 64.0F, light);
        addQuad(pose, consumer, -HALF_WIDTH, 0.05F, 0.0F, 0.72F, 4.0F / 64.0F, 20.0F / 64.0F, 8.0F / 64.0F, 32.0F / 64.0F, light);
        addQuad(pose, consumer, 0.0F, 0.05F, HALF_WIDTH, 0.72F, 20.0F / 64.0F, 52.0F / 64.0F, 24.0F / 64.0F, 64.0F / 64.0F, light);
        poseStack.popPose();
    }

    private static void addQuad(PoseStack.Pose pose,
                                VertexConsumer consumer,
                                float minX,
                                float minY,
                                float maxX,
                                float maxY,
                                float minU,
                                float minV,
                                float maxU,
                                float maxV,
                                int light) {
        consumer.addVertex(pose.pose(), minX, minY, 0.0F).setColor(255, 255, 255, 255).setUv(minU, maxV).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0.0F, 0.0F, 1.0F);
        consumer.addVertex(pose.pose(), maxX, minY, 0.0F).setColor(255, 255, 255, 255).setUv(maxU, maxV).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0.0F, 0.0F, 1.0F);
        consumer.addVertex(pose.pose(), maxX, maxY, 0.0F).setColor(255, 255, 255, 255).setUv(maxU, minV).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0.0F, 0.0F, 1.0F);
        consumer.addVertex(pose.pose(), minX, maxY, 0.0F).setColor(255, 255, 255, 255).setUv(minU, minV).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0.0F, 0.0F, 1.0F);
    }

    private static double lerp(float partialTick, double previous, double current) {
        return previous + (current - previous) * partialTick;
    }
}
