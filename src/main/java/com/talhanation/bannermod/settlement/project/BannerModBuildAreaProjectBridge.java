package com.talhanation.bannermod.settlement.project;

import com.talhanation.bannermod.entity.civilian.workarea.BuildArea;
import com.talhanation.bannermod.entity.civilian.workarea.WorkAreaIndex;
import com.talhanation.bannermod.persistence.military.RecruitsClaim;
import com.talhanation.bannermod.persistence.military.RecruitsClaimManager;
import com.talhanation.bannermod.settlement.growth.PendingProject;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Bridge between a {@link SettlementProjectScheduler} queue and the existing
 * player-authored BuildArea subsystem.
 *
 * <p>The bridge is deliberately read-only with respect to {@link BuildArea}: it selects
 * an executable target but does not start or mutate the build.
 */
public final class BannerModBuildAreaProjectBridge {

    public BannerModBuildAreaProjectBridge() {
    }

    /**
     * Strategy for picking a {@link com.talhanation.bannermod.entity.civilian.workarea.BuildArea}
     * to host a given {@link PendingProject}. Purely a lookup — the resolver must not
     * start the build or mutate BuildArea state.
     */
    public interface BuildAreaResolver {
        Optional<BuildAreaBinding> resolveCandidate(UUID claimUuid, PendingProject project);
    }

    /**
     * Snapshot of a BuildArea candidate selected by a {@link BuildAreaResolver}.
     *
     * @param buildAreaUuid       the entity UUID of the resolved BuildArea.
     * @param hasTemplateLoaded   whether the structure NBT is already populated. If false, the
     *                            assignment starts in {@link AssignmentPhase#MATERIALS_PENDING}.
     * @param estimatedTickCost   best-effort remaining tick cost for the build, used for back-pressure.
     */
    public record BuildAreaBinding(UUID buildAreaUuid, boolean hasTemplateLoaded, int estimatedTickCost) {
        public BuildAreaBinding {
            if (buildAreaUuid == null) {
                throw new IllegalArgumentException("buildAreaUuid must not be null");
            }
            if (estimatedTickCost < 0) {
                estimatedTickCost = 0;
            }
        }
    }

    /**
     * Resolver that never binds a project. Production callers should replace this with a real
     * implementation that walks the active {@code BuildArea} entities in the claim.
     *
     * <pre>
     * // FIXME slice-C-follow-up: provide a production BuildAreaResolver backed by
     * //   com.talhanation.bannermod.entity.civilian.workarea.BuildArea lookups.
     * //   The existing BuildArea API exposes getUUID(), hasStructureTemplate(),
     * //   hasPendingBuildWork(), and setStartBuild(boolean) — enough to implement
     * //   resolution once the settlement <-> claim plumbing (slice D) is committed.
     * </pre>
     */
    public static final class NoopBuildAreaResolver implements BuildAreaResolver {
        @Override
        public Optional<BuildAreaBinding> resolveCandidate(UUID claimUuid, PendingProject project) {
            return Optional.empty();
        }
    }

    /** Resolver backed by live {@link BuildArea} entities inside the target claim. */
    public static final class ClaimBuildAreaResolver implements BuildAreaResolver {
        private final ServerLevel level;
        private final RecruitsClaim claim;

        public ClaimBuildAreaResolver(ServerLevel level, RecruitsClaim claim) {
            this.level = level;
            this.claim = claim;
        }

        @Override
        public Optional<BuildAreaBinding> resolveCandidate(UUID claimUuid, PendingProject project) {
            if (level == null || claim == null || claimUuid == null || !claimUuid.equals(claim.getUUID())) {
                return Optional.empty();
            }
            return collectBuildAreas(level, claim).stream()
                    .filter(buildArea -> !buildArea.isDone())
                    .filter(BuildArea::hasStructureTemplate)
                    .min(Comparator.comparing(BuildArea::getUUID))
                    .map(buildArea -> new BuildAreaBinding(
                            buildArea.getUUID(),
                            true,
                            project == null ? 0 : project.estimatedTickCost()
                    ));
        }
    }

    /** Resolver that looks up the claim in the active claim manager before scanning BuildAreas. */
    public static final class ClaimManagerBuildAreaResolver implements BuildAreaResolver {
        private final ServerLevel level;
        private final RecruitsClaimManager claimManager;

