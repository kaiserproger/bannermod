package com.talhanation.bannermod.settlement.workorder;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Source-level migration contract for WORKGOAL-006.
 *
 * <p>For a fixed enabled trade where the merchant has the trade good, the player can pay,
 * and both inventories have room, the observable output must remain: remove merchant goods,
 * move currency to the merchant, remove currency from the player, grant the trade good,
 * increment the trade counter, persist trades, and refresh the settlement snapshot.</p>
 */
class MerchantWorkGoalMigrationContractTest {
    private static final Path ROOT = Path.of("");

    private static final String MERCHANT_ENTITY =
            "src/main/java/com/talhanation/bannermod/entity/civilian/MerchantEntity.java";
    private static final String ABSTRACT_WORKER_ENTITY =
            "src/main/java/com/talhanation/bannermod/entity/civilian/AbstractWorkerEntity.java";
    private static final String LEGACY_MERCHANT_GOAL =
            "src/main/java/com/talhanation/bannermod/ai/civilian/MerchantWorkGoal.java";

    @Test
    void merchantRegistersSettlementOrderWorkGoalOnly() throws IOException {
        String merchant = read(MERCHANT_ENTITY);
        String worker = read(ABSTRACT_WORKER_ENTITY);

        assertFalse(Files.exists(ROOT.resolve(LEGACY_MERCHANT_GOAL)),
                "MerchantWorkGoal must be deleted from src/main");
        assertTrue(worker.contains("new SettlementOrderWorkGoal(this)"),
                "AbstractWorkerEntity must execute settlement work orders for merchants through super.registerGoals()");
        assertTrue(merchant.contains("protected void registerGoals()")
                        && merchant.indexOf("super.registerGoals()", merchant.indexOf("protected void registerGoals()")) > 0,
                "MerchantEntity must keep the inherited settlement-order goal path");
        assertFalse(merchant.contains("MerchantWorkGoal"),
                "MerchantEntity must not reference the legacy merchant goal");
        assertFalse(merchant.contains("new SettlementOrderWorkGoal(this)"),
                "MerchantEntity must not register a duplicate settlement-order goal");
    }

    @Test
    void fixedSuccessfulTradeOutputRemainsOwnedByMerchantEntity() throws IOException {
        String merchant = read(MERCHANT_ENTITY);

        int enabledCheck = merchant.indexOf("if(!trade.enabled) return");
        int countMerchantGood = merchant.indexOf("this.countMerchantItemStack(tradeItemStack, false)", enabledCheck);
        int playerCanPay = merchant.indexOf("boolean playerCanPay = playerEmeralds >= price", countMerchantGood);
        int merchantCanReceiveCurrency = merchant.indexOf("boolean canAddItemToInv = canAddItemToMerchant(currencyItem)", playerCanPay);
        int shrinkMerchantGood = merchant.indexOf("shrinkMerchantItemStack(tradeItemStack, tradeCount, false)", merchantCanReceiveCurrency);
        int addCurrencyToMerchant = merchant.indexOf("addItemToMerchant(itemStackInSlot, amount)", shrinkMerchantGood);
        int shrinkPlayerCurrency = merchant.indexOf("itemStackInSlot.shrink(amount)", addCurrencyToMerchant);
        int grantTradeGood = merchant.indexOf("addItemWithMaxStackCount(playerInv, tradeGood, tradeCount)", shrinkPlayerCurrency);
        int incrementTradeCount = merchant.indexOf("trade.currentTrades++", grantTradeGood);
        int persistTrades = merchant.indexOf("this.setTrades(currents)", incrementTradeCount);
        int refreshSettlement = merchant.indexOf("this.refreshSettlementSnapshot()", persistTrades);

        assertTrue(enabledCheck >= 0, "fixed trade scenario must start from an enabled trade");
        assertTrue(countMerchantGood > enabledCheck,
                "successful trade must verify merchant goods before mutating output");
        assertTrue(playerCanPay > countMerchantGood,
                "successful trade must verify player currency before mutating output");
        assertTrue(merchantCanReceiveCurrency > playerCanPay,
                "successful trade must verify merchant currency capacity before mutating output");
        assertTrue(shrinkMerchantGood > merchantCanReceiveCurrency,
                "successful fixed trade must remove the merchant good, matching legacy output");
        assertTrue(addCurrencyToMerchant > shrinkMerchantGood,
                "successful fixed trade must move currency to the merchant, matching legacy output");
        assertTrue(shrinkPlayerCurrency > addCurrencyToMerchant,
                "successful fixed trade must remove paid currency from the player, matching legacy output");
        assertTrue(grantTradeGood > shrinkPlayerCurrency,
                "successful fixed trade must grant the trade good, matching legacy output");
        assertTrue(incrementTradeCount > grantTradeGood,
                "successful fixed trade must increment the trade counter, matching legacy output");
        assertTrue(persistTrades > incrementTradeCount,
                "successful fixed trade must persist the updated trade list");
        assertTrue(refreshSettlement > persistTrades,
                "successful fixed trade must refresh the settlement snapshot after persisted output");
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(ROOT.resolve(relativePath));
    }
}
