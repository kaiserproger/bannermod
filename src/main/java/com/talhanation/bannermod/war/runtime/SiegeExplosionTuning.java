package com.talhanation.bannermod.war.runtime;

import com.talhanation.bannermod.compat.MedievalSiegeMachinesCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public final class SiegeExplosionTuning {
    private static final Set<String> FULL_SIEGE_NAMESPACES = Set.of(
            MedievalSiegeMachinesCompat.MOD_ID,
            MedievalSiegeMachinesCompat.LEGACY_MOD_ID
    );
    private static final String SMALLSHIPS_NAMESPACE = "smallships";
    private static final int MAX_AFFECTED_BLOCKS = 20;
    private static final double MAX_BLOCK_RADIUS = 3.5D;
    private static final double MAX_BLOCK_RADIUS_SQR = MAX_BLOCK_RADIUS * MAX_BLOCK_RADIUS;

    private SiegeExplosionTuning() {
    }

    public static boolean shouldLimitTerrainDamage(@Nullable Entity sourceEntity) {
        return isSiegeExplosionSource(entityTypeId(sourceEntity));
    }

    public static boolean isSiegeExplosionSource(@Nullable ResourceLocation entityId) {
        if (entityId == null) {
            return false;
        }
        String namespace = entityId.getNamespace();
        if (FULL_SIEGE_NAMESPACES.contains(namespace)) {
            return true;
        }
        if (!SMALLSHIPS_NAMESPACE.equals(namespace)) {
            return false;
        }
        String path = entityId.getPath();
        return path.contains("cannon") || path.contains("bomb") || path.contains("shell");
    }

    public static void limitAffectedBlocks(Vec3 center, List<BlockPos> affectedBlocks) {
        if (affectedBlocks == null || affectedBlocks.size() <= MAX_AFFECTED_BLOCKS) {
            return;
        }

        List<BlockPos> ordered = new ArrayList<>(affectedBlocks);
        ordered.sort(Comparator.comparingDouble(pos -> distanceSqr(center, pos)));

        List<BlockPos> kept = new ArrayList<>(MAX_AFFECTED_BLOCKS);
        for (BlockPos pos : ordered) {
            if (distanceSqr(center, pos) <= MAX_BLOCK_RADIUS_SQR) {
                kept.add(pos);
                if (kept.size() == MAX_AFFECTED_BLOCKS) {
                    affectedBlocks.clear();
                    affectedBlocks.addAll(kept);
                    return;
                }
            }
        }

        for (BlockPos pos : ordered) {
            if (kept.contains(pos)) {
                continue;
            }
            kept.add(pos);
            if (kept.size() == MAX_AFFECTED_BLOCKS) {
                break;
            }
        }

        affectedBlocks.clear();
        affectedBlocks.addAll(kept);
    }

    private static double distanceSqr(Vec3 center, BlockPos pos) {
        return center.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
    }

    @Nullable
    private static ResourceLocation entityTypeId(@Nullable Entity entity) {
        return entity == null ? null : BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
    }
}
