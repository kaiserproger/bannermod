package com.talhanation.bannermod.events;

import com.talhanation.bannermod.war.runtime.SiegeExplosionTuning;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiegeExplosionTuningTest {
    @Test
    void recognizesConfiguredSiegeNamespaces() {
        assertTrue(SiegeExplosionTuning.isSiegeExplosionSource(ResourceLocation.fromNamespaceAndPath("siegemachines", "catapult_boulder")));
        assertTrue(SiegeExplosionTuning.isSiegeExplosionSource(ResourceLocation.fromNamespaceAndPath("siegeweapons", "trebuchet_stone")));
        assertTrue(SiegeExplosionTuning.isSiegeExplosionSource(ResourceLocation.fromNamespaceAndPath("smallships", "cannonball")));
        assertFalse(SiegeExplosionTuning.isSiegeExplosionSource(ResourceLocation.fromNamespaceAndPath("smallships", "galley")));
        assertFalse(SiegeExplosionTuning.isSiegeExplosionSource(ResourceLocation.fromNamespaceAndPath("minecraft", "tnt")));
    }

    @Test
    void keepsOnlyTighterNearestBreachBlocks() {
        List<BlockPos> affected = new ArrayList<>();
        for (int x = 0; x < 30; x++) {
            affected.add(new BlockPos(x, 64, 0));
        }

        SiegeExplosionTuning.limitAffectedBlocks(new Vec3(0.5D, 64.5D, 0.5D), affected);

        assertEquals(20, affected.size());
        assertTrue(affected.contains(new BlockPos(0, 64, 0)));
        assertTrue(affected.contains(new BlockPos(3, 64, 0)));
        assertFalse(affected.contains(new BlockPos(29, 64, 0)));
    }
}
