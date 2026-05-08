package com.talhanation.bannermod.entity.military;

import com.talhanation.bannermod.citizen.CitizenRole;
import com.talhanation.bannermod.entity.military.runtime.RecruitEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.Optional;
import java.util.UUID;

final class RecruitPersistenceBridge {
    private RecruitPersistenceBridge() {
    }

    static void writeRecruitData(AbstractRecruitEntity recruit, CompoundTag nbt) {
        nbt.putInt("despawnTimer", recruit.despawnTimer);
        nbt.putInt("AggroState", recruit.getState());
        nbt.putInt("FollowState", recruit.getFollowState());
        nbt.putBoolean("ShouldFollow", recruit.getShouldFollow());
        nbt.putBoolean("ShouldMount", recruit.getShouldMount());
        nbt.putBoolean("ShouldProtect", recruit.getShouldProtect());
        nbt.putBoolean("ShouldBlock", recruit.getShouldBlock());
        if (recruit.getGroup() != null) nbt.putUUID("Group", recruit.getGroup());
        nbt.putInt("Variant", recruit.getVariant());
        nbt.putBoolean("Listen", recruit.getListen());
        nbt.putBoolean("Fleeing", recruit.getFleeing());
        nbt.putBoolean("isFollowing", recruit.isFollowing());
        nbt.putInt("Xp", recruit.getXp());
        nbt.putInt("Level", recruit.getXpLevel());
        nbt.putInt("Kills", recruit.getKills());
        nbt.putFloat("Hunger", recruit.getHunger());
        nbt.putFloat("Moral", recruit.getMorale());
        nbt.putBoolean("isOwned", recruit.getIsOwned());
        nbt.putInt("Cost", recruit.getCost());
        nbt.putInt("mountTimer", recruit.getMountTimer());
        nbt.putInt("upkeepTimer", recruit.getUpkeepTimer());
        nbt.putInt("Color", recruit.getColor());
        nbt.putInt("Biome", recruit.getBiome());
        nbt.putInt("MaxFallDistance", recruit.getMaxFallDistance());
        nbt.putInt("formationPos", recruit.formationPos);
        nbt.putBoolean("ShouldRest", recruit.getShouldRest());
        nbt.putBoolean("ShouldRanged", recruit.getShouldRanged());
        nbt.putBoolean("isInFormation", recruit.isInFormation);
        nbt.putInt("paymentTimer", recruit.paymentTimer);
        nbt.putString("CombatStance", recruit.getCombatStance().name());
        nbt.putString("CitizenRole", CitizenRole.CONTROLLED_RECRUIT.name());

        nbt.put("PerkProgress", recruit.getPerkProgress().toNbt());

        AbstractRecruitEntity.persistCitizenStateToLegacy(recruit.getCitizenCore(), nbt);

        if (recruit.getMountUUID() != null) {
            nbt.putUUID("MountUUID", recruit.getMountUUID());
        }
        if (recruit.getProtectUUID() != null) {
            nbt.putUUID("ProtectUUID", recruit.getProtectUUID());
        }
        if (recruit.getUpkeepUUID() != null) {
            nbt.putUUID("UpkeepUUID", recruit.getUpkeepUUID());
        }
        if (recruit.getUpkeepPos() != null) {
            nbt.putInt("UpkeepPosX", recruit.getUpkeepPos().getX());
            nbt.putInt("UpkeepPosY", recruit.getUpkeepPos().getY());
            nbt.putInt("UpkeepPosZ", recruit.getUpkeepPos().getZ());
        }
    }

