package com.talhanation.bannermod.client.civilian;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the WorkerStatusScreen dismiss contract from WORKERUI-001B.
 * Source-level invariants only — no Minecraft client bootstrap.
 */
class WorkerStatusDismissContractTest {
    private static final Path ROOT = Path.of("");

    private static final String WORKER_STATUS_SCREEN =
            "src/main/java/com/talhanation/bannermod/client/civilian/gui/WorkerStatusScreen.java";
    private static final String DISMISS_MESSAGE =
            "src/main/java/com/talhanation/bannermod/network/messages/civilian/MessageDismissWorker.java";
    private static final String DISMISS_SERVICE =
            "src/main/java/com/talhanation/bannermod/entity/civilian/WorkerDismissService.java";
    private static final String CATALOG =
            "src/main/java/com/talhanation/bannermod/network/catalog/CivilianPacketCatalog.java";
    private static final String EN_LANG =
            "src/main/resources/assets/bannermod/lang/en_us.json";
    private static final String RU_LANG =
            "src/main/resources/assets/bannermod/lang/ru_ru.json";

    @Test
    void workerStatusScreenExposesDismissAction() throws IOException {
        String src = read(WORKER_STATUS_SCREEN);
        assertTrue(src.contains("MessageDismissWorker"),
                "WorkerStatusScreen must send MessageDismissWorker for dismiss intent");
        assertTrue(src.contains("ActionMenuButton"),
                "WorkerStatusScreen must keep dismiss inside the compact action-menu style");
        assertTrue(src.contains("gui.bannermod.worker_screen.dismiss"),
                "WorkerStatusScreen must expose a localized Dismiss entry");
        assertTrue(src.contains("buildWorkerActionEntries"),
                "WorkerStatusScreen must group worker mutation actions without adding another bottom-row button");
    }

    @Test
    void dismissMessageIsServerAuthoritative() throws IOException {
        assertTrue(Files.exists(ROOT.resolve(DISMISS_MESSAGE)),
                "MessageDismissWorker.java must exist");
        String src = read(DISMISS_MESSAGE);
        assertTrue(src.contains("BannerModMessage.serverbound()"),
                "MessageDismissWorker must declare serverbound packet flow");
        assertTrue(src.contains("context.getSender()"),
                "MessageDismissWorker must derive authority from the real server sender");
        assertTrue(src.contains("WorkerDismissService.dismissDeniedReasonKey(player, worker)"),
                "MessageDismissWorker must validate before mutating worker state");
        assertTrue(src.contains("WorkerDismissService.dismiss(player, worker)"),
                "MessageDismissWorker must delegate the mutation to the server-side dismiss service");
    }

    @Test
    void ownerAndAdminCanDismissButNonOwnerCannotMutate() throws IOException {
        String src = read(DISMISS_SERVICE);
        assertTrue(src.contains("player.getUUID().equals(worker.getOwnerUUID()) || player.hasPermissions(2)"),
                "Dismiss authority must allow only the worker owner or an admin");
        assertTrue(src.contains("return \"chat.bannermod.workerui.dismiss.denied.not_owner\""),
                "Non-owner/non-admin dismiss attempts must receive denied feedback");
        int denialCheck = src.indexOf("dismissDeniedReasonKey(player, worker) != null");
        int workAreaMutation = src.indexOf("setBeingWorkedOn(false)");
        int discardMutation = src.indexOf("worker.discard()");
        assertTrue(denialCheck >= 0 && workAreaMutation > denialCheck && discardMutation > denialCheck,
                "Dismiss must perform no worker state mutation before the owner/admin denial check");
    }

    @Test
    void dismissMessageRegisteredAndLocalized() throws IOException {
        assertTrue(read(CATALOG).contains("MessageDismissWorker.class"),
                "CivilianPacketCatalog must register MessageDismissWorker");
        String[] keys = {
                "gui.bannermod.worker_screen.actions",
                "gui.bannermod.worker_screen.actions.tooltip",
                "gui.bannermod.worker_screen.dismiss",
                "chat.bannermod.workerui.dismiss.success",
                "chat.bannermod.workerui.dismiss.denied.missing",
                "chat.bannermod.workerui.dismiss.denied.dead",
                "chat.bannermod.workerui.dismiss.denied.too_far",
                "chat.bannermod.workerui.dismiss.denied.not_owner",
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
