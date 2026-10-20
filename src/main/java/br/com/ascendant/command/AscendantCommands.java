package br.com.ascendant.command;

import br.com.ascendant.Ascendant;
import br.com.ascendant.flight.FlightController;
import br.com.ascendant.powers.IPlayerPowerData;
import br.com.ascendant.powers.PlayerPowerDataProvider;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;

/**
 * /ascendant power grant <player> <power_id>
 * /ascendant power revoke <player> <power_id>
 * /ascendant power list <player>
 *
 * Requires OP level 2. Powers are identified by ResourceLocations,
 * e.g. "ascendant:flight". The command runs on the server; no client authority.
 */
public final class AscendantCommands {

    private AscendantCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("ascendant")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("power")
                    .then(Commands.literal("grant")
                        .then(Commands.argument("targets", EntityArgument.players())
                            .then(Commands.argument("power", ResourceLocationArgument.id())
                                .executes(ctx -> grantPower(ctx,
                                    EntityArgument.getPlayers(ctx, "targets"),
                                    ResourceLocationArgument.getId(ctx, "power"))))))
                    .then(Commands.literal("revoke")
                        .then(Commands.argument("targets", EntityArgument.players())
                            .then(Commands.argument("power", ResourceLocationArgument.id())
                                .executes(ctx -> revokePower(ctx,
                                    EntityArgument.getPlayers(ctx, "targets"),
                                    ResourceLocationArgument.getId(ctx, "power"))))))
                    .then(Commands.literal("list")
                        .then(Commands.argument("target", EntityArgument.player())
                            .executes(ctx -> listPowers(ctx,
                                EntityArgument.getPlayer(ctx, "target"))))))
        );
    }

    private static int grantPower(CommandContext<CommandSourceStack> ctx,
                                   Collection<ServerPlayer> targets, ResourceLocation powerId) {
        int count = 0;
        for (ServerPlayer player : targets) {
            IPlayerPowerData data = player.getCapability(PlayerPowerDataProvider.CAPABILITY).orElse(null);
            if (data == null) {
                ctx.getSource().sendFailure(Component.literal(
                    "Player " + player.getName().getString() + " has no power capability."));
                continue;
            }
            boolean changed = data.unlockPower(powerId);
            String name = player.getName().getString();
            if (changed) {
                ctx.getSource().sendSuccess(
                    () -> Component.literal("Granted power " + powerId + " to " + name + "."), true);
                Ascendant.LOGGER.info("[ASC] Granted power {} to player {}", powerId, name);
            } else {
                ctx.getSource().sendSuccess(
                    () -> Component.literal(name + " already has power " + powerId + "."), false);
            }
            count++;
        }
        return count;
    }

    private static int revokePower(CommandContext<CommandSourceStack> ctx,
                                    Collection<ServerPlayer> targets, ResourceLocation powerId) {
        int count = 0;
        for (ServerPlayer player : targets) {
            IPlayerPowerData data = player.getCapability(PlayerPowerDataProvider.CAPABILITY).orElse(null);
            if (data == null) {
                ctx.getSource().sendFailure(Component.literal(
                    "Player " + player.getName().getString() + " has no power capability."));
                continue;
            }
            boolean changed = data.revokePower(powerId);
            String name = player.getName().getString();
            if (changed) {
                // If flight power was revoked and player is flying, stop flight immediately.
                if (powerId.equals(FlightController.FLIGHT_POWER)) {
                    FlightController.stopAndSyncForRevoke(player);
                }
                ctx.getSource().sendSuccess(
                    () -> Component.literal("Revoked power " + powerId + " from " + name + "."), true);
                Ascendant.LOGGER.info("[ASC] Revoked power {} from player {}", powerId, name);
            } else {
                ctx.getSource().sendSuccess(
                    () -> Component.literal(name + " does not have power " + powerId + "."), false);
            }
            count++;
        }
        return count;
    }

    private static int listPowers(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        IPlayerPowerData data = target.getCapability(PlayerPowerDataProvider.CAPABILITY).orElse(null);
        if (data == null) {
            ctx.getSource().sendFailure(Component.literal("Player has no power capability."));
            return 0;
        }
        String name = target.getName().getString();
        var powers = data.getUnlockedPowers();
        if (powers.isEmpty()) {
            ctx.getSource().sendSuccess(
                () -> Component.literal(name + " has no unlocked powers."), false);
        } else {
            String list = powers.stream().map(ResourceLocation::toString)
                .sorted().reduce((a, b) -> a + ", " + b).orElse("");
            ctx.getSource().sendSuccess(
                () -> Component.literal(name + " has: " + list), false);
        }
        return powers.size();
    }
}