    static void readRecruitData(AbstractRecruitEntity recruit, CompoundTag nbt) {
        if (nbt.contains("despawnTimer")) recruit.despawnTimer = nbt.getInt("despawnTimer");
        else recruit.despawnTimer = -1;

        recruit.setXpLevel(nbt.getInt("Level"));
        recruit.setAggroState(nbt.getInt("AggroState"));
        recruit.setFollowState(nbt.getInt("FollowState"));
        recruit.setShouldFollow(nbt.getBoolean("ShouldFollow"));
        recruit.setShouldMount(nbt.getBoolean("ShouldMount"));
        recruit.setShouldBlock(nbt.getBoolean("ShouldBlock"));
        recruit.setShouldProtect(nbt.getBoolean("ShouldProtect"));
        recruit.setFleeing(nbt.getBoolean("Fleeing"));
        recruit.setListen(nbt.getBoolean("Listen"));
        recruit.setIsFollowing(nbt.getBoolean("isFollowing"));
        recruit.setXp(nbt.getInt("Xp"));
        recruit.setKills(nbt.getInt("Kills"));
        recruit.setVariant(nbt.getInt("Variant"));
        recruit.setHunger(nbt.getFloat("Hunger"));
        recruit.setMoral(nbt.getFloat("Moral"));
        recruit.setIsOwned(nbt.getBoolean("isOwned"));
        recruit.setCost(nbt.getInt("Cost"));
        recruit.setMountTimer(nbt.getInt("mountTimer"));
        if (nbt.contains("UpkeepTimer")) recruit.setUpkeepTimer(nbt.getInt("UpkeepTimer"));
        else recruit.setUpkeepTimer(nbt.getInt("upkeepTimer"));
        recruit.setColor(nbt.getByte("Color"));
        recruit.setMaxFallDistance(nbt.getInt("MaxFallDistance"));
        recruit.formationPos = nbt.getInt("formationPos");
        recruit.setShouldRest(nbt.getBoolean("ShouldRest"));
        recruit.isInFormation = nbt.getBoolean("isInFormation");

        if (nbt.contains("paymentTimer")) {
            recruit.paymentTimer = nbt.getInt("paymentTimer");
        }
        else {
            recruit.resetPaymentTimer();
        }

        if (nbt.contains("CombatStance")) {
            recruit.setCombatStance(
                    com.talhanation.bannermod.ai.military.CombatStance.fromName(nbt.getString("CombatStance"))
            );
        } else {
            recruit.setCombatStance(com.talhanation.bannermod.ai.military.CombatStance.LOOSE);
        }

        if (nbt.contains("PerkProgress", Tag.TAG_COMPOUND)) {
            recruit.getPerkProgress().fromNbt(nbt.getCompound("PerkProgress"));
        } else {
            recruit.getPerkProgress().fromNbt(null);
        }

        AbstractRecruitEntity.hydrateCitizenStateFromLegacy(recruit.getCitizenCore(), nbt);

        if (nbt.contains("ProtectUUID")) {
            recruit.setProtectUUID(Optional.of(nbt.getUUID("ProtectUUID")));
        }
        if (nbt.contains("MountUUID")) {
            recruit.setMountUUID(Optional.of(nbt.getUUID("MountUUID")));
        }
        if (nbt.contains("UpkeepUUID")) {
            recruit.setUpkeepUUID(Optional.of(nbt.getUUID("UpkeepUUID")));
        }
        if (nbt.contains("UpkeepPosX") && nbt.contains("UpkeepPosY") && nbt.contains("UpkeepPosZ")) {
            recruit.setUpkeepPos(new BlockPos(
                    nbt.getInt("UpkeepPosX"),
                    nbt.getInt("UpkeepPosY"),
                    nbt.getInt("UpkeepPosZ")
            ));
        }

        if (nbt.contains("Biome")) recruit.setBiome(nbt.getByte("Biome"));
        else RecruitSpawnService.applyBiomeAndVariant(recruit);

        if (recruit.getCommandSenderWorld().isClientSide()) return;

        if (nbt.contains("Group")) {
            Tag tag = nbt.get("Group");
            int type = tag.getId();
            if (type == Tag.TAG_INT) {
                RecruitEvents.handleGroupBackwardCompatibility(recruit, nbt.getInt("Group"));
            }
            else {
                recruit.setGroupUUID(nbt.getUUID("Group"));
            }
        }
    }
}
