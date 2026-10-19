package br.com.ascendant.flight;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

final class FlightClientPackets {
    private FlightClientPackets() {
    }

    static void update(int entityId, boolean active, boolean noGravity) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        Entity entity = minecraft.level.getEntity(entityId);
        if (entity instanceof Player player) {
            player.getCapability(FlightStateProvider.CAPABILITY)
                    .ifPresent(state -> state.setActive(active));
        }
        if (minecraft.player != null && minecraft.player.getId() == entityId) {
            minecraft.player.setNoGravity(noGravity);
            FlightClientInput.onServerState(active);
        }
    }
}
