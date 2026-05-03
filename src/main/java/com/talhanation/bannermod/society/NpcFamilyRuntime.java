package com.talhanation.bannermod.society;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class NpcFamilyRuntime {
    private final Map<UUID, NpcFamilyRecord> familyByResident = new LinkedHashMap<>();
    private Runnable dirtyListener = () -> {
    };

    public void setDirtyListener(Runnable dirtyListener) {
        this.dirtyListener = dirtyListener == null ? () -> {
        } : dirtyListener;
    }

    public Optional<NpcFamilyRecord> familyFor(UUID residentUuid) {
        if (residentUuid == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(this.familyByResident.get(residentUuid));
    }

    public void replaceHousehold(UUID householdId, Collection<NpcFamilyRecord> records) {
        boolean changed = false;
        if (householdId == null) {
            return;
        }
        List<UUID> toRemove = new ArrayList<>();
        for (NpcFamilyRecord record : this.familyByResident.values()) {
            if (record != null && householdId.equals(record.householdId())) {
                toRemove.add(record.residentUuid());
            }
        }
        for (UUID residentUuid : toRemove) {
            if (this.familyByResident.remove(residentUuid) != null) {
                changed = true;
            }
        }
        if (records != null) {
            for (NpcFamilyRecord record : records) {
                if (record != null) {
                    NpcFamilyRecord previous = this.familyByResident.put(record.residentUuid(), record);
                    if (!record.equals(previous)) {
                        changed = true;
                    }
                }
            }
        }
        if (changed) {
            markDirty();
        }
    }

    public void moveResident(UUID fromResidentUuid, UUID toResidentUuid, long gameTime) {
        if (fromResidentUuid == null || toResidentUuid == null || fromResidentUuid.equals(toResidentUuid)) {
            return;
        }
        boolean changed = false;
        NpcFamilyRecord ownRecord = this.familyByResident.remove(fromResidentUuid);
        if (ownRecord != null) {
            this.familyByResident.put(toResidentUuid, ownRecord.moveResident(toResidentUuid, gameTime));
            changed = true;
        }
        List<NpcFamilyRecord> updated = new ArrayList<>();
        for (NpcFamilyRecord record : this.familyByResident.values()) {
            updated.add(record == null ? null : record.replaceReference(fromResidentUuid, toResidentUuid, gameTime));
        }
        if (!updated.isEmpty()) {
            this.familyByResident.clear();
            for (NpcFamilyRecord record : updated) {
                if (record != null) {
                    this.familyByResident.put(record.residentUuid(), record);
                }
            }
            changed = true;
        }
        if (changed) {
            markDirty();
        }
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        ListTag families = new ListTag();
        for (NpcFamilyRecord record : this.familyByResident.values()) {
            families.add(record.toTag());
        }
        tag.put("Families", families);
        return tag;
    }

    public static NpcFamilyRuntime fromTag(CompoundTag tag) {
        NpcFamilyRuntime runtime = new NpcFamilyRuntime();
        List<NpcFamilyRecord> records = new ArrayList<>();
        for (Tag entry : tag.getList("Families", Tag.TAG_COMPOUND)) {
            records.add(NpcFamilyRecord.fromTag((CompoundTag) entry));
        }
        runtime.restoreSnapshot(records);
        return runtime;
    }

    public void restoreSnapshot(@Nullable Collection<NpcFamilyRecord> records) {
        this.familyByResident.clear();
        if (records != null) {
            for (NpcFamilyRecord record : records) {
                if (record != null) {
                    this.familyByResident.put(record.residentUuid(), record);
                }
            }
        }
    }

    private void markDirty() {
        this.dirtyListener.run();
    }
}
