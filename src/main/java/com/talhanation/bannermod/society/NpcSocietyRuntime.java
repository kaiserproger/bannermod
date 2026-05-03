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

public final class NpcSocietyRuntime {
    private final Map<UUID, NpcSocietyProfile> profilesByResident = new LinkedHashMap<>();
    private Runnable dirtyListener = () -> {
    };

    public void setDirtyListener(Runnable dirtyListener) {
        this.dirtyListener = dirtyListener == null ? () -> {
        } : dirtyListener;
    }

    public Optional<NpcSocietyProfile> profileFor(UUID residentUuid) {
        if (residentUuid == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(this.profilesByResident.get(residentUuid));
    }

    public NpcSocietyProfile ensureResident(UUID residentUuid, long gameTime) {
        if (residentUuid == null) {
            throw new IllegalArgumentException("residentUuid must not be null");
        }
        NpcSocietyProfile existing = this.profilesByResident.get(residentUuid);
        if (existing != null) {
            return existing;
        }
        NpcSocietyProfile created = NpcSocietyProfile.createDefault(residentUuid, gameTime);
        this.profilesByResident.put(residentUuid, created);
        markDirty();
        return created;
    }

    public NpcSocietyProfile seedResident(NpcSocietyProfile initialProfile) {
        if (initialProfile == null || initialProfile.residentUuid() == null) {
            throw new IllegalArgumentException("initialProfile must not be null");
        }
        NpcSocietyProfile existing = this.profilesByResident.get(initialProfile.residentUuid());
        if (existing != null) {
            return existing;
        }
        this.profilesByResident.put(initialProfile.residentUuid(), initialProfile);
        markDirty();
        return initialProfile;
    }

    public NpcSocietyProfile reconcilePhaseOneState(UUID residentUuid,
                                                    @Nullable UUID householdId,
                                                    @Nullable UUID homeBuildingUuid,
                                                    @Nullable UUID workBuildingUuid,
                                                    NpcDailyPhase dailyPhase,
                                                    NpcIntent currentIntent,
                                                    NpcAnchorType currentAnchor,
                                                    long gameTime) {
        NpcSocietyProfile profile = ensureResident(residentUuid, gameTime);
        NpcSocietyProfile updated = profile.withPhaseOneState(
                householdId,
                homeBuildingUuid,
                workBuildingUuid,
                dailyPhase,
                currentIntent,
                currentAnchor,
                gameTime
        );
        if (updated == profile) {
            return profile;
        }
        this.profilesByResident.put(residentUuid, updated);
        markDirty();
        return updated;
    }

    public NpcSocietyProfile moveResident(UUID fromResidentUuid, UUID toResidentUuid, long gameTime) {
        if (toResidentUuid == null) {
            throw new IllegalArgumentException("toResidentUuid must not be null");
        }
        if (fromResidentUuid == null || fromResidentUuid.equals(toResidentUuid)) {
            return ensureResident(toResidentUuid, gameTime);
        }
        NpcSocietyProfile source = this.profilesByResident.remove(fromResidentUuid);
        NpcSocietyProfile target = source == null
                ? NpcSocietyProfile.createDefault(toResidentUuid, gameTime)
                : source.moveToResident(toResidentUuid, gameTime);
        NpcSocietyProfile existing = this.profilesByResident.put(toResidentUuid, target);
        if (!target.equals(existing)) {
            markDirty();
        }
        return target;
    }

    public NpcSocietyProfile reconcileNeedState(UUID residentUuid,
                                                int hungerNeed,
                                                int fatigueNeed,
                                                int socialNeed,
                                                int safetyNeed,
                                                long gameTime) {
        NpcSocietyProfile profile = ensureResident(residentUuid, gameTime);
        NpcSocietyProfile updated = profile.withNeedState(hungerNeed, fatigueNeed, socialNeed, safetyNeed, gameTime);
        if (updated == profile) {
            return profile;
        }
        this.profilesByResident.put(residentUuid, updated);
        markDirty();
        return updated;
    }

    public List<NpcSocietyProfile> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(this.profilesByResident.values()));
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        ListTag profiles = new ListTag();
        for (NpcSocietyProfile profile : snapshot()) {
            profiles.add(profile.toTag());
        }
        tag.put("Profiles", profiles);
        return tag;
    }

    public static NpcSocietyRuntime fromTag(CompoundTag tag) {
        NpcSocietyRuntime runtime = new NpcSocietyRuntime();
        List<NpcSocietyProfile> profiles = new ArrayList<>();
        for (Tag entry : tag.getList("Profiles", Tag.TAG_COMPOUND)) {
            profiles.add(NpcSocietyProfile.fromTag((CompoundTag) entry));
        }
        runtime.restoreSnapshot(profiles);
        return runtime;
    }

    public void restoreSnapshot(@Nullable Collection<NpcSocietyProfile> profiles) {
        List<NpcSocietyProfile> before = snapshot();
        this.profilesByResident.clear();
        if (profiles != null) {
            for (NpcSocietyProfile profile : profiles) {
                if (profile != null && profile.residentUuid() != null) {
                    this.profilesByResident.put(profile.residentUuid(), profile);
                }
            }
        }
        if (!before.equals(snapshot())) {
            markDirty();
        }
    }

    public void reset() {
        if (!this.profilesByResident.isEmpty()) {
            this.profilesByResident.clear();
            markDirty();
        }
    }

    private void markDirty() {
        this.dirtyListener.run();
    }
}
