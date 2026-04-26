package com.talhanation.bannermod.commands.war;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.talhanation.bannermod.war.WarRuntimeContext;
import com.talhanation.bannermod.war.registry.PoliticalEntityRecord;
import com.talhanation.bannermod.war.registry.PoliticalRegistryRuntime;
import com.talhanation.bannermod.war.runtime.WarAllyInviteRecord;
import com.talhanation.bannermod.war.runtime.WarAllyInviteRuntime;
import com.talhanation.bannermod.war.runtime.WarAllyService;
import com.talhanation.bannermod.war.runtime.WarDeclarationRecord;
import com.talhanation.bannermod.war.runtime.WarDeclarationRuntime;
import com.talhanation.bannermod.war.runtime.WarSide;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.UUID;

/** Slash subtree exposing the {@link WarAllyService} flow. */
public final class WarAllyCommands {
    private WarAllyCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("ally")
                .then(Commands.literal("invite")
                        .then(Commands.argument("warId", StringArgumentType.word())
                                .then(Commands.argument("side", StringArgumentType.word())
                                        .then(Commands.argument("entity", StringArgumentType.word())
                                                .executes(WarAllyCommands::invite)))))
                .then(Commands.literal("accept")
                        .then(Commands.argument("inviteId", StringArgumentType.word())
                                .executes(WarAllyCommands::accept)))
                .then(Commands.literal("decline")
                        .then(Commands.argument("inviteId", StringArgumentType.word())
                                .executes(WarAllyCommands::decline)))
                .then(Commands.literal("cancel")
                        .then(Commands.argument("inviteId", StringArgumentType.word())
                                .executes(WarAllyCommands::cancel)))
                .then(Commands.literal("list")
                        .then(Commands.argument("warId", StringArgumentType.word())
                                .executes(WarAllyCommands::list)));
    }

    private static int invite(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerLevel level = WarCommandSupport.level(ctx);
        ServerPlayer actor = ctx.getSource().getPlayerOrException();

        UUID warId = WarCommandSupport.requireWar(ctx,
                StringArgumentType.getString(ctx, "warId")).id();
        WarSide side = parseSide(ctx, StringArgumentType.getString(ctx, "side"));

        UUID inviteeId = WarCommandSupport.requireEntity(ctx,
                StringArgumentType.getString(ctx, "entity")).id();

        WarAllyService.InviteResult result = WarAllyService.invite(level, actor, warId, side, inviteeId);
        if (!result.ok()) {
            ctx.getSource().sendFailure(deniedMessage("Invite", result.outcome()));
            return 0;
        }
        WarAllyInviteRecord record = result.record();
        ctx.getSource().sendSuccess(() -> Component.literal("Invite issued: " + record.id())
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int accept(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return respond(ctx, true);
    }

    private static int decline(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return respond(ctx, false);
    }

    private static int respond(CommandContext<CommandSourceStack> ctx, boolean accept) throws CommandSyntaxException {
        ServerLevel level = WarCommandSupport.level(ctx);
        ServerPlayer actor = ctx.getSource().getPlayerOrException();

        UUID inviteId = resolveInviteId(level, StringArgumentType.getString(ctx, "inviteId"));
        if (inviteId == null) {
            ctx.getSource().sendFailure(Component.literal("Invite not found.").withStyle(ChatFormatting.RED));
            return 0;
        }
        WarAllyService.InviteResult result = accept
                ? WarAllyService.accept(level, actor, inviteId)
                : WarAllyService.decline(level, actor, inviteId);
        if (!result.ok()) {
            ctx.getSource().sendFailure(deniedMessage(accept ? "Accept" : "Decline", result.outcome()));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(accept ? "Joined as ally." : "Invite declined.")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int cancel(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerLevel level = WarCommandSupport.level(ctx);
        ServerPlayer actor = ctx.getSource().getPlayerOrException();

        UUID inviteId = resolveInviteId(level, StringArgumentType.getString(ctx, "inviteId"));
        if (inviteId == null) {
            ctx.getSource().sendFailure(Component.literal("Invite not found.").withStyle(ChatFormatting.RED));
            return 0;
        }
        WarAllyService.InviteResult result = WarAllyService.cancel(level, actor, inviteId);
        if (!result.ok()) {
            ctx.getSource().sendFailure(deniedMessage("Cancel", result.outcome()));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Invite cancelled.").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerLevel level = WarCommandSupport.level(ctx);
        WarDeclarationRecord war = WarCommandSupport.requireWar(ctx,
                StringArgumentType.getString(ctx, "warId"));

        PoliticalRegistryRuntime registry = WarRuntimeContext.registry(level);
        WarAllyInviteRuntime invites = WarRuntimeContext.allyInvites(level);
        var pending = invites.forWar(war.id());

        Component header = Component.literal("Allies for war " + shortId(war.id())).withStyle(ChatFormatting.AQUA);
        ctx.getSource().sendSuccess(() -> header, false);

        ctx.getSource().sendSuccess(() -> Component.literal(
                "Attacker side allies: " + describeIds(registry, war.attackerAllyIds())), false);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Defender side allies: " + describeIds(registry, war.defenderAllyIds())), false);

        if (pending.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("Pending invites: (none)").withStyle(ChatFormatting.GRAY),
                    false);
        } else {
            for (WarAllyInviteRecord invite : pending) {
                String inviteeName = describeId(registry, invite.inviteePoliticalEntityId());
                String line = "  invite " + shortId(invite.id())
                        + " side=" + invite.side().name()
                        + " invitee=" + inviteeName;
                ctx.getSource().sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.YELLOW), false);
            }
        }
        return 1;
    }

    private static UUID resolveInviteId(ServerLevel level, String token) {
        WarAllyInviteRuntime invites = WarRuntimeContext.allyInvites(level);
        return invites.byIdFragment(token).map(WarAllyInviteRecord::id).orElse(null);
    }

    private static WarSide parseSide(CommandContext<CommandSourceStack> ctx, String raw) {
        WarSide side = WarSide.parse(raw);
        if (side == null) {
            ctx.getSource().sendFailure(Component.literal("Side must be ATTACKER or DEFENDER.").withStyle(ChatFormatting.RED));
            return WarSide.ATTACKER;
        }
        return side;
    }

    private static String describeIds(PoliticalRegistryRuntime registry, java.util.List<UUID> ids) {
        if (ids.isEmpty()) return "(none)";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(describeId(registry, ids.get(i)));
        }
        return sb.toString();
    }

    private static String describeId(PoliticalRegistryRuntime registry, UUID id) {
        if (id == null) return "?";
        Optional<PoliticalEntityRecord> entity = registry.byId(id);
        if (entity.isEmpty() || entity.get().name().isBlank()) return shortId(id);
        return entity.get().name() + "(" + shortId(id) + ")";
    }

    private static String shortId(UUID id) {
        if (id == null) return "?";
        String s = id.toString();
        return s.length() > 8 ? s.substring(0, 8) : s;
    }

    private static Component deniedMessage(String label, WarAllyService.Outcome outcome) {
        return Component.literal(label + " denied: " + outcome.token()).withStyle(ChatFormatting.RED);
    }
}
