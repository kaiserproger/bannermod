package com.talhanation.bannermod;

import com.talhanation.bannermod.ai.civilian.TransportContainerExchange;
import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.settlement.SettlementOrchestrator;
import com.talhanation.bannermod.settlement.workorder.SettlementWorkOrder;
import com.talhanation.bannermod.settlement.workorder.SettlementWorkOrderRuntime;
import com.talhanation.bannermod.settlement.workorder.SettlementWorkOrderStatus;
import com.talhanation.bannermod.settlement.workorder.SettlementWorkOrderType;
import com.talhanation.bannermod.shared.logistics.BannerModLogisticsItemFilter;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * Live coverage for the gameplay-observable contract behind SETTLEMENT-005's three remaining
 * acceptance gaps. The unit-level {@link TransportContainerExchangeTest} only locks the
 * resource-hint parser; this suite drives {@link TransportContainerExchange#withdrawInto} and
 * {@link TransportContainerExchange#depositInto} against real {@link ChestBlockEntity}
 * instances inside a live server world, which is the registry-aware path
 * {@link com.talhanation.bannermod.ai.civilian.SettlementOrderWorkGoal} ultimately calls.
 *
 * <p>The covered acceptance bullets are:</p>
 * <ul>
 *   <li>"Workers/couriers can fetch inputs for production" — exercised by
 *       {@link #fetchInputOrderRoutesItemsFromSourceToDestinationChest}, which publishes a
 *       FETCH_INPUT order, drains the source chest into a carrier, and deposits into the
 *       destination — the same four-step contract the in-world goal performs.</li>
 *   <li>"Resource-hint filter against shared chests" — exercised by
 *       {@link #fetchInputResourceHintFilterIgnoresNonMatchingItemsInSharedChest}: a chest
 *       holds wheat alongside bread; only wheat moves.</li>
 *   <li>"Multi-storage routing under settlement-tick load" — exercised by
 *       {@link #multiStorageRoutingDrainsOnlyTheChestAtTheOrdersSourceAddress}: three chests
 *       hold the same item type, but the routing must only touch the one at the order's
 *       sourcePos / destinationPos addresses.</li>
 * </ul>
 *
 * <p>The tests treat the order's sourcePos/destinationPos as the routing address — which is
 * the contract callers depend on — and confirm runtime status transitions
 * (PENDING → CLAIMED → COMPLETED) align with the container deltas.</p>
 */
@GameTestHolder(BannerModMain.MOD_ID)
public class BannerModWorkOrderTransportGameTests {

    private static final UUID CLAIM_UUID = UUID.fromString("00000000-0000-0000-0000-000000005c01");
    private static final UUID BUILDING_UUID = UUID.fromString("00000000-0000-0000-0000-000000005d01");
    private static final UUID RESIDENT_UUID = UUID.fromString("00000000-0000-0000-0000-000000005e01");

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty", batch = "workorder_fetch_input")
    public static void fetchInputOrderRoutesItemsFromSourceToDestinationChest(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos sourceAbs = helper.absolutePos(new BlockPos(3, 3, 5));
        BlockPos destinationAbs = helper.absolutePos(new BlockPos(8, 3, 5));
        level.setBlockAndUpdate(sourceAbs, Blocks.CHEST.defaultBlockState());
        level.setBlockAndUpdate(destinationAbs, Blocks.CHEST.defaultBlockState());
        ChestBlockEntity sourceChest = (ChestBlockEntity) level.getBlockEntity(sourceAbs);
        ChestBlockEntity destinationChest = (ChestBlockEntity) level.getBlockEntity(destinationAbs);
        helper.assertTrue(sourceChest != null && destinationChest != null,
                "Expected vanilla chests at the configured FETCH_INPUT anchor positions.");
        sourceChest.setItem(0, new ItemStack(Items.WHEAT, 8));

        SettlementWorkOrderRuntime runtime = SettlementOrchestrator.workOrderRuntime(level);
        helper.assertTrue(runtime != null, "Expected the level to expose a SettlementWorkOrderRuntime.");
        SettlementWorkOrder published = runtime.publish(SettlementWorkOrder.pendingTransport(
                CLAIM_UUID,
                BUILDING_UUID,
                SettlementWorkOrderType.FETCH_INPUT,
                sourceAbs,
                destinationAbs,
                "minecraft:wheat",
                8,
                70,
                level.getGameTime()
        )).orElseThrow();
        SettlementWorkOrder claimed = runtime.claim(
                CLAIM_UUID, RESIDENT_UUID, null, level.getGameTime(), 0L).orElseThrow();
        helper.assertTrue(claimed.status() == SettlementWorkOrderStatus.CLAIMED,
                "Expected the FETCH_INPUT order to advance to CLAIMED on claim.");

        // Drive the same four-step contract the in-world goal executes:
        //   1. resolve filter from the order's resource hint
        //   2. withdraw from the source chest into a carrier
        //   3. deposit from the carrier into the destination chest
        //   4. complete the order in the runtime
        BannerModLogisticsItemFilter filter = TransportContainerExchange
                .filterFromResourceHint(claimed.resourceHintId());
        SimpleContainer carrier = new SimpleContainer(36);
        int withdrawn = TransportContainerExchange.withdrawInto(
                sourceChest, carrier, filter, claimed.itemCount());
        helper.assertTrue(withdrawn == 8,
                "Expected the FETCH_INPUT route to withdraw exactly 8 wheat from the source chest, got " + withdrawn);
        int deposited = TransportContainerExchange.depositInto(destinationChest, carrier, filter);
        helper.assertTrue(deposited == 8,
                "Expected the FETCH_INPUT route to deposit exactly 8 wheat into the destination chest, got " + deposited);
        runtime.complete(published.orderUuid(), level.getGameTime());

        helper.assertTrue(runtime.find(published.orderUuid()).isEmpty(),
                "Expected the FETCH_INPUT order to be removed from the runtime after completion.");
        helper.assertTrue(sourceChest.getItem(0).isEmpty(),
                "Expected the wheat slot on the source chest to be empty after the FETCH_INPUT route completes.");
        ItemStack destStack = destinationChest.getItem(0);
        helper.assertTrue(destStack.is(Items.WHEAT) && destStack.getCount() == 8,
                "Expected 8 wheat in the destination chest's first non-empty slot.");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty", batch = "workorder_filter_shared_chest")
    public static void fetchInputResourceHintFilterIgnoresNonMatchingItemsInSharedChest(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos sourceAbs = helper.absolutePos(new BlockPos(3, 3, 5));
        BlockPos destinationAbs = helper.absolutePos(new BlockPos(8, 3, 5));
        level.setBlockAndUpdate(sourceAbs, Blocks.CHEST.defaultBlockState());
        level.setBlockAndUpdate(destinationAbs, Blocks.CHEST.defaultBlockState());
        ChestBlockEntity sourceChest = (ChestBlockEntity) level.getBlockEntity(sourceAbs);
        ChestBlockEntity destinationChest = (ChestBlockEntity) level.getBlockEntity(destinationAbs);
        helper.assertTrue(sourceChest != null && destinationChest != null, "Expected vanilla chests.");
        // Mixed-content source chest. The order names only wheat; bread must be left in place
        // because settlements share storage between consumers and a wheat fetcher cannot
        // legitimately drain bread reserved for the bakery.
        sourceChest.setItem(0, new ItemStack(Items.WHEAT, 8));
        sourceChest.setItem(1, new ItemStack(Items.BREAD, 8));

        SettlementWorkOrderRuntime runtime = SettlementOrchestrator.workOrderRuntime(level);
        SettlementWorkOrder claimed = runtime.publish(SettlementWorkOrder.pendingTransport(
                CLAIM_UUID, BUILDING_UUID, SettlementWorkOrderType.FETCH_INPUT,
                sourceAbs, destinationAbs, "minecraft:wheat", 8, 70, level.getGameTime()
        )).flatMap(o -> runtime.claim(CLAIM_UUID, RESIDENT_UUID, null, level.getGameTime(), 0L))
                .orElseThrow();

        BannerModLogisticsItemFilter filter = TransportContainerExchange
                .filterFromResourceHint(claimed.resourceHintId());
        SimpleContainer carrier = new SimpleContainer(36);
        int withdrawn = TransportContainerExchange.withdrawInto(
                sourceChest, carrier, filter, claimed.itemCount());
        int deposited = TransportContainerExchange.depositInto(destinationChest, carrier, filter);
        runtime.complete(claimed.orderUuid(), level.getGameTime());

        helper.assertTrue(withdrawn == 8 && deposited == 8,
                "Expected the wheat-only filter to move exactly 8 items, got withdrawn=" + withdrawn
                        + " deposited=" + deposited);
        helper.assertTrue(sourceChest.getItem(0).isEmpty(),
                "Expected the wheat slot to be drained.");
        ItemStack breadStack = sourceChest.getItem(1);
        helper.assertTrue(breadStack.is(Items.BREAD) && breadStack.getCount() == 8,
                "Expected the bread slot to remain at 8 — the resource hint named only wheat, "
                        + "so the filter must skip non-matching items in a shared chest.");
        ItemStack destStack = destinationChest.getItem(0);
        helper.assertTrue(destStack.is(Items.WHEAT) && destStack.getCount() == 8,
                "Expected 8 wheat in the destination chest.");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty", batch = "workorder_multi_storage")
    public static void multiStorageRoutingDrainsOnlyTheChestAtTheOrdersSourceAddress(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos sourceAbs = helper.absolutePos(new BlockPos(3, 3, 5));
        BlockPos destinationAbs = helper.absolutePos(new BlockPos(8, 3, 5));
        BlockPos decoyAAbs = helper.absolutePos(new BlockPos(3, 3, 8));
        BlockPos decoyBAbs = helper.absolutePos(new BlockPos(8, 3, 8));
        level.setBlockAndUpdate(sourceAbs, Blocks.CHEST.defaultBlockState());
        level.setBlockAndUpdate(destinationAbs, Blocks.CHEST.defaultBlockState());
        level.setBlockAndUpdate(decoyAAbs, Blocks.CHEST.defaultBlockState());
        level.setBlockAndUpdate(decoyBAbs, Blocks.CHEST.defaultBlockState());
        ChestBlockEntity source = (ChestBlockEntity) level.getBlockEntity(sourceAbs);
        ChestBlockEntity destination = (ChestBlockEntity) level.getBlockEntity(destinationAbs);
        ChestBlockEntity decoyA = (ChestBlockEntity) level.getBlockEntity(decoyAAbs);
        ChestBlockEntity decoyB = (ChestBlockEntity) level.getBlockEntity(decoyBAbs);
        // Decoys hold the same item type as the order's source — the routing must NOT touch
        // them. The contract is "address-pinned routing", not "any nearby storage with stock".
        source.setItem(0, new ItemStack(Items.WHEAT, 12));
        decoyA.setItem(0, new ItemStack(Items.WHEAT, 64));
        // decoyB stays empty — must not silently receive the deposit either.

        SettlementWorkOrderRuntime runtime = SettlementOrchestrator.workOrderRuntime(level);
        SettlementWorkOrder claimed = runtime.publish(SettlementWorkOrder.pendingTransport(
                CLAIM_UUID, BUILDING_UUID, SettlementWorkOrderType.FETCH_INPUT,
                sourceAbs, destinationAbs, "minecraft:wheat", 12, 70, level.getGameTime()
        )).flatMap(o -> runtime.claim(CLAIM_UUID, RESIDENT_UUID, null, level.getGameTime(), 0L))
                .orElseThrow();

        BannerModLogisticsItemFilter filter = TransportContainerExchange
                .filterFromResourceHint(claimed.resourceHintId());
        SimpleContainer carrier = new SimpleContainer(36);
        TransportContainerExchange.withdrawInto(source, carrier, filter, claimed.itemCount());
        TransportContainerExchange.depositInto(destination, carrier, filter);
        runtime.complete(claimed.orderUuid(), level.getGameTime());

        helper.assertTrue(source.getItem(0).isEmpty(),
                "Expected the source chest at the order's sourcePos to be drained.");
        ItemStack destStack = destination.getItem(0);
        helper.assertTrue(destStack.is(Items.WHEAT) && destStack.getCount() == 12,
                "Expected 12 wheat at the destination address; got "
                        + destStack.getItem() + " x " + destStack.getCount());
        helper.assertTrue(decoyA.getItem(0).is(Items.WHEAT) && decoyA.getItem(0).getCount() == 64,
                "Expected decoy chest A to keep all 64 wheat — multi-storage routing must "
                        + "respect the order's source address, not the nearest match.");
        helper.assertTrue(decoyB.getItem(0).isEmpty(),
                "Expected decoy chest B to remain empty — multi-storage routing must respect "
                        + "the order's destination address, not the nearest empty chest.");
        helper.succeed();
    }
}
