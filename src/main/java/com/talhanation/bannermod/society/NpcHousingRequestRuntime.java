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

public final class NpcHousingRequestRuntime {
    private final Map<UUID, NpcHousingRequestRecord> requestsByHousehold = new LinkedHashMap<>();
    private Runnable dirtyListener = () -> {
    };

    public void setDirtyListener(Runnable dirtyListener) {
        this.dirtyListener = dirtyListener == null ? () -> {
        } : dirtyListener;
    }

    public Optional<NpcHousingRequestRecord> requestForHousehold(UUID householdId) {
        if (householdId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(this.requestsByHousehold.get(householdId));
    }

    public NpcHousingRequestRecord ensureRequest(UUID householdId,
                                                 UUID residentUuid,
                                                 UUID claimUuid,
                                                 UUID projectId,
                                                 @Nullable UUID lordPlayerUuid,
                                                 long gameTime) {
        NpcHousingRequestRecord existing = this.requestsByHousehold.get(householdId);
        if (existing != null && existing.status() != NpcHousingRequestStatus.FULFILLED) {
            return existing;
        }
        NpcHousingRequestRecord created = NpcHousingRequestRecord.create(householdId, residentUuid, claimUuid, projectId, lordPlayerUuid, gameTime);
        this.requestsByHousehold.put(householdId, created);
        markDirty();
        return created;
    }

    public NpcHousingRequestRecord approve(UUID householdId, long gameTime) {
        NpcHousingRequestRecord existing = this.requestsByHousehold.get(householdId);
        if (existing == null) {
            throw new IllegalArgumentException("No housing request exists for household " + householdId);
        }
        NpcHousingRequestRecord updated = existing.approve(gameTime);
        if (!updated.equals(existing)) {
            this.requestsByHousehold.put(householdId, updated);
            markDirty();
        }
        return updated;
    }

    public NpcHousingRequestRecord deny(UUID householdId, long gameTime) {
        NpcHousingRequestRecord existing = this.requestsByHousehold.get(householdId);
        if (existing == null) {
            throw new IllegalArgumentException("No housing request exists for household " + householdId);
        }
        NpcHousingRequestRecord updated = existing.deny(gameTime);
        if (!updated.equals(existing)) {
            this.requestsByHousehold.put(householdId, updated);
            markDirty();
        }
        return updated;
    }

    public void fulfill(UUID householdId, long gameTime) {
        NpcHousingRequestRecord existing = this.requestsByHousehold.get(householdId);
        if (existing == null) {
            return;
        }
        NpcHousingRequestRecord updated = existing.fulfill(gameTime);
        if (!updated.equals(existing)) {
            this.requestsByHousehold.put(householdId, updated);
            markDirty();
        }
    }

    public List<NpcHousingRequestRecord> requestsForClaim(UUID claimUuid) {
        if (claimUuid == null) {
            return Collections.emptyList();
        }
        List<NpcHousingRequestRecord> matches = new ArrayList<>();
        for (NpcHousingRequestRecord request : this.requestsByHousehold.values()) {
            if (request != null && claimUuid.equals(request.claimUuid())) {
                matches.add(request);
            }
        }
        return matches;
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        ListTag requests = new ListTag();
        for (NpcHousingRequestRecord request : this.requestsByHousehold.values()) {
            requests.add(request.toTag());
        }
        tag.put("Requests", requests);
        return tag;
    }

    public static NpcHousingRequestRuntime fromTag(CompoundTag tag) {
        NpcHousingRequestRuntime runtime = new NpcHousingRequestRuntime();
        List<NpcHousingRequestRecord> requests = new ArrayList<>();
        for (Tag entry : tag.getList("Requests", Tag.TAG_COMPOUND)) {
            requests.add(NpcHousingRequestRecord.fromTag((CompoundTag) entry));
        }
        runtime.restoreSnapshot(requests);
        return runtime;
    }

    public void restoreSnapshot(@Nullable Collection<NpcHousingRequestRecord> requests) {
        this.requestsByHousehold.clear();
        if (requests != null) {
            for (NpcHousingRequestRecord request : requests) {
                if (request != null) {
                    this.requestsByHousehold.put(request.householdId(), request);
                }
            }
        }
    }

    private void markDirty() {
        this.dirtyListener.run();
    }
}
