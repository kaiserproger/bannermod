package com.talhanation.bannermod.war.runtime;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** In-memory store of pending {@link WarAllyInviteRecord}s, keyed by id. */
public class WarAllyInviteRuntime {
    private final Map<UUID, WarAllyInviteRecord> recordsById = new LinkedHashMap<>();
    private Runnable dirtyListener = () -> { };

    public void setDirtyListener(Runnable dirtyListener) {
        this.dirtyListener = dirtyListener == null ? () -> { } : dirtyListener;
    }

    public WarAllyInviteRecord create(UUID warId,
                                      WarSide side,
                                      UUID inviteePoliticalEntityId,
                                      UUID inviterPlayerUuid,
                                      long gameTime) {
        WarAllyInviteRecord record = new WarAllyInviteRecord(
                UUID.randomUUID(),
                warId,
                side,
                inviteePoliticalEntityId,
                inviterPlayerUuid,
                gameTime
        );
        recordsById.put(record.id(), record);
        dirtyListener.run();
        return record;
    }

    public boolean remove(UUID id) {
        boolean removed = recordsById.remove(id) != null;
        if (removed) dirtyListener.run();
        return removed;
    }

    public int removeForWar(UUID warId) {
        if (warId == null) return 0;
        int before = recordsById.size();
        recordsById.values().removeIf(r -> warId.equals(r.warId()));
        int removed = before - recordsById.size();
        if (removed > 0) dirtyListener.run();
        return removed;
    }

    public Optional<WarAllyInviteRecord> byId(UUID id) {
        return Optional.ofNullable(recordsById.get(id));
    }

    public Optional<WarAllyInviteRecord> byIdFragment(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        try {
            return byId(UUID.fromString(token));
        } catch (IllegalArgumentException ignored) {
            String lower = token.toLowerCase(Locale.ROOT);
            for (WarAllyInviteRecord record : recordsById.values()) {
                if (record.id().toString().toLowerCase(Locale.ROOT).startsWith(lower)) {
                    return Optional.of(record);
                }
            }
            return Optional.empty();
        }
    }

    public Collection<WarAllyInviteRecord> all() {
        return List.copyOf(recordsById.values());
    }

    public Collection<WarAllyInviteRecord> forWar(UUID warId) {
        if (warId == null) return List.of();
        List<WarAllyInviteRecord> matches = new ArrayList<>();
        for (WarAllyInviteRecord record : recordsById.values()) {
            if (warId.equals(record.warId())) matches.add(record);
        }
        return matches;
    }

    public Optional<WarAllyInviteRecord> existing(UUID warId, WarSide side, UUID inviteeEntityId) {
        if (warId == null || side == null || inviteeEntityId == null) return Optional.empty();
        for (WarAllyInviteRecord record : recordsById.values()) {
            if (warId.equals(record.warId())
                    && side == record.side()
                    && inviteeEntityId.equals(record.inviteePoliticalEntityId())) {
                return Optional.of(record);
            }
        }
        return Optional.empty();
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (WarAllyInviteRecord record : recordsById.values()) {
            list.add(record.toTag());
        }
        tag.put("Invites", list);
        return tag;
    }

    public static WarAllyInviteRuntime fromTag(CompoundTag tag) {
        WarAllyInviteRuntime runtime = new WarAllyInviteRuntime();
        if (tag == null) return runtime;
        ListTag list = tag.getList("Invites", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            WarAllyInviteRecord record = WarAllyInviteRecord.fromTag(list.getCompound(i));
            if (record != null) runtime.recordsById.put(record.id(), record);
        }
        return runtime;
    }
}
