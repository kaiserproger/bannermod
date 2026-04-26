package com.talhanation.bannermod.network;

import com.talhanation.bannermod.bootstrap.BannerModMain;
import de.maxhenkel.corelib.CommonRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

// Military network messages (migrated from recruits.network.*)
import com.talhanation.bannermod.network.messages.military.*;

// Civilian network messages (migrated from workers.network.*)
import com.talhanation.bannermod.network.messages.civilian.*;

// War network messages (warfare-RP runtime sync)
import com.talhanation.bannermod.network.messages.war.MessageCreatePoliticalEntity;
import com.talhanation.bannermod.network.messages.war.MessagePlaceSiegeStandardHere;
import com.talhanation.bannermod.network.messages.war.MessageRenamePoliticalEntity;
import com.talhanation.bannermod.network.messages.war.MessageSetGovernmentForm;
import com.talhanation.bannermod.network.messages.war.MessageSetPoliticalEntityCapital;
import com.talhanation.bannermod.network.messages.war.MessageToClientUpdateWarState;

/**
 * Owns the single shared SimpleChannel for the merged bannermod runtime.
 *
 * Military packets are registered at indices [0..MILITARY_MESSAGES.length) and
 * civilian packets at [MILITARY_MESSAGES.length..MILITARY_MESSAGES.length+CIVILIAN_MESSAGES.length).
 *
 * Existing ordering within each family is preserved from the legacy messages[] arrays
 * in recruits.Main.setup() and workers.WorkersMain.setup() respectively; newly restored
 * packets are appended to avoid renumbering existing military packets.
 *
 * workerPacketOffset() == MILITARY_MESSAGES.length == 107.
 */
public class BannerModNetworkBootstrap {

    /**
     * Military message catalog (verbatim order from recruits.Main legacy setup).
     * Registered at channel indices [0..MILITARY_MESSAGES.length).
     * Count: 107.
     */
    @SuppressWarnings({"rawtypes"})
    public static final Class[] MILITARY_MESSAGES = {
        MessageMovement.class,
        MessageCommandScreen.class,
        MessageRecruitGui.class,
        MessageHire.class,
        MessageHireGui.class,
        MessageDisband.class,
        MessageRest.class,
        MessageAttack.class,
        MessageAggro.class,
        MessageAggroGui.class,
        MessageListen.class,
        MessageProtectEntity.class,
        MessageMountEntity.class,
        MessageMountEntityGui.class,
        MessageDismount.class,
        MessageDismountGui.class,
        MessageBackToMountEntity.class,
        MessageRangedFire.class,
        MessageStrategicFire.class,
        MessageShields.class,
        MessageGroup.class,
        MessageSplitGroup.class,
        MessageMergeGroup.class,
        MessageDisbandGroup.class,
        MessageSetLeaderGroup.class,
        MessageRecruitCount.class,
        MessageAssassinCount.class,
        MessageAssassinGui.class,
        MessageAssassinate.class,
        MessageFaceCommand.class,
        MessageTeleportPlayer.class,
        MessageScoutTask.class,
        MessageFollowGui.class,
        MessageClearTarget.class,
        MessageClearTargetGui.class,
        MessageOpenSpecialScreen.class,
        MessageOpenPromoteScreen.class,
        MessageOpenDisbandScreen.class,
        MessageToClientOpenNobleTradeScreen.class,
        MessageHireFromNobleVillager.class,
        MessageWriteSpawnEgg.class,
        MessageDoPayment.class,
        MessageUpkeepEntity.class,
        MessageUpkeepPos.class,
        MessageClearUpkeep.class,
        MessageClearUpkeepGui.class,
        MessageToClientUpdateOnlinePlayers.class,
        MessageSendMessenger.class,
        MessageAnswerMessenger.class,
        MessageToClientOpenMessengerAnswerScreen.class,
        MessageToClientUpdateMessengerScreen.class,
        MessageToClientUpdateHireState.class,
        MessageToClientUpdateClaim.class,
        MessageToClientUpdateClaims.class,
        MessageUpdateClaim.class,
        MessageDeleteClaim.class,
        MessageToClientReceiveRoute.class,
        MessageTransferRoute.class,
        MessageToClientUpdateGroups.class,
        MessageUpdateGroup.class,
        MessageAssignGroupToPlayer.class,
        MessageAssignGroupToCompanion.class,
        MessageRemoveAssignedGroupFromCompanion.class,
        MessageAssignNearbyRecruitsInGroup.class,
        MessageApplyNoGroup.class,
        MessageToClientUpdateUnitInfo.class,
        MessageToClientUpdateLeaderScreen.class,
        MessageSaveFormationFollowMovement.class,
        MessageFormationFollowMovement.class,
        MessagePatrolLeaderSetRoute.class,
        MessagePatrolLeaderAddWayPoint.class,
        MessagePatrolLeaderRemoveWayPoint.class,
        MessagePatrolLeaderSetCycle.class,
        MessagePatrolLeaderSetPatrolState.class,
        MessagePatrolLeaderSetPatrollingSpeed.class,
        MessagePatrolLeaderSetWaitTime.class,
        MessagePatrolLeaderSetEnemyAction.class,
        MessagePatrolLeaderSetInfoMode.class,
        MessageToClientSetToast.class,
        MessagePromoteRecruit.class,
        MessageOpenGovernorScreen.class,
        MessageToClientUpdateGovernorScreen.class,
        MessageUpdateGovernorPolicy.class,
        MessageToggleGovernorAutoManage.class,
        MessageOpenContractBoard.class,
        MessageToClientUpdateContractBoard.class,
        MessageAcceptContract.class,
        MessageCancelContract.class,
        MessagePinContract.class,
        MessageSetContractMaxReward.class,
        MessageDebugGui.class,
        MessageDebugScreen.class,
        MessageSelectRecruits.class,
        MessageCombatStance.class,
        MessageCombatStanceGui.class,
        MessageAssignRecruitToPlayer.class,
        MessageRequestFormationMapSnapshot.class,
        MessageToClientUpdateFormationMapSnapshot.class,
        MessageFormationMapMoveOrder.class,
        MessageFormationMapEngage.class,
    };

