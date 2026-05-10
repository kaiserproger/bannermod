package com.talhanation.bannermod.war.runtime;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class EconomicObjectiveRuntime {
    private final Map<UUID, EconomicObjectiveRecord> objectivesById = new LinkedHashMap<>();
    private Runnable dirtyListener = () -> { };

    public void setDirtyListener(Runnable dirtyListener) {
        this.dirtyListener = dirtyListener == null ? () -> { } : dirtyListener;
    }

    public EconomicObjectiveRecord add(EconomicObjectiveRecord record) {
        objectivesById.put(record.id(), record);
        dirtyListener.run();
        return record;
    }

    public EconomicObjectiveRecord createMineDispute(@Nullable UUID warId,
                                                     @Nullable UUID localHostilityId,
                                                     UUID claimUuid,
                                                     UUID mineSiteId,
                                                     long createdGameTime,
                                                     long expiresGameTime) {
        return add(new EconomicObjectiveRecord(
                UUID.randomUUID(),
                EconomicObjectiveType.MINE_DISPUTE,
                EconomicObjectiveTargetKind.MINE,
                warId,
                localHostilityId,
                claimUuid,
                mineSiteId,
                createdGameTime,
                expiresGameTime,
                0L
        ));
    }

    public EconomicObjectiveRecord createBlockade(@Nullable UUID warId,
                                                  @Nullable UUID localHostilityId,
                                                  UUID claimUuid,
                                                  EconomicObjectiveTargetKind targetKind,
                                                  @Nullable UUID strategicObjectId,
                                                  long createdGameTime,
                                                  long expiresGameTime) {
        EconomicObjectiveTargetKind kind = targetKind == EconomicObjectiveTargetKind.STORAGE
                ? EconomicObjectiveTargetKind.STORAGE
                : EconomicObjectiveTargetKind.ROUTE;
        return add(new EconomicObjectiveRecord(
                UUID.randomUUID(),
                EconomicObjectiveType.BLOCKADE,
                kind,
                warId,
                localHostilityId,
                claimUuid,
                strategicObjectId,
                createdGameTime,
                expiresGameTime,
                0L
        ));
    }

    public EconomicObjectiveRecord createRaid(@Nullable UUID warId,
                                              @Nullable UUID localHostilityId,
                                              UUID claimUuid,
                                              EconomicObjectiveTargetKind targetKind,
                                              @Nullable UUID strategicObjectId,
                                              long createdGameTime,
                                              long expiresGameTime) {
        EconomicObjectiveTargetKind kind = targetKind == EconomicObjectiveTargetKind.MINE
                || targetKind == EconomicObjectiveTargetKind.ROUTE
                || targetKind == EconomicObjectiveTargetKind.STORAGE
                ? targetKind
                : EconomicObjectiveTargetKind.STORAGE;
        return add(new EconomicObjectiveRecord(
                UUID.randomUUID(),
                EconomicObjectiveType.RAID,
                kind,
                warId,
                localHostilityId,
                claimUuid,
                strategicObjectId,
                createdGameTime,
                expiresGameTime,
                0L
        ));
    }

    public EconomicObjectiveRecord createOutpostCapture(@Nullable UUID warId,
                                                        @Nullable UUID localHostilityId,
                                                        UUID claimUuid,
                                                        UUID outpostId,
                                                        long createdGameTime,
                                                        long expiresGameTime) {
        return add(new EconomicObjectiveRecord(
                UUID.randomUUID(),
                EconomicObjectiveType.OUTPOST_CAPTURE,
                EconomicObjectiveTargetKind.OUTPOST,
                warId,
                localHostilityId,
                claimUuid,
                outpostId,
                createdGameTime,
                expiresGameTime,
                0L
        ));
    }

    public boolean resolve(UUID objectiveId, long gameTime) {
        EconomicObjectiveRecord existing = objectivesById.get(objectiveId);
        if (existing == null || !existing.isActiveAt(gameTime)) {
            return false;
        }
        objectivesById.put(objectiveId, existing.resolve(gameTime));
        dirtyListener.run();
        return true;
    }

    public int pruneExpired(long gameTime) {
        int before = objectivesById.size();
        objectivesById.values().removeIf(objective -> objective.expiresGameTime() > 0L && gameTime >= objective.expiresGameTime());
        int removed = before - objectivesById.size();
        if (removed > 0) {
            dirtyListener.run();
        }
        return removed;
    }

    public Optional<EconomicObjectiveRecord> byId(UUID id) {
        return Optional.ofNullable(objectivesById.get(id));
    }

    public Collection<EconomicObjectiveRecord> all() {
        return List.copyOf(objectivesById.values());
    }

    public List<EconomicObjectiveRecord> activeForClaim(UUID claimUuid, long gameTime) {
        if (claimUuid == null) {
            return List.of();
        }
        return objectivesById.values().stream()
                .filter(objective -> claimUuid.equals(objective.claimUuid()) && objective.isActiveAt(gameTime))
                .toList();
    }

    public EconomicObjectiveState stateForMine(UUID claimUuid, UUID mineSiteId, long gameTime) {
        return stateForTarget(claimUuid, EconomicObjectiveTargetKind.MINE, mineSiteId, gameTime);
    }

    public EconomicObjectiveState stateForRoute(UUID claimUuid, @Nullable UUID routeId, long gameTime) {
        return stateForTarget(claimUuid, EconomicObjectiveTargetKind.ROUTE, routeId, gameTime);
    }

    public EconomicObjectiveState stateForStorage(UUID claimUuid, @Nullable UUID storageId, long gameTime) {
        return stateForTarget(claimUuid, EconomicObjectiveTargetKind.STORAGE, storageId, gameTime);
    }

    public EconomicObjectiveState stateForTarget(UUID claimUuid,
                                                 EconomicObjectiveTargetKind targetKind,
                                                 @Nullable UUID strategicObjectId,
                                                 long gameTime) {
        EconomicObjectiveState state = EconomicObjectiveState.NORMAL;
        for (EconomicObjectiveRecord objective : activeForClaim(claimUuid, gameTime)) {
            if (objective.targetKind() != targetKind) {
                continue;
            }
            if (strategicObjectId != null && objective.strategicObjectId() != null
                    && !strategicObjectId.equals(objective.strategicObjectId())) {
                continue;
            }
            state = state.merge(objective.economyStateAt(gameTime));
        }
        return state;
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (EconomicObjectiveRecord record : objectivesById.values()) {
            list.add(record.toTag());
        }
        tag.put("EconomicObjectives", list);
        return tag;
    }

    public static EconomicObjectiveRuntime fromTag(CompoundTag tag) {
        EconomicObjectiveRuntime runtime = new EconomicObjectiveRuntime();
        ListTag list = tag.getList("EconomicObjectives", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            EconomicObjectiveRecord record = EconomicObjectiveRecord.fromTag(list.getCompound(i));
            runtime.objectivesById.put(record.id(), record);
        }
        return runtime;
    }
}
