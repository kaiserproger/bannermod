package com.talhanation.bannermod.war.client;

import com.talhanation.bannermod.persistence.military.RecruitsClaim;
import com.talhanation.bannermod.war.registry.PoliticalEntityRecord;
import com.talhanation.bannermod.war.runtime.BattleWindowSchedule;
import com.talhanation.bannermod.war.runtime.SiegeStandardRecord;
import com.talhanation.bannermod.war.runtime.WarAllyInviteRecord;
import com.talhanation.bannermod.war.runtime.WarDeclarationRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Read-only client-side mirror of warfare-RP state synced from the server.
 *
 * <p>The server pushes a snapshot of {@link PoliticalEntityRecord},
 * {@link WarDeclarationRecord}, and {@link SiegeStandardRecord} collections whenever the
 * underlying {@code SavedData} changes. The mirror stays small (a few KB even with many
 * wars), so it's fully replaced on each update rather than diffed.
 */
public final class WarClientState {
    public static final String NBT_ENTITIES = "Entities";
    public static final String NBT_WARS = "Wars";
    public static final String NBT_SIEGES = "Sieges";
    public static final String NBT_SCHEDULE = "Schedule";
    public static final String NBT_ALLY_INVITES = "AllyInvites";

    private static List<PoliticalEntityRecord> entities = List.of();
    private static List<WarDeclarationRecord> wars = List.of();
    private static List<SiegeStandardRecord> sieges = List.of();
    private static BattleWindowSchedule schedule = new BattleWindowSchedule(List.of());
    private static List<WarAllyInviteRecord> allyInvites = List.of();
    private static Map<UUID, PoliticalEntityRecord> entitiesById = Map.of();
    private static int version = 0;

    private WarClientState() {
    }

    public static List<PoliticalEntityRecord> entities() {
        return entities;
    }

    public static List<WarDeclarationRecord> wars() {
        return wars;
    }

    public static List<SiegeStandardRecord> sieges() {
        return sieges;
    }

    public static BattleWindowSchedule schedule() {
        return schedule;
    }

    public static List<WarAllyInviteRecord> allyInvites() {
        return allyInvites;
    }

    public static List<WarAllyInviteRecord> allyInvitesForWar(UUID warId) {
        if (warId == null) return List.of();
        List<WarAllyInviteRecord> matches = new ArrayList<>();
        for (WarAllyInviteRecord invite : allyInvites) {
            if (warId.equals(invite.warId())) matches.add(invite);
        }
        return matches;
    }

    public static PoliticalEntityRecord entityById(UUID id) {
        return entitiesById.get(id);
    }

    public static int version() {
        return version;
    }

    public static void clear() {
        entities = List.of();
        wars = List.of();
        sieges = List.of();
        schedule = new BattleWindowSchedule(List.of());
        allyInvites = List.of();
        entitiesById = Map.of();
        version++;
    }

    public static void applyFromNbt(CompoundTag tag) {
        if (tag == null) {
            clear();
            return;
        }
        entities = decodeEntities(tag.getList(NBT_ENTITIES, Tag.TAG_COMPOUND));
        wars = decodeWars(tag.getList(NBT_WARS, Tag.TAG_COMPOUND));
        sieges = decodeSieges(tag.getList(NBT_SIEGES, Tag.TAG_COMPOUND));
        schedule = BattleWindowSchedule.fromListTag(tag.getList(NBT_SCHEDULE, Tag.TAG_COMPOUND));
        allyInvites = decodeAllyInvites(tag.getList(NBT_ALLY_INVITES, Tag.TAG_COMPOUND));
        Map<UUID, PoliticalEntityRecord> byId = new HashMap<>();
        for (PoliticalEntityRecord entity : entities) {
            byId.put(entity.id(), entity);
        }
        entitiesById = Map.copyOf(byId);
        version++;
    }

    public static CompoundTag encode(Iterable<PoliticalEntityRecord> entitySrc,
                                     Iterable<WarDeclarationRecord> warSrc,
                                     Iterable<SiegeStandardRecord> siegeSrc,
                                     BattleWindowSchedule scheduleSrc,
                                     Iterable<WarAllyInviteRecord> allyInviteSrc) {
        CompoundTag tag = new CompoundTag();
        ListTag entitiesTag = new ListTag();
        for (PoliticalEntityRecord entity : entitySrc) entitiesTag.add(entity.toTag());
        ListTag warsTag = new ListTag();
        for (WarDeclarationRecord war : warSrc) warsTag.add(war.toTag());
        ListTag siegesTag = new ListTag();
        for (SiegeStandardRecord siege : siegeSrc) siegesTag.add(siege.toTag());
        tag.put(NBT_ENTITIES, entitiesTag);
        tag.put(NBT_WARS, warsTag);
        tag.put(NBT_SIEGES, siegesTag);
        if (scheduleSrc != null) {
            tag.put(NBT_SCHEDULE, scheduleSrc.toListTag());
        }
        if (allyInviteSrc != null) {
            ListTag invitesTag = new ListTag();
            for (WarAllyInviteRecord invite : allyInviteSrc) {
                if (invite != null) invitesTag.add(invite.toTag());
            }
            tag.put(NBT_ALLY_INVITES, invitesTag);
        }
        return tag;
    }

    /** Pure-logic mirror of {@code WarSiegeQueries.isClaimUnderSiege} for client overlays. */
    public static boolean isClaimUnderSiege(RecruitsClaim claim) {
        if (claim == null) return false;
        Set<UUID> activeWarIds = new HashSet<>();
        for (WarDeclarationRecord war : wars) {
            if (war.state() != null && war.state().allowsBattleWindowActivation()) {
                activeWarIds.add(war.id());
            }
        }
        if (activeWarIds.isEmpty()) return false;
        for (SiegeStandardRecord siege : sieges) {
            if (!activeWarIds.contains(siege.warId())) continue;
            BlockPos pos = siege.pos();
            if (pos == null) continue;
            if (claim.containsChunk(new ChunkPos(pos))) {
                return true;
            }
        }
        return false;
    }

    private static List<PoliticalEntityRecord> decodeEntities(ListTag list) {
        List<PoliticalEntityRecord> result = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            result.add(PoliticalEntityRecord.fromTag(list.getCompound(i)));
        }
        return List.copyOf(result);
    }

    private static List<WarDeclarationRecord> decodeWars(ListTag list) {
        List<WarDeclarationRecord> result = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            result.add(WarDeclarationRecord.fromTag(list.getCompound(i)));
        }
        return List.copyOf(result);
    }

    private static List<SiegeStandardRecord> decodeSieges(ListTag list) {
        List<SiegeStandardRecord> result = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            result.add(SiegeStandardRecord.fromTag(list.getCompound(i)));
        }
        return List.copyOf(result);
    }

    private static List<WarAllyInviteRecord> decodeAllyInvites(ListTag list) {
        if (list == null || list.isEmpty()) return List.of();
        List<WarAllyInviteRecord> result = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            WarAllyInviteRecord record = WarAllyInviteRecord.fromTag(list.getCompound(i));
            if (record != null) result.add(record);
        }
        return List.copyOf(result);
    }
}
