package com.talhanation.bannermod.settlement.project;

import com.talhanation.bannermod.settlement.SettlementBuildingCategory;
import com.talhanation.bannermod.settlement.SettlementBuildingProfileSeed;
import com.talhanation.bannermod.settlement.growth.PendingProject;
import com.talhanation.bannermod.settlement.growth.ProjectBlocker;
import com.talhanation.bannermod.settlement.growth.ProjectKind;

import java.util.UUID;

/**
 * Package-private helper for slice-C tests. Keeps test files from duplicating
 * the {@link PendingProject} constructor boilerplate.
 */
final class ProjectTestFactory {

    private ProjectTestFactory() {
    }

    static PendingProject general(int priority, int tickCost) {
        return new PendingProject(
                UUID.randomUUID(),
                ProjectKind.NEW_BUILDING,
                null,
                SettlementBuildingCategory.GENERAL,
                SettlementBuildingProfileSeed.GENERAL,
                priority,
                0L,
                tickCost,
                ProjectBlocker.NONE
        );
    }

    static PendingProject withKind(ProjectKind kind, int priority) {
        return new PendingProject(
                UUID.randomUUID(),
                kind,
                kind == ProjectKind.NEW_BUILDING ? null : UUID.randomUUID(),
                SettlementBuildingCategory.GENERAL,
                SettlementBuildingProfileSeed.GENERAL,
                priority,
                0L,
                5,
                ProjectBlocker.NONE
        );
    }
}
