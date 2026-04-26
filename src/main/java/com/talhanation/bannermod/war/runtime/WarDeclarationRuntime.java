package com.talhanation.bannermod.war.runtime;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class WarDeclarationRuntime {
    private final Map<UUID, WarDeclarationRecord> warsById = new LinkedHashMap<>();
    private Runnable dirtyListener = () -> { };

    public void setDirtyListener(Runnable dirtyListener) {
        this.dirtyListener = dirtyListener == null ? () -> { } : dirtyListener;
    }

    public Optional<WarDeclarationRecord> declareWar(UUID attackerPoliticalEntityId,
                                                     UUID defenderPoliticalEntityId,
                                                     WarGoalType goalType,
                                                     String casusBelli,
                                                     List<BlockPos> targetPositions,
                                                     List<UUID> attackerAllyIds,
                                                     List<UUID> defenderAllyIds,
                                                     long declaredAtGameTime,
                                                     long minimumDelayTicks) {
        if (attackerPoliticalEntityId == null
                || defenderPoliticalEntityId == null
                || attackerPoliticalEntityId.equals(defenderPoliticalEntityId)) {
            return Optional.empty();
        }
        WarDeclarationRecord record = new WarDeclarationRecord(
                UUID.randomUUID(),
                attackerPoliticalEntityId,
                defenderPoliticalEntityId,
                goalType,
                casusBelli,
                targetPositions,
                attackerAllyIds,
                defenderAllyIds,
                declaredAtGameTime,
                declaredAtGameTime + Math.max(0L, minimumDelayTicks),
                WarState.DECLARED
        );
        warsById.put(record.id(), record);
        dirtyListener.run();
        return Optional.of(record);
    }

    public Optional<WarDeclarationRecord> byId(UUID id) {
        return Optional.ofNullable(warsById.get(id));
    }

    public Optional<WarDeclarationRecord> byIdFragment(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            return byId(UUID.fromString(token));
        } catch (IllegalArgumentException ignored) {
            String lower = token.toLowerCase(java.util.Locale.ROOT);
            for (WarDeclarationRecord war : warsById.values()) {
                if (war.id().toString().startsWith(lower)) {
                    return Optional.of(war);
                }
            }
            return Optional.empty();
        }
    }

    public Collection<WarDeclarationRecord> all() {
        return List.copyOf(warsById.values());
    }

    public Collection<WarDeclarationRecord> activeOrDeclared() {
        return warsById.values().stream()
                .filter(war -> war.state().allowsBattleWindowActivation())
                .toList();
    }

    public boolean updateState(UUID id, WarState state) {
        WarDeclarationRecord record = warsById.get(id);
        if (record == null || state == null) {
            return false;
        }
        warsById.put(id, record.withState(state));
        dirtyListener.run();
        return true;
    }

    public Optional<WarDeclarationRecord> appendAlly(UUID warId, WarSide side, UUID allyEntityId) {
        if (warId == null || side == null || allyEntityId == null) return Optional.empty();
        WarDeclarationRecord record = warsById.get(warId);
        if (record == null) return Optional.empty();
        List<UUID> existing = record.alliesFor(side);
        if (existing.contains(allyEntityId)) return Optional.of(record);
        List<UUID> updated = new java.util.ArrayList<>(existing);
        updated.add(allyEntityId);
        WarDeclarationRecord next = side == WarSide.ATTACKER
                ? record.withAttackerAllyIds(updated)
                : record.withDefenderAllyIds(updated);
        warsById.put(warId, next);
        dirtyListener.run();
        return Optional.of(next);
    }

    public Optional<WarDeclarationRecord> removeAlly(UUID warId, WarSide side, UUID allyEntityId) {
        if (warId == null || side == null || allyEntityId == null) return Optional.empty();
        WarDeclarationRecord record = warsById.get(warId);
        if (record == null) return Optional.empty();
        List<UUID> existing = record.alliesFor(side);
        if (!existing.contains(allyEntityId)) return Optional.of(record);
        List<UUID> updated = new java.util.ArrayList<>(existing);
        updated.remove(allyEntityId);
        WarDeclarationRecord next = side == WarSide.ATTACKER
                ? record.withAttackerAllyIds(updated)
                : record.withDefenderAllyIds(updated);
        warsById.put(warId, next);
        dirtyListener.run();
        return Optional.of(next);
    }

    public boolean hasRecentWarBetween(UUID first, UUID second, long nowGameTime, long cooldownTicks) {
        if (first == null || second == null) {
            return false;
        }
        long minTime = nowGameTime - Math.max(0L, cooldownTicks);
        for (WarDeclarationRecord war : warsById.values()) {
            boolean samePair = (war.attackerPoliticalEntityId().equals(first) && war.defenderPoliticalEntityId().equals(second))
                    || (war.attackerPoliticalEntityId().equals(second) && war.defenderPoliticalEntityId().equals(first));
            if (samePair && war.declaredAtGameTime() >= minTime) {
                return true;
            }
        }
        return false;
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        ListTag wars = new ListTag();
        for (WarDeclarationRecord war : warsById.values()) {
            wars.add(war.toTag());
        }
        tag.put("Wars", wars);
        return tag;
    }

    public static WarDeclarationRuntime fromTag(CompoundTag tag) {
        WarDeclarationRuntime runtime = new WarDeclarationRuntime();
        ListTag wars = tag.getList("Wars", Tag.TAG_COMPOUND);
        for (int i = 0; i < wars.size(); i++) {
            WarDeclarationRecord war = WarDeclarationRecord.fromTag(wars.getCompound(i));
            runtime.warsById.put(war.id(), war);
        }
        return runtime;
    }
}
