package com.talhanation.bannermod.society;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class NpcHouseholdRuntime {
    private final Map<UUID, NpcHouseholdRecord> householdsById = new LinkedHashMap<>();
    private final Map<UUID, UUID> householdByResident = new LinkedHashMap<>();
    private final Map<UUID, UUID> householdByHomeBuilding = new LinkedHashMap<>();
    private Runnable dirtyListener = () -> {
    };

    public void setDirtyListener(Runnable dirtyListener) {
        this.dirtyListener = dirtyListener == null ? () -> {
        } : dirtyListener;
    }

    public Optional<NpcHouseholdRecord> householdFor(UUID householdId) {
        if (householdId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(this.householdsById.get(householdId));
    }

    public Optional<NpcHouseholdRecord> householdForResident(UUID residentUuid) {
        if (residentUuid == null) {
            return Optional.empty();
        }
        return householdFor(this.householdByResident.get(residentUuid));
    }

    public Optional<NpcHouseholdRecord> householdForHome(UUID homeBuildingUuid) {
        if (homeBuildingUuid == null) {
            return Optional.empty();
        }
        return householdFor(this.householdByHomeBuilding.get(homeBuildingUuid));
    }

    public void updateHead(UUID householdId, @Nullable UUID headResidentUuid, long gameTime) {
        if (householdId == null) {
            return;
        }
        NpcHouseholdRecord household = this.householdsById.get(householdId);
        if (household == null) {
            return;
        }
        NpcHouseholdRecord updated = household.withHead(headResidentUuid, gameTime);
        if (!updated.equals(household)) {
            this.householdsById.put(householdId, updated);
            markDirty();
        }
    }

    public @Nullable UUID reconcileResidentHome(UUID residentUuid,
                                                @Nullable UUID homeBuildingUuid,
                                                int residentCapacity,
                                                long gameTime) {
        if (residentUuid == null) {
            throw new IllegalArgumentException("residentUuid must not be null");
        }
        UUID currentHouseholdId = this.householdByResident.get(residentUuid);
        NpcHouseholdRecord currentHousehold = currentHouseholdId == null ? null : this.householdsById.get(currentHouseholdId);
        if (homeBuildingUuid == null) {
            return reconcileHomelessResident(residentUuid, currentHousehold, gameTime);
        }

        int normalizedCapacity = Math.max(1, residentCapacity);
        boolean changed = false;
        NpcHouseholdRecord targetHousehold = householdForHome(homeBuildingUuid).orElse(null);
        if (targetHousehold == null) {
            if (currentHousehold != null
                    && currentHousehold.homeBuildingUuid() == null
                    && currentHousehold.memberResidentUuids().size() == 1
                    && currentHousehold.hasMember(residentUuid)) {
                targetHousehold = currentHousehold;
            } else {
                targetHousehold = NpcHouseholdRecord.create(
                        UUID.randomUUID(),
                        homeBuildingUuid,
                        null,
                        List.of(),
                        normalizedCapacity,
                        NpcHouseholdHousingState.NORMAL,
                        gameTime
                );
            }
            this.householdsById.put(targetHousehold.householdId(), targetHousehold);
            changed = true;
        }

        if (currentHouseholdId != null && !currentHouseholdId.equals(targetHousehold.householdId())) {
            changed |= clearResidentInternal(residentUuid, gameTime);
        }

        NpcHouseholdRecord stored = this.householdsById.get(targetHousehold.householdId());
        if (stored == null) {
            stored = targetHousehold;
            this.householdsById.put(stored.householdId(), stored);
            changed = true;
        }

        NpcHouseholdRecord updated = stored.addMember(residentUuid, gameTime);
        updated = applyHousing(updated, homeBuildingUuid, normalizedCapacity, gameTime);
        if (!updated.equals(stored)) {
            this.householdsById.put(updated.householdId(), updated);
            changed = true;
        }

        UUID previousResidentHousehold = this.householdByResident.put(residentUuid, updated.householdId());
        if (!updated.householdId().equals(previousResidentHousehold)) {
            changed = true;
        }
        UUID previousHomeHousehold = this.householdByHomeBuilding.put(homeBuildingUuid, updated.householdId());
        if (!updated.householdId().equals(previousHomeHousehold)) {
            changed = true;
        }

        if (changed) {
            markDirty();
        }
        return updated.householdId();
    }

    public void clearResident(UUID residentUuid, long gameTime) {
        if (residentUuid == null) {
            return;
        }
        if (clearResidentInternal(residentUuid, gameTime)) {
            markDirty();
        }
    }

    public void moveResident(UUID fromResidentUuid, UUID toResidentUuid, long gameTime) {
        if (toResidentUuid == null) {
            throw new IllegalArgumentException("toResidentUuid must not be null");
        }
        if (fromResidentUuid == null || fromResidentUuid.equals(toResidentUuid)) {
            return;
        }
        UUID fromHouseholdId = this.householdByResident.get(fromResidentUuid);
        if (fromHouseholdId == null) {
            clearResident(toResidentUuid, gameTime);
            return;
        }

        boolean changed = false;
        UUID toHouseholdId = this.householdByResident.get(toResidentUuid);
        if (toHouseholdId != null && !toHouseholdId.equals(fromHouseholdId)) {
            changed |= clearResidentInternal(toResidentUuid, gameTime);
        }

        NpcHouseholdRecord household = this.householdsById.get(fromHouseholdId);
        if (household != null) {
            NpcHouseholdRecord updated = household.moveMember(fromResidentUuid, toResidentUuid, gameTime);
            updated = applyHousing(updated, updated.homeBuildingUuid(), updated.residentCapacity(), gameTime);
            if (!updated.equals(household)) {
                this.householdsById.put(updated.householdId(), updated);
                changed = true;
            }
        }

        if (this.householdByResident.remove(fromResidentUuid) != null) {
            changed = true;
        }
        UUID previous = this.householdByResident.put(toResidentUuid, fromHouseholdId);
        if (!fromHouseholdId.equals(previous)) {
            changed = true;
        }

        if (changed) {
            markDirty();
        }
    }

    public List<NpcHouseholdRecord> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(this.householdsById.values()));
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        ListTag households = new ListTag();
        for (NpcHouseholdRecord household : snapshot()) {
            households.add(household.toTag());
        }
        tag.put("Households", households);
        return tag;
    }

    public static NpcHouseholdRuntime fromTag(CompoundTag tag) {
        NpcHouseholdRuntime runtime = new NpcHouseholdRuntime();
        List<NpcHouseholdRecord> households = new ArrayList<>();
        for (Tag entry : tag.getList("Households", Tag.TAG_COMPOUND)) {
            households.add(NpcHouseholdRecord.fromTag((CompoundTag) entry));
        }
        runtime.restoreSnapshot(households);
        return runtime;
    }

    public void restoreSnapshot(@Nullable Collection<NpcHouseholdRecord> households) {
        List<NpcHouseholdRecord> before = snapshot();
        this.householdsById.clear();
        this.householdByResident.clear();
        this.householdByHomeBuilding.clear();
        if (households != null) {
            for (NpcHouseholdRecord household : households) {
                if (household == null || household.householdId() == null) {
                    continue;
                }
                this.householdsById.put(household.householdId(), household);
                if (household.homeBuildingUuid() != null) {
                    this.householdByHomeBuilding.put(household.homeBuildingUuid(), household.householdId());
                }
                for (UUID member : household.memberResidentUuids()) {
                    if (member != null) {
                        this.householdByResident.put(member, household.householdId());
                    }
                }
            }
        }
        if (!before.equals(snapshot())) {
            markDirty();
        }
    }

    public void reset() {
        if (this.householdsById.isEmpty() && this.householdByResident.isEmpty() && this.householdByHomeBuilding.isEmpty()) {
            return;
        }
        this.householdsById.clear();
        this.householdByResident.clear();
        this.householdByHomeBuilding.clear();
        markDirty();
    }

    private UUID reconcileHomelessResident(UUID residentUuid,
                                           @Nullable NpcHouseholdRecord currentHousehold,
                                           long gameTime) {
        boolean changed = false;
        NpcHouseholdRecord household = currentHousehold;
        if (household == null) {
            household = NpcHouseholdRecord.create(
                    UUID.randomUUID(),
                    null,
                    residentUuid,
                    List.of(residentUuid),
                    0,
                    NpcHouseholdHousingState.HOMELESS,
                    gameTime
            );
            this.householdsById.put(household.householdId(), household);
            this.householdByResident.put(residentUuid, household.householdId());
            markDirty();
            return household.householdId();
        }

        if (household.homeBuildingUuid() != null) {
            changed |= clearResidentInternal(residentUuid, gameTime);
            household = NpcHouseholdRecord.create(
                    UUID.randomUUID(),
                    null,
                    residentUuid,
                    List.of(residentUuid),
                    0,
                    NpcHouseholdHousingState.HOMELESS,
                    gameTime
            );
            this.householdsById.put(household.householdId(), household);
            changed = true;
        } else {
            NpcHouseholdRecord updated = applyHousing(household.addMember(residentUuid, gameTime), null, 0, gameTime);
            if (!updated.equals(household)) {
                this.householdsById.put(updated.householdId(), updated);
                household = updated;
                changed = true;
            }
        }

        UUID previous = this.householdByResident.put(residentUuid, household.householdId());
        if (!household.householdId().equals(previous)) {
            changed = true;
        }
        if (changed) {
            markDirty();
        }
        return household.householdId();
    }

    private boolean clearResidentInternal(UUID residentUuid, long gameTime) {
        UUID householdId = this.householdByResident.remove(residentUuid);
        if (householdId == null) {
            return false;
        }
        NpcHouseholdRecord household = this.householdsById.get(householdId);
        if (household == null) {
            return true;
        }
        NpcHouseholdRecord updated = household.removeMember(residentUuid, gameTime);
        if (updated.isEmpty()) {
            this.householdsById.remove(householdId);
            if (household.homeBuildingUuid() != null && householdId.equals(this.householdByHomeBuilding.get(household.homeBuildingUuid()))) {
                this.householdByHomeBuilding.remove(household.homeBuildingUuid());
            }
        } else {
            updated = applyHousing(updated, updated.homeBuildingUuid(), updated.residentCapacity(), gameTime);
            if (!updated.equals(household)) {
                this.householdsById.put(householdId, updated);
            }
        }
        return true;
    }

    private NpcHouseholdRecord applyHousing(NpcHouseholdRecord household,
                                            @Nullable UUID homeBuildingUuid,
                                            int residentCapacity,
                                            long gameTime) {
        int normalizedCapacity = homeBuildingUuid == null ? 0 : Math.max(1, residentCapacity);
        NpcHouseholdHousingState housingState = resolveHousingState(homeBuildingUuid, normalizedCapacity, household.memberResidentUuids().size());
        return household.withHousing(homeBuildingUuid, normalizedCapacity, housingState, gameTime);
    }

    private static NpcHouseholdHousingState resolveHousingState(@Nullable UUID homeBuildingUuid,
                                                                int residentCapacity,
                                                                int memberCount) {
        if (homeBuildingUuid == null) {
            return NpcHouseholdHousingState.HOMELESS;
        }
        return memberCount > Math.max(1, residentCapacity)
                ? NpcHouseholdHousingState.OVERCROWDED
                : NpcHouseholdHousingState.NORMAL;
    }

    private void markDirty() {
        this.dirtyListener.run();
    }
}
