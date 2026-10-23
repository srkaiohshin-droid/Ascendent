package br.com.ascendant.flight;

import br.com.ascendant.Ascendant;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

public final class FlightNetwork {
    private static final String PROTOCOL = "2";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Ascendant.MOD_ID, "flight"), () -> PROTOCOL,
            PROTOCOL::equals, PROTOCOL::equals);
    private static boolean registered;

    private FlightNetwork() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        int id = 0;
        CHANNEL.registerMessage(id++, ToggleRequest.class, ToggleRequest::encode, ToggleRequest::decode,
                ToggleRequest::handle, OptionalDirection.PLAY_TO_SERVER);
        CHANNEL.registerMessage(id++, InputUpdate.class, InputUpdate::encode, InputUpdate::decode,
                InputUpdate::handle, OptionalDirection.PLAY_TO_SERVER);
        CHANNEL.registerMessage(id++, ManeuverRequest.class, ManeuverRequest::encode,
                ManeuverRequest::decode, ManeuverRequest::handle, OptionalDirection.PLAY_TO_SERVER);
        CHANNEL.registerMessage(id, FlightStateUpdate.class, FlightStateUpdate::encode,
                FlightStateUpdate::decode, FlightStateUpdate::handle, OptionalDirection.PLAY_TO_CLIENT);
        registered = true;
    }

    public static void requestActive(boolean active) {
        CHANNEL.sendToServer(new ToggleRequest(active));
    }

    public static void sendInput(int inputMask) {
        CHANNEL.sendToServer(new InputUpdate(inputMask));
    }

    public static void requestManeuver(int direction) {
        CHANNEL.sendToServer(new ManeuverRequest(direction));
    }

    static void syncState(ServerPlayer player) {
        FlightState state = FlightController.getState(player);
        boolean active = state != null && state.isActive();
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new FlightStateUpdate(player.getId(), active, player.isNoGravity(),
                        state == null ? 0 : state.getManeuverDirection(),
                        state == null ? 0 : state.getManeuverTicksRemaining()));
    }

    static void syncTrackedPlayer(ServerPlayer recipient, ServerPlayer target) {
        FlightState state = FlightController.getState(target);
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> recipient),
                new FlightStateUpdate(target.getId(), state != null && state.isActive(), target.isNoGravity(),
                        state == null ? 0 : state.getManeuverDirection(),
                        state == null ? 0 : state.getManeuverTicksRemaining()));
    }

    private static void handleToggle(ToggleRequest message, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            if (sender != null) {
                FlightController.requestActive(sender, message.active());
            }
        });
        ctx.setPacketHandled(true);
    }

    private static void handleInput(InputUpdate message, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            if (sender != null) {
                FlightController.acceptInput(sender, message.inputMask());
            }
        });
        ctx.setPacketHandled(true);
    }

    private static void handleManeuver(ManeuverRequest message, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            if (sender != null) {
                FlightController.requestManeuver(sender, message.direction());
            }
        });
        ctx.setPacketHandled(true);
    }

    private static void handleState(FlightStateUpdate message, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> FlightClientPackets.update(message.entityId(), message.active(), message.noGravity(),
                        message.maneuverDirection(), message.maneuverTicksRemaining())));
        ctx.setPacketHandled(true);
    }

    private record ToggleRequest(boolean active) {
        private static void encode(ToggleRequest message, FriendlyByteBuf buffer) {
            buffer.writeBoolean(message.active());
        }

        private static ToggleRequest decode(FriendlyByteBuf buffer) {
            return new ToggleRequest(buffer.readBoolean());
        }

        private static void handle(ToggleRequest message, Supplier<NetworkEvent.Context> context) {
            FlightNetwork.handleToggle(message, context);
        }
    }

    private record InputUpdate(int inputMask) {
        private static void encode(InputUpdate message, FriendlyByteBuf buffer) {
            buffer.writeByte(message.inputMask());
        }

        private static InputUpdate decode(FriendlyByteBuf buffer) {
            return new InputUpdate(buffer.readUnsignedByte());
        }

        private static void handle(InputUpdate message, Supplier<NetworkEvent.Context> context) {
            FlightNetwork.handleInput(message, context);
        }
    }

    private record ManeuverRequest(int direction) {
        private static void encode(ManeuverRequest message, FriendlyByteBuf buffer) {
            buffer.writeByte(message.direction());
        }

        private static ManeuverRequest decode(FriendlyByteBuf buffer) {
            return new ManeuverRequest(buffer.readByte());
        }

        private static void handle(ManeuverRequest message, Supplier<NetworkEvent.Context> context) {
            FlightNetwork.handleManeuver(message, context);
        }
    }

    private record FlightStateUpdate(int entityId, boolean active, boolean noGravity,
                                     int maneuverDirection, int maneuverTicksRemaining) {
        private static void encode(FlightStateUpdate message, FriendlyByteBuf buffer) {
            buffer.writeVarInt(message.entityId());
            buffer.writeBoolean(message.active());
            buffer.writeBoolean(message.noGravity());
            buffer.writeByte(message.maneuverDirection());
            buffer.writeByte(message.maneuverTicksRemaining());
        }

        private static FlightStateUpdate decode(FriendlyByteBuf buffer) {
            return new FlightStateUpdate(buffer.readVarInt(), buffer.readBoolean(), buffer.readBoolean(),
                    buffer.readByte(), buffer.readUnsignedByte());
        }

        private static void handle(FlightStateUpdate message, Supplier<NetworkEvent.Context> context) {
            FlightNetwork.handleState(message, context);
        }
    }

    private static final class OptionalDirection {
        private static final java.util.Optional<NetworkDirection> PLAY_TO_SERVER =
                java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER);
        private static final java.util.Optional<NetworkDirection> PLAY_TO_CLIENT =
                java.util.Optional.of(NetworkDirection.PLAY_TO_CLIENT);
    }
}
