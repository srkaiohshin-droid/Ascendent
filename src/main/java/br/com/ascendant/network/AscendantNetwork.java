package br.com.ascendant.network;

import br.com.ascendant.Ascendant;
import br.com.ascendant.power.AbilityDefinition;
import br.com.ascendant.power.PowerDefinition;
import br.com.ascendant.power.runtime.PowerRuntimeEngine;
import br.com.ascendant.speedster.SpeedsterController;
import br.com.ascendant.suit.SuitManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.*;
import java.util.function.Supplier;

/**
 * Main network channel for Superpower Framework actions, toggles, and suit commands.
 */
public final class AscendantNetwork {
    private static final String PROTOCOL = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Ascendant.MOD_ID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals
    );
    private static boolean registered;

    private AscendantNetwork() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        int id = 0;
        CHANNEL.registerMessage(id++, ActivateAbilityPacket.class,
                ActivateAbilityPacket::encode, ActivateAbilityPacket::decode,
                ActivateAbilityPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, ToggleAbilityRequestPacket.class,
                ToggleAbilityRequestPacket::encode, ToggleAbilityRequestPacket::decode,
                ToggleAbilityRequestPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, TogglePowerPacket.class,
                TogglePowerPacket::encode, TogglePowerPacket::decode,
                TogglePowerPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, EquipSuitPacket.class,
                EquipSuitPacket::encode, EquipSuitPacket::decode,
                EquipSuitPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, ActivatePowerPacket.class,
                ActivatePowerPacket::encode, ActivatePowerPacket::decode,
                ActivatePowerPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id, TogglePowerRequestPacket.class,
                TogglePowerRequestPacket::encode, TogglePowerRequestPacket::decode,
                TogglePowerRequestPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        id++;
        CHANNEL.registerMessage(id++, SpeedsterControlPacket.class,
                SpeedsterControlPacket::encode, SpeedsterControlPacket::decode,
                SpeedsterControlPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, SpeedsterInputPacket.class,
                SpeedsterInputPacket::encode, SpeedsterInputPacket::decode,
                SpeedsterInputPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id, SpeedsterStatePacket.class,
                SpeedsterStatePacket::encode, SpeedsterStatePacket::decode,
                SpeedsterStatePacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        id++;
        CHANNEL.registerMessage(id++, CatalogStateRequestPacket.class,
                CatalogStateRequestPacket::encode, CatalogStateRequestPacket::decode,
                CatalogStateRequestPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id, CatalogStatePacket.class,
                CatalogStatePacket::encode, CatalogStatePacket::decode,
                CatalogStatePacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        registered = true;
    }

    public static void sendActivateAbility(ResourceLocation abilityId) {
        CHANNEL.sendToServer(new ActivateAbilityPacket(abilityId));
    }

    public static void sendToggleAbilityRequest(ResourceLocation abilityId) {
        CHANNEL.sendToServer(new ToggleAbilityRequestPacket(abilityId));
    }

    public static void requestCatalogState() {
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> br.com.ascendant.ui.client.CatalogClientState.beginRequest());
        CHANNEL.sendToServer(new CatalogStateRequestPacket());
    }

    public static void sendTogglePower(ResourceLocation powerId, boolean enable) {
        CHANNEL.sendToServer(new TogglePowerPacket(powerId, enable));
    }

    public static void sendTogglePowerRequest(ResourceLocation powerId) {
        CHANNEL.sendToServer(new TogglePowerRequestPacket(powerId));
    }

    public static void sendEquipSuit(ResourceLocation suitId, boolean equip) {
        CHANNEL.sendToServer(new EquipSuitPacket(suitId, equip));
    }

    public static void sendUnequipSuit() {
        CHANNEL.sendToServer(new EquipSuitPacket(new ResourceLocation(Ascendant.MOD_ID, "catalog_unequip"), false));
    }

    public static void sendActivatePower(ResourceLocation powerId) {
        CHANNEL.sendToServer(new ActivatePowerPacket(powerId));
    }

    public static void sendSpeedsterControl(int action) {
        CHANNEL.sendToServer(new SpeedsterControlPacket(action));
    }

    public static void sendSpeedsterInput(int inputMask) {
        CHANNEL.sendToServer(new SpeedsterInputPacket(inputMask));
    }

    public static void syncSpeedsterState(ServerPlayer player, boolean active, int level) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new SpeedsterStatePacket(player.getId(), active, level));
    }

    public static void syncSpeedsterState(ServerPlayer recipient, int targetEntityId,
                                          boolean active, int level) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> recipient),
                new SpeedsterStatePacket(targetEntityId, active, level));
    }

    /** Re-sends the authoritative catalog snapshot, for example after an automatic mode stop. */
    public static void syncCatalogState(ServerPlayer player) {
        sendCatalogState(player);
    }

    private static void sendAbilityResult(ServerPlayer player, PowerRuntimeEngine.AbilityResult result,
                                          ResourceLocation abilityId) {
        if (result == PowerRuntimeEngine.AbilityResult.COOLDOWN) {
            long remaining = PowerRuntimeEngine.getCooldownRemainingTicks(player, abilityId);
            player.displayClientMessage(Component.translatable(
                    "message.ascendant.ability.cooldown_ticks", abilityId.toString(), remaining), true);
            return;
        }
        player.displayClientMessage(Component.translatable(result.translationKey(), abilityId.toString()), true);
    }

    private static void sendTogglePowerResult(ServerPlayer player, ResourceLocation powerId, boolean enable) {
        String failure = PowerRuntimeEngine.getPowerToggleFailureReason(player, powerId, enable);
        if (failure != null) {
            player.displayClientMessage(Component.translatable(failure, powerId.toString()), true);
            return;
        }
        boolean toggled = PowerRuntimeEngine.togglePower(player, powerId, enable);
        player.displayClientMessage(Component.translatable(
                toggled ? enable ? "message.ascendant.power.toggle_enabled"
                        : "message.ascendant.power.toggle_disabled"
                        : "message.ascendant.power.toggle_failed",
                powerId.toString()), true);
    }

    private static void sendCatalogState(ServerPlayer player) {
        Set<ResourceLocation> powers = PowerRuntimeEngine.getAllActiveAndEquippedPowers(player);
        Set<ResourceLocation> abilities = new HashSet<>();
        Set<ResourceLocation> toggledPowers = new HashSet<>();
        Set<ResourceLocation> toggledAbilities = new HashSet<>();
        Map<ResourceLocation, Integer> cooldowns = new HashMap<>();
        for (ResourceLocation powerId : powers) {
            PowerDefinition power = br.com.ascendant.power.PowerRegistry.get(powerId);
            if (power != null && power.isFunctional()
                    && (power.getType() == br.com.ascendant.core.PowerType.TOGGLE
                    || power.getType() == br.com.ascendant.core.PowerType.SUSTAINED)
                    && PowerRuntimeEngine.isPowerToggled(player, powerId)) {
                toggledPowers.add(powerId);
            }
            long remaining = PowerRuntimeEngine.getCooldownRemainingTicks(player, powerId);
            if (remaining > 0) {
                cooldowns.put(powerId, (int) Math.min(Integer.MAX_VALUE, remaining));
            }
        }
        for (AbilityDefinition ability : PowerRuntimeEngine.getAccessibleAbilities(player)) {
            abilities.add(ability.getId());
            if (PowerRuntimeEngine.isAbilityToggled(player, ability.getId())) {
                toggledAbilities.add(ability.getId());
            }
            long remaining = PowerRuntimeEngine.getCooldownRemainingTicks(player, ability.getId());
            if (remaining > 0) {
                cooldowns.put(ability.getId(), (int) Math.min(Integer.MAX_VALUE, remaining));
            }
        }
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new CatalogStatePacket(powers, abilities, toggledPowers, toggledAbilities, cooldowns,
                        SuitManager.getEquippedSuitId(player)));
    }

    private static void writeIds(FriendlyByteBuf buf, Collection<ResourceLocation> ids) {
        if (ids.size() > 4096) {
            throw new IllegalArgumentException("Too many catalog IDs to synchronize.");
        }
        buf.writeVarInt(ids.size());
        ids.forEach(buf::writeResourceLocation);
    }

    private static Set<ResourceLocation> readIds(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        if (size < 0 || size > 4096) {
            throw new IllegalArgumentException("Invalid catalog ID count: " + size);
        }
        Set<ResourceLocation> ids = new HashSet<>(size);
        for (int index = 0; index < size; index++) {
            ids.add(buf.readResourceLocation());
        }
        return ids;
    }

    public record CatalogStateRequestPacket() {
        public static void encode(CatalogStateRequestPacket msg, FriendlyByteBuf buf) {
        }

        public static CatalogStateRequestPacket decode(FriendlyByteBuf buf) {
            return new CatalogStateRequestPacket();
        }

        public static void handle(CatalogStateRequestPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            ctx.enqueueWork(() -> {
                ServerPlayer player = ctx.getSender();
                if (player != null) {
                    sendCatalogState(player);
                }
            });
            ctx.setPacketHandled(true);
        }
    }

    public record CatalogStatePacket(Set<ResourceLocation> powers, Set<ResourceLocation> abilities,
                                    Set<ResourceLocation> toggledPowers, Set<ResourceLocation> toggledAbilities,
                                    Map<ResourceLocation, Integer> cooldowns, ResourceLocation equippedSuit) {
        public static void encode(CatalogStatePacket msg, FriendlyByteBuf buf) {
            writeIds(buf, msg.powers());
            writeIds(buf, msg.abilities());
            writeIds(buf, msg.toggledPowers());
            writeIds(buf, msg.toggledAbilities());
            if (msg.cooldowns().size() > 4096) {
                throw new IllegalArgumentException("Too many catalog cooldowns to synchronize.");
            }
            buf.writeVarInt(msg.cooldowns().size());
            msg.cooldowns().forEach((id, ticks) -> {
                buf.writeResourceLocation(id);
                buf.writeVarInt(Math.max(0, ticks));
            });
            buf.writeBoolean(msg.equippedSuit() != null);
            if (msg.equippedSuit() != null) {
                buf.writeResourceLocation(msg.equippedSuit());
            }
        }

        public static CatalogStatePacket decode(FriendlyByteBuf buf) {
            Set<ResourceLocation> powers = readIds(buf);
            Set<ResourceLocation> abilities = readIds(buf);
            Set<ResourceLocation> toggledPowers = readIds(buf);
            Set<ResourceLocation> toggledAbilities = readIds(buf);
            int cooldownCount = buf.readVarInt();
            if (cooldownCount < 0 || cooldownCount > 4096) {
                throw new IllegalArgumentException("Invalid catalog cooldown count: " + cooldownCount);
            }
            Map<ResourceLocation, Integer> cooldowns = new HashMap<>(cooldownCount);
            for (int index = 0; index < cooldownCount; index++) {
                cooldowns.put(buf.readResourceLocation(), Math.max(0, buf.readVarInt()));
            }
            ResourceLocation suitId = buf.readBoolean() ? buf.readResourceLocation() : null;
            return new CatalogStatePacket(powers, abilities, toggledPowers, toggledAbilities, cooldowns, suitId);
        }

        public static void handle(CatalogStatePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            ctx.enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                    net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> br.com.ascendant.ui.client.CatalogClientState.update(
                            msg.powers(), msg.abilities(), msg.toggledPowers(), msg.toggledAbilities(),
                            msg.cooldowns(), msg.equippedSuit())));
            ctx.setPacketHandled(true);
        }
    }

    public record SpeedsterControlPacket(int action) {
        public static void encode(SpeedsterControlPacket msg, FriendlyByteBuf buf) {
            buf.writeByte(msg.action());
        }

        public static SpeedsterControlPacket decode(FriendlyByteBuf buf) {
            return new SpeedsterControlPacket(buf.readUnsignedByte());
        }

        public static void handle(SpeedsterControlPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            ctx.enqueueWork(() -> {
                ServerPlayer player = ctx.getSender();
                if (player != null) {
                    SpeedsterController.handleControl(player, msg.action());
                }
            });
            ctx.setPacketHandled(true);
        }
    }

    public record SpeedsterInputPacket(int inputMask) {
        public static void encode(SpeedsterInputPacket msg, FriendlyByteBuf buf) {
            buf.writeByte(msg.inputMask());
        }

        public static SpeedsterInputPacket decode(FriendlyByteBuf buf) {
            return new SpeedsterInputPacket(buf.readUnsignedByte());
        }

        public static void handle(SpeedsterInputPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            ctx.enqueueWork(() -> {
                ServerPlayer player = ctx.getSender();
                if (player != null) {
                    SpeedsterController.acceptInput(player, msg.inputMask());
                }
            });
            ctx.setPacketHandled(true);
        }
    }

    public record SpeedsterStatePacket(int entityId, boolean active, int level) {
        public static void encode(SpeedsterStatePacket msg, FriendlyByteBuf buf) {
            buf.writeVarInt(msg.entityId());
            buf.writeBoolean(msg.active());
            buf.writeByte(msg.level());
        }

        public static SpeedsterStatePacket decode(FriendlyByteBuf buf) {
            return new SpeedsterStatePacket(buf.readVarInt(), buf.readBoolean(), buf.readUnsignedByte());
        }

        public static void handle(SpeedsterStatePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            ctx.enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                    net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> br.com.ascendant.speedster.client.SpeedsterClientPackets
                            .update(msg.entityId(), msg.active(), msg.level())));
            ctx.setPacketHandled(true);
        }
    }

    public record ActivatePowerPacket(ResourceLocation powerId) {
        public static void encode(ActivatePowerPacket msg, FriendlyByteBuf buf) {
            buf.writeResourceLocation(msg.powerId());
        }

        public static ActivatePowerPacket decode(FriendlyByteBuf buf) {
            return new ActivatePowerPacket(buf.readResourceLocation());
        }

        public static void handle(ActivatePowerPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            ctx.enqueueWork(() -> {
                ServerPlayer player = ctx.getSender();
                if (player != null) {
                    boolean activated = PowerRuntimeEngine.activatePower(player, msg.powerId());
                    player.sendSystemMessage(Component.translatable(
                            activated ? "message.ascendant.power.activated"
                                    : "message.ascendant.power.activation_failed",
                            msg.powerId().toString()));
                    sendCatalogState(player);
                }
            });
            ctx.setPacketHandled(true);
        }
    }

    public record ActivateAbilityPacket(ResourceLocation abilityId) {
        public static void encode(ActivateAbilityPacket msg, FriendlyByteBuf buf) {
            buf.writeResourceLocation(msg.abilityId());
        }

        public static ActivateAbilityPacket decode(FriendlyByteBuf buf) {
            return new ActivateAbilityPacket(buf.readResourceLocation());
        }

        public static void handle(ActivateAbilityPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            ctx.enqueueWork(() -> {
                ServerPlayer player = ctx.getSender();
                if (player != null) {
                    PowerRuntimeEngine.AbilityResult result =
                            PowerRuntimeEngine.activateAbilityWithResult(player, msg.abilityId());
                    sendAbilityResult(player, result, msg.abilityId());
                    sendCatalogState(player);
                }
            });
            ctx.setPacketHandled(true);
        }
    }

    public record TogglePowerPacket(ResourceLocation powerId, boolean enable) {
        public static void encode(TogglePowerPacket msg, FriendlyByteBuf buf) {
            buf.writeResourceLocation(msg.powerId());
            buf.writeBoolean(msg.enable());
        }

        public static TogglePowerPacket decode(FriendlyByteBuf buf) {
            return new TogglePowerPacket(buf.readResourceLocation(), buf.readBoolean());
        }

        public static void handle(TogglePowerPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            ctx.enqueueWork(() -> {
                ServerPlayer player = ctx.getSender();
                if (player != null) {
                    sendTogglePowerResult(player, msg.powerId(), msg.enable());
                    sendCatalogState(player);
                }
            });
            ctx.setPacketHandled(true);
        }
    }

    public record TogglePowerRequestPacket(ResourceLocation powerId) {
        public static void encode(TogglePowerRequestPacket msg, FriendlyByteBuf buf) {
            buf.writeResourceLocation(msg.powerId());
        }

        public static TogglePowerRequestPacket decode(FriendlyByteBuf buf) {
            return new TogglePowerRequestPacket(buf.readResourceLocation());
        }

        public static void handle(TogglePowerRequestPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            ctx.enqueueWork(() -> {
                ServerPlayer player = ctx.getSender();
                if (player != null) {
                    boolean enable = !PowerRuntimeEngine.isPowerToggled(player, msg.powerId());
                    sendTogglePowerResult(player, msg.powerId(), enable);
                    sendCatalogState(player);
                }
            });
            ctx.setPacketHandled(true);
        }
    }

    public record EquipSuitPacket(ResourceLocation suitId, boolean equip) {
        public static void encode(EquipSuitPacket msg, FriendlyByteBuf buf) {
            buf.writeResourceLocation(msg.suitId());
            buf.writeBoolean(msg.equip());
        }

        public static EquipSuitPacket decode(FriendlyByteBuf buf) {
            return new EquipSuitPacket(buf.readResourceLocation(), buf.readBoolean());
        }

        public static void handle(EquipSuitPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            ctx.enqueueWork(() -> {
                ServerPlayer player = ctx.getSender();
                if (player != null) {
                    if (msg.equip()) {
                        boolean equipped = SuitManager.equipSuit(player, msg.suitId());
                        player.sendSystemMessage(Component.translatable(
                                equipped ? "message.ascendant.suit.equipped"
                                        : "message.ascendant.suit.equip_failed",
                                msg.suitId().toString()));
                    } else {
                        if (SuitManager.unequipSuit(player)) {
                            player.sendSystemMessage(Component.translatable("message.ascendant.suit.unequipped"));
                        }
                    }
                    sendCatalogState(player);
                }
            });
            ctx.setPacketHandled(true);
        }
    }

    public record ToggleAbilityRequestPacket(ResourceLocation abilityId) {
        public static void encode(ToggleAbilityRequestPacket msg, FriendlyByteBuf buf) {
            buf.writeResourceLocation(msg.abilityId());
        }

        public static ToggleAbilityRequestPacket decode(FriendlyByteBuf buf) {
            return new ToggleAbilityRequestPacket(buf.readResourceLocation());
        }

        public static void handle(ToggleAbilityRequestPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            ctx.enqueueWork(() -> {
                ServerPlayer player = ctx.getSender();
                if (player != null) {
                    boolean enable = !PowerRuntimeEngine.isAbilityToggled(player, msg.abilityId());
                    PowerRuntimeEngine.AbilityResult result =
                            PowerRuntimeEngine.toggleAbility(player, msg.abilityId(), enable);
                    sendAbilityResult(player, result, msg.abilityId());
                    sendCatalogState(player);
                }
            });
            ctx.setPacketHandled(true);
        }
    }
}
