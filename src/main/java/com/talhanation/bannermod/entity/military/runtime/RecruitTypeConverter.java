package com.talhanation.bannermod.entity.military.runtime;

import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.entity.military.BowmanEntity;
import com.talhanation.bannermod.entity.military.CrossBowmanEntity;
import com.talhanation.bannermod.entity.military.HorsemanEntity;
import com.talhanation.bannermod.entity.military.RecruitEntity;
import com.talhanation.bannermod.entity.military.RecruitShieldmanEntity;
import com.talhanation.bannermod.registry.military.ModEntityTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

/**
 * Converts a base recruit (swordsman / bowman / pikeman / crossbowman / cavalry) into another
 * base recruit type while preserving owner, group, XP, level, kills, morale, hunger, inventory,
 * and command state.
 *
 * <p>Distinct from the promote pipeline (see {@code RecruitEvents#promoteRecruit}) which handles
 * companion-tier upgrades (messenger / scout / patrol-leader / governor). The promote pipeline
 * relies on the {@code ICompanion} interface; the base recruit classes do not implement it, so
 * this helper does an equivalent state copy without that contract.
 */
public final class RecruitTypeConverter {

    public enum Kind {
        SWORDSMAN,
        BOWMAN,
        PIKEMAN,
        CROSSBOWMAN,
        CAVALRY;

        public static Kind ofOrdinal(int ordinal) {
            Kind[] values = values();
            return ordinal < 0 || ordinal >= values.length ? SWORDSMAN : values[ordinal];
        }
    }

    private RecruitTypeConverter() {
    }

    /**
     * Replace {@code source} with a fresh entity of {@code kind}'s type at the same position,
     * carrying over all owner/group/progression/inventory state. The source entity is discarded.
     *
     * @return {@code true} if the conversion produced a new entity, {@code false} on no-op (same
     *         type already, or world isn't a ServerLevel).
     */
    public static boolean convert(AbstractRecruitEntity source, Kind kind, @Nullable ServerPlayer requester) {
        if (source == null || kind == null) return false;
        if (!(source.getCommandSenderWorld() instanceof ServerLevel serverLevel)) return false;

        EntityType<? extends AbstractRecruitEntity> targetType = entityTypeFor(kind);
        if (targetType == null) {
            if (requester != null) {
                requester.sendSystemMessage(Component.translatable("chat.bannermod.convert.unsupported_kind"));
            }
            return false;
        }
        if (targetType == source.getType()) {
            // already that type — surface a chat hint so the player isn't left wondering why
            // the button "did nothing".
            if (requester != null) {
                requester.sendSystemMessage(Component.translatable("chat.bannermod.convert.already_same"));
            }
            return false;
        }

        AbstractRecruitEntity target = targetType.create(serverLevel);
        if (target == null) return false;

        target.copyPosition(source);
        copyState(source, target);

        source.discard();
        serverLevel.addFreshEntity(target);
        return true;
    }

    @Nullable
    public static EntityType<? extends AbstractRecruitEntity> entityTypeFor(Kind kind) {
        return switch (kind) {
            case SWORDSMAN -> ModEntityTypes.RECRUIT.get();
            case BOWMAN -> ModEntityTypes.BOWMAN.get();
            case PIKEMAN -> ModEntityTypes.RECRUIT_SHIELDMAN.get();
            case CROSSBOWMAN -> ModEntityTypes.CROSSBOWMAN.get();
            case CAVALRY -> ModEntityTypes.HORSEMAN.get();
        };
    }

    /**
     * Replicates {@code ICompanion#applyRecruitValues} for recruits that don't implement that
     * interface. Touches everything that survives the entity swap: attributes, owner/team,
     * progression (xp/level/kills/morale), command state, biome, and inventory.
     */
    private static void copyState(AbstractRecruitEntity source, AbstractRecruitEntity target) {
        // ATTRIBUTES — copies any modifiers RecruitProgressionService applied for the level system.
        target.getAttributes().assignBaseValues(source.getAttributes());

        target.setHunger(source.getHunger());
        target.setVariant(source.getVariant());

        if (source.getOwnerUUID() != null) {
            target.setOwnerUUID(Optional.of(source.getOwnerUUID()));
        }
        UUID upkeepUUID = source.getUpkeepUUID();
        if (upkeepUUID != null) target.setUpkeepUUID(Optional.of(upkeepUUID));
        if (source.getUpkeepPos() != null) target.setUpkeepPos(source.getUpkeepPos());

        target.updateTeam();
        target.setIsOwned(source.isOwned());

        if (source.getHoldPos() != null) target.setHoldPos(source.getHoldPos());
        if (source.getMovePos() != null) target.setMovePos(source.getMovePos());

        target.setGroupUUID(source.getGroup());
        target.setKills(source.getKills());
        target.setXp(source.getXp());
        target.setXpLevel(source.getXpLevel());
        target.setAggroState(source.getState());
        target.setFollowState(source.getFollowState());
        target.setListen(source.getListen());
        target.setBiome((byte) source.getBiome());

        // INVENTORY — equipment slots 0..5 + storage slots 6..end. Mirrors applyRecruitValues.
        for (int i = 0; i < source.getInventory().getContainerSize(); i++) {
            ItemStack stack = source.getInventory().getItem(i);
            if (i > 5) {
                target.getInventory().setItem(i, stack);
            } else {
                target.setItemSlot(target.getEquipmentSlotIndex(i), stack);
            }
        }

        // Morale: copy directly without the +20 promote bonus — this is a sideways type swap,
        // not a promotion, so we shouldn't manufacture morale. Same heal-on-promote effect is
        // also intentionally skipped.
        target.setMoral(source.getMorale());

        if (source.getCustomName() != null) {
            target.setCustomName(source.getCustomName());
        }
    }

    /** Reuse-friendly check used by client UI to decide whether to show the Convert action. */
    public static boolean isConvertibleBaseType(AbstractRecruitEntity recruit) {
        if (recruit == null) return false;
        Class<?> cls = recruit.getClass();
        return cls == RecruitEntity.class
                || cls == BowmanEntity.class
                || cls == RecruitShieldmanEntity.class
                || cls == CrossBowmanEntity.class
                || cls == HorsemanEntity.class;
    }
}
