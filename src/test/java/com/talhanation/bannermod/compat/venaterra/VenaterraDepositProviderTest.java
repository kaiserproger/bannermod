package com.talhanation.bannermod.compat.venaterra;

import com.talhanation.bannermod.persistence.military.RecruitsClaim;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VenaterraDepositProviderTest {

    @Test
    void disabledProviderReturnsNoCandidates() {
        VenaterraDepositProvider provider = VenaterraDepositProvider.create(false, className -> FakeApi.class);

        assertTrue(provider.findClaimDeposits(null, claimAtOrigin()).isEmpty());
    }

    @Test
    void missingApiClassReturnsNoCandidates() {
        VenaterraDepositProvider provider = VenaterraDepositProvider.create(true, className -> {
            throw new ClassNotFoundException(className);
        });

        assertTrue(provider.findClaimDeposits(null, claimAtOrigin()).isEmpty());
    }

    @Test
    void mapsReflectiveSurveyResultToBannerModCandidate() {
        VenaterraDepositCandidate candidate = VenaterraDepositProvider.mapSurveyResult(new FakeSurveyResult(
                ResourceLocation.withDefaultNamespace("iron_ore"),
                ResourceLocation.withDefaultNamespace("raw_iron"),
                new BlockPos(5, 32, 6),
                0.8F,
                0.65D,
                Level.OVERWORLD.location(),
                FakeApiCategory.IRON
        ));

        assertEquals(VenaterraDepositCategory.IRON, candidate.category());
        assertEquals(new BlockPos(5, 32, 6), candidate.center());
        assertEquals(0.8F, candidate.richness());
        assertEquals(0.65D, candidate.confidence());
        assertEquals(VenaterraDepositProvider.MOD_ID, candidate.source().modId());
        assertEquals(VenaterraDepositProvider.API_CLASS_NAME, candidate.source().apiClass());
    }

    @Test
    void unknownReflectiveCategoryMapsToUnknownCandidate() {
        VenaterraDepositCandidate candidate = VenaterraDepositProvider.mapSurveyResult(new FakeSurveyResult(
                ResourceLocation.withDefaultNamespace("modded_ore"),
                ResourceLocation.withDefaultNamespace("modded_drop"),
                new BlockPos(5, 32, 6),
                0.2F,
                0.1D,
                Level.OVERWORLD.location(),
                FakeApiCategory.NEW_CATEGORY
        ));

        assertEquals(VenaterraDepositCategory.UNKNOWN_OTHER, candidate.category());
    }

    @Test
    void reflectiveProviderFiltersToClaimLocalCenters() {
        VenaterraDepositProvider provider = VenaterraDepositProvider.create(true, className -> {
            if (VenaterraDepositProvider.API_CLASS_NAME.equals(className)) {
                return FakeApi.class;
            }
            if (VenaterraDepositProvider.RESULT_CLASS_NAME.equals(className)) {
                return FakeSurveyResult.class;
            }
            throw new ClassNotFoundException(className);
        });

        List<VenaterraDepositCandidate> candidates = provider.findClaimDeposits(null, claimAtOrigin());

        assertEquals(1, candidates.size());
        assertEquals(new BlockPos(4, 16, 4), candidates.getFirst().center());
    }

    @Test
    void reflectiveProviderRejectsBoundingBoxDepositsOutsideClaimedChunks() {
        VenaterraDepositProvider provider = VenaterraDepositProvider.create(true, className -> {
            if (VenaterraDepositProvider.API_CLASS_NAME.equals(className)) {
                return FakeApi.class;
            }
            if (VenaterraDepositProvider.RESULT_CLASS_NAME.equals(className)) {
                return FakeSurveyResult.class;
            }
            throw new ClassNotFoundException(className);
        });

        List<VenaterraDepositCandidate> candidates = provider.findClaimDeposits(null, claimWithGap());

        assertEquals(List.of(new BlockPos(4, 16, 4), new BlockPos(40, 16, 40)), candidates.stream().map(VenaterraDepositCandidate::center).toList());
    }


    @Test
    void failingApiCallReturnsNoCandidates() {
        VenaterraDepositProvider provider = VenaterraDepositProvider.create(true, className -> {
            if (VenaterraDepositProvider.API_CLASS_NAME.equals(className)) {
                return FailingApi.class;
            }
            if (VenaterraDepositProvider.RESULT_CLASS_NAME.equals(className)) {
                return FakeSurveyResult.class;
            }
            throw new ClassNotFoundException(className);
        });

        assertTrue(provider.findClaimDeposits(null, claimAtOrigin()).isEmpty());
    }

    private static RecruitsClaim claimAtOrigin() {
        RecruitsClaim claim = new RecruitsClaim("test", null);
        claim.addChunk(new ChunkPos(0, 0));
        return claim;
    }

    private static RecruitsClaim claimWithGap() {
        RecruitsClaim claim = new RecruitsClaim("test", null);
        claim.addChunk(new ChunkPos(0, 0));
        claim.addChunk(new ChunkPos(2, 2));
        return claim;
    }

    public static final class FakeApi {
        public static List<FakeSurveyResult> findDepositsInArea(ServerLevel level, BlockPos center, int horizontalRadius) {
            return List.of(
                    new FakeSurveyResult(
                            ResourceLocation.withDefaultNamespace("iron_ore"),
                            ResourceLocation.withDefaultNamespace("raw_iron"),
                            new BlockPos(4, 16, 4),
                            0.75F,
                            0.5D,
                            Level.OVERWORLD.location(),
                            FakeApiCategory.IRON
                    ),
                    new FakeSurveyResult(
                            ResourceLocation.withDefaultNamespace("coal_ore"),
                            ResourceLocation.withDefaultNamespace("coal"),
                            new BlockPos(20, 16, 20),
                            0.3F,
                            0.2D,
                            Level.OVERWORLD.location(),
                            FakeApiCategory.INDUSTRIAL_FUEL
                    ),
                    new FakeSurveyResult(
                            ResourceLocation.withDefaultNamespace("coal_ore"),
                            ResourceLocation.withDefaultNamespace("coal"),
                            new BlockPos(40, 16, 40),
                            0.3F,
                            0.2D,
                            Level.OVERWORLD.location(),
                            FakeApiCategory.INDUSTRIAL_FUEL
                    )
            );
        }
    }

    public static final class FailingApi {
        public static List<FakeSurveyResult> findDepositsInArea(ServerLevel level, BlockPos center, int horizontalRadius) {
            throw new IllegalStateException("boom");
        }
    }

    private record FakeSurveyResult(
            ResourceLocation oreId,
            ResourceLocation dropItemId,
            BlockPos center,
            float richness,
            double chance,
            ResourceLocation dimension,
            FakeApiCategory category
    ) {
    }

    private enum FakeApiCategory {
        IRON,
        INDUSTRIAL_FUEL,
        NEW_CATEGORY
    }
}
