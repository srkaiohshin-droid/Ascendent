package br.com.ascendant.flight;

import br.com.ascendant.Ascendant;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
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
     * When player dies, immediately cancel flight and restore gravity so corpses do not float.
     */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            FlightController.deactivateForLifecycle(player);
            FlightNetwork.syncState(player);
        }
    }

    /**
     * On clone (death respawn or End return): clear flight from the original.
     * The new entity starts with a clean state.
     */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        FlightController.deactivateForLifecycle(event.getOriginal());
    }

    /**
     * Send flight state on initial player login to the server.
     * Cleans up stale noGravity if a dirty server crash occurred during previous flight.
     */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            FlightState state = FlightController.getState(player);
            if (state == null || !state.isActive()) {
                if (player.isNoGravity() && !player.isSpectator() && !player.isCreative()) {
                    player.setNoGravity(false);
                }
            }
            FlightController.syncToClient(player);
        }
    }

    /**
     * Send flight state when the player respawns (after death or End return).
     * In Forge 1.20.1, PlayerLoggedInEvent only fires on connection join;
     * PlayerRespawnEvent handles world re-entry after death/End.
     */
    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            FlightState state = FlightController.getState(player);
            if (state == null || !state.isActive()) {
                if (player.isNoGravity() && !player.isSpectator() && !player.isCreative()) {
                    player.setNoGravity(false);
                }
            }
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

    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event) {
        if (event.getEntity() instanceof Player player) {
            FlightState state = FlightController.getState(player);
            if (state != null && state.isActive()) {
                event.setCanceled(true);
                event.setDistance(0.0F);
            }
        }
    }
}
