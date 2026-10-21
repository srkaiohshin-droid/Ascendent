package br.com.ascendant.command;

import br.com.ascendant.Ascendant;
import br.com.ascendant.flight.FlightController;
import br.com.ascendant.pack.PackManager;
import br.com.ascendant.pack.PackManifest;
import br.com.ascendant.power.AbilityDefinition;
import br.com.ascendant.power.AbilityRegistry;
import br.com.ascendant.power.PowerDefinition;
import br.com.ascendant.power.PowerRegistry;
import br.com.ascendant.power.runtime.PowerRuntimeEngine;
import br.com.ascendant.powers.IPlayerPowerData;
import br.com.ascendant.powers.PlayerPowerDataProvider;
import br.com.ascendant.suit.SuitDefinition;
import br.com.ascendant.suit.SuitManager;
import br.com.ascendant.suit.SuitRegistry;
import br.com.ascendant.tools.ContentValidator;
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
import java.util.List;

/**
 * /ascendant power grant <player> <power_id>
 * /ascendant power revoke <player> <power_id>
 * /ascendant power list <player>
 * /ascendant power info <power_id>
 * /ascendant power activate <player> <ability_id>
 * /ascendant power use <player> <power_id>
 * /ascendant suit equip <player> <suit_id>
 * /ascendant suit unequip <player>
 * /ascendant suit list
 * /ascendant pack list
 * /ascendant pack reload
 * /ascendant stats
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
                                    ResourceLocationArgument.getId(ctx, "power")))))))
                    .then(Commands.literal("revoke")
                        .then(Commands.argument("targets", EntityArgument.players())
                            .then(Commands.argument("power", ResourceLocationArgument.id())
                                .executes(ctx -> revokePower(ctx,
                                    EntityArgument.getPlayers(ctx, "targets"),
                                    ResourceLocationArgument.getId(ctx, "power"))))))
                    .then(Commands.literal("list")
                        .then(Commands.argument("target", EntityArgument.player())
                            .executes(ctx -> listPowers(ctx,
                                EntityArgument.getPlayer(ctx, "target")))))
                    .then(Commands.literal("info")
                        .then(Commands.argument("power", ResourceLocationArgument.id())
                            .executes(ctx -> powerInfo(ctx, ResourceLocationArgument.getId(ctx, "power")))))
                    .then(Commands.literal("activate")
                        .then(Commands.argument("targets", EntityArgument.players())
                            .then(Commands.argument("ability", ResourceLocationArgument.id())
                                .executes(ctx -> activateAbility(ctx,
                                    EntityArgument.getPlayers(ctx, "targets"),
                                    ResourceLocationArgument.getId(ctx, "ability"))))))
                    .then(Commands.literal("use")
                        .then(Commands.argument("targets", EntityArgument.players())
                            .then(Commands.argument("power", ResourceLocationArgument.id())
                                .executes(ctx -> activatePower(ctx,
                                    EntityArgument.getPlayers(ctx, "targets"),
                                    ResourceLocationArgument.getId(ctx, "power"))))))
                .then(Commands.literal("suit")
                    .then(Commands.literal("list")
                        .executes(AscendantCommands::listSuits))
                    .then(Commands.literal("equip")
                        .then(Commands.argument("targets", EntityArgument.players())
                            .then(Commands.argument("suit", ResourceLocationArgument.id())
                                .executes(ctx -> equipSuit(ctx,
                                    EntityArgument.getPlayers(ctx, "targets"),
                                    ResourceLocationArgument.getId(ctx, "suit"))))))
                    .then(Commands.literal("unequip")
                        .then(Commands.argument("targets", EntityArgument.players())
                            .executes(ctx -> unequipSuit(ctx, EntityArgument.getPlayers(ctx, "targets"))))))
                .then(Commands.literal("pack")
                    .then(Commands.literal("list")
                        .executes(AscendantCommands::listPacks))
                    .then(Commands.literal("reload")
                        .executes(AscendantCommands::reloadPacks))
                    .then(Commands.literal("export")
                        .executes(AscendantCommands::exportCatalog)))
                .then(Commands.literal("stats")
                    .executes(AscendantCommands::showStats))
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
                PowerRuntimeEngine.deactivatePowerIfInaccessible(player, powerId);
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

    private static int powerInfo(CommandContext<CommandSourceStack> ctx, ResourceLocation powerId) {
        PowerDefinition power = PowerRegistry.get(powerId);
        if (power == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown power: " + powerId));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(String.format(
                "Power: %s | Category: %s | Type: %s | Functional: %b | Cooldown: %d ticks",
                power.getId(), power.getCategory(), power.getType(), power.isFunctional(), power.getCooldownTicks()
        )), false);
        return 1;
    }

    private static int activateAbility(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets, ResourceLocation abilityId) {
        AbilityDefinition ability = AbilityRegistry.get(abilityId);
        if (ability == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown ability: " + abilityId));
            return 0;
        }
        int executed = 0;
        for (ServerPlayer player : targets) {
            boolean success = PowerRuntimeEngine.activateAbility(player, abilityId);
            if (success) {
                executed++;
                ctx.getSource().sendSuccess(() -> Component.literal("Activated " + abilityId + " on " + player.getName().getString()), true);
            } else {
                ctx.getSource().sendFailure(Component.literal("Failed to activate " + abilityId + " on " + player.getName().getString() + " (cooldown or condition)"));
            }
        }
        return executed;
    }

    private static int activatePower(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets, ResourceLocation powerId) {
        PowerDefinition power = PowerRegistry.get(powerId);
        if (power == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown power: " + powerId));
            return 0;
        }
        int executed = 0;
        for (ServerPlayer player : targets) {
            if (PowerRuntimeEngine.activatePower(player, powerId)) {
                executed++;
                ctx.getSource().sendSuccess(() -> Component.literal(
                        "Activated " + powerId + " on " + player.getName().getString()), true);
            } else {
                ctx.getSource().sendFailure(Component.literal("Failed to activate " + powerId + " on "
                        + player.getName().getString() + " (power access, type, cooldown or condition)"));
            }
        }
        return executed;
    }

    private static int listSuits(CommandContext<CommandSourceStack> ctx) {
        var suits = SuitRegistry.getAll();
        ctx.getSource().sendSuccess(() -> Component.literal("=== Registered Suits (" + suits.size() + ") ==="), false);
        for (SuitDefinition suit : suits) {
            ctx.getSource().sendSuccess(() -> Component.literal(String.format(
                    "- %s [%s] (%s by %s)",
                    suit.getId(), suit.getRarity(), suit.getUniverse(), suit.getAuthor()
            )), false);
        }
        return suits.size();
    }

    private static int equipSuit(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets, ResourceLocation suitId) {
        SuitDefinition suit = SuitRegistry.get(suitId);
        if (suit == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown suit: " + suitId));
            return 0;
        }
        int count = 0;
        for (ServerPlayer player : targets) {
            if (SuitManager.equipSuit(player, suitId)) {
                count++;
                ctx.getSource().sendSuccess(() -> Component.literal("Equipped suit " + suitId + " on " + player.getName().getString()), true);
            }
        }
        return count;
    }

    private static int unequipSuit(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets) {
        int count = 0;
        for (ServerPlayer player : targets) {
            if (SuitManager.unequipSuit(player)) {
                count++;
                ctx.getSource().sendSuccess(() -> Component.literal("Unequipped suit on " + player.getName().getString()), true);
            }
        }
        return count;
    }

    private static int listPacks(CommandContext<CommandSourceStack> ctx) {
        List<PackManifest> packs = PackManager.getLocalCatalog().listAvailablePacks();
        ctx.getSource().sendSuccess(() -> Component.literal("=== Installed Content Packs (" + packs.size() + ") ==="), false);
        for (PackManifest p : packs) {
            ctx.getSource().sendSuccess(() -> Component.literal(String.format(
                    "- %s v%s by %s: %s", p.getId(), p.getVersion(), p.getAuthor(), p.getName()
            )), false);
        }
        return packs.size();
    }

    private static int reloadPacks(CommandContext<CommandSourceStack> ctx) {
        PackManager.loadAllInstalledPacks();
        ctx.getSource().sendSuccess(() -> Component.literal("Reloaded all installed content packs."), true);
        return 1;
    }

    private static int exportCatalog(CommandContext<CommandSourceStack> ctx) {
        java.nio.file.Path targetDir = java.nio.file.Path.of("exported_catalog");
        br.com.ascendant.tools.CatalogExporter.exportToDirectory(targetDir);
        ctx.getSource().sendSuccess(() -> Component.literal("Exported entire catalog (500 powers, 500 abilities, suits) to " + targetDir.toAbsolutePath()), true);
        return 1;
    }

    private static int showStats(CommandContext<CommandSourceStack> ctx) {
        ContentValidator.ValidationReport report = ContentValidator.validate();
        ctx.getSource().sendSuccess(() -> Component.literal(report.summary()), false);
        return 1;
    }
}
