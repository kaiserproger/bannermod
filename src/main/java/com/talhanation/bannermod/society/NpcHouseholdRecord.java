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

public record NpcHouseholdRecord(
        UUID householdId,
        @Nullable UUID homeBuildingUuid,
        @Nullable UUID headResidentUuid,
        List<UUID> memberResidentUuids,
        int residentCapacity,
        NpcHouseholdHousingState housingState,
        long version,
        long lastUpdatedGameTime
) {
    public NpcHouseholdRecord {
        if (householdId == null) {
            throw new IllegalArgumentException("householdId must not be null");
        }
        memberResidentUuids = sanitizeMembers(memberResidentUuids);
        residentCapacity = Math.max(0, residentCapacity);
        housingState = housingState == null ? NpcHouseholdHousingState.HOMELESS : housingState;
        version = Math.max(1L, version);
    }

    public static NpcHouseholdRecord create(UUID householdId,
                                            @Nullable UUID homeBuildingUuid,
                                            @Nullable UUID headResidentUuid,
                                            @Nullable Collection<UUID> memberResidentUuids,
                                            int residentCapacity,
                                            NpcHouseholdHousingState housingState,
                                            long gameTime) {
        return new NpcHouseholdRecord(
                householdId,
                homeBuildingUuid,
                headResidentUuid,
                copyMembers(memberResidentUuids),
                residentCapacity,
                housingState,
                1L,
                gameTime
        );
    }

    public boolean hasMember(UUID residentUuid) {
        return residentUuid != null && this.memberResidentUuids.contains(residentUuid);
    }

    public boolean isEmpty() {
        return this.memberResidentUuids.isEmpty();
    }

    public NpcHouseholdRecord withHome(@Nullable UUID homeBuildingUuid, long gameTime) {
        if (sameNullableUuid(this.homeBuildingUuid, homeBuildingUuid)) {
            return this;
        }
        return new NpcHouseholdRecord(
                this.householdId,
                homeBuildingUuid,
                this.headResidentUuid,
                this.memberResidentUuids,
                this.residentCapacity,
                this.housingState,
                this.version + 1L,
                gameTime
        );
    }

    public NpcHouseholdRecord withHousing(@Nullable UUID homeBuildingUuid,
                                          int residentCapacity,
                                          NpcHouseholdHousingState housingState,
                                          long gameTime) {
        int normalizedCapacity = Math.max(0, residentCapacity);
        NpcHouseholdHousingState normalizedState = housingState == null ? NpcHouseholdHousingState.HOMELESS : housingState;
        if (sameNullableUuid(this.homeBuildingUuid, homeBuildingUuid)
                && this.residentCapacity == normalizedCapacity
                && this.housingState == normalizedState) {
            return this;
        }
        return new NpcHouseholdRecord(
                this.householdId,
                homeBuildingUuid,
                this.headResidentUuid,
                this.memberResidentUuids,
                normalizedCapacity,
                normalizedState,
                this.version + 1L,
                gameTime
        );
    }

    public NpcHouseholdRecord withHead(@Nullable UUID headResidentUuid, long gameTime) {
        if (sameNullableUuid(this.headResidentUuid, headResidentUuid)) {
            return this;
        }
        return new NpcHouseholdRecord(
                this.householdId,
                this.homeBuildingUuid,
                headResidentUuid,
                this.memberResidentUuids,
                this.residentCapacity,
                this.housingState,
                this.version + 1L,
                gameTime
        );
    }

    public NpcHouseholdRecord addMember(UUID residentUuid, long gameTime) {
        if (residentUuid == null || this.memberResidentUuids.contains(residentUuid)) {
            return this;
        }
        List<UUID> updatedMembers = new ArrayList<>(this.memberResidentUuids);
        updatedMembers.add(residentUuid);
        return new NpcHouseholdRecord(
                this.householdId,
                this.homeBuildingUuid,
                this.headResidentUuid,
                updatedMembers,
                this.residentCapacity,
                this.housingState,
                this.version + 1L,
                gameTime
        );
    }

    public NpcHouseholdRecord removeMember(UUID residentUuid, long gameTime) {
        if (residentUuid == null || !this.memberResidentUuids.contains(residentUuid)) {
            return this;
        }
        List<UUID> updatedMembers = new ArrayList<>(this.memberResidentUuids);
        updatedMembers.remove(residentUuid);
        return new NpcHouseholdRecord(
                this.householdId,
                this.homeBuildingUuid,
                residentUuid.equals(this.headResidentUuid) ? null : this.headResidentUuid,
                updatedMembers,
                this.residentCapacity,
                this.housingState,
                this.version + 1L,
                gameTime
        );
    }

    public NpcHouseholdRecord moveMember(UUID fromResidentUuid, UUID toResidentUuid, long gameTime) {
        if (fromResidentUuid == null || toResidentUuid == null || fromResidentUuid.equals(toResidentUuid)) {
            return this;
        }
        if (!this.memberResidentUuids.contains(fromResidentUuid)) {
            return this;
        }
        List<UUID> updatedMembers = new ArrayList<>(this.memberResidentUuids);
        updatedMembers.remove(fromResidentUuid);
        if (!updatedMembers.contains(toResidentUuid)) {
            updatedMembers.add(toResidentUuid);
        }
        return new NpcHouseholdRecord(
                this.householdId,
                this.homeBuildingUuid,
                fromResidentUuid.equals(this.headResidentUuid) ? toResidentUuid : this.headResidentUuid,
                updatedMembers,
                this.residentCapacity,
                this.housingState,
                this.version + 1L,
                gameTime
        );
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("HouseholdId", this.householdId);
        if (this.homeBuildingUuid != null) {
            tag.putUUID("HomeBuildingUuid", this.homeBuildingUuid);
        }
        if (this.headResidentUuid != null) {
            tag.putUUID("HeadResidentUuid", this.headResidentUuid);
        }
        ListTag members = new ListTag();
        for (UUID member : this.memberResidentUuids) {
            if (member == null) {
                continue;
            }
            CompoundTag memberTag = new CompoundTag();
            memberTag.putUUID("ResidentUuid", member);
            members.add(memberTag);
        }
        tag.put("Members", members);
        tag.putInt("ResidentCapacity", this.residentCapacity);
        tag.putString("HousingState", this.housingState.name());
        tag.putLong("Version", this.version);
        tag.putLong("LastUpdatedGameTime", this.lastUpdatedGameTime);
        return tag;
    }

    public static NpcHouseholdRecord fromTag(CompoundTag tag) {
        List<UUID> members = new ArrayList<>();
        for (Tag entry : tag.getList("Members", Tag.TAG_COMPOUND)) {
            CompoundTag memberTag = (CompoundTag) entry;
            if (memberTag.contains("ResidentUuid")) {
                members.add(memberTag.getUUID("ResidentUuid"));
            }
        }
        return new NpcHouseholdRecord(
                tag.getUUID("HouseholdId"),
                tag.contains("HomeBuildingUuid") ? tag.getUUID("HomeBuildingUuid") : null,
                tag.contains("HeadResidentUuid") ? tag.getUUID("HeadResidentUuid") : null,
                members,
                tag.contains("ResidentCapacity") ? tag.getInt("ResidentCapacity") : 0,
                tag.contains("HousingState")
                        ? NpcHouseholdHousingState.fromName(tag.getString("HousingState"))
                        : (tag.contains("HomeBuildingUuid") ? NpcHouseholdHousingState.NORMAL : NpcHouseholdHousingState.HOMELESS),
                Math.max(1L, tag.getLong("Version")),
                tag.getLong("LastUpdatedGameTime")
        );
    }

    private static boolean sameNullableUuid(@Nullable UUID left, @Nullable UUID right) {
        if (left == null || right == null) {
            return left == null && right == null;
        }
        return left.equals(right);
    }

    private static List<UUID> copyMembers(@Nullable Collection<UUID> members) {
        return members == null ? List.of() : new ArrayList<>(members);
    }

    private static List<UUID> sanitizeMembers(@Nullable Collection<UUID> members) {
        if (members == null || members.isEmpty()) {
            return List.of();
        }
        Set<UUID> ordered = new LinkedHashSet<>();
        for (UUID member : members) {
            if (member != null) {
                ordered.add(member);
            }
        }
        return List.copyOf(ordered);
    }
}
