package br.com.ascendant.flight;

import br.com.ascendant.Ascendant;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class FlightEvents {
    private static final ResourceLocation CAPABILITY_ID =
            new ResourceLocation(Ascendant.MOD_ID, "flight_state");

    private FlightEvents() {
    }

    @SubscribeEvent
    public static void attachFlightState(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            FlightStateProvider provider = new FlightStateProvider();
            event.addCapability(CAPABILITY_ID, provider);
            event.addListener(provider::invalidate);
        }
    }

    @SubscribeEvent
    public static void tickPlayer(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.START && !event.player.level().isClientSide) {
            FlightController.tickServer(event.player);
        }
    }

    /**
     * On clone (death respawn or End return): clear flight from the original.
     * The new entity starts with a clean state. After the player re-enters the
     * world, onLogin or StartTracking will sync state to the client.
     */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        // Only clear the original; new player flight state starts inactive by default.
        FlightController.deactivateForLifecycle(event.getOriginal());
    }

    /**
     * Send flight state on player login and after respawn.
     * In Forge 1.20.1, PlayerLoggedInEvent fires both on initial join and after
     * server respawn (the respawned player goes through placeNewPlayer again).
     */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            FlightController.syncToClient(player);
        }
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        FlightController.deactivateForLifecycle(event.getEntity());
        if (event.getEntity() instanceof ServerPlayer player) {
            FlightController.syncToClient(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        FlightController.deactivateForLifecycle(event.getEntity());
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target && event.getEntity() instanceof ServerPlayer recipient) {
            FlightNetwork.syncTrackedPlayer(recipient, target);
        }
    }
}
