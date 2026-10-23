package br.com.ascendant.network;

import br.com.ascendant.Ascendant;
import br.com.ascendant.core.AbilityTrigger;
import br.com.ascendant.power.AbilityRegistry;
import br.com.ascendant.power.runtime.PowerRuntimeEngine;
import br.com.ascendant.speedster.SpeedsterController;
import br.com.ascendant.suit.SuitManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
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
        registered = true;
    }

    public static void sendActivateAbility(ResourceLocation abilityId) {
        CHANNEL.sendToServer(new ActivateAbilityPacket(abilityId));
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
                    PowerRuntimeEngine.activatePower(player, msg.powerId());
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
                    var ability = AbilityRegistry.get(msg.abilityId());
                    if (ability != null
                            && ability.getTrigger() == AbilityTrigger.TOGGLE) {
                        PowerRuntimeEngine.toggleAbility(player, msg.abilityId());
                    } else {
                        PowerRuntimeEngine.activateAbilityWithFeedback(player, msg.abilityId());
                    }
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
                    PowerRuntimeEngine.togglePower(player, msg.powerId(), msg.enable());
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
                    PowerRuntimeEngine.togglePower(player, msg.powerId(),
                            !PowerRuntimeEngine.isPowerToggled(player, msg.powerId()));
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
                        SuitManager.equipSuit(player, msg.suitId());
                    } else {
                        SuitManager.unequipSuit(player);
                    }
                }
            });
            ctx.setPacketHandled(true);
        }
    }
}
