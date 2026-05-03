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
    private final Map<UUID, NpcHousingRequestRecord> requestsByResident = new LinkedHashMap<>();
    private Runnable dirtyListener = () -> {
    };

    public void setDirtyListener(Runnable dirtyListener) {
        this.dirtyListener = dirtyListener == null ? () -> {
        } : dirtyListener;
    }

    public Optional<NpcHousingRequestRecord> requestFor(UUID residentUuid) {
        if (residentUuid == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(this.requestsByResident.get(residentUuid));
    }

    public NpcHousingRequestRecord ensureRequest(UUID residentUuid,
                                                 UUID claimUuid,
                                                 UUID projectId,
                                                 @Nullable UUID lordPlayerUuid,
                                                 long gameTime) {
        NpcHousingRequestRecord existing = this.requestsByResident.get(residentUuid);
        if (existing != null && existing.status() != NpcHousingRequestStatus.FULFILLED) {
            return existing;
        }
        NpcHousingRequestRecord created = NpcHousingRequestRecord.create(residentUuid, claimUuid, projectId, lordPlayerUuid, gameTime);
        this.requestsByResident.put(residentUuid, created);
        markDirty();
        return created;
    }

    public NpcHousingRequestRecord approve(UUID residentUuid, long gameTime) {
        NpcHousingRequestRecord existing = this.requestsByResident.get(residentUuid);
        if (existing == null) {
            throw new IllegalArgumentException("No housing request exists for resident " + residentUuid);
        }
        NpcHousingRequestRecord updated = existing.approve(gameTime);
        if (!updated.equals(existing)) {
            this.requestsByResident.put(residentUuid, updated);
            markDirty();
        }
        return updated;
    }

    public void fulfill(UUID residentUuid, long gameTime) {
        NpcHousingRequestRecord existing = this.requestsByResident.get(residentUuid);
        if (existing == null) {
            return;
        }
        NpcHousingRequestRecord updated = existing.fulfill(gameTime);
        if (!updated.equals(existing)) {
            this.requestsByResident.put(residentUuid, updated);
            markDirty();
        }
    }

    public List<NpcHousingRequestRecord> requestsForClaim(UUID claimUuid) {
        if (claimUuid == null) {
            return Collections.emptyList();
        }
        List<NpcHousingRequestRecord> matches = new ArrayList<>();
        for (NpcHousingRequestRecord request : this.requestsByResident.values()) {
            if (request != null && claimUuid.equals(request.claimUuid())) {
                matches.add(request);
            }
        }
        return matches;
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        ListTag requests = new ListTag();
        for (NpcHousingRequestRecord request : this.requestsByResident.values()) {
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
        this.requestsByResident.clear();
        if (requests != null) {
            for (NpcHousingRequestRecord request : requests) {
                if (request != null) {
                    this.requestsByResident.put(request.residentUuid(), request);
                }
            }
        }
    }

    private void markDirty() {
        this.dirtyListener.run();
    }
}
