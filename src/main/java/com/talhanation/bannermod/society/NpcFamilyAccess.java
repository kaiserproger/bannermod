package com.talhanation.bannermod.society;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class NpcFamilyAccess {
    private NpcFamilyAccess() {
    }

    public static void reconcileFamilyForResident(ServerLevel level, UUID residentUuid, long gameTime) {
        if (level == null || residentUuid == null) {
            return;
        }
        NpcHouseholdRecord household = NpcHouseholdAccess.householdForResident(level, residentUuid).orElse(null);
        if (household == null) {
            return;
        }
        reconcileHousehold(level, household, gameTime);
    }

    public static void reconcileHousehold(ServerLevel level, UUID householdId, long gameTime) {
        if (level == null || householdId == null) {
            return;
        }
        NpcHouseholdRecord household = NpcHouseholdAccess.householdFor(level, householdId).orElse(null);
        if (household == null) {
            return;
        }
        reconcileHousehold(level, household, gameTime);
    }

    public static void moveResident(ServerLevel level, UUID fromResidentUuid, UUID toResidentUuid, long gameTime) {
        if (level == null) {
            return;
        }
        NpcFamilySavedData.get(level).runtime().moveResident(fromResidentUuid, toResidentUuid, gameTime);
    }

    public static NpcFamilyTreeSnapshot familyTreeSnapshot(ServerLevel level, UUID residentUuid, long gameTime) {
        if (level == null || residentUuid == null) {
            return NpcFamilyTreeSnapshot.empty();
        }
        NpcSocietyProfile selfProfile = NpcSocietyAccess.ensureResident(level, residentUuid, gameTime);
        NpcFamilyRecord family = NpcFamilySavedData.get(level).runtime().familyFor(residentUuid).orElse(null);
        NpcFamilyMemberSnapshot self = memberSnapshot(level, residentUuid, selfProfile, "self", gameTime);
        if (family == null) {
            return new NpcFamilyTreeSnapshot(self, null, null, null, List.of());
        }
        NpcFamilyMemberSnapshot spouse = family.spouseUuid() == null ? null : memberSnapshot(level, family.spouseUuid(), null, "spouse", gameTime);
        NpcFamilyMemberSnapshot mother = family.motherUuid() == null ? null : memberSnapshot(level, family.motherUuid(), null, "mother", gameTime);
        NpcFamilyMemberSnapshot father = family.fatherUuid() == null ? null : memberSnapshot(level, family.fatherUuid(), null, "father", gameTime);
        List<NpcFamilyMemberSnapshot> children = new ArrayList<>();
        for (UUID childUuid : family.childUuids()) {
            if (childUuid != null) {
                children.add(memberSnapshot(level, childUuid, null, "child", gameTime));
            }
        }
        return new NpcFamilyTreeSnapshot(self, spouse, mother, father, List.copyOf(children));
    }

    private static void reconcileHousehold(ServerLevel level, NpcHouseholdRecord household, long gameTime) {
        NpcFamilyRuntime runtime = NpcFamilySavedData.get(level).runtime();
        List<NpcSocietyProfile> members = new ArrayList<>();
        Map<UUID, NpcFamilyRecord> existingByResident = new LinkedHashMap<>();
        for (UUID memberResidentUuid : household.memberResidentUuids()) {
            if (memberResidentUuid == null) {
                continue;
            }
            members.add(NpcSocietyAccess.ensureResident(level, memberResidentUuid, gameTime));
            runtime.familyFor(memberResidentUuid).ifPresent(record -> existingByResident.put(memberResidentUuid, record));
        }
        members.sort(memberOrder());
        Set<UUID> validResidentIds = new LinkedHashSet<>();
        for (NpcSocietyProfile member : members) {
            if (member.residentUuid() != null) {
                validResidentIds.add(member.residentUuid());
            }
        }

        UUID headResidentUuid = chooseHead(household, members, validResidentIds);
        SpousePair spousePair = chooseSpousePair(members, existingByResident, validResidentIds);
        UUID defaultMotherUuid = spousePair == null ? null : spousePair.femaleAdult();
        UUID defaultFatherUuid = spousePair == null ? null : spousePair.maleAdult();

        Map<UUID, UUID> motherByChild = new LinkedHashMap<>();
        Map<UUID, UUID> fatherByChild = new LinkedHashMap<>();
        for (NpcSocietyProfile member : members) {
            UUID residentUuid = member.residentUuid();
            if (residentUuid == null || !isMinor(member)) {
                continue;
            }
            NpcFamilyRecord existing = existingByResident.get(residentUuid);
            UUID motherUuid = existing != null && validResidentIds.contains(existing.motherUuid()) ? existing.motherUuid() : defaultMotherUuid;
            UUID fatherUuid = existing != null && validResidentIds.contains(existing.fatherUuid()) ? existing.fatherUuid() : defaultFatherUuid;
            motherByChild.put(residentUuid, motherUuid);
            fatherByChild.put(residentUuid, fatherUuid);
        }

        Map<UUID, List<UUID>> childrenByParent = new LinkedHashMap<>();
        for (NpcSocietyProfile member : members) {
            if (member.residentUuid() != null) {
                childrenByParent.put(member.residentUuid(), new ArrayList<>());
            }
        }
        for (Map.Entry<UUID, UUID> entry : motherByChild.entrySet()) {
            if (entry.getValue() != null && childrenByParent.containsKey(entry.getValue())) {
                childrenByParent.get(entry.getValue()).add(entry.getKey());
            }
        }
        for (Map.Entry<UUID, UUID> entry : fatherByChild.entrySet()) {
            if (entry.getValue() != null && childrenByParent.containsKey(entry.getValue())
                    && !childrenByParent.get(entry.getValue()).contains(entry.getKey())) {
                childrenByParent.get(entry.getValue()).add(entry.getKey());
            }
        }

        List<NpcFamilyRecord> records = new ArrayList<>();
        for (NpcSocietyProfile member : members) {
            UUID residentUuid = member.residentUuid();
            if (residentUuid == null) {
                continue;
            }
            NpcFamilyRecord existing = existingByResident.get(residentUuid);
            UUID spouseUuid = resolveSpouse(residentUuid, spousePair, validResidentIds, existing);
            UUID motherUuid = isMinor(member) ? motherByChild.get(residentUuid) : null;
            UUID fatherUuid = isMinor(member) ? fatherByChild.get(residentUuid) : null;
            List<UUID> ownChildren = childrenByParent.getOrDefault(residentUuid, List.of());
            records.add(NpcFamilyRecord.create(
                    residentUuid,
                    household.householdId(),
                    spouseUuid,
                    motherUuid,
                    fatherUuid,
                    ownChildren,
                    gameTime
            ));
        }

        NpcHouseholdAccess.updateHead(level, household.householdId(), headResidentUuid, gameTime);
        runtime.replaceHousehold(household.householdId(), records);
    }

    private static @Nullable UUID chooseHead(NpcHouseholdRecord household,
                                             List<NpcSocietyProfile> members,
                                             Set<UUID> validResidentIds) {
        if (household.headResidentUuid() != null && validResidentIds.contains(household.headResidentUuid())) {
            return household.headResidentUuid();
        }
        for (NpcSocietyProfile member : members) {
            if (isAdult(member) && member.residentUuid() != null) {
                return member.residentUuid();
            }
        }
        return members.isEmpty() ? null : members.getFirst().residentUuid();
    }

    private static @Nullable SpousePair chooseSpousePair(List<NpcSocietyProfile> members,
                                                         Map<UUID, NpcFamilyRecord> existingByResident,
                                                         Set<UUID> validResidentIds) {
        for (NpcSocietyProfile member : members) {
            UUID residentUuid = member.residentUuid();
            if (residentUuid == null || !isAdult(member)) {
                continue;
            }
            NpcFamilyRecord existing = existingByResident.get(residentUuid);
            if (existing == null || existing.spouseUuid() == null || !validResidentIds.contains(existing.spouseUuid())) {
                continue;
            }
            NpcFamilyRecord spouseRecord = existingByResident.get(existing.spouseUuid());
            if (spouseRecord == null || !residentUuid.equals(spouseRecord.spouseUuid())) {
                continue;
            }
            NpcSocietyProfile spouseProfile = profileById(members, existing.spouseUuid());
            if (spouseProfile != null && isAdult(spouseProfile)) {
                return SpousePair.of(member, spouseProfile);
            }
        }

        NpcSocietyProfile female = null;
        NpcSocietyProfile male = null;
        for (NpcSocietyProfile member : members) {
            if (!isAdult(member) || member.residentUuid() == null) {
                continue;
            }
            if (female == null && member.sex() == NpcSex.FEMALE) {
                female = member;
            } else if (male == null && member.sex() == NpcSex.MALE) {
                male = member;
            }
        }
        if (female != null && male != null && !female.residentUuid().equals(male.residentUuid())) {
            return SpousePair.of(female, male);
        }
        return null;
    }

    private static @Nullable UUID resolveSpouse(UUID residentUuid,
                                                @Nullable SpousePair spousePair,
                                                Set<UUID> validResidentIds,
                                                @Nullable NpcFamilyRecord existing) {
        if (spousePair != null) {
            if (residentUuid.equals(spousePair.first())) {
                return spousePair.second();
            }
            if (residentUuid.equals(spousePair.second())) {
                return spousePair.first();
            }
        }
        if (existing != null && validResidentIds.contains(existing.spouseUuid())) {
            return existing.spouseUuid();
        }
        return null;
    }

    private static boolean isAdult(NpcSocietyProfile profile) {
        return profile.lifeStage() == NpcLifeStage.ADULT || profile.lifeStage() == NpcLifeStage.ELDER;
    }

    private static boolean isMinor(NpcSocietyProfile profile) {
        return profile.lifeStage() == NpcLifeStage.CHILD || profile.lifeStage() == NpcLifeStage.ADOLESCENT;
    }

    private static Comparator<NpcSocietyProfile> memberOrder() {
        return Comparator
                .comparingInt((NpcSocietyProfile profile) -> isAdult(profile) ? 0 : 1)
                .thenComparing(profile -> profile.residentUuid(), Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private static @Nullable NpcSocietyProfile profileById(List<NpcSocietyProfile> members, UUID residentUuid) {
        for (NpcSocietyProfile member : members) {
            if (residentUuid != null && residentUuid.equals(member.residentUuid())) {
                return member;
            }
        }
        return null;
    }

    private static NpcFamilyMemberSnapshot memberSnapshot(ServerLevel level,
                                                          UUID residentUuid,
                                                          @Nullable NpcSocietyProfile profile,
                                                          String relationTag,
                                                          long gameTime) {
        NpcSocietyProfile resolvedProfile = profile == null
                ? NpcSocietyAccess.ensureResident(level, residentUuid, gameTime)
                : profile;
        Entity entity = level.getEntity(residentUuid);
        String displayName = entity == null ? shortId(residentUuid) : entity.getName().getString();
        return new NpcFamilyMemberSnapshot(
                residentUuid,
                entity == null ? -1 : entity.getId(),
                displayName,
                resolvedProfile.lifeStage().name(),
                resolvedProfile.sex().name(),
                relationTag
        );
    }

    private static String shortId(UUID residentUuid) {
        return residentUuid == null ? "-" : residentUuid.toString().substring(0, 8);
    }

    private record SpousePair(UUID first, UUID second, UUID femaleAdult, UUID maleAdult) {
        private static SpousePair of(NpcSocietyProfile left, NpcSocietyProfile right) {
            UUID femaleAdult = left.sex() == NpcSex.FEMALE ? left.residentUuid() : right.sex() == NpcSex.FEMALE ? right.residentUuid() : null;
            UUID maleAdult = left.sex() == NpcSex.MALE ? left.residentUuid() : right.sex() == NpcSex.MALE ? right.residentUuid() : null;
            return new SpousePair(left.residentUuid(), right.residentUuid(), femaleAdult, maleAdult);
        }
    }
}
