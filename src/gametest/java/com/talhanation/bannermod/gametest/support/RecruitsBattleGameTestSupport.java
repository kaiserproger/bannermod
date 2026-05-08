package com.talhanation.bannermod.gametest.support;

import com.talhanation.bannermod.entity.military.runtime.RecruitEvents;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.entity.military.BowmanEntity;
import com.talhanation.bannermod.entity.military.CrossBowmanEntity;
import com.talhanation.bannermod.entity.military.RecruitEntity;
import com.talhanation.bannermod.entity.military.RecruitShieldmanEntity;
import com.talhanation.bannermod.registry.military.ModEntityTypes;
import com.talhanation.bannermod.persistence.military.RecruitsGroup;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class RecruitsBattleGameTestSupport {
    public static final BlockPos WEST_FRONTLINE_POS = new BlockPos(2, 2, 4);
    public static final BlockPos WEST_FLANK_POS = new BlockPos(2, 2, 6);
    public static final BlockPos WEST_RANGED_LEFT_POS = new BlockPos(4, 2, 3);
    public static final BlockPos WEST_RANGED_RIGHT_POS = new BlockPos(4, 2, 7);
    public static final BlockPos EAST_FRONTLINE_POS = new BlockPos(12, 2, 4);
    public static final BlockPos EAST_FLANK_POS = new BlockPos(12, 2, 6);
    public static final BlockPos EAST_RANGED_LEFT_POS = new BlockPos(10, 2, 3);
    public static final BlockPos EAST_RANGED_RIGHT_POS = new BlockPos(10, 2, 7);
    public static final BlockPos WEST_RECOVERY_LEFT_POS = new BlockPos(4, 2, 4);
    public static final BlockPos WEST_RECOVERY_RIGHT_POS = new BlockPos(4, 2, 6);
    public static final BlockPos EAST_RECOVERY_LEFT_POS = new BlockPos(10, 2, 4);
    public static final BlockPos EAST_RECOVERY_RIGHT_POS = new BlockPos(10, 2, 6);

    private RecruitsBattleGameTestSupport() {
    }

    public static BattleSquad spawnWestMixedSquad(GameTestHelper helper, UUID ownerId) {
        return spawnMixedSquad(helper, SquadAnchor.WEST, ownerId, "West");
    }

    public static BattleSquad spawnEastMixedSquad(GameTestHelper helper, UUID ownerId) {
        return spawnMixedSquad(helper, SquadAnchor.EAST, ownerId, "East");
    }

    public static BattleSquad spawnRecoveryPair(GameTestHelper helper, SquadAnchor anchor, UUID ownerId, String squadName) {
        List<AbstractRecruitEntity> recruits = new ArrayList<>();
        recruits.add(spawnConfiguredRecruit(helper, ModEntityTypes.RECRUIT.get(), anchor.recoveryLeftPos(), squadName + " Guard", ownerId));
        recruits.add(spawnConfiguredRecruit(helper, ModEntityTypes.BOWMAN.get(), anchor.recoveryRightPos(), squadName + " Archer", ownerId));
        return new BattleSquad(anchor.anchor(), recruits);
    }

    public static void assignOwners(List<? extends AbstractRecruitEntity> recruits, UUID ownerId) {
        for (AbstractRecruitEntity recruit : recruits) {
            recruit.setOwnerUUID(Optional.of(ownerId));
            recruit.setIsOwned(true);
        }
    }

    public static void setMutualTargets(List<? extends AbstractRecruitEntity> attackers, List<? extends AbstractRecruitEntity> defenders) {
        for (AbstractRecruitEntity attacker : attackers) {
            AbstractRecruitEntity nearestTarget = defenders.stream()
                    .filter(AbstractRecruitEntity::isAlive)
                    .min(Comparator.comparingDouble(attacker::distanceToSqr))
                    .orElse(null);

            attacker.setAggroState(1);
            attacker.setTarget(nearestTarget);
        }
    }

    public static void setSharedTarget(List<? extends AbstractRecruitEntity> attackers, LivingEntity target) {
        for (AbstractRecruitEntity attacker : attackers) {
            attacker.setAggroState(1);
            attacker.setTarget(target);
        }
    }

    public static void removeOtherRecruits(GameTestHelper helper, List<? extends AbstractRecruitEntity> retainedRecruits) {
        Set<UUID> retainedIds = retainedRecruits.stream()
                .map(AbstractRecruitEntity::getUUID)
                .collect(Collectors.toSet());
        if (retainedRecruits.isEmpty()) {
            return;
        }

        AbstractRecruitEntity anchor = retainedRecruits.get(0);
        AABB bounds = new AABB(anchor.blockPosition()).inflate(12.0D);
        for (AbstractRecruitEntity recruit : helper.getLevel().getEntitiesOfClass(AbstractRecruitEntity.class, bounds)) {
            if (!retainedIds.contains(recruit.getUUID())) {
                recruit.kill();
            }
        }
    }

    public static void setHoldFormation(BattleSquad squad) {
        for (AbstractRecruitEntity recruit : squad.recruits()) {
            recruit.setFollowState(2);
        }
    }

    public static void assignFormationCohort(List<? extends AbstractRecruitEntity> recruits, UUID groupId) {
        if (recruits.isEmpty()) {
            return;
        }

        AbstractRecruitEntity leader = recruits.get(0);
        RecruitsGroup group = new RecruitsGroup("GameTest Formation", leader.getOwnerUUID(), "GameTest Owner", 0);
        group.setUUID(groupId);
        for (AbstractRecruitEntity recruit : recruits) {
            group.addMember(recruit.getUUID());
        }

        if (RecruitEvents.groupsManager() != null && leader.level() instanceof ServerLevel serverLevel) {
            RecruitEvents.groupsManager().addPatrolGroup(group, serverLevel);
        }

        for (AbstractRecruitEntity recruit : recruits) {
            recruit.setGroupUUID(groupId);
            recruit.needsGroupUpdate = false;
            recruit.isInFormation = true;
            recruit.setFollowState(3);
            recruit.setAggroState(1);
            recruit.setTarget(null);
        }
    }

    public static void setReturnToFormation(BattleSquad squad) {
        for (AbstractRecruitEntity recruit : squad.recruits()) {
            recruit.setHoldPos(Vec3.atCenterOf(recruit.blockPosition()));
            recruit.setFollowState(3);
        }
    }

    public static void assertAfterDelay(GameTestHelper helper, int delay, Runnable assertions) {
        helper.runAfterDelay(delay, assertions);
    }

    public static Vec3 formationAnchor(GameTestHelper helper, SquadAnchor anchor) {
        return Vec3.atCenterOf(helper.absolutePos(anchor.anchor()));
    }

    public static <T extends AbstractRecruitEntity> T spawnConfiguredRecruit(GameTestHelper helper, EntityType<T> entityType, BlockPos relativePos, String customName, UUID ownerId) {
        T recruit = spawnRecruit(helper, entityType, relativePos);
        recruit.setCustomName(Component.literal(customName));
        recruit.setCustomNameVisible(true);
        recruit.setPersistenceRequired();
        recruit.setFollowState(2);
        recruit.setHoldPos(Vec3.atCenterOf(helper.absolutePos(relativePos)));
        recruit.setAggroState(0);
        recruit.setTarget(null);
        recruit.setHealth(recruit.getMaxHealth());
        recruit.getInventory().clearContent();
        applyBattleLoadout(recruit);
        assignOwners(List.of(recruit), ownerId);
        return recruit;
    }

    public static <T extends AbstractRecruitEntity> T spawnRecruit(GameTestHelper helper, EntityType<T> entityType, BlockPos relativePos) {
        ServerLevel level = helper.getLevel();
        T recruit = entityType.create(level);

        if (recruit == null) {
            throw new IllegalArgumentException("Failed to create recruit test entity");
        }

        BlockPos absolutePos = helper.absolutePos(relativePos);
        recruit.moveTo(absolutePos.getX() + 0.5D, absolutePos.getY(), absolutePos.getZ() + 0.5D, 0.0F, 0.0F);
        recruit.initSpawn();

        if (!level.addFreshEntity(recruit)) {
            throw new IllegalArgumentException("Failed to insert recruit test entity into GameTest level");
        }

        return recruit;
    }

    private static BattleSquad spawnMixedSquad(GameTestHelper helper, SquadAnchor anchor, UUID ownerId, String squadName) {
        List<AbstractRecruitEntity> recruits = new ArrayList<>();
        recruits.add(spawnConfiguredRecruit(helper, ModEntityTypes.RECRUIT_SHIELDMAN.get(), anchor.frontlinePos(), squadName + " Shield", ownerId));
        recruits.add(spawnConfiguredRecruit(helper, ModEntityTypes.RECRUIT.get(), anchor.flankPos(), squadName + " Recruit", ownerId));
        recruits.add(spawnConfiguredRecruit(helper, ModEntityTypes.BOWMAN.get(), anchor.rangedLeftPos(), squadName + " Bowman", ownerId));
        recruits.add(spawnConfiguredRecruit(helper, ModEntityTypes.CROSSBOWMAN.get(), anchor.rangedRightPos(), squadName + " Crossbow", ownerId));
        return new BattleSquad(anchor.anchor(), recruits);
    }

    private static void applyBattleLoadout(AbstractRecruitEntity recruit) {
        if (recruit instanceof RecruitShieldmanEntity) {
            recruit.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            recruit.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
            return;
        }

        if (recruit instanceof RecruitEntity) {
            recruit.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            return;
        }

        if (recruit instanceof BowmanEntity) {
            recruit.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
            recruit.getInventory().addItem(new ItemStack(Items.ARROW, 64));
            return;
        }

        if (recruit instanceof CrossBowmanEntity) {
            recruit.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
            recruit.getInventory().addItem(new ItemStack(Items.ARROW, 64));
        }
    }

    public record BattleSquad(BlockPos relativeAnchor, List<AbstractRecruitEntity> recruits) {
    }

    public enum SquadAnchor {
        WEST(new BlockPos(3, 2, 5), WEST_FRONTLINE_POS, WEST_FLANK_POS, WEST_RANGED_LEFT_POS, WEST_RANGED_RIGHT_POS, WEST_RECOVERY_LEFT_POS, WEST_RECOVERY_RIGHT_POS),
        EAST(new BlockPos(11, 2, 5), EAST_FRONTLINE_POS, EAST_FLANK_POS, EAST_RANGED_LEFT_POS, EAST_RANGED_RIGHT_POS, EAST_RECOVERY_LEFT_POS, EAST_RECOVERY_RIGHT_POS);

        private final BlockPos anchor;
        private final BlockPos frontlinePos;
        private final BlockPos flankPos;
        private final BlockPos rangedLeftPos;
        private final BlockPos rangedRightPos;
        private final BlockPos recoveryLeftPos;
        private final BlockPos recoveryRightPos;

        SquadAnchor(BlockPos anchor, BlockPos frontlinePos, BlockPos flankPos, BlockPos rangedLeftPos, BlockPos rangedRightPos, BlockPos recoveryLeftPos, BlockPos recoveryRightPos) {
            this.anchor = anchor;
            this.frontlinePos = frontlinePos;
            this.flankPos = flankPos;
            this.rangedLeftPos = rangedLeftPos;
            this.rangedRightPos = rangedRightPos;
            this.recoveryLeftPos = recoveryLeftPos;
            this.recoveryRightPos = recoveryRightPos;
        }

        public BlockPos anchor() {
            return anchor;
        }

        public BlockPos frontlinePos() {
            return frontlinePos;
        }

        public BlockPos flankPos() {
            return flankPos;
        }

        public BlockPos rangedLeftPos() {
            return rangedLeftPos;
        }

        public BlockPos rangedRightPos() {
            return rangedRightPos;
        }

        public BlockPos recoveryLeftPos() {
            return recoveryLeftPos;
        }

        public BlockPos recoveryRightPos() {
            return recoveryRightPos;
        }
    }
}
