package br.com.ascendant.flight;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

final class FlightClientPackets {
    private FlightClientPackets() {
    }

    static void update(int entityId, boolean active, boolean noGravity, int maneuverKind,
                       int maneuverDirection, int maneuverTicksRemaining, boolean inverted,
                       boolean launchedFromGround, int endReason,
                       float maneuverYaw, float maneuverSpeed, boolean maneuverAborted) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        Entity entity = minecraft.level.getEntity(entityId);
        if (entity instanceof Player player) {
            player.getCapability(FlightStateProvider.CAPABILITY).ifPresent(state -> {
                int previousKind = state.getManeuverKind();
                int previousDirection = state.getManeuverDirection();
                int previousRemaining = state.getManeuverTicksRemaining();
                state.setActive(active);
                state.setLastEndReason(endReason);
                if (active) {
                    state.setLaunchedFromGround(launchedFromGround);
                    state.setManeuver(maneuverKind, maneuverDirection, maneuverTicksRemaining);
                    state.setManeuverBasis(maneuverYaw, maneuverSpeed);
                    state.setInverted(inverted);
                    state.setManeuverAborted(maneuverAborted);
                    if (player == minecraft.player) {
                        boolean newManeuver = maneuverKind != FlightState.MANEUVER_NONE
                                && (previousKind != maneuverKind
                                || previousDirection != maneuverDirection
                                || previousRemaining <= 0);
                        if (newManeuver) {
                            // Local prediction only starts after the server has
                            // authorised the trajectory. A normal completion is
                            // allowed to play out; an abort clears it at once.
                            FlightController.startClientManeuver(maneuverKind,
                                    maneuverDirection, maneuverYaw, maneuverSpeed,
                                    maneuverTicksRemaining);
                        }
                        if (maneuverAborted) {
                            FlightController.abortClientManeuver();
                        }
                    }
                } else {
                    state.setLaunchedFromGround(false);
                    state.clearInput();
                    if (player == minecraft.player) {
                        FlightController.abortClientManeuver();
                    }
                }
            });
        }
        if (minecraft.player != null && minecraft.player.getId() == entityId) {
            minecraft.player.setNoGravity(noGravity);
            FlightClientInput.onServerState(active);
        }
    }
}
