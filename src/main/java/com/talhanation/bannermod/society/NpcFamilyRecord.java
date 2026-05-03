package com.talhanation.bannermod.society;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record NpcFamilyRecord(
        UUID residentUuid,
        UUID householdId,
        @Nullable UUID spouseUuid,
        @Nullable UUID motherUuid,
        @Nullable UUID fatherUuid,
        List<UUID> childUuids,
        long version,
        long lastUpdatedGameTime
) {
    public NpcFamilyRecord {
        if (residentUuid == null) {
            throw new IllegalArgumentException("residentUuid must not be null");
        }
        if (householdId == null) {
            throw new IllegalArgumentException("householdId must not be null");
        }
        childUuids = sanitizeChildren(childUuids);
        version = Math.max(1L, version);
    }

    public static NpcFamilyRecord create(UUID residentUuid,
                                         UUID householdId,
                                         @Nullable UUID spouseUuid,
                                         @Nullable UUID motherUuid,
                                         @Nullable UUID fatherUuid,
                                         @Nullable Collection<UUID> childUuids,
                                         long gameTime) {
        return new NpcFamilyRecord(residentUuid, householdId, spouseUuid, motherUuid, fatherUuid, copyChildren(childUuids), 1L, gameTime);
    }

    public NpcFamilyRecord moveResident(UUID toResidentUuid, long gameTime) {
        if (toResidentUuid == null || toResidentUuid.equals(this.residentUuid)) {
            return this;
        }
        return new NpcFamilyRecord(
                toResidentUuid,
                this.householdId,
                this.spouseUuid,
                this.motherUuid,
                this.fatherUuid,
                this.childUuids,
                this.version + 1L,
                gameTime
        );
    }

    public NpcFamilyRecord replaceReference(UUID fromResidentUuid, UUID toResidentUuid, long gameTime) {
        if (fromResidentUuid == null || toResidentUuid == null || fromResidentUuid.equals(toResidentUuid)) {
            return this;
        }
        boolean changed = false;
        UUID spouse = this.spouseUuid;
        UUID mother = this.motherUuid;
        UUID father = this.fatherUuid;
        if (fromResidentUuid.equals(spouse)) {
            spouse = toResidentUuid;
            changed = true;
        }
        if (fromResidentUuid.equals(mother)) {
            mother = toResidentUuid;
            changed = true;
        }
        if (fromResidentUuid.equals(father)) {
            father = toResidentUuid;
            changed = true;
        }
        List<UUID> children = new ArrayList<>(this.childUuids);
        for (int i = 0; i < children.size(); i++) {
            if (fromResidentUuid.equals(children.get(i))) {
                children.set(i, toResidentUuid);
                changed = true;
            }
        }
        if (!changed) {
            return this;
        }
        return new NpcFamilyRecord(
                this.residentUuid,
                this.householdId,
                spouse,
                mother,
                father,
                children,
                this.version + 1L,
                gameTime
        );
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("ResidentUuid", this.residentUuid);
        tag.putUUID("HouseholdId", this.householdId);
        if (this.spouseUuid != null) {
            tag.putUUID("SpouseUuid", this.spouseUuid);
        }
        if (this.motherUuid != null) {
            tag.putUUID("MotherUuid", this.motherUuid);
        }
        if (this.fatherUuid != null) {
            tag.putUUID("FatherUuid", this.fatherUuid);
        }
        ListTag children = new ListTag();
        for (UUID childUuid : this.childUuids) {
            if (childUuid == null) {
                continue;
            }
            CompoundTag childTag = new CompoundTag();
            childTag.putUUID("ChildUuid", childUuid);
            children.add(childTag);
        }
        tag.put("ChildUuids", children);
        tag.putLong("Version", this.version);
        tag.putLong("LastUpdatedGameTime", this.lastUpdatedGameTime);
        return tag;
    }

    public static NpcFamilyRecord fromTag(CompoundTag tag) {
        List<UUID> children = new ArrayList<>();
        for (Tag entry : tag.getList("ChildUuids", Tag.TAG_COMPOUND)) {
            CompoundTag childTag = (CompoundTag) entry;
            if (childTag.contains("ChildUuid")) {
                children.add(childTag.getUUID("ChildUuid"));
            }
        }
        return new NpcFamilyRecord(
                tag.getUUID("ResidentUuid"),
                tag.contains("HouseholdId") ? tag.getUUID("HouseholdId") : tag.getUUID("ResidentUuid"),
                tag.contains("SpouseUuid") ? tag.getUUID("SpouseUuid") : null,
                tag.contains("MotherUuid") ? tag.getUUID("MotherUuid") : null,
                tag.contains("FatherUuid") ? tag.getUUID("FatherUuid") : null,
                children,
                Math.max(1L, tag.getLong("Version")),
                tag.getLong("LastUpdatedGameTime")
        );
    }

    private static List<UUID> copyChildren(@Nullable Collection<UUID> children) {
        return children == null ? List.of() : new ArrayList<>(children);
    }

    private static List<UUID> sanitizeChildren(@Nullable Collection<UUID> children) {
        if (children == null || children.isEmpty()) {
            return List.of();
        }
        Set<UUID> ordered = new LinkedHashSet<>();
        for (UUID child : children) {
            if (child != null) {
                ordered.add(child);
            }
        }
        return List.copyOf(ordered);
    }
}
