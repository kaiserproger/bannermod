package com.talhanation.bannermod.settlement.growth;

import com.talhanation.bannermod.settlement.BannerModSettlementBuildingCategory;
import com.talhanation.bannermod.settlement.BannerModSettlementBuildingProfileSeed;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Candidate project a settlement could start next. Patterned on Millenaire's
 * {@code Village.PendingProject}; re-implemented to avoid copying GPL source.
 * {@code priorityScore} is clamped to 0..1000, higher is picked sooner.
 */
public record PendingProject(
        UUID projectId,
        ProjectKind kind,
        @Nullable UUID targetBuildingUuid,
        @Nullable ResourceLocation prefabId,
        BannerModSettlementBuildingCategory buildingCategory,
        BannerModSettlementBuildingProfileSeed profileSeed,
        int priorityScore,
        long proposedAtGameTime,
        int estimatedTickCost,
        ProjectBlocker blockerReason
) {
    public PendingProject {
        if (projectId == null) {
            throw new IllegalArgumentException("projectId must not be null");
        }
        if (kind == null) {
            throw new IllegalArgumentException("kind must not be null");
        }
        if (profileSeed == null) {
            profileSeed = BannerModSettlementBuildingProfileSeed.GENERAL;
        }
        if (buildingCategory == null) {
            buildingCategory = profileSeed.category();
        }
        priorityScore = Math.max(0, Math.min(1000, priorityScore));
        estimatedTickCost = Math.max(0, estimatedTickCost);
        if (blockerReason == null) {
            blockerReason = ProjectBlocker.NONE;
        }
        if (kind == ProjectKind.NEW_BUILDING) {
            targetBuildingUuid = null;
        }
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", projectId);
        tag.putString("Kind", kind.name());
        if (targetBuildingUuid != null) {
            tag.putUUID("Target", targetBuildingUuid);
        }
        if (prefabId != null) {
            tag.putString("PrefabId", prefabId.toString());
        }
        tag.putString("Category", buildingCategory.name());
        tag.putString("Profile", profileSeed.name());
        tag.putInt("Priority", priorityScore);
        tag.putLong("ProposedAt", proposedAtGameTime);
        tag.putInt("Cost", estimatedTickCost);
        tag.putString("Blocker", blockerReason.name());
        return tag;
    }

    public static PendingProject fromTag(CompoundTag tag) {
        UUID target = tag.hasUUID("Target") ? tag.getUUID("Target") : null;
        return new PendingProject(
                tag.getUUID("Id"),
                kindFromTagName(tag.getString("Kind")),
                target,
                tag.contains("PrefabId") ? ResourceLocation.tryParse(tag.getString("PrefabId")) : null,
                BannerModSettlementBuildingCategory.fromTagName(tag.getString("Category")),
                BannerModSettlementBuildingProfileSeed.fromTagName(tag.getString("Profile")),
                tag.getInt("Priority"),
                tag.getLong("ProposedAt"),
                tag.getInt("Cost"),
                blockerFromTagName(tag.getString("Blocker"))
        );
    }

    private static ProjectKind kindFromTagName(String name) {
        try {
            return ProjectKind.valueOf(name);
        } catch (IllegalArgumentException | NullPointerException exception) {
            return ProjectKind.NEW_BUILDING;
        }
    }

    private static ProjectBlocker blockerFromTagName(String name) {
        try {
            return ProjectBlocker.valueOf(name);
        } catch (IllegalArgumentException | NullPointerException exception) {
            return ProjectBlocker.NONE;
        }
    }
}
