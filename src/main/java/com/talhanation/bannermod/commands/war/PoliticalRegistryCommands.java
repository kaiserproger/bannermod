package com.talhanation.bannermod.commands.war;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.talhanation.bannermod.war.WarRuntimeContext;
import com.talhanation.bannermod.war.config.WarServerConfig;
import com.talhanation.bannermod.war.cooldown.WarCooldownKind;
import com.talhanation.bannermod.war.cooldown.WarCooldownPolicy;
import com.talhanation.bannermod.war.cooldown.WarCooldownRuntime;
import com.talhanation.bannermod.persistence.military.RecruitsClaim;
import com.talhanation.bannermod.persistence.military.RecruitsClaimSaveData;
import com.talhanation.bannermod.settlement.SettlementManager;
import com.talhanation.bannermod.settlement.SettlementSnapshot;
import com.talhanation.bannermod.war.registry.PoliticalEntityRecord;
import com.talhanation.bannermod.war.registry.PoliticalEntityStatus;
import com.talhanation.bannermod.war.registry.PoliticalRegistryRuntime;
import com.talhanation.bannermod.war.registry.PoliticalRegistryValidation;
import com.talhanation.bannermod.war.registry.PoliticalStatePromotionPolicy;
import com.talhanation.bannermod.war.rp.PoliticalCharterFormatter;
import com.talhanation.bannermod.war.runtime.OccupationRecord;
import com.talhanation.bannermod.war.runtime.OccupationRuntime;
import com.talhanation.bannermod.war.runtime.RevoltRecord;
import com.talhanation.bannermod.war.runtime.RevoltRuntime;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.UUID;

