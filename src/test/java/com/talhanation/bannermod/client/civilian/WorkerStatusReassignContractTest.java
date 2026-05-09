package com.talhanation.bannermod.client.civilian;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the WorkerStatusScreen reassign contract from WORKERUI-001.
 * Source-level invariants only — no Minecraft client bootstrap.
 */
class WorkerStatusReassignContractTest {
    private static final Path ROOT = Path.of("");

    private static final String WORKER_STATUS_SCREEN =
            "src/main/java/com/talhanation/bannermod/client/civilian/gui/WorkerStatusScreen.java";
    private static final String REASSIGN_MESSAGE =
            "src/main/java/com/talhanation/bannermod/network/messages/civilian/MessageReassignWorkerProfession.java";
    private static final String EN_LANG =
            "src/main/resources/assets/bannermod/lang/en_us.json";
    private static final String RU_LANG =
            "src/main/resources/assets/bannermod/lang/ru_ru.json";
    private static final String SNAPSHOT =
            "src/main/java/com/talhanation/bannermod/entity/civilian/WorkerInspectionSnapshot.java";
    private static final String CONVERSION_SERVICE =
            "src/main/java/com/talhanation/bannermod/entity/civilian/WorkerCitizenConversionService.java";
    private static final String CATALOG =
            "src/main/java/com/talhanation/bannermod/network/catalog/CivilianPacketCatalog.java";

    @Test
    void workerStatusScreenWiresReassignAction() throws IOException {
        String src = read(WORKER_STATUS_SCREEN);
        assertTrue(src.contains("MessageReassignWorkerProfession"),
                "WorkerStatusScreen must send MessageReassignWorkerProfession to swap profession");
        assertTrue(src.contains("ActionMenuButton"),
                "WorkerStatusScreen must use ActionMenuButton for the Reassign dropdown");
        assertTrue(src.contains("gui.bannermod.worker_screen.reassign"),
                "WorkerStatusScreen must reference the reassign trigger translatable key");
        assertTrue(src.contains("gui.bannermod.worker_screen.actions"),
                "Convert action must live inside the compact worker Actions menu after the dismiss split");
        assertTrue(src.contains("CitizenProfession"),
                "Reassign menu must enumerate CitizenProfession entries to filter out the current one");
    }

    @Test
    void reassignMessageReusesServerAuth() throws IOException {
        assertTrue(Files.exists(ROOT.resolve(REASSIGN_MESSAGE)),
                "MessageReassignWorkerProfession.java must exist");
        String src = read(REASSIGN_MESSAGE);
        assertTrue(src.contains("WorkerCitizenConversionService"),
                "MessageReassignWorkerProfession must reuse WorkerCitizenConversionService for auth + spawn");
        assertTrue(src.contains("BannerModMessage.serverbound()"),
                "MessageReassignWorkerProfession must declare serverbound packet flow");
        assertTrue(src.contains("executeServerSide"),
                "MessageReassignWorkerProfession must expose executeServerSide handler");
    }

    @Test
    void reassignProfessionPipelineExposed() throws IOException {
        String svc = read(CONVERSION_SERVICE);
        assertTrue(svc.contains("reassignProfession"),
                "WorkerCitizenConversionService must expose reassignProfession atomic helper");
        assertTrue(svc.contains("workerProfessionTag"),
                "WorkerCitizenConversionService must expose workerProfessionTag mapping helper");
        assertTrue(svc.contains("convertDeniedReasonKey"),
                "Reassign path must reuse convertDeniedReasonKey for auth");
    }

    @Test
    void snapshotCarriesCurrentProfessionTag() throws IOException {
        String snap = read(SNAPSHOT);
        assertTrue(snap.contains("currentProfessionTag"),
                "WorkerInspectionSnapshot must carry currentProfessionTag for the dropdown filter");
        // New field at the END of the byte format.
        int writeIdx = snap.indexOf("buf.writeUtf(currentProfessionTag");
        int writeBlocked = snap.indexOf("buf.writeUtf(convertBlockedReasonKey)");
        assertTrue(writeIdx > writeBlocked && writeIdx >= 0,
                "currentProfessionTag must serialize AFTER pre-existing fields to keep network compat");
    }

    @Test
    void packetCatalogRegistersReassignMessage() throws IOException {
        String catalog = read(CATALOG);
        assertTrue(catalog.contains("MessageReassignWorkerProfession.class"),
                "CivilianPacketCatalog must register MessageReassignWorkerProfession");
    }

    @Test
    void langKeysPresentInBothLocales() throws IOException {
        String[] keys = {
                "gui.bannermod.worker_screen.reassign",
                "gui.bannermod.worker_screen.convert.tooltip.dismiss_path",
                "chat.bannermod.workerui.reassign.success",
                "chat.bannermod.workerui.reassign.denied.missing",
                "chat.bannermod.workerui.reassign.denied.invalid_profession",
                "chat.bannermod.workerui.reassign.denied.same_profession",
                "chat.bannermod.workerui.reassign.denied.cant_afford",
                "chat.bannermod.workerui.reassign.denied.convert_failed",
                "gui.bannermod.worker_screen.reassign.option.farmer",
                "gui.bannermod.worker_screen.reassign.option.lumberjack",
                "gui.bannermod.worker_screen.reassign.option.miner",
                "gui.bannermod.worker_screen.reassign.option.animal_farmer",
                "gui.bannermod.worker_screen.reassign.option.builder",
                "gui.bannermod.worker_screen.reassign.option.merchant",
                "gui.bannermod.worker_screen.reassign.option.fisherman",
        };
        String en = read(EN_LANG);
        String ru = read(RU_LANG);
        for (String key : keys) {
            assertTrue(en.contains("\"" + key + "\""), "en_us.json missing key: " + key);
            assertTrue(ru.contains("\"" + key + "\""), "ru_ru.json missing key: " + key);
        }
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(ROOT.resolve(relativePath));
    }
}
