package com.talhanation.bannermod.society;

import com.talhanation.bannermod.settlement.goal.impl.DefendResidentGoal;
import com.talhanation.bannermod.settlement.goal.impl.EatResidentGoal;
import com.talhanation.bannermod.settlement.goal.impl.HideResidentGoal;
import com.talhanation.bannermod.settlement.goal.impl.IdleResidentGoal;
import com.talhanation.bannermod.settlement.goal.impl.RestResidentGoal;
import com.talhanation.bannermod.settlement.goal.impl.SeekSuppliesResidentGoal;
import com.talhanation.bannermod.settlement.goal.impl.WorkResidentGoal;
import com.talhanation.bannermod.settlement.household.GoHomeResidentGoal;
import com.talhanation.bannermod.settlement.household.LeaveHomeResidentGoal;
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
                                                    @Nullable NpcSocietyDecisionSnapshot decisionSnapshot,
                                                    long gameTime) {
        NpcSocietyProfile profile = ensureResident(residentUuid, gameTime);
        NpcSocietyDecisionSnapshot normalizedDecisionSnapshot = normalizeDecisionSnapshot(
                profile,
                currentIntent,
                decisionSnapshot,
                gameTime
        );
        NpcSocietyProfile updated = profile.withPhaseOneState(
                householdId,
                homeBuildingUuid,
                workBuildingUuid,
                dailyPhase,
                currentIntent,
                currentAnchor,
                normalizedDecisionSnapshot,
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
                                                 int safetyNeed,
                                                 long gameTime) {
        NpcSocietyProfile profile = ensureResident(residentUuid, gameTime);
        NpcSocietyProfile updated = profile.withNeedState(hungerNeed, fatigueNeed, safetyNeed, gameTime);
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

    private static NpcSocietyDecisionSnapshot normalizeDecisionSnapshot(NpcSocietyProfile profile,
                                                                        NpcIntent currentIntent,
                                                                        @Nullable NpcSocietyDecisionSnapshot decisionSnapshot,
                                                                        long gameTime) {
        if (currentIntent == null || currentIntent == NpcIntent.UNSPECIFIED) {
            return decisionSnapshot == null ? NpcSocietyDecisionSnapshot.empty() : decisionSnapshot;
        }
        if (decisionSnapshot != null && decisionSnapshot.currentGoalId() != null && !decisionSnapshot.currentGoalId().isBlank()) {
            return decisionSnapshot;
        }
        String goalId = goalIdForIntent(currentIntent);
        if (goalId == null) {
            return decisionSnapshot == null ? NpcSocietyDecisionSnapshot.empty() : decisionSnapshot;
        }
        String lastIntentTag = profile == null || profile.currentIntent() == null
                ? NpcIntent.UNSPECIFIED.name()
                : profile.currentIntent().name();
        return new NpcSocietyDecisionSnapshot(
                "EXECUTING",
                goalId,
                defaultChoiceReasonTag(currentIntent),
                defaultRouteReasonTag(currentIntent),
                null,
                NpcSocietyDecisionSnapshot.BLOCKED_REASON_NONE,
                lastIntentTag,
                gameTime
        );
    }

    @Nullable
    private static String goalIdForIntent(@Nullable NpcIntent intent) {
        if (intent == null) {
            return null;
        }
        return switch (intent) {
            case GO_HOME -> GoHomeResidentGoal.ID.toString();
            case REST -> RestResidentGoal.ID.toString();
            case LEAVE_HOME -> LeaveHomeResidentGoal.ID.toString();
            case EAT -> EatResidentGoal.ID.toString();
            case SEEK_SUPPLIES -> SeekSuppliesResidentGoal.ID.toString();
            case IDLE -> IdleResidentGoal.ID.toString();
            case HIDE -> HideResidentGoal.ID.toString();
            case DEFEND -> DefendResidentGoal.ID.toString();
            case WORK -> WorkResidentGoal.ID.toString();
            default -> null;
        };
    }

    private static String defaultChoiceReasonTag(NpcIntent intent) {
        return switch (intent) {
            case GO_HOME -> "HOMEWARD_PULL";
            case REST -> "REST_WINDOW";
            case LEAVE_HOME -> "EARLY_ACTIVE_WINDOW";
            case EAT -> "HUNGER_PRESSURE";
            case SEEK_SUPPLIES -> "HOME_FOOD_SHORTAGE";
            case IDLE -> "NO_HIGHER_PRIORITY_GOAL";
            case HIDE -> "THREAT_AVOIDANCE";
            case DEFEND -> "THREAT_RESPONSE";
            case WORK -> "ASSIGNED_SHIFT";
            default -> "UNKNOWN";
        };
    }

    private static String defaultRouteReasonTag(NpcIntent intent) {
        return switch (intent) {
            case GO_HOME -> "RETURNING_HOME_ROUTE";
            case REST -> "RESTING_AT_HOME";
            case LEAVE_HOME -> "LEAVING_HOME_FOR_DAY";
            case EAT -> "MEAL_AT_MARKET";
            case SEEK_SUPPLIES -> "MARKET_SUPPLY_RUN";
            case IDLE -> "NO_CLEAR_ROUTE";
            case HIDE -> "SEEKING_SHELTER_ROUTE";
            case DEFEND -> "MOVING_TO_DEFENSE_POST";
            case WORK -> "HEADING_TO_WORKPLACE";
            default -> "NO_CLEAR_ROUTE";
        };
    }
}
