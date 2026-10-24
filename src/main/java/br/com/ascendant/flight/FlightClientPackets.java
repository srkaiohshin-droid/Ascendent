package br.com.ascendant.flight;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

final class FlightClientPackets {
    private FlightClientPackets() {
    }

    static void update(int entityId, boolean active, boolean noGravity, int maneuverKind,
                       int maneuverDirection, int maneuverTicksRemaining, boolean inverted,
                       boolean launchedFromGround, int endReason, float takeoffImpulse) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        Entity entity = minecraft.level.getEntity(entityId);
        if (entity instanceof Player player) {
            player.getCapability(FlightStateProvider.CAPABILITY).ifPresent(state -> {
                boolean wasActive = state.isActive();
                state.setActive(active);
                state.setLastEndReason(endReason);
                if (active) {
                    state.setLaunchedFromGround(launchedFromGround);
                    state.setManeuver(maneuverKind, maneuverDirection, maneuverTicksRemaining);
                    state.setInverted(inverted);
                } else {
                    state.setLaunchedFromGround(false);
                    state.clearInput();
                }
                if (!wasActive && active && launchedFromGround && takeoffImpulse > 0.0F
                        && player == minecraft.player) {
                    // The owning client applies the exact same impulse as the
                    // server so local prediction and the server copy agree.
                    FlightController.applyTakeoffImpulse(player, takeoffImpulse);
                }
            });
        }
        if (minecraft.player != null && minecraft.player.getId() == entityId) {
            minecraft.player.setNoGravity(noGravity);
            FlightClientInput.onServerState(active);
        }
    }
}
