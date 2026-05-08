package com.talhanation.bannermod;

import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.entity.citizen.CitizenEntity;
import com.talhanation.bannermod.entity.civilian.FarmerEntity;
import com.talhanation.bannermod.entity.military.RecruitEntity;
import com.talhanation.bannermod.network.messages.civilian.MessageAssignHome;
import com.talhanation.bannermod.settlement.building.BuildingType;
import com.talhanation.bannermod.settlement.building.BuildingValidationState;
import com.talhanation.bannermod.settlement.building.ValidatedBuildingRecord;
import com.talhanation.bannermod.settlement.building.ValidatedBuildingRegistryData;
import com.talhanation.bannermod.settlement.building.ZoneRole;
import com.talhanation.bannermod.settlement.building.ZoneSelection;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * HOMEASSIGN-002 acceptance tests:
 *
 * <ul>
 *   <li>Recruits, workers, and citizens all expose a {@code homePos} that
 *       round-trips through NBT.</li>
 *   <li>{@link MessageAssignHome#handle} validates ownership and bed
 *       targets, then applies the same server-side home update used by the
 *       30-second Assign Home selector flow.</li>
 * </ul>
 *
 * <p>Save/load round-trip is exercised in the harness by the
 * addAdditionalSaveData -> readAdditionalSaveData pair on a freshly spawned
 * sibling entity, which is the same path the level loader uses for entity
 * persistence on chunk save/load.
 */
@GameTestHolder(BannerModMain.MOD_ID)
public class BannerModHomeAssignGameTests {

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void recruitHomePosRoundTripsThroughSaveLoad(GameTestHelper helper) {
        RecruitEntity recruit = BannerModGameTestSupport.spawnOwnedRecruit(
                helper,
                helper.makeMockPlayer(GameType.SURVIVAL),
                BlockPos.ZERO
        );

        BlockPos homePos = new BlockPos(123, 64, -456);
        recruit.setHomePos(homePos);
        helper.assertTrue(homePos.equals(recruit.getHomePos()),
                "recruit#getHomePos must return the assigned BlockPos in-memory");
        helper.assertTrue(homePos.equals(recruit.getUpkeepPos()),
                "recruit#setHomePos must alias to upkeepPos so the existing AI sees it");

        CompoundTag saved = new CompoundTag();
        recruit.addAdditionalSaveData(saved);

        RecruitEntity reloaded = BannerModGameTestSupport.spawnOwnedRecruit(
                helper,
                helper.makeMockPlayer(GameType.SURVIVAL),
                BlockPos.ZERO.above()
        );
        reloaded.readAdditionalSaveData(saved);

        helper.assertTrue(homePos.equals(reloaded.getHomePos()),
                "recruit homePos must survive save/load via the upkeepPos alias");

        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void workerHomePosRoundTripsThroughSaveLoad(GameTestHelper helper) {
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        FarmerEntity worker = BannerModGameTestSupport.spawnOwnedFarmer(helper, owner, BlockPos.ZERO);

        BlockPos homePos = new BlockPos(7, 70, -7);
        worker.setHomePos(homePos);
        helper.assertTrue(homePos.equals(worker.getHomePos()),
                "worker#getHomePos must return the assigned BlockPos");

        CompoundTag saved = new CompoundTag();
        worker.addAdditionalSaveData(saved);

        FarmerEntity reloaded = BannerModGameTestSupport.spawnOwnedFarmer(helper, owner, BlockPos.ZERO.east());
        reloaded.readAdditionalSaveData(saved);

        helper.assertTrue(homePos.equals(reloaded.getHomePos()),
                "worker homePos must survive save/load");

        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void citizenHomePosRoundTripsThroughSaveLoad(GameTestHelper helper) {
        CitizenEntity citizen = BannerModGameTestSupport.spawnEntity(
                helper,
                com.talhanation.bannermod.registry.citizen.ModCitizenEntityTypes.CITIZEN.get(),
                BlockPos.ZERO
        );

        BlockPos homePos = new BlockPos(99, 65, 99);
        citizen.setHomePos(homePos);
        helper.assertTrue(homePos.equals(citizen.getHomePos()),
                "citizen#getHomePos must return the assigned BlockPos");

        CompoundTag saved = new CompoundTag();
        citizen.addAdditionalSaveData(saved);

        CitizenEntity reloaded = BannerModGameTestSupport.spawnEntity(
                helper,
                com.talhanation.bannermod.registry.citizen.ModCitizenEntityTypes.CITIZEN.get(),
                BlockPos.ZERO.south()
        );
        reloaded.readAdditionalSaveData(saved);

        helper.assertTrue(homePos.equals(reloaded.getHomePos()),
                "citizen homePos must survive save/load");

        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void assignHomeMessageAcceptsBedFromOwner(GameTestHelper helper) {
        UUID ownerId = UUID.randomUUID();
        ServerPlayer owner = (ServerPlayer) BannerModDedicatedServerGameTestSupport.createPositionedFakeServerPlayer(
                helper.getLevel(), ownerId, "homeassign-owner", helper.absolutePos(BlockPos.ZERO));

        RecruitEntity recruit = BannerModGameTestSupport.spawnOwnedRecruit(helper, owner, BlockPos.ZERO);
        // Pin the recruit's owner explicitly to the fake server player so the
        // ownership check inside MessageAssignHome#handle accepts the request.
        recruit.setOwnerUUID(Optional.of(ownerId));
        recruit.setHomeBuildAreaUUID(UUID.randomUUID());

        BlockPos bedRel = new BlockPos(1, 1, 1);
        BlockPos bedAbs = helper.absolutePos(bedRel);
        BlockState bedState = Blocks.RED_BED.defaultBlockState();
        helper.getLevel().setBlock(bedAbs, bedState, 3);

        boolean accepted = MessageAssignHome.handle(owner, recruit.getUUID(), bedAbs);
        helper.assertTrue(accepted, "Owner should be allowed to assign a bed as home");
        helper.assertTrue(bedAbs.equals(recruit.getHomePos()),
                "Recruit homePos must update to the bed BlockPos through MessageAssignHome");
        helper.assertTrue(recruit.getHomeBuildAreaUUID() == null,
                "MessageAssignHome must clear stale prefab home linkage for direct bed assignment");

        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void assignHomeMessageAcceptsValidatedSleepingZoneFromOwner(GameTestHelper helper) {
        UUID ownerId = UUID.randomUUID();
        ServerPlayer owner = (ServerPlayer) BannerModDedicatedServerGameTestSupport.createPositionedFakeServerPlayer(
                helper.getLevel(), ownerId, "homeassign-zone-owner", helper.absolutePos(BlockPos.ZERO));

        RecruitEntity recruit = BannerModGameTestSupport.spawnOwnedRecruit(helper, owner, BlockPos.ZERO);
        recruit.setOwnerUUID(Optional.of(ownerId));
        recruit.setHomeBuildAreaUUID(UUID.randomUUID());

        BlockPos sleepMin = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos sleepMax = helper.absolutePos(new BlockPos(3, 1, 3));
        BlockPos target = helper.absolutePos(new BlockPos(2, 1, 2));
        ValidatedBuildingRegistryData.get(helper.getLevel()).registerBuilding(new ValidatedBuildingRecord(
                UUID.randomUUID(),
                UUID.randomUUID(),
                BuildingType.HOUSE,
                Level.OVERWORLD,
                target,
                List.of(new ZoneSelection(ZoneRole.SLEEPING, sleepMin, sleepMax, null)),
                new AABB(sleepMin.getX(), sleepMin.getY(), sleepMin.getZ(), sleepMax.getX() + 1.0D, sleepMax.getY() + 1.0D, sleepMax.getZ() + 1.0D),
                BuildingValidationState.VALID,
                1,
                80,
                helper.getLevel().getGameTime(),
                helper.getLevel().getGameTime(),
                0L
        ));

        boolean accepted = MessageAssignHome.handle(owner, recruit.getUUID(), target);
        helper.assertTrue(accepted, "Owner should be allowed to assign a validated sleeping zone as home");
        helper.assertTrue(target.equals(recruit.getHomePos()),
                "Recruit homePos must update to the sleeping-zone BlockPos through MessageAssignHome");
        helper.assertTrue(recruit.getHomeBuildAreaUUID() == null,
                "MessageAssignHome must clear stale prefab home linkage for direct sleeping-zone assignment");

        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void assignHomeRejectsNonBedBlock(GameTestHelper helper) {
        UUID ownerId = UUID.randomUUID();
        ServerPlayer owner = (ServerPlayer) BannerModDedicatedServerGameTestSupport.createPositionedFakeServerPlayer(
                helper.getLevel(), ownerId, "homeassign-owner-2", helper.absolutePos(BlockPos.ZERO));

        RecruitEntity recruit = BannerModGameTestSupport.spawnOwnedRecruit(helper, owner, BlockPos.ZERO);
        recruit.setOwnerUUID(Optional.of(ownerId));

        BlockPos rel = new BlockPos(2, 1, 2);
        BlockPos abs = helper.absolutePos(rel);
        helper.getLevel().setBlock(abs, Blocks.STONE.defaultBlockState(), 3);

        boolean accepted = MessageAssignHome.handle(owner, recruit.getUUID(), abs);
        helper.assertFalse(accepted, "Stone must not be accepted as a home target");
        helper.assertTrue(recruit.getHomePos() == null,
                "Recruit homePos must remain unset after a rejected assignment");

        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void assignHomeRejectsForeignSender(GameTestHelper helper) {
        UUID ownerId = UUID.randomUUID();
        UUID intruderId = UUID.randomUUID();
        ServerPlayer owner = (ServerPlayer) BannerModDedicatedServerGameTestSupport.createPositionedFakeServerPlayer(
                helper.getLevel(), ownerId, "homeassign-owner-3", helper.absolutePos(BlockPos.ZERO));
        ServerPlayer intruder = (ServerPlayer) BannerModDedicatedServerGameTestSupport.createPositionedFakeServerPlayer(
                helper.getLevel(), intruderId, "homeassign-intruder", helper.absolutePos(BlockPos.ZERO));

        RecruitEntity recruit = BannerModGameTestSupport.spawnOwnedRecruit(helper, owner, BlockPos.ZERO);
        recruit.setOwnerUUID(Optional.of(ownerId));

        BlockPos bedAbs = helper.absolutePos(new BlockPos(1, 1, 2));
        helper.getLevel().setBlock(bedAbs, Blocks.RED_BED.defaultBlockState(), 3);

        boolean accepted = MessageAssignHome.handle(intruder, recruit.getUUID(), bedAbs);
        helper.assertFalse(accepted, "Foreign sender must not assign another player's recruit home");
        helper.assertTrue(recruit.getHomePos() == null,
                "Recruit homePos must remain unset after a rejected foreign assignment");

        helper.succeed();
    }
}
