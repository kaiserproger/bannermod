package com.talhanation.bannermod.army.map;

import com.talhanation.bannermod.army.command.CommandHierarchy;
import com.talhanation.bannermod.army.command.CommandRole;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.entity.military.IStrategicFire;
import com.talhanation.bannermod.entity.military.RecruitPoliticalContext;
import com.talhanation.bannermod.entity.military.RecruitIndex;
import com.talhanation.bannermod.util.AdaptiveRuntimeBudgets;
import com.talhanation.bannermod.util.RuntimeProfilingCounters;
import com.talhanation.bannermod.war.WarRuntimeContext;
import com.talhanation.bannermod.war.registry.PoliticalRelations;
import com.talhanation.bannermod.war.registry.PoliticalRegistryRuntime;
import com.talhanation.bannermod.war.runtime.WarDeclarationRuntime;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FormationMapSnapshotService {
    private static final int CACHE_WINDOW_TICKS = 5;
    private static final int THROTTLE_TICKS = 1;
    private static final String COUNTER_PREFIX = "formation_map.snapshot";
    private static final Map<CacheKey, CacheEntry> CACHE = new ConcurrentHashMap<>();
    private static final Map<CacheKey, Long> LAST_REQUEST_TICK = new ConcurrentHashMap<>();

    private FormationMapSnapshotService() {
    }

    public static SnapshotRequestResult requestSnapshot(ServerPlayer viewer) {
        RuntimeProfilingCounters.increment(COUNTER_PREFIX + ".requests");
        if (viewer == null) return SnapshotRequestResult.throttledResult();
        ServerLevel level = viewer.serverLevel();
        CacheKey key = new CacheKey(viewer.getUUID(), level.dimension());
        long gameTime = level.getGameTime();
        int throttleTicks = AdaptiveRuntimeBudgets.throttleTicks(
                "formation_map.snapshot_throttle",
                THROTTLE_TICKS,
                THROTTLE_TICKS * 4
        );
        Long lastRequestTick = LAST_REQUEST_TICK.put(key, gameTime);
        if (lastRequestTick != null && gameTime - lastRequestTick < throttleTicks) {
            RuntimeProfilingCounters.increment(COUNTER_PREFIX + ".throttles");
            return SnapshotRequestResult.throttledResult();
        }

        long cacheWindow = gameTime / CACHE_WINDOW_TICKS;
        long recruitVersion = RecruitIndex.instance().version(level);
        CacheEntry cached = CACHE.get(key);
        if (cached != null && cached.cacheWindow == cacheWindow && cached.recruitVersion == recruitVersion) {
            RuntimeProfilingCounters.increment(COUNTER_PREFIX + ".cache_hits");
            RuntimeProfilingCounters.add(COUNTER_PREFIX + ".emitted_contacts", cached.contacts.size());
            return SnapshotRequestResult.contactsResult(cached.contacts);
        }

        List<FormationMapContact> contacts = buildSnapshot(viewer);
        List<FormationMapContact> immutableContacts = List.copyOf(contacts);
        CACHE.put(key, new CacheEntry(cacheWindow, recruitVersion, immutableContacts));
        RuntimeProfilingCounters.add(COUNTER_PREFIX + ".emitted_contacts", immutableContacts.size());
        return SnapshotRequestResult.contactsResult(immutableContacts);
    }

    public static List<FormationMapContact> buildSnapshot(ServerPlayer viewer) {
        if (viewer == null) return List.of();
        int viewDistanceBlocks = Math.max(16, viewer.server.getPlayerList().getViewDistance() * 16);
        double viewDistanceSqr = (double) viewDistanceBlocks * (double) viewDistanceBlocks;

        ServerLevel level = viewer.serverLevel();
        List<AbstractRecruitEntity> recruits = RecruitIndex.instance().all(level, true);
        if (recruits == null) {
            recruits = level.getEntitiesOfClass(
                    AbstractRecruitEntity.class,
                    new AABB(viewer.blockPosition()).inflate(viewDistanceBlocks)
            );
        }
        RuntimeProfilingCounters.add(COUNTER_PREFIX + ".recruit_candidates", recruits.size());

        RelationContext relationContext = RelationContext.forViewer(viewer, level);
        Map<UUID, Bucket> buckets = new LinkedHashMap<>();
        for (AbstractRecruitEntity recruit : recruits) {
            if (recruit == null || !recruit.isAlive() || recruit.distanceToSqr(viewer) > viewDistanceSqr) continue;
            CommandRole role = CommandHierarchy.roleFor(viewer, recruit);
            FormationMapRelation relation = relationFor(recruit, role, relationContext);
            boolean subordinate = relation == FormationMapRelation.SUBORDINATE;
            boolean visible = subordinate || FormationMapVisibilityPolicy.canRevealContact(viewer, recruit, viewDistanceSqr);
            if (!visible) continue;

            UUID groupId = recruit.getGroup();
            UUID contactId = groupId == null ? recruit.getUUID() : groupId;
            Bucket bucket = buckets.computeIfAbsent(contactId, ignored -> new Bucket(contactId, groupId, recruit));
            bucket.add(recruit, role, relation, subordinate || visible);
        }

        List<FormationMapContact> contacts = new ArrayList<>(buckets.size());
        for (Bucket bucket : buckets.values()) {
            contacts.add(bucket.toContact());
        }
        return contacts;
    }

    private static FormationMapRelation relationFor(AbstractRecruitEntity recruit, CommandRole role, RelationContext context) {
        if (role != CommandRole.NONE) return FormationMapRelation.SUBORDINATE;
        String recruitTeam = recruit.getTeam() == null ? null : recruit.getTeam().getName();
        if (context.viewerTeam != null && context.viewerTeam.equals(recruitTeam)) return FormationMapRelation.FRIENDLY;
        UUID recruitPoliticalEntityId = RecruitPoliticalContext.politicalEntityIdOf(recruit, context.registry);
        if (PoliticalRelations.atWar(context.declarations, context.viewerPoliticalEntityId, recruitPoliticalEntityId)) return FormationMapRelation.HOSTILE;
        if (PoliticalRelations.ally(context.registry, context.viewerPoliticalEntityId, recruitPoliticalEntityId)) return FormationMapRelation.FRIENDLY;
        return FormationMapRelation.NEUTRAL;
    }

    public record SnapshotRequestResult(boolean throttled, List<FormationMapContact> contacts) {
        private static SnapshotRequestResult throttledResult() {
            return new SnapshotRequestResult(true, List.of());
        }

        private static SnapshotRequestResult contactsResult(List<FormationMapContact> contacts) {
            return new SnapshotRequestResult(false, contacts == null ? List.of() : contacts);
        }
    }

    private record CacheKey(UUID viewerUuid, ResourceKey<Level> dimension) {
    }

    private record CacheEntry(long cacheWindow, long recruitVersion, List<FormationMapContact> contacts) {
    }

    private record RelationContext(String viewerTeam,
                                   UUID viewerPoliticalEntityId,
                                   PoliticalRegistryRuntime registry,
                                   WarDeclarationRuntime declarations) {
        private static RelationContext forViewer(ServerPlayer viewer, ServerLevel level) {
            PoliticalRegistryRuntime registry = WarRuntimeContext.registry(level);
            return new RelationContext(
                    viewer.getTeam() == null ? null : viewer.getTeam().getName(),
                    RecruitPoliticalContext.politicalEntityIdOf(viewer, registry),
                    registry,
                    WarRuntimeContext.declarations(level)
            );
        }
    }

    private static final class Bucket {
        private final UUID contactId;
        private final UUID groupId;
        private final String teamId;
        private UUID leaderId;
        private FormationMapRelation relation = FormationMapRelation.NEUTRAL;
        private CommandRole commandRole = CommandRole.NONE;
        private double x;
        private double y;
        private double z;
        private int unitCount;
        private int visibleUnitCount;
        private int rangedUnitCount;

        private Bucket(UUID contactId, UUID groupId, AbstractRecruitEntity first) {
            this.contactId = contactId;
            this.groupId = groupId;
            this.teamId = first.getTeam() == null ? null : first.getTeam().getName();
        }

        private void add(AbstractRecruitEntity recruit, CommandRole role, FormationMapRelation newRelation, boolean visible) {
            unitCount++;
            if (visible) visibleUnitCount++;
            if (recruit instanceof IStrategicFire) rangedUnitCount++;
            if (leaderId == null) leaderId = recruit.getUUID();
            if (role.ordinal() > commandRole.ordinal()) commandRole = role;
            if (relationPriority(newRelation) > relationPriority(relation)) relation = newRelation;
            x += recruit.getX();
            y += recruit.getY();
            z += recruit.getZ();
        }

        private FormationMapContact toContact() {
            int divisor = Math.max(1, unitCount);
            return new FormationMapContact(
                    contactId,
                    groupId,
                    leaderId,
                    teamId,
                    relation,
                    commandRole,
                    x / divisor,
                    y / divisor,
                    z / divisor,
                    unitCount,
                    visibleUnitCount,
                    rangedUnitCount
            );
        }

        private static int relationPriority(FormationMapRelation relation) {
            return switch (relation) {
                case SUBORDINATE -> 4;
                case HOSTILE -> 3;
                case FRIENDLY -> 2;
                case NEUTRAL -> 1;
            };
        }
    }
}
