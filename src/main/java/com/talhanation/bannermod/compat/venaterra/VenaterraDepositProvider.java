package com.talhanation.bannermod.compat.venaterra;

import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.persistence.military.RecruitsClaim;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.fml.ModList;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public interface VenaterraDepositProvider {
    String MOD_ID = "venaterra";
    String API_CLASS_NAME = "ru.kaiserroman.venaterra.api.VenaterraDepositApi";
    String RESULT_CLASS_NAME = "ru.kaiserroman.venaterra.api.DepositSurveyResult";

    List<VenaterraDepositCandidate> findClaimDeposits(ServerLevel level, RecruitsClaim claim);

    static VenaterraDepositProvider create() {
        return create(ModList.get().isLoaded(MOD_ID), Class::forName);
    }

    static VenaterraDepositProvider create(boolean venaterraLoaded, ReflectiveClassResolver classResolver) {
        if (!venaterraLoaded) {
            return EmptyVenaterraDepositProvider.INSTANCE;
        }

        try {
            Class<?> apiClass = classResolver.resolve(API_CLASS_NAME);
            Class<?> resultClass = classResolver.resolve(RESULT_CLASS_NAME);
            Method findDepositsInArea = apiClass.getMethod("findDepositsInArea", ServerLevel.class, BlockPos.class, int.class);
            return new ReflectiveVenaterraDepositProvider(findDepositsInArea, resultClass);
        }
        catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            BannerModMain.LOGGER.warn("VenaTerra compatibility disabled; public deposit API is unavailable", exception);
            return EmptyVenaterraDepositProvider.INSTANCE;
        }
    }

    @FunctionalInterface
    interface ReflectiveClassResolver {
        Class<?> resolve(String className) throws ClassNotFoundException;
    }

    final class EmptyVenaterraDepositProvider implements VenaterraDepositProvider {
        static final EmptyVenaterraDepositProvider INSTANCE = new EmptyVenaterraDepositProvider();

        private EmptyVenaterraDepositProvider() {
        }

        @Override
        public List<VenaterraDepositCandidate> findClaimDeposits(ServerLevel level, RecruitsClaim claim) {
            return List.of();
        }
    }

    final class ReflectiveVenaterraDepositProvider implements VenaterraDepositProvider {
        private final Method findDepositsInArea;
        private final Class<?> resultClass;

        ReflectiveVenaterraDepositProvider(Method findDepositsInArea, Class<?> resultClass) {
            this.findDepositsInArea = findDepositsInArea;
            this.resultClass = resultClass;
        }

        @Override
        public List<VenaterraDepositCandidate> findClaimDeposits(ServerLevel level, RecruitsClaim claim) {
            if (claim == null || claim.getClaimedChunks().isEmpty()) {
                return List.of();
            }

            ClaimQueryArea queryArea = ClaimQueryArea.fromClaim(claim);
            try {
                Object rawResults = findDepositsInArea.invoke(null, level, queryArea.center(), queryArea.horizontalRadius());
                if (!(rawResults instanceof Collection<?> results)) {
                    return List.of();
                }

                return results.stream()
                        .filter(resultClass::isInstance)
                        .map(VenaterraDepositProvider::mapSurveyResult)
                        .filter(Objects::nonNull)
                        .filter(candidate -> queryArea.contains(candidate.center()))
                        .toList();
            }
            catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
                BannerModMain.LOGGER.warn("VenaTerra deposit query failed; returning no BannerMod deposit candidates", exception);
                return List.of();
            }
        }
    }

    record ClaimQueryArea(BlockPos center, int horizontalRadius, int minBlockX, int maxBlockX, int minBlockZ, int maxBlockZ, Set<ChunkPos> claimedChunks) {
        static ClaimQueryArea fromClaim(RecruitsClaim claim) {
            int minChunkX = Integer.MAX_VALUE;
            int maxChunkX = Integer.MIN_VALUE;
            int minChunkZ = Integer.MAX_VALUE;
            int maxChunkZ = Integer.MIN_VALUE;
            Set<ChunkPos> claimedChunks = Set.copyOf(claim.getClaimedChunks());

            for (ChunkPos chunk : claimedChunks) {
                minChunkX = Math.min(minChunkX, chunk.x);
                maxChunkX = Math.max(maxChunkX, chunk.x);
                minChunkZ = Math.min(minChunkZ, chunk.z);
                maxChunkZ = Math.max(maxChunkZ, chunk.z);
            }

            int minBlockX = minChunkX << 4;
            int maxBlockX = (maxChunkX << 4) + 15;
            int minBlockZ = minChunkZ << 4;
            int maxBlockZ = (maxChunkZ << 4) + 15;
            int centerX = minBlockX + ((maxBlockX - minBlockX) / 2);
            int centerZ = minBlockZ + ((maxBlockZ - minBlockZ) / 2);
            int horizontalRadius = Math.max(maxBlockX - centerX, maxBlockZ - centerZ);
            return new ClaimQueryArea(new BlockPos(centerX, 0, centerZ), horizontalRadius, minBlockX, maxBlockX, minBlockZ, maxBlockZ, claimedChunks);
        }

        boolean contains(BlockPos pos) {
            return pos.getX() >= minBlockX
                    && pos.getX() <= maxBlockX
                    && pos.getZ() >= minBlockZ
                    && pos.getZ() <= maxBlockZ
                    && claimedChunks.contains(new ChunkPos(pos));
        }
    }

    @Nullable
    static VenaterraDepositCandidate mapSurveyResult(Object result) {
        try {
            ResourceLocation oreId = invoke(result, "oreId", ResourceLocation.class);
            ResourceLocation dropItemId = invoke(result, "dropItemId", ResourceLocation.class);
            BlockPos center = invoke(result, "center", BlockPos.class);
            Float richness = invokeNumber(result, "richness", Float.class);
            Double chance = invokeNumber(result, "chance", Double.class);
            ResourceLocation dimension = invoke(result, "dimension", ResourceLocation.class);
            Object category = invoke(result, "category", Object.class);

            if (center == null || richness == null || chance == null) {
                return null;
            }

            return new VenaterraDepositCandidate(
                    VenaterraDepositCategory.fromApiCategory(category),
                    oreId,
                    dropItemId,
                    center,
                    richness,
                    chance,
                    VenaterraDepositCandidate.SourceMetadata.venaterra(dimension)
            );
        }
        catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return null;
        }
    }

    @Nullable
    private static <T> T invoke(Object target, String methodName, Class<T> expectedType) throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(methodName);
        method.setAccessible(true);
        Object value = method.invoke(target);
        return expectedType.isInstance(value) ? expectedType.cast(value) : null;
    }

    @Nullable
    private static <T extends Number> T invokeNumber(Object target, String methodName, Class<T> expectedType) throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(methodName);
        method.setAccessible(true);
        Object value = method.invoke(target);
        if (expectedType.isInstance(value)) {
            return expectedType.cast(value);
        }
        if (value instanceof Number number) {
            if (expectedType == Float.class) {
                return expectedType.cast(number.floatValue());
            }
            if (expectedType == Double.class) {
                return expectedType.cast(number.doubleValue());
            }
        }
        return null;
    }
}
