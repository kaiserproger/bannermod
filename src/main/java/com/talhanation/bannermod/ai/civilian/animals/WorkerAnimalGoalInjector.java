package com.talhanation.bannermod.ai.civilian.animals;

import com.talhanation.bannermod.ai.civilian.animals.WorkerTemptGoal;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

public final class WorkerAnimalGoalInjector {

    private WorkerAnimalGoalInjector() {
    }

    public static void injectTemptGoal(Entity entity) {
        if (entity instanceof Chicken chicken) {
            chicken.goalSelector.addGoal(3, new WorkerTemptGoal(chicken, 1.0,
                    Ingredient.of(Items.WHEAT_SEEDS, Items.BEETROOT_SEEDS, Items.PUMPKIN_SEEDS,
                            Items.MELON_SEEDS, Items.TORCHFLOWER_SEEDS, Items.PITCHER_POD)));
            return;
        }
        if (entity instanceof Cow cow) {
            cow.goalSelector.addGoal(3, new WorkerTemptGoal(cow, 1.0, Ingredient.of(Items.WHEAT)));
            return;
        }
        if (entity instanceof Sheep sheep) {
            sheep.goalSelector.addGoal(3, new WorkerTemptGoal(sheep, 1.0, Ingredient.of(Items.WHEAT)));
            return;
        }
        if (entity instanceof Pig pig) {
            pig.goalSelector.addGoal(3, new WorkerTemptGoal(pig, 1.0,
                    Ingredient.of(Items.CARROT, Items.POTATO, Items.BEETROOT)));
        }
    }
}
