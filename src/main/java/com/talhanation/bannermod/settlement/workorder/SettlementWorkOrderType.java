package com.talhanation.bannermod.settlement.workorder;

/**
 * Kind of action a {@link SettlementWorkOrder} requests. Ordered by the rough production
 * pipeline so publishers and consumers can reason about ordering when a building emits a
 * mixed batch.
 */
public enum SettlementWorkOrderType {
    TILL_SOIL,
    WATER_FIELD,
    PLANT_CROP,
    HARVEST_CROP,

    FELL_TREE,
    REPLANT_TREE,

    MINE_BLOCK,

    FISH,

    ANIMAL_BREED,
    ANIMAL_SPECIAL_TASK,
    ANIMAL_SLAUGHTER,

    BREAK_BLOCK,
    BUILD_BLOCK,

    FETCH_INPUT,
    HAUL_RESOURCE,
    STOCK_MARKET
}