    /**
     * Civilian message catalog (verbatim order from workers.WorkersMain legacy setup, minus
     * the dead {@code MessageAddWorkArea} slot retired in 2026-04 — see SETTLEMENT-002).
     * Registered at channel indices [MILITARY_MESSAGES.length..MILITARY_MESSAGES.length+CIVILIAN_MESSAGES.length).
     * Count: 22. workerPacketOffset == MILITARY_MESSAGES.length == 107.
     */
    @SuppressWarnings({"rawtypes"})
    public static final Class[] CIVILIAN_MESSAGES = {
        MessageToClientOpenWorkAreaScreen.class,
        MessageUpdateWorkArea.class,
        MessageUpdateCropArea.class,
        MessageUpdateLumberArea.class,
        MessageUpdateBuildArea.class,
        MessageUpdateMiningArea.class,
        MessageUpdateMerchantTrade.class,
        MessageUpdateMerchant.class,
        MessageDoTradeWithMerchant.class,
        MessageOpenMerchantEditTradeScreen.class,
        MessageOpenMerchantTradeScreen.class,
        MessageToClientUpdateConfig.class,
        MessageUpdateStorageArea.class,
        MessageUpdateAnimalPenArea.class,
        MessageRotateWorkArea.class,
        MessageMoveMerchantTrade.class,
        MessageUpdateMarketArea.class,
        MessageUpdateOwner.class,
        MessageRecoverWorkerControl.class,
        MessageRequestPlaceBuilding.class,
        MessageRequestValidateBuilding.class,
        MessageRequestRegisterBuilding.class,
    };

    /**
     * War message catalog. Registered after civilian packets at indices
     * [MILITARY_MESSAGES.length + CIVILIAN_MESSAGES.length .. ).
     */
    @SuppressWarnings({"rawtypes"})
    public static final Class[] WAR_MESSAGES = {
        MessageToClientUpdateWarState.class,
        MessageCreatePoliticalEntity.class,
        MessageRenamePoliticalEntity.class,
        MessageSetPoliticalEntityCapital.class,
        MessagePlaceSiegeStandardHere.class,
        MessageSetGovernmentForm.class,
    };

    private BannerModNetworkBootstrap() {
    }

    /**
     * Returns the offset at which civilian (worker) packets begin in the shared channel.
     * Equal to MILITARY_MESSAGES.length (107).
     * Matches the merged runtime's current worker packet offset.
     */
    public static int workerPacketOffset() {
        return MILITARY_MESSAGES.length;
    }

    /**
     * Creates and returns the single shared SimpleChannel with all military and civilian
     * packets registered. Must be called once during FMLCommonSetupEvent.
     *
     * Military packets: indices [0..107)
     * Civilian packets: indices [107..130)
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static SimpleChannel createSharedChannel() {
        SimpleChannel channel = CommonRegistry.registerChannel(BannerModMain.MOD_ID, "default");

        // --- Military messages [0..MILITARY_MESSAGES.length) ---
        for (int i = 0; i < MILITARY_MESSAGES.length; i++) {
            CommonRegistry.registerMessage(channel, i, MILITARY_MESSAGES[i]);
        }

        // --- Civilian messages [MILITARY_MESSAGES.length..MILITARY_MESSAGES.length+CIVILIAN_MESSAGES.length) ---
        for (int j = 0; j < CIVILIAN_MESSAGES.length; j++) {
            CommonRegistry.registerMessage(channel, MILITARY_MESSAGES.length + j, CIVILIAN_MESSAGES[j]);
        }

        // --- War messages [MILITARY_MESSAGES.length + CIVILIAN_MESSAGES.length .. ) ---
        int warOffset = MILITARY_MESSAGES.length + CIVILIAN_MESSAGES.length;
        for (int k = 0; k < WAR_MESSAGES.length; k++) {
            CommonRegistry.registerMessage(channel, warOffset + k, WAR_MESSAGES[k]);
        }

        // Bind to WorkersRuntime for compatibility
        com.talhanation.bannermod.bootstrap.WorkersRuntime.bindChannel(channel);

        return channel;
    }
}
