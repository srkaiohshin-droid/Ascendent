package br.com.ascendant.powers;

import br.com.ascendant.Ascendant;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class PlayerPowerDataEvents {
    private static final ResourceLocation CAPABILITY_ID =
            new ResourceLocation(Ascendant.MOD_ID, "player_power_data");

    private PlayerPowerDataEvents() {
    }

    @SubscribeEvent
    public static void attachPlayerData(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            PlayerPowerDataProvider provider = new PlayerPowerDataProvider();
            event.addCapability(CAPABILITY_ID, provider);
            event.addListener(provider::invalidate);
        }
    }

    @SubscribeEvent
    public static void copyPlayerDataOnClone(PlayerEvent.Clone event) {
        event.getOriginal().reviveCaps();
        try {
            event.getOriginal().getCapability(PlayerPowerDataProvider.CAPABILITY).ifPresent(originalData ->
                    event.getEntity().getCapability(PlayerPowerDataProvider.CAPABILITY)
                            .ifPresent(newData -> newData.copyFrom(originalData)));
        } finally {
            event.getOriginal().invalidateCaps();
        }
    }
}
