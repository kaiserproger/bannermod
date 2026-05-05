package com.talhanation.bannermod.network.catalog;

import com.talhanation.bannermod.network.messages.civilian.*;

/**
 * Civilian worker packet catalog, registered after all military packets.
 * Order is the legacy workers.WorkersMain setup order minus retired dead slots.
 */
public final class CivilianPacketCatalog {
    public static final Class<?>[] MESSAGES = {
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
        MessageSetSurveyorMode.class,
        MessageSetSurveyorRole.class,
        MessageModifySurveyorSession.class,
        MessageValidateSurveyorSession.class,
        MessageToClientOpenWorkerScreen.class,
        MessageOpenWorkerScreen.class,
        MessageOpenNpcProfile.class,
        MessageConvertWorkerToCitizen.class,
        MessageReassignWorkerProfession.class,
        MessageAssignCitizenVacancy.class,
        MessageAssignHome.class,
        MessageToClientUpdateHousingState.class,
        MessageRequestHousingSnapshot.class,
        MessageApproveHousingRequest.class,
        MessageDenyHousingRequest.class,
        MessageToClientUpdateHamletState.class,
        MessageRequestHamletSnapshot.class,
        MessageRegisterHamlet.class,
        MessageRenameHamlet.class,
    };

    public static final PacketCatalog CATALOG = new PacketCatalog(MESSAGES);

    private CivilianPacketCatalog() {
    }
}