public final class PoliticalRegistryCommands {
    private PoliticalRegistryCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("state")
                .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(PoliticalRegistryCommands::createState)))
                .then(Commands.literal("setcapital")
                        .then(Commands.argument("entity", StringArgumentType.word())
                                .executes(ctx -> setCapitalAtSource(ctx, null))
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(ctx -> setCapitalAtSource(ctx,
                                                BlockPosArgument.getBlockPos(ctx, "pos"))))))
                .then(Commands.literal("status")
                        .then(Commands.argument("entity", StringArgumentType.word())
                                .then(Commands.argument("status", StringArgumentType.word())
                                        .executes(PoliticalRegistryCommands::setStatus))))
                .then(Commands.literal("info")
                        .then(Commands.argument("entity", StringArgumentType.word())
                                .executes(PoliticalRegistryCommands::info)))
                .then(Commands.literal("list")
                        .executes(PoliticalRegistryCommands::list))
                .then(Commands.literal("revolt")
                        .then(Commands.literal("declare")
                                .then(Commands.argument("occupationId", StringArgumentType.word())
                                        .executes(PoliticalRegistryCommands::revoltDeclare))));
    }

    private static int createState(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(context, "name");
        PoliticalRegistryRuntime registry = WarCommandSupport.registry(context);
        PoliticalRegistryValidation.Result validation = registry.canCreate(name, player.getUUID());
        if (!validation.valid()) {
            context.getSource().sendFailure(Component.literal("Cannot create state: " + validation.reason()));
            return 0;
        }
        long gameTime = context.getSource().getServer().overworld().getGameTime();
        Optional<PoliticalEntityRecord> created = registry.create(
                name,
                player.getUUID(),
                player.blockPosition(),
                "",
                "",
                "",
                "",
                gameTime
        );
        if (created.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Failed to create state."));
            return 0;
        }
        WarCommandSupport.replyComponent(context,
                Component.literal("Created state: ")
                        .append(PoliticalCharterFormatter.summary(created.get())));
        return 1;
    }

    private static int setCapitalAtSource(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
                                          BlockPos overridePos) throws CommandSyntaxException {
        String token = StringArgumentType.getString(context, "entity");
        PoliticalEntityRecord record = WarCommandSupport.requireEntity(context, token);
        if (!WarCommandSupport.canAct(context, record)) {
            throw WarCommandSupport.ERR_NOT_AUTHORIZED.create();
        }
        BlockPos pos = overridePos;
        if (pos == null) {
            if (context.getSource().getEntity() == null) {
                context.getSource().sendFailure(Component.literal("Provide a position or run as a player."));
                return 0;
            }
            pos = context.getSource().getEntity().blockPosition();
        }
        boolean updated = WarCommandSupport.registry(context).updateCapital(record.id(), pos);
        if (!updated) {
            context.getSource().sendFailure(Component.literal("Failed to update capital."));
            return 0;
        }
        WarCommandSupport.reply(context,
                "Capital of " + record.name() + " set to " + pos.toShortString() + ".");
        return 1;
    }

    private static int setStatus(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        String token = StringArgumentType.getString(context, "entity");
        String statusToken = StringArgumentType.getString(context, "status");
        PoliticalEntityRecord record = WarCommandSupport.requireEntity(context, token);
        if (!WarCommandSupport.canAct(context, record)) {
            throw WarCommandSupport.ERR_NOT_AUTHORIZED.create();
        }
        PoliticalEntityStatus status;
        try {
            status = PoliticalEntityStatus.valueOf(statusToken.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            context.getSource().sendFailure(Component.literal("Unknown status: " + statusToken
                    + " (expected SETTLEMENT|STATE|VASSAL|PEACEFUL)"));
            return 0;
        }
        if (status == PoliticalEntityStatus.STATE && record.status() != PoliticalEntityStatus.STATE) {
            PoliticalStatePromotionPolicy.Result promotion = PoliticalStatePromotionPolicy.evaluate(
                    settlementSnapshotForEntity(context, record.id()).orElse(null));
            if (!promotion.allowed()) {
                context.getSource().sendFailure(Component.literal(promotion.denialReason()));
                return 0;
            }
        }
        boolean togglesPeaceful = (status == PoliticalEntityStatus.PEACEFUL)
                != (record.status() == PoliticalEntityStatus.PEACEFUL);
        WarCooldownRuntime cooldowns = WarRuntimeContext.cooldowns(WarCommandSupport.level(context));
        long gameTime = WarCommandSupport.level(context).getGameTime();
        if (togglesPeaceful) {
            WarCooldownPolicy.Result gate = WarCooldownPolicy.canTogglePeacefulStatus(
                    record.id(), gameTime, cooldowns);
            if (!gate.valid()) {
                context.getSource().sendFailure(Component.literal("Cannot toggle PEACEFUL: " + gate.reason()));
                return 0;
            }
        }
        boolean updated = WarCommandSupport.registry(context).updateStatus(record.id(), status);
        if (!updated) {
            context.getSource().sendFailure(Component.literal("Failed to update status."));
            return 0;
        }
        if (togglesPeaceful) {
            cooldowns.grant(record.id(), WarCooldownKind.PEACEFUL_TOGGLE_RECENT,
                    gameTime + WarServerConfig.peacefulToggleCooldownTicks());
        }
        WarCommandSupport.reply(context,
                "Status of " + record.name() + " set to " + status.name() + ".");
        return 1;
    }

    private static Optional<SettlementSnapshot> settlementSnapshotForEntity(
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
            UUID politicalEntityId) {
        var level = WarCommandSupport.level(context);
        SettlementManager settlements = SettlementManager.get(level);
        for (RecruitsClaim claim : RecruitsClaimSaveData.get(level).getAllClaims()) {
            if (politicalEntityId.equals(claim.getOwnerPoliticalEntityId())) {
                SettlementSnapshot snapshot = settlements.getSnapshot(claim.getUUID());
                if (snapshot != null) {
                    return Optional.of(snapshot);
                }
            }
        }
        return Optional.empty();
    }

    private static int info(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        String token = StringArgumentType.getString(context, "entity");
        PoliticalEntityRecord record = WarCommandSupport.requireEntity(context, token);
        WarCommandSupport.replyComponent(context, PoliticalCharterFormatter.detail(record));
        return 1;
    }

    private static int list(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        PoliticalRegistryRuntime registry = WarCommandSupport.registry(context);
        if (registry.all().isEmpty()) {
            WarCommandSupport.reply(context, "No political entities registered.");
            return 0;
        }
        MutableComponent text = Component.literal("Political entities:\n");
        for (PoliticalEntityRecord record : registry.all()) {
            text.append(PoliticalCharterFormatter.summary(record)).append(Component.literal("\n"));
        }
        WarCommandSupport.replyComponent(context, text);
        return registry.all().size();
    }

    private static int revoltDeclare(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        String token = StringArgumentType.getString(context, "occupationId");
        var level = WarCommandSupport.level(context);
        OccupationRuntime occupations = WarRuntimeContext.occupations(level);
        Optional<OccupationRecord> occupation = occupations.byIdFragment(token);
        if (occupation.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Occupation not found."));
            return 0;
        }
        OccupationRecord record = occupation.get();
        PoliticalRegistryRuntime registry = WarRuntimeContext.registry(level);
        Optional<PoliticalEntityRecord> rebel = registry.byId(record.occupiedEntityId());
        if (rebel.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Occupied entity record missing."));
            return 0;
        }
        if (!WarCommandSupport.canAct(context, rebel.get())) {
            throw WarCommandSupport.ERR_NOT_AUTHORIZED.create();
        }
        RevoltRuntime revolts = WarRuntimeContext.revolts(level);
        Optional<RevoltRecord> scheduled = revolts.schedule(
                record.warId(), record.id(), record.occupiedEntityId(), record.occupierEntityId(),
                level.getGameTime());
        if (scheduled.isEmpty()) {
            context.getSource().sendFailure(Component.literal(
                    "Cannot schedule revolt (already pending or invalid)."));
            return 0;
        }
        WarRuntimeContext.audit(level).append(record.warId(), "REVOLT_DECLARED",
                "occupation=" + record.id() + ";rebel=" + record.occupiedEntityId(),
                level.getGameTime());
        WarCommandSupport.reply(context,
                "Revolt scheduled. Awaits resolution: /bannermod war revolt resolve "
                        + scheduled.get().id().toString().substring(0, 8) + " success|fail");
        return 1;
    }
}
