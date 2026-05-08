package com.talhanation.bannermod.ai.military.villager;

import com.talhanation.bannermod.entity.military.VillagerNobleEntity;
import com.talhanation.bannermod.entity.military.runtime.VillagerConversionService;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

import java.util.List;

public class VillagerBecomeNobleGoal extends Goal {

    /** Minimum villager neighbours required to promote one of them to a noble. */
    static final int MIN_VILLAGERS_FOR_PROMOTION = 7;

    public Villager villager;

    private int timer;
    public VillagerBecomeNobleGoal(Villager villager) {
        this.villager = villager;
    }

    @Override
    public boolean canUse() {
        return !this.villager.isBaby() && !villager.isSleeping() && this.villager.getVillagerData().getProfession().equals(VillagerProfession.NONE);
    }

    @Override
    public boolean canContinueToUse() {
        return timer > 0;
    }

    @Override
    public void start() {
        super.start();
        timer = 1200 + villager.getRandom().nextInt(600);
    }

    @Override
    public void tick() {
        super.tick();
        if(this.villager.getCommandSenderWorld().isClientSide()) return;
        if(timer > 0) timer--;
    }

    @Override
    public void stop() {
        super.stop();
        if(this.villager.getCommandSenderWorld().isClientSide()) return;
        List<LivingEntity> list = this.villager.getCommandSenderWorld().getEntitiesOfClass(LivingEntity.class, this.villager.getBoundingBox().inflate(100));

        // Single-pass fold replacing the prior triple stream chain
        // (toList + anyMatch + filter/count). Short-circuits on a nearby noble
        // and avoids allocating intermediate streams / lambdas per invocation.
        boolean noblePresent = false;
        int villagers = 0;
        for (int i = 0, n = list.size(); i < n; i++) {
            LivingEntity e = list.get(i);
            if (e instanceof VillagerNobleEntity) {
                noblePresent = true;
                break;
            }
            if (e instanceof Villager) {
                villagers++;
            }
        }

        if (shouldPromote(noblePresent, villagers)) {
            VillagerConversionService.createNobleVillager(villager);
        }
    }

    /**
     * Pure predicate: promote when no noble is already nearby and the villager
     * neighbourhood has reached the promotion threshold. Extracted so the
     * threshold / short-circuit semantics can be unit tested without standing
     * up a full Minecraft {@code Level}.
     */
    static boolean shouldPromote(boolean noblePresent, int villagerCount) {
        if (noblePresent) {
            return false;
        }
        return villagerCount >= MIN_VILLAGERS_FOR_PROMOTION;
    }
}
