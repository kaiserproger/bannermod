package com.talhanation.bannermod.settlement.validation.types;

import com.talhanation.bannermod.settlement.building.ZoneRole;
import com.talhanation.bannermod.settlement.building.ZoneSelection;
import com.talhanation.bannermod.settlement.validation.BuildingValidationRequest;
import com.talhanation.bannermod.settlement.validation.ValidationIssue;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.Map;

public record BuildingValidationContext(
        ServerLevel level,
        Player player,
        BuildingValidationRequest request,
        Map<ZoneRole, ZoneSelection> zonesByRole,
        List<ValidationIssue> warnings,
        List<ValidationIssue> blocking
) {
}
