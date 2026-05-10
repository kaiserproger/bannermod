package com.talhanation.bannermod.settlement.economy;

import javax.annotation.Nullable;
import java.util.Map;

public record FortLevelDefinition(
        int level,
        String name,
        int workerCap,
        int soldierCap,
        int mineCap,
        int outpostCap,
        @Nullable UpgradeRequirement nextLevelRequirement
) {
    public static final int MIN_LEVEL = 1;
    public static final int MAX_LEVEL = 4;

    private static final Map<Integer, FortLevelDefinition> DEFINITIONS = Map.of(
            1, new FortLevelDefinition(1, "Outpost/Zastava", 4, 4, 1, 0,
                    new UpgradeRequirement(120, 40, 160, 120, 250)),
            2, new FortLevelDefinition(2, "Fort", 8, 10, 1, 1,
                    new UpgradeRequirement(280, 120, 360, 280, 750)),
            3, new FortLevelDefinition(3, "Stronghold", 16, 25, 2, 2,
                    new UpgradeRequirement(640, 320, 800, 640, 1800)),
            4, new FortLevelDefinition(4, "Town/City", 32, 50, 3, 4, null)
    );

    public FortLevelDefinition {
        level = clampLevel(level);
        name = name == null ? "" : name;
        workerCap = Math.max(0, workerCap);
        soldierCap = Math.max(0, soldierCap);
        mineCap = Math.max(0, mineCap);
        outpostCap = Math.max(0, outpostCap);
    }

    public static FortLevelDefinition forLevel(int level) {
        return DEFINITIONS.get(clampLevel(level));
    }

    public static int clampLevel(int level) {
        return Math.max(MIN_LEVEL, Math.min(MAX_LEVEL, level));
    }

    public record UpgradeRequirement(int food, int iron, int wood, int stone, int coins) {
        public UpgradeRequirement {
            food = Math.max(0, food);
            iron = Math.max(0, iron);
            wood = Math.max(0, wood);
            stone = Math.max(0, stone);
            coins = Math.max(0, coins);
        }

        public String debugString() {
            return "food=" + this.food
                    + " iron=" + this.iron
                    + " wood=" + this.wood
                    + " stone=" + this.stone
                    + " coins=" + this.coins;
        }
    }
}