        public ClaimManagerBuildAreaResolver(ServerLevel level, RecruitsClaimManager claimManager) {
            this.level = level;
            this.claimManager = claimManager;
        }

        @Override
        public Optional<BuildAreaBinding> resolveCandidate(UUID claimUuid, PendingProject project) {
            if (claimManager == null || claimUuid == null) {
                return Optional.empty();
            }
            for (RecruitsClaim claim : claimManager.getAllClaims()) {
                if (claim != null && claimUuid.equals(claim.getUUID())) {
                    return new ClaimBuildAreaResolver(level, claim).resolveCandidate(claimUuid, project);
                }
            }
            return Optional.empty();
        }
    }

    /**
     * Inspect the next {@link PendingProject} for {@code claimUuid}, try to resolve a BuildArea
     * for it, and return a fresh {@link ProjectAssignment} bound to that BuildArea.
     *
     * <p>If the resolver cannot bind a BuildArea the queue is left unchanged and
     * {@link Optional#empty()} is returned so the caller can retry next tick.
     */
    public Optional<ProjectAssignment> attemptAssignment(
            SettlementProjectScheduler scheduler,
            UUID claimUuid,
            long gameTime,
            BuildAreaResolver resolver
    ) {
        if (scheduler == null || claimUuid == null || resolver == null) {
            return Optional.empty();
        }
        Optional<PendingProject> head = scheduler.peek(claimUuid);
        if (head.isEmpty()) {
            return Optional.empty();
        }
        PendingProject project = head.get();
        Optional<BuildAreaBinding> binding;
        try {
            binding = resolver.resolveCandidate(claimUuid, project);
        } catch (RuntimeException resolverError) {
            // Resolver failures must not drop the project.
            throw resolverError;
        }
        if (binding.isEmpty()) {
            return Optional.empty();
        }
        scheduler.pollNext(claimUuid);
        BuildAreaBinding resolved = binding.get();
        AssignmentPhase phase = resolved.hasTemplateLoaded()
                ? AssignmentPhase.SEARCHING_BUILDER
                : AssignmentPhase.MATERIALS_PENDING;
        return Optional.of(new ProjectAssignment(
                project.projectId(),
                claimUuid,
                resolved.buildAreaUuid(),
                project,
                gameTime,
                phase
        ));
    }

    static List<BuildArea> collectBuildAreas(ServerLevel level, RecruitsClaim claim) {
        WorkAreaIndex index = WorkAreaIndex.instance();
        if (index.sizeFor(level.dimension()) > 0) {
            return index.queryInChunks(level, claim.getClaimedChunks(), BuildArea.class).stream()
                    .filter(buildArea -> claim.containsChunk(buildArea.chunkPosition()))
                    .toList();
        }
        return level.getEntitiesOfClass(BuildArea.class, claimBounds(level, claim),
                buildArea -> buildArea.isAlive() && claim.containsChunk(buildArea.chunkPosition()));
    }

    private static AABB claimBounds(ServerLevel level, RecruitsClaim claim) {
        ChunkPos anchor = claim.getCenter() == null
                ? claim.getClaimedChunks().stream().findFirst().orElse(new ChunkPos(0, 0))
                : claim.getCenter();
        int minChunkX = claim.getClaimedChunks().stream().mapToInt(chunkPos -> chunkPos.x).min().orElse(anchor.x);
        int maxChunkX = claim.getClaimedChunks().stream().mapToInt(chunkPos -> chunkPos.x).max().orElse(anchor.x);
        int minChunkZ = claim.getClaimedChunks().stream().mapToInt(chunkPos -> chunkPos.z).min().orElse(anchor.z);
        int maxChunkZ = claim.getClaimedChunks().stream().mapToInt(chunkPos -> chunkPos.z).max().orElse(anchor.z);
        return new AABB(
                minChunkX * 16.0D,
                level.getMinBuildHeight(),
                minChunkZ * 16.0D,
                (maxChunkX + 1) * 16.0D,
                level.getMaxBuildHeight(),
                (maxChunkZ + 1) * 16.0D
        );
    }
}
