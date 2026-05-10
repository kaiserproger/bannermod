package com.talhanation.bannermod.society;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class NpcSocietyProfileTest {
    @Test
    void socialNeedDefaultsToZeroAndStaysDemoted() {
        UUID residentId = UUID.fromString("00000000-0000-0000-0000-00000000aa01");
        NpcSocietyProfile profile = NpcSocietyProfile.createDefault(residentId, 6000L)
                .withNeedState(10, 20, 95, 30, 6001L);

        assertEquals(0, profile.socialNeed(), "social pressure should stay demoted out of the cheap runtime profile");
    }

    @Test
    void legacyTagsDropStoredSocialNeedOnLoad() {
        CompoundTag tag = NpcSocietyProfile.createDefault(
                UUID.fromString("00000000-0000-0000-0000-00000000aa02"),
                6000L
        ).toTag();
        tag.putInt("SocialNeed", 88);
        tag.putInt("TrustScore", 77);
        tag.putInt("FearScore", 66);
        tag.putInt("AngerScore", 55);
        tag.putInt("GratitudeScore", 44);
        tag.putInt("LoyaltyScore", 33);

        NpcSocietyProfile loaded = NpcSocietyProfile.fromTag(tag);
        CompoundTag normalized = loaded.toTag();

        assertEquals(0, loaded.socialNeed(), "legacy social-need values should normalize to zero in the cheap runtime model");
        assertFalse(normalized.contains("TrustScore"), "legacy trust values should not survive the cheap runtime rewrite");
        assertFalse(normalized.contains("FearScore"), "legacy fear values should not survive the cheap runtime rewrite");
        assertFalse(normalized.contains("AngerScore"), "legacy anger values should not survive the cheap runtime rewrite");
        assertFalse(normalized.contains("GratitudeScore"), "legacy gratitude values should not survive the cheap runtime rewrite");
        assertFalse(normalized.contains("LoyaltyScore"), "legacy loyalty values should not survive the cheap runtime rewrite");
    }
}
