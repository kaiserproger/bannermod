package com.talhanation.bannermod;

import com.talhanation.bannermod.shared.settlement.BannerModSettlementBinding;
import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.config.WorkersServerConfig;
import com.talhanation.bannermod.entity.civilian.FarmerEntity;
import com.talhanation.bannermod.entity.civilian.workarea.AbstractWorkAreaEntity;
import com.talhanation.bannermod.entity.civilian.workarea.CropArea;
import com.talhanation.bannermod.events.ClaimEvents;
import com.talhanation.bannermod.network.messages.civilian.WorkAreaAuthoringRules;
import com.talhanation.bannermod.persistence.military.RecruitsClaim;
import com.talhanation.bannermod.registry.civilian.ModEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

@GameTestHolder(BannerModMain.MOD_ID)
public class BannerModSettlementFactionEnforcementGameTests {

    private static final UUID FRIENDLY_OWNER_UUID = UUID.fromString("00000000-0000-0000-0000-000000001001");
    private static final UUID HOSTILE_PLAYER_UUID = UUID.fromString("00000000-0000-0000-0000-000000001002");
    private static final UUID UNCLAIMED_PLAYER_UUID = UUID.fromString("00000000-0000-0000-0000-000000001003");
    private static final String FRIENDLY_TEAM_ID = "phase10_friendly";
    private static final String HOSTILE_TEAM_ID = "phase10_hostile";

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void friendlyClaimBindingAllowsPlacementAndSettlementOperation(GameTestHelper helper) {
        WorkersServerConfig.setTestOverride(WorkersServerConfig.ShouldWorkAreaOnlyBeInFactionClaim, true);

        try {
            ServerLevel level = helper.getLevel();
            ServerPlayer owner = createPlayer(helper, level, FRIENDLY_OWNER_UUID, "friendly-owner", FRIENDLY_TEAM_ID);
            BlockPos workAreaPos = helper.absolutePos(new BlockPos(2, 2, 2));
            clearCropArea(level, workAreaPos);

            BannerModDedicatedServerGameTestSupport.seedClaim(level, workAreaPos, FRIENDLY_TEAM_ID, owner.getUUID(), owner.getScoreboardName());
            String friendlyPoliticalEntityId = BannerModDedicatedServerGameTestSupport.politicalEntityIdString(level, FRIENDLY_TEAM_ID);

            boolean placed = attemptWorkAreaPlacement(level, owner, workAreaPos);
            CropArea cropArea = findCropArea(level, workAreaPos);
            FarmerEntity worker = BannerModGameTestSupport.spawnOwnedFarmer(helper, owner, new BlockPos(4, 2, 2));
            BannerModDedicatedServerGameTestSupport.joinTeam(level, FRIENDLY_TEAM_ID, worker);
            worker.setCurrentWorkArea(cropArea);

            BannerModSettlementBinding.Binding binding = BannerModSettlementBinding.resolveFactionStatus(
                    ClaimEvents.claimManager(),
                    workAreaPos,
                    friendlyPoliticalEntityId
            );

            helper.assertTrue(placed,
                    "Expected a faction-aligned player to place a crop area inside a friendly claim through the live placement seam");
            helper.assertTrue(cropArea != null,
                    "Expected the friendly placement path to insert a crop area into the level");
            helper.assertTrue(binding.status() == BannerModSettlementBinding.Status.FRIENDLY_CLAIM,
                    "Expected the placed crop area to resolve as FRIENDLY_CLAIM through the consolidated settlement binding seam");
            helper.assertTrue(cropArea.canWorkHere(worker),
                    "Expected the owned worker to remain operational inside the friendly claim-backed settlement area");
        } finally {
            WorkersServerConfig.setTestOverride(WorkersServerConfig.ShouldWorkAreaOnlyBeInFactionClaim, null);
        }

        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void hostileClaimDeniesPlacementAndReportsHostileBinding(GameTestHelper helper) {
        WorkersServerConfig.setTestOverride(WorkersServerConfig.ShouldWorkAreaOnlyBeInFactionClaim, true);

        try {
            ServerLevel level = helper.getLevel();
            ServerPlayer claimOwner = createPlayer(helper, level, FRIENDLY_OWNER_UUID, "claim-owner", FRIENDLY_TEAM_ID);
            ServerPlayer hostilePlayer = createPlayer(helper, level, HOSTILE_PLAYER_UUID, "hostile-player", HOSTILE_TEAM_ID);
            BlockPos workAreaPos = helper.absolutePos(new BlockPos(2, 2, 2));
            clearCropArea(level, workAreaPos);

            BannerModDedicatedServerGameTestSupport.seedClaim(level, workAreaPos, FRIENDLY_TEAM_ID, claimOwner.getUUID(), claimOwner.getScoreboardName());
            String hostilePoliticalEntityId = BannerModDedicatedServerGameTestSupport.politicalEntityIdString(level, HOSTILE_TEAM_ID);

            boolean placed = attemptWorkAreaPlacement(level, hostilePlayer, workAreaPos);
            BannerModSettlementBinding.Binding binding = BannerModSettlementBinding.resolveFactionStatus(
                    ClaimEvents.claimManager(),
                    workAreaPos,
                    hostilePoliticalEntityId
            );

            helper.assertFalse(placed,
                    "Expected hostile-faction work-area placement to be denied when claim restriction is enabled");
            helper.assertTrue(findCropArea(level, workAreaPos) == null,
                    "Expected hostile placement denial to avoid spawning a crop area in the claimed chunk");
            helper.assertTrue(binding.status() == BannerModSettlementBinding.Status.HOSTILE_CLAIM,
                    "Expected hostile placement denial to resolve as HOSTILE_CLAIM through the shared settlement binding seam");
        } finally {
            WorkersServerConfig.setTestOverride(WorkersServerConfig.ShouldWorkAreaOnlyBeInFactionClaim, null);
        }

        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void unclaimedTerritoryDeniesRestrictedPlacementAndReportsUnclaimed(GameTestHelper helper) {
        WorkersServerConfig.setTestOverride(WorkersServerConfig.ShouldWorkAreaOnlyBeInFactionClaim, true);

        try {
            ServerLevel level = helper.getLevel();
            ServerPlayer player = createPlayer(helper, level, UNCLAIMED_PLAYER_UUID, "unclaimed-player", FRIENDLY_TEAM_ID);
            BlockPos workAreaPos = helper.absolutePos(new BlockPos(2, 2, 2));
            clearCropArea(level, workAreaPos);

            if (ClaimEvents.claimManager() != null) {
                RecruitsClaim existingClaim = ClaimEvents.claimManager().getClaim(new ChunkPos(workAreaPos));
                if (existingClaim != null) {
                    BannerModDedicatedServerGameTestSupport.removeClaim(level, existingClaim);
                }
            }

            String friendlyPoliticalEntityId = BannerModDedicatedServerGameTestSupport.politicalEntityIdString(level, FRIENDLY_TEAM_ID);
            boolean placed = attemptWorkAreaPlacement(level, player, workAreaPos);
            BannerModSettlementBinding.Binding binding = BannerModSettlementBinding.resolveFactionStatus(
                    ClaimEvents.claimManager(),
                    workAreaPos,
                    friendlyPoliticalEntityId
            );

            helper.assertFalse(placed,
                    "Expected unclaimed-territory placement to be denied when faction-claim restriction is enabled");
            helper.assertTrue(findCropArea(level, workAreaPos) == null,
                    "Expected unclaimed placement denial to avoid spawning a crop area without a backing claim");
            helper.assertTrue(binding.status() == BannerModSettlementBinding.Status.UNCLAIMED,
                    "Expected the shared settlement binding seam to report UNCLAIMED for restricted placement in claimless territory");
        } finally {
            WorkersServerConfig.setTestOverride(WorkersServerConfig.ShouldWorkAreaOnlyBeInFactionClaim, null);
        }

        helper.succeed();
    }

    private static ServerPlayer createPlayer(GameTestHelper helper, ServerLevel level, UUID playerId, String name, String teamId) {
        Player player = BannerModDedicatedServerGameTestSupport.createFakeServerPlayer(level, playerId, name);
        BannerModDedicatedServerGameTestSupport.ensureFaction(level, teamId, playerId, name);
        BannerModDedicatedServerGameTestSupport.joinTeam(level, teamId, player);
        BlockPos spawnPos = helper.absolutePos(new BlockPos(1, 2, 1));
        player.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, 0.0F, 0.0F);
        return (ServerPlayer) player;
    }

    private static CropArea findCropArea(ServerLevel level, BlockPos workAreaPos) {
        List<CropArea> cropAreas = level.getEntitiesOfClass(CropArea.class, new AABB(workAreaPos.above()).inflate(1.0D));
        return cropAreas.isEmpty() ? null : cropAreas.get(0);
    }

    private static void clearCropArea(ServerLevel level, BlockPos workAreaPos) {
        CropArea existing = findCropArea(level, workAreaPos);
        if (existing != null) {
            existing.discard();
        }
    }

    /**
     * Mirrors the gating + spawn responsibilities of the legacy work-area authoring path
     * without requiring a {@code NetworkEvent.Context}. Returns {@code true} when the consolidated
     * authoring gate would have allowed the placement (and the test crop area was actually inserted),
     * {@code false} otherwise.
     */
    private static boolean attemptWorkAreaPlacement(ServerLevel level,
                                                    ServerPlayer player,
                                                    BlockPos workAreaPos) {
        CropArea workArea = new CropArea(ModEntityTypes.CROPAREA.get(), level);
        workArea.setWidthSize(9);
        workArea.setHeightSize(2);
        workArea.setDepthSize(9);
        String teamStringID = player.getTeam() != null ? player.getTeam().getName() : "";
        workArea.setFacing(player.getDirection());
        workArea.moveTo(workAreaPos.above(), 0, 0);
        workArea.createArea();
        workArea.setTeamStringID(teamStringID);
        workArea.setDone(false);
        workArea.setPlayerName(player.getName().getString());
        workArea.setPlayerUUID(player.getUUID());

        boolean isInsideOwnFactionClaim = isInsideOwnFactionClaim(player, workAreaPos);
        boolean isOverlapping = AbstractWorkAreaEntity.isAreaOverlapping(level, null, workArea.getArea());
        WorkAreaAuthoringRules.Decision decision = WorkAreaAuthoringRules.createDecision(isInsideOwnFactionClaim, isOverlapping);
        if (!WorkAreaAuthoringRules.isAllowed(decision)) {
            return false;
        }
        return level.addFreshEntity(workArea);
    }

    private static boolean isInsideOwnFactionClaim(ServerPlayer player, BlockPos targetPos) {
        if (!WorkersServerConfig.shouldWorkAreaOnlyBeInFactionClaim()) {
            return true;
        }
        if (player.getTeam() == null || ClaimEvents.claimManager() == null) {
            return false;
        }
        BannerModSettlementBinding.Binding binding = BannerModSettlementBinding.resolveFactionStatus(
                ClaimEvents.claimManager(),
                targetPos,
                player.getTeam().getName()
        );
        return binding.isFriendly();
    }
}
