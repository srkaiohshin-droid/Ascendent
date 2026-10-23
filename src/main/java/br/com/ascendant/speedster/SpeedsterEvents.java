package br.com.ascendant.speedster;

import br.com.ascendant.Ascendant;
import br.com.ascendant.network.AscendantNetwork;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class SpeedsterEvents {
    private static final ResourceLocation CAPABILITY_ID =
            new ResourceLocation(Ascendant.MOD_ID, "speedster_state");

    private SpeedsterEvents() {
    }

    @SubscribeEvent
    public static void attachState(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            SpeedsterStateProvider provider = new SpeedsterStateProvider();
            event.addCapability(CAPABILITY_ID, provider);
            event.addListener(provider::invalidate);
        }
    }

    @SubscribeEvent
    public static void tickPlayer(TickEvent.PlayerTickEvent event) {
        if (event.side.isServer() && event.phase == TickEvent.Phase.END
                && event.player instanceof ServerPlayer player) {
            SpeedsterController.tickServer(player);
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        SpeedsterController.clearForLifecycle(event.getOriginal(), false);
        SpeedsterController.clearForLifecycle(event.getEntity(), false);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SpeedsterController.clearForLifecycle(player, false);
            SpeedsterController.syncState(player);
        }
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        SpeedsterController.clearForLifecycle(event.getEntity(), false);
        if (event.getEntity() instanceof ServerPlayer player) {
            SpeedsterController.syncState(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SpeedsterController.clearForLifecycle(event.getEntity(), false);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SpeedsterController.syncState(player);
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target
                && event.getEntity() instanceof ServerPlayer recipient) {
            SpeedsterState state = SpeedsterController.getState(target);
            if (state != null) {
                AscendantNetwork.syncSpeedsterState(recipient, target.getId(), state.isActive(), state.getLevel());
            }
        }
    }
}
