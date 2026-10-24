package br.com.ascendant.speedster.client;

import br.com.ascendant.speedster.SpeedsterLevel;
import br.com.ascendant.speedster.SpeedsterState;
import br.com.ascendant.speedster.SpeedsterStateProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public final class SpeedsterClientPackets {
    private SpeedsterClientPackets() {
    }

    public static void update(int entityId, boolean active, int level) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        Entity entity = minecraft.level.getEntity(entityId);
        if (entity instanceof Player player) {
            player.getCapability(SpeedsterStateProvider.CAPABILITY).ifPresent(state -> {
                state.setLevel(SpeedsterLevel.byId(level));
                state.setActive(active);
            });
        }
    }
}
