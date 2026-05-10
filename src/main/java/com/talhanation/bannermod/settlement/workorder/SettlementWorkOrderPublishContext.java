package com.talhanation.bannermod.settlement.workorder;

import com.talhanation.bannermod.settlement.SettlementBuildingRecord;
import com.talhanation.bannermod.settlement.SettlementSnapshot;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;

/**
 * Bundle passed to {@link SettlementWorkOrderPublisher#publish} each tick.
 *
 * <p>The {@code level} may be {@code null} under test conditions where the real world is not
 * available. Publishers that depend on the world must handle that case gracefully.</p>
 */
public record SettlementWorkOrderPublishContext(
        SettlementWorkOrderRuntime runtime,
        UUID claimUuid,
        SettlementBuildingRecord building,
        SettlementSnapshot snapshot,
        @Nullable ServerLevel level,
        long gameTime
) {
    public SettlementWorkOrderPublishContext {
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(claimUuid, "claimUuid");
        Objects.requireNonNull(building, "building");
        Objects.requireNonNull(snapshot, "snapshot");
    }

    public @Nullable <T extends Entity> T resolveBuildingEntity(Class<T> entityClass) {
        if (entityClass == null || this.level == null) {
            return null;
        }
        Entity direct = this.level.getEntity(this.building.buildingUuid());
        if (entityClass.isInstance(direct) && direct != null && direct.isAlive()) {
            return entityClass.cast(direct);
        }
        if (this.building.originPos() == null) {
            return null;
        }
        AABB searchBox = new AABB(this.building.originPos()).inflate(1.5D);
        return this.level.getEntitiesOfClass(entityClass, searchBox,
                        entity -> entity != null && entity.isAlive() && this.building.originPos().equals(entity.blockPosition()))
                .stream()
                .findFirst()
                .orElse(null);
    }
}
