package com.talhanation.bannermod.war.runtime;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Immutable declaration record. Runtime services decide when it becomes active. */
public record WarDeclarationRecord(
        UUID id,
        UUID attackerPoliticalEntityId,
        UUID defenderPoliticalEntityId,
        WarGoalType goalType,
        String casusBelli,
        List<BlockPos> targetPositions,
        List<UUID> attackerAllyIds,
        List<UUID> defenderAllyIds,
        long declaredAtGameTime,
        long earliestActivationGameTime,
        WarState state
) {
    public WarDeclarationRecord {
        goalType = goalType == null ? WarGoalType.WHITE_PEACE : goalType;
        state = state == null ? WarState.DECLARED : state;
        casusBelli = casusBelli == null ? "" : casusBelli.trim();
        targetPositions = targetPositions == null ? List.of() : List.copyOf(targetPositions);
        attackerAllyIds = attackerAllyIds == null ? List.of() : List.copyOf(attackerAllyIds);
        defenderAllyIds = defenderAllyIds == null ? List.of() : List.copyOf(defenderAllyIds);
    }

    public WarDeclarationRecord withState(WarState newState) {
        return new WarDeclarationRecord(id, attackerPoliticalEntityId, defenderPoliticalEntityId, goalType,
                casusBelli, targetPositions, attackerAllyIds, defenderAllyIds,
                declaredAtGameTime, earliestActivationGameTime, newState);
    }

    public WarDeclarationRecord withAttackerAllyIds(List<UUID> newAllies) {
        return new WarDeclarationRecord(id, attackerPoliticalEntityId, defenderPoliticalEntityId, goalType,
                casusBelli, targetPositions, newAllies, defenderAllyIds,
                declaredAtGameTime, earliestActivationGameTime, state);
    }

    public WarDeclarationRecord withDefenderAllyIds(List<UUID> newAllies) {
        return new WarDeclarationRecord(id, attackerPoliticalEntityId, defenderPoliticalEntityId, goalType,
                casusBelli, targetPositions, attackerAllyIds, newAllies,
                declaredAtGameTime, earliestActivationGameTime, state);
    }

    public List<UUID> alliesFor(WarSide side) {
        return side == WarSide.ATTACKER ? attackerAllyIds : defenderAllyIds;
    }

    public UUID mainSideEntityId(WarSide side) {
        return side == WarSide.ATTACKER ? attackerPoliticalEntityId : defenderPoliticalEntityId;
    }

    public boolean involves(UUID politicalEntityId) {
        return politicalEntityId != null
                && (politicalEntityId.equals(attackerPoliticalEntityId)
                || politicalEntityId.equals(defenderPoliticalEntityId)
                || attackerAllyIds.contains(politicalEntityId)
                || defenderAllyIds.contains(politicalEntityId));
    }

    public boolean opposingSides(UUID first, UUID second) {
        if (first == null || second == null) {
            return false;
        }
        boolean firstAttacker = first.equals(attackerPoliticalEntityId) || attackerAllyIds.contains(first);
        boolean firstDefender = first.equals(defenderPoliticalEntityId) || defenderAllyIds.contains(first);
        boolean secondAttacker = second.equals(attackerPoliticalEntityId) || attackerAllyIds.contains(second);
        boolean secondDefender = second.equals(defenderPoliticalEntityId) || defenderAllyIds.contains(second);
        return (firstAttacker && secondDefender) || (firstDefender && secondAttacker);
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putUUID("Attacker", attackerPoliticalEntityId);
        tag.putUUID("Defender", defenderPoliticalEntityId);
        tag.putString("Goal", goalType.name());
        tag.putString("CasusBelli", casusBelli);
        tag.putLong("DeclaredAtGameTime", declaredAtGameTime);
        tag.putLong("EarliestActivationGameTime", earliestActivationGameTime);
        tag.putString("State", state.name());

        ListTag targets = new ListTag();
        for (BlockPos target : targetPositions) {
            CompoundTag targetTag = new CompoundTag();
            targetTag.putInt("X", target.getX());
            targetTag.putInt("Y", target.getY());
            targetTag.putInt("Z", target.getZ());
            targets.add(targetTag);
        }
        tag.put("Targets", targets);

        tag.put("AttackerAllies", uuidListToTag(attackerAllyIds));
        tag.put("DefenderAllies", uuidListToTag(defenderAllyIds));
        return tag;
    }

    public static WarDeclarationRecord fromTag(CompoundTag tag) {
        return new WarDeclarationRecord(
                tag.getUUID("Id"),
                tag.getUUID("Attacker"),
                tag.getUUID("Defender"),
                readEnum(WarGoalType.class, tag.getString("Goal"), WarGoalType.WHITE_PEACE),
                tag.getString("CasusBelli"),
                blockPosListFromTag(tag.getList("Targets", Tag.TAG_COMPOUND)),
                uuidListFromTag(tag.getList("AttackerAllies", Tag.TAG_COMPOUND)),
                uuidListFromTag(tag.getList("DefenderAllies", Tag.TAG_COMPOUND)),
                tag.getLong("DeclaredAtGameTime"),
                tag.getLong("EarliestActivationGameTime"),
                readEnum(WarState.class, tag.getString("State"), WarState.DECLARED)
        );
    }

    private static ListTag uuidListToTag(List<UUID> uuids) {
        ListTag list = new ListTag();
        for (UUID uuid : uuids) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Uuid", uuid);
            list.add(tag);
        }
        return list;
    }

    private static List<UUID> uuidListFromTag(ListTag tags) {
        List<UUID> uuids = new ArrayList<>();
        for (int i = 0; i < tags.size(); i++) {
            CompoundTag tag = tags.getCompound(i);
            if (tag.hasUUID("Uuid")) {
                uuids.add(tag.getUUID("Uuid"));
            }
        }
        return uuids;
    }

    private static List<BlockPos> blockPosListFromTag(ListTag tags) {
        List<BlockPos> positions = new ArrayList<>();
        for (int i = 0; i < tags.size(); i++) {
            CompoundTag tag = tags.getCompound(i);
            positions.add(new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z")));
        }
        return positions;
    }

    private static <E extends Enum<E>> E readEnum(Class<E> enumClass, String value, E fallback) {
        try {
            return Enum.valueOf(enumClass, value);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
