package com.talhanation.bannermod.society;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class NpcLivelihoodRequestRuntime {
    private final Map<UUID, Map<NpcLivelihoodRequestType, NpcLivelihoodRequestRecord>> requestsByClaim = new LinkedHashMap<>();
    private Runnable dirtyListener = () -> {
    };

    public void setDirtyListener(Runnable dirtyListener) {
        this.dirtyListener = dirtyListener == null ? () -> {
        } : dirtyListener;
    }

    public Optional<NpcLivelihoodRequestRecord> requestFor(UUID claimUuid, NpcLivelihoodRequestType type) {
        if (claimUuid == null || type == null) {
            return Optional.empty();
        }
        Map<NpcLivelihoodRequestType, NpcLivelihoodRequestRecord> byType = this.requestsByClaim.get(claimUuid);
        return byType == null ? Optional.empty() : Optional.ofNullable(byType.get(type));
    }

    public NpcLivelihoodRequestRecord ensureRequest(UUID claimUuid,
                                                    UUID representativeResidentUuid,
                                                    NpcLivelihoodRequestType type,
                                                    UUID projectId,
                                                    @Nullable UUID lordPlayerUuid,
                                                    long gameTime) {
        NpcLivelihoodRequestRecord existing = requestFor(claimUuid, type).orElse(null);
        if (existing != null && existing.status() != NpcLivelihoodRequestStatus.FULFILLED) {
            return existing;
        }
        NpcLivelihoodRequestRecord created = NpcLivelihoodRequestRecord.create(
                claimUuid,
                representativeResidentUuid,
                type,
                projectId,
                lordPlayerUuid,
                gameTime
        );
        this.requestsByClaim.computeIfAbsent(claimUuid, ignored -> new EnumMap<>(NpcLivelihoodRequestType.class))
                .put(type, created);
        markDirty();
        return created;
    }

    public NpcLivelihoodRequestRecord approve(UUID claimUuid, NpcLivelihoodRequestType type, long gameTime) {
        NpcLivelihoodRequestRecord existing = require(claimUuid, type);
        NpcLivelihoodRequestRecord updated = existing.approve(gameTime);
        storeIfChanged(claimUuid, type, existing, updated);
        return updated;
    }

    public NpcLivelihoodRequestRecord deny(UUID claimUuid, NpcLivelihoodRequestType type, long gameTime) {
        NpcLivelihoodRequestRecord existing = require(claimUuid, type);
        NpcLivelihoodRequestRecord updated = existing.deny(gameTime);
        storeIfChanged(claimUuid, type, existing, updated);
        return updated;
    }

    public void fulfill(UUID claimUuid, NpcLivelihoodRequestType type, long gameTime) {
        NpcLivelihoodRequestRecord existing = requestFor(claimUuid, type).orElse(null);
        if (existing == null) {
            return;
        }
        NpcLivelihoodRequestRecord updated = existing.fulfill(gameTime);
        storeIfChanged(claimUuid, type, existing, updated);
    }

    public List<NpcLivelihoodRequestRecord> requestsForClaim(UUID claimUuid) {
        if (claimUuid == null) {
            return Collections.emptyList();
        }
        Map<NpcLivelihoodRequestType, NpcLivelihoodRequestRecord> byType = this.requestsByClaim.get(claimUuid);
        if (byType == null || byType.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(byType.values());
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        ListTag requests = new ListTag();
        for (Map<NpcLivelihoodRequestType, NpcLivelihoodRequestRecord> byType : this.requestsByClaim.values()) {
            for (NpcLivelihoodRequestRecord request : byType.values()) {
                requests.add(request.toTag());
            }
        }
        tag.put("Requests", requests);
        return tag;
    }

    public static NpcLivelihoodRequestRuntime fromTag(CompoundTag tag) {
        NpcLivelihoodRequestRuntime runtime = new NpcLivelihoodRequestRuntime();
        List<NpcLivelihoodRequestRecord> requests = new ArrayList<>();
        for (Tag entry : tag.getList("Requests", Tag.TAG_COMPOUND)) {
            requests.add(NpcLivelihoodRequestRecord.fromTag((CompoundTag) entry));
        }
        runtime.restoreSnapshot(requests);
        return runtime;
    }

    public void restoreSnapshot(@Nullable Collection<NpcLivelihoodRequestRecord> requests) {
        this.requestsByClaim.clear();
        if (requests != null) {
            for (NpcLivelihoodRequestRecord request : requests) {
                if (request != null) {
                    this.requestsByClaim.computeIfAbsent(request.claimUuid(), ignored -> new EnumMap<>(NpcLivelihoodRequestType.class))
                            .put(request.type(), request);
                }
            }
        }
    }

    private NpcLivelihoodRequestRecord require(UUID claimUuid, NpcLivelihoodRequestType type) {
        return requestFor(claimUuid, type)
                .orElseThrow(() -> new IllegalArgumentException("No livelihood request exists for claim " + claimUuid + " and type " + type));
    }

    private void storeIfChanged(UUID claimUuid,
                                NpcLivelihoodRequestType type,
                                NpcLivelihoodRequestRecord existing,
                                NpcLivelihoodRequestRecord updated) {
        if (!updated.equals(existing)) {
            this.requestsByClaim.computeIfAbsent(claimUuid, ignored -> new EnumMap<>(NpcLivelihoodRequestType.class))
                    .put(type, updated);
            markDirty();
        }
    }

    private void markDirty() {
        this.dirtyListener.run();
    }
}
