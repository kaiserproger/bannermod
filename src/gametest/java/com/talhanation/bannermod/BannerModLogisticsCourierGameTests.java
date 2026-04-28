package com.talhanation.bannermod;

import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.entity.civilian.MerchantEntity;
import com.talhanation.bannermod.entity.civilian.workarea.StorageArea;
import com.talhanation.bannermod.shared.logistics.BannerModCourierTask;
import com.talhanation.bannermod.shared.logistics.BannerModLogisticsAuthoringState;
import com.talhanation.bannermod.shared.logistics.BannerModLogisticsRuntime;
import com.talhanation.bannermod.shared.logistics.BannerModSeaTradeDirection;
import com.talhanation.bannermod.shared.logistics.BannerModSeaTradeEntrypoint;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.EnumSet;
import java.util.List;

@GameTestHolder(BannerModMain.MOD_ID)
public class BannerModLogisticsCourierGameTests {

    @PrefixGameTestTemplate(false)
    // 800-tick (40-second) outer budget: the original 480 sometimes was not enough on a
    // background-loaded gametest server because the courier path-finder's solve time scales
    // with how many other entities tick on the same tick. The poll-based `succeedWhen` below
    // exits the moment the delivery actually completes, so a generous outer budget only
    // matters when something is genuinely wrong — at which point we want the assertion
    // message, not a timeout.
    @GameTest(template = "harness_empty", timeoutTicks = 800)
    public static void authoredRouteCourierMovesItemsBetweenStorageEndpoints(GameTestHelper helper) {
        BannerModLogisticsRuntime.resetForTests();
        ServerLevel level = helper.getLevel();
        Player owner = helper.makeMockPlayer();
        MerchantEntity courier = BannerModGameTestSupport.spawnOwnedMerchant(helper, owner, new BlockPos(2, 2, 4));
        StorageArea sourceStorage = BannerModGameTestSupport.spawnOwnedStorageArea(helper, owner, new BlockPos(2, 2, 2));
        StorageArea destinationStorage = BannerModGameTestSupport.spawnOwnedStorageArea(helper, owner, new BlockPos(10, 2, 2));
        StorageArea staleStorage = BannerModGameTestSupport.spawnOwnedStorageArea(helper, owner, new BlockPos(18, 2, 2));

        int merchantMask = sourceStorage.getStorageMask(EnumSet.of(StorageArea.StorageType.MERCHANTS));
        sourceStorage.setStorageTypes(merchantMask);
        destinationStorage.setStorageTypes(merchantMask);
        staleStorage.setStorageTypes(merchantMask);
        destinationStorage.setPortEntrypoint(true);
        sourceStorage.setLogisticsRoute(BannerModLogisticsAuthoringState.parse(destinationStorage.getUUID().toString(), "minecraft:oak_planks", "8", "NORMAL"));
        staleStorage.setLogisticsRoute(BannerModLogisticsAuthoringState.parse("00000000-0000-0000-0000-00000000dead", "minecraft:oak_planks", "8", "HIGH"));

        BlockPos sourceChestPos = placeScannableChest(level, sourceStorage);
        BlockPos destinationChestPos = placeScannableChest(level, destinationStorage);

        ChestBlockEntity sourceChest = (ChestBlockEntity) level.getBlockEntity(sourceChestPos);
        ChestBlockEntity destinationChest = (ChestBlockEntity) level.getBlockEntity(destinationChestPos);
        sourceChest.setItem(0, new ItemStack(Items.OAK_PLANKS, 8));
        sourceStorage.scanStorageBlocks();
        destinationStorage.scanStorageBlocks();

        helper.assertTrue(sourceStorage.storageMap.containsKey(sourceChestPos), "Expected the source chest to sit inside the scanned storage footprint.");
        helper.assertTrue(destinationStorage.storageMap.containsKey(destinationChestPos), "Expected the destination chest to sit inside the scanned storage footprint.");

        BannerModCourierTask task = BannerModLogisticsRuntime.service()
                .claimNextTask(courier.getUUID(), List.of(sourceStorage.getAuthoredLogisticsRoute().orElseThrow()), route -> true, level.getGameTime(), 200L)
                .orElseThrow();
        courier.setActiveCourierTask(task);

        // Poll-based completion check. The previous fixed-tick `runAfterDelay(320)` sometimes
        // missed the deadline when path planning happened to land on a slow tick — the
        // `timeoutTicks=480` outer deadline gave enough room overall but not 320 ticks
        // exactly. `succeedWhen` retries every tick until the assertions stop throwing or
        // the outer timeout fires, which is what we actually want: succeed as soon as the
        // delivery completes, fail with the *latest* assertion message if it never does.
        helper.succeedWhen(() -> {
            helper.assertTrue(sourceChest.getItem(0).isEmpty(),
                    "Expected the courier route to remove the reserved planks from the source chest.");
            helper.assertTrue(destinationChest.getItem(0).getCount() == 8 && destinationChest.getItem(0).is(Items.OAK_PLANKS),
                    "Expected the courier route to deliver the reserved planks into the destination chest.");
            helper.assertFalse(courier.hasActiveCourierTask(),
                    "Expected the courier to release the authored route after delivery completes.");
            helper.assertTrue(BannerModLogisticsRuntime.service().getReservation(task.reservation().reservationId()) == null,
                    "Expected the courier reservation to be released from the shared logistics runtime after delivery completes.");
            List<BannerModSeaTradeEntrypoint> seaEntrypoints = BannerModLogisticsRuntime.listSeaTradeEntrypoints(
                    List.of(sourceStorage, destinationStorage, staleStorage)
            );
            helper.assertTrue(seaEntrypoints.size() == 1, "Expected only live port-backed routes to publish sea-trade entrypoints.");
            helper.assertTrue(seaEntrypoints.get(0).direction() == BannerModSeaTradeDirection.EXPORT,
                    "Expected the destination port route to publish an export sea-trade entrypoint.");
            helper.assertTrue(seaEntrypoints.get(0).portStorageAreaId().equals(destinationStorage.getUUID()),
                    "Expected the published sea-trade entrypoint to point at the destination port storage.");
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty", timeoutTicks = 100)
    public static void courierUsesNearbyHorseOnSupportedRoute(GameTestHelper helper) {
        BannerModLogisticsRuntime.resetForTests();
        ServerLevel level = helper.getLevel();
        Player owner = helper.makeMockPlayer();
        MerchantEntity courier = BannerModGameTestSupport.spawnOwnedMerchant(helper, owner, new BlockPos(2, 2, 4));
        Horse horse = BannerModGameTestSupport.spawnEntity(helper, EntityType.HORSE, new BlockPos(3, 2, 4));
        StorageArea sourceStorage = BannerModGameTestSupport.spawnOwnedStorageArea(helper, owner, new BlockPos(2, 2, 2));
        StorageArea destinationStorage = BannerModGameTestSupport.spawnOwnedStorageArea(helper, owner, new BlockPos(24, 2, 2));

        int merchantMask = sourceStorage.getStorageMask(EnumSet.of(StorageArea.StorageType.MERCHANTS));
        sourceStorage.setStorageTypes(merchantMask);
        destinationStorage.setStorageTypes(merchantMask);
        sourceStorage.setLogisticsRoute(BannerModLogisticsAuthoringState.parse(destinationStorage.getUUID().toString(), "minecraft:oak_planks", "1", "NORMAL"));

        BannerModCourierTask task = BannerModLogisticsRuntime.service()
                .claimNextTask(courier.getUUID(), List.of(sourceStorage.getAuthoredLogisticsRoute().orElseThrow()), route -> true, level.getGameTime(), 200L)
                .orElseThrow();
        courier.getInventory().addItem(new ItemStack(Items.OAK_PLANKS));
        courier.setActiveCourierTask(task);
        double initialHorseDistanceToDestination = horse.distanceToSqr(destinationStorage);

        helper.succeedWhen(() -> {
            helper.assertTrue(courier.isPassenger(), "Expected courier to mount an approved nearby horse for a supported route.");
            helper.assertTrue(courier.getVehicle() == horse, "Expected courier to keep its assigned horse transport.");
            helper.assertTrue(horse.distanceToSqr(destinationStorage) < initialHorseDistanceToDestination,
                    "Expected mounted transport to move the courier toward the active route target.");
            helper.assertTrue(courier.getActiveCourierTask() == task, "Expected mounted transport to leave courier route ownership intact.");
            courier.clearActiveCourierTask();
            helper.assertFalse(courier.isPassenger(), "Expected courier to dismount when the courier route is cleared.");
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty", timeoutTicks = 100)
    public static void courierFallsBackWhenNoMountAvailable(GameTestHelper helper) {
        BannerModLogisticsRuntime.resetForTests();
        ServerLevel level = helper.getLevel();
        Player owner = helper.makeMockPlayer();
        MerchantEntity courier = BannerModGameTestSupport.spawnOwnedMerchant(helper, owner, new BlockPos(2, 2, 4));
        StorageArea sourceStorage = BannerModGameTestSupport.spawnOwnedStorageArea(helper, owner, new BlockPos(2, 2, 2));
        StorageArea destinationStorage = BannerModGameTestSupport.spawnOwnedStorageArea(helper, owner, new BlockPos(24, 2, 2));

        int merchantMask = sourceStorage.getStorageMask(EnumSet.of(StorageArea.StorageType.MERCHANTS));
        sourceStorage.setStorageTypes(merchantMask);
        destinationStorage.setStorageTypes(merchantMask);
        sourceStorage.setLogisticsRoute(BannerModLogisticsAuthoringState.parse(destinationStorage.getUUID().toString(), "minecraft:oak_planks", "1", "NORMAL"));

        BannerModCourierTask task = BannerModLogisticsRuntime.service()
                .claimNextTask(courier.getUUID(), List.of(sourceStorage.getAuthoredLogisticsRoute().orElseThrow()), route -> true, level.getGameTime(), 200L)
                .orElseThrow();
        courier.getInventory().addItem(new ItemStack(Items.OAK_PLANKS));
        courier.setActiveCourierTask(task);

        helper.runAfterDelay(20, () -> {
            helper.assertFalse(courier.isPassenger(), "Expected courier to keep normal unmounted movement when no approved mount is available.");
            helper.assertTrue(courier.getActiveCourierTask() == task, "Expected unmounted fallback to preserve courier route ownership.");
            helper.succeed();
        });
    }

    private static BlockPos placeScannableChest(ServerLevel level, StorageArea storageArea) {
        BlockPos origin = storageArea.getOnPos();
        AABB searchBounds = storageArea.getArea().inflate(2.0D, 0.0D, 2.0D);
        int minX = (int) Math.floor(searchBounds.minX);
        int maxX = (int) Math.floor(searchBounds.maxX);
        int minY = origin.getY() - 1;
        int maxY = origin.getY() + 1;
        int minZ = (int) Math.floor(searchBounds.minZ);
        int maxZ = (int) Math.floor(searchBounds.maxZ);

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos candidate = new BlockPos(x, y, z);
                    level.setBlockAndUpdate(candidate, Blocks.CHEST.defaultBlockState());
                    storageArea.scanStorageBlocks();
                    if (storageArea.storageMap.containsKey(candidate)) {
                        return candidate;
                    }
                    level.setBlockAndUpdate(candidate, Blocks.AIR.defaultBlockState());
                }
            }
        }

        throw new IllegalStateException("Could not place a scannable chest inside the storage footprint");
    }
}
