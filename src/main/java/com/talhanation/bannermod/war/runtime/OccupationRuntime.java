package com.talhanation.bannermod.war.runtime;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class OccupationRuntime {
    private final Map<UUID, OccupationRecord> recordsById = new LinkedHashMap<>();
    private Runnable dirtyListener = () -> { };

    public void setDirtyListener(Runnable dirtyListener) {
        this.dirtyListener = dirtyListener == null ? () -> { } : dirtyListener;
    }

    public Optional<OccupationRecord> place(UUID warId,
                                            UUID occupierEntityId,
                                            UUID occupiedEntityId,
                                            List<ChunkPos> chunks,
                                            long gameTime) {
        if (warId == null || occupierEntityId == null || occupiedEntityId == null
                || occupierEntityId.equals(occupiedEntityId)
                || chunks == null || chunks.isEmpty()) {
            return Optional.empty();
        }
        OccupationRecord record = new OccupationRecord(
                UUID.randomUUID(), warId, occupierEntityId, occupiedEntityId,
                chunks, gameTime);
        recordsById.put(record.id(), record);
        dirtyListener.run();
        return Optional.of(record);
    }

    public boolean remove(UUID id) {
        boolean removed = recordsById.remove(id) != null;
        if (removed) {
            dirtyListener.run();
        }
        return removed;
    }

    public Optional<OccupationRecord> updateLastTaxedAt(UUID id, long lastTaxedAtGameTime) {
        OccupationRecord existing = recordsById.get(id);
        if (existing == null) {
            return Optional.empty();
        }
        if (existing.lastTaxedAtGameTime() == lastTaxedAtGameTime) {
            return Optional.of(existing);
        }
        OccupationRecord updated = existing.withLastTaxedAtGameTime(lastTaxedAtGameTime);
        recordsById.put(id, updated);
        dirtyListener.run();
        return Optional.of(updated);
    }

    public Optional<OccupationRecord> byId(UUID id) {
        return Optional.ofNullable(recordsById.get(id));
    }

    public Optional<OccupationRecord> byIdFragment(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            return byId(UUID.fromString(token));
        } catch (IllegalArgumentException ignored) {
            String lower = token.toLowerCase(java.util.Locale.ROOT);
            for (OccupationRecord record : recordsById.values()) {
                if (record.id().toString().startsWith(lower)) {
                    return Optional.of(record);
                }
            }
            return Optional.empty();
        }
    }

    public Collection<OccupationRecord> all() {
        return List.copyOf(recordsById.values());
    }

    public Collection<OccupationRecord> forOccupied(UUID occupiedEntityId) {
        if (occupiedEntityId == null) {
            return List.of();
        }
        List<OccupationRecord> matches = new ArrayList<>();
        for (OccupationRecord record : recordsById.values()) {
            if (occupiedEntityId.equals(record.occupiedEntityId())) {
                matches.add(record);
            }
        }
        return matches;
    }

    public Collection<OccupationRecord> forOccupiedClaim(UUID occupiedEntityId, List<ChunkPos> claimChunks) {
        if (occupiedEntityId == null || claimChunks == null || claimChunks.isEmpty()) {
            return List.of();
        }
        List<OccupationRecord> matches = new ArrayList<>();
        for (OccupationRecord record : recordsById.values()) {
            if (!occupiedEntityId.equals(record.occupiedEntityId())) {
                continue;
            }
            for (ChunkPos chunk : claimChunks) {
                if (record.chunks().contains(chunk)) {
                    matches.add(record);
                    break;
                }
            }
        }
        return matches;
    }

    public Collection<OccupationRecord> forOccupiedClaimChunk(UUID occupiedEntityId, ChunkPos claimChunk) {
        if (occupiedEntityId == null || claimChunk == null) {
            return List.of();
        }
        List<OccupationRecord> matches = new ArrayList<>();
        for (OccupationRecord record : recordsById.values()) {
            if (occupiedEntityId.equals(record.occupiedEntityId()) && record.chunks().contains(claimChunk)) {
                matches.add(record);
            }
        }
        return matches;
    }

    public Collection<OccupationRecord> forOccupier(UUID occupierEntityId) {
        if (occupierEntityId == null) {
            return List.of();
        }
        List<OccupationRecord> matches = new ArrayList<>();
        for (OccupationRecord record : recordsById.values()) {
            if (occupierEntityId.equals(record.occupierEntityId())) {
                matches.add(record);
            }
        }
        return matches;
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (OccupationRecord record : recordsById.values()) {
            list.add(record.toTag());
        }
        tag.put("Occupations", list);
        return tag;
    }

    public static OccupationRuntime fromTag(CompoundTag tag) {
        OccupationRuntime runtime = new OccupationRuntime();
        ListTag list = tag.getList("Occupations", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            OccupationRecord record = OccupationRecord.fromTag(list.getCompound(i));
            runtime.recordsById.put(record.id(), record);
        }
        return runtime;
    }
}
