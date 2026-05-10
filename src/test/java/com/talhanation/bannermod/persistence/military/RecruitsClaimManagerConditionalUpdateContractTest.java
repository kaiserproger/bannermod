package com.talhanation.bannermod.persistence.military;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RecruitsClaimManagerConditionalUpdateContractTest {
    private static final Path ROOT = Path.of("");
    private static final String MANAGER = "src/main/java/com/talhanation/bannermod/persistence/military/RecruitsClaimManager.java";

    @Test
    void conditionalUpdateMutatesBeforeEventAndRollsBackCanceledEvent() throws IOException {
        String src = Files.readString(ROOT.resolve(MANAGER));

        int beforePersist = src.indexOf("beforePersist.getAsBoolean()");
        int eventPost = src.indexOf("NeoForge.EVENT_BUS.post(updateEvent)");
        int rollback = src.indexOf("rollback.run()");
        int persist = src.indexOf("persistClaims(RecruitsClaimSaveData.get(level))");

        assertTrue(beforePersist >= 0, "conditional update must run the mutation/debit callback");
        assertTrue(eventPost >= 0, "conditional update must still post ClaimEvent.Updated");
        assertTrue(rollback >= 0, "conditional update must rollback if ClaimEvent.Updated is canceled");
        assertTrue(beforePersist < eventPost, "listeners must see the intended final claim state");
        assertTrue(eventPost < persist, "claim event must remain a pre-persist veto point");
        assertTrue(rollback < persist, "canceled events must rollback before any persistence");
    }
}
