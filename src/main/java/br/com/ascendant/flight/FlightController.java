package br.com.ascendant.flight;

import br.com.ascendant.power.runtime.PowerRuntimeEngine;
import br.com.ascendant.speedster.SpeedsterController;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class FlightController {
    public static final ResourceLocation FLIGHT_POWER = new ResourceLocation("ascendant", "flight");
    public static final int MANEUVER_COOLDOWN_TICKS = 24;
    private static final long INPUT_TIMEOUT_TICKS = 20L;
    private static final double SAFE_TRANSITION_MAX_HORIZONTAL = 0.28D;

    private FlightController() {
    }

    public static boolean requestActive(ServerPlayer player, boolean requestedActive) {
        FlightState state = getState(player);
        if (state == null) {
            return false;
        }
        if (!requestedActive) {
            deactivate(player, state, FlightState.END_MANUAL);
            FlightNetwork.syncState(player);
            return true;
        }
        if (!canActivate(player)) {
            deactivate(player, state, state.isActive() ? FlightState.END_REVOKED : FlightState.END_NONE);
            FlightNetwork.syncState(player);
            return false;
        }
        SpeedsterController.deactivateForFlight(player);
        if (!state.isActive()) {
            boolean groundLaunch = player.onGround();
            state.setHadNoGravity(player.isNoGravity());
            state.setActive(true);
            state.setLaunchedFromGround(groundLaunch);
            state.setHasLeftGround(!groundLaunch);
            player.setNoGravity(true);
            player.resetFallDistance();
            if (groundLaunch) {
                applyTakeoffImpulse(player, FlightTuning.TAKEOFF_IMPULSE.get());
            }
            FlightNetwork.syncState(player);
        }
        return true;
    }

    public static boolean canActivate(Player player) {
        if (!FlightTuning.ENABLED.get() || !player.isAlive() || player.isSpectator()
                || player.isPassenger() || player.isSleeping() || player.isInWater()
                || player.isInLava() || player.getAbilities().mayfly || player.level().isClientSide) {
            return false;
        }
        // Suit-granted flight must work exactly like intrinsically unlocked flight.
        return PowerRuntimeEngine.hasPowerAccess(player, FLIGHT_POWER);
    }

    public static void acceptInput(ServerPlayer player, int inputMask) {
        FlightState state = getState(player);
        if (state == null || !state.isActive() || !canMaintainFlight(player)) {
            if (state != null && state.isActive()) {
                deactivate(player, state, FlightState.END_REVOKED);
                FlightNetwork.syncState(player);
            }
            return;
        }
        state.acceptInput(inputMask, player.level().getGameTime());
    }

    /**
     * Requests a server-authorised full-body roll. The maneuver changes only the
     * rendered pose; position, velocity and collision remain untouched.
     */
    public static boolean requestManeuver(ServerPlayer player, int direction) {
        if (direction != -1 && direction != 1) {
            return false;
        }
        FlightState state = getState(player);
        if (state == null || !state.isActive() || !canMaintainFlight(player)) {
            return false;
        }
        if (!state.startManeuver(direction, FlightPoseLibrary.MANEUVER_TICKS)) {
            return false;
        }
        state.setManeuverCooldownTicks(MANEUVER_COOLDOWN_TICKS);
        FlightNetwork.syncState(player);
        return true;
    }

    public static void tickServer(Player player) {
        FlightState state = getState(player);
        if (state == null || !state.isActive()) {
            return;
        }
        ServerPlayer serverPlayer = player instanceof ServerPlayer server ? server : null;
        if (serverPlayer == null || !canMaintainFlight(player)) {
            if (serverPlayer != null) {
                deactivate(player, state, FlightState.END_REVOKED);
                FlightNetwork.syncState(serverPlayer);
            }
            return;
        }

        if (!player.isNoGravity()) {
            player.setNoGravity(true);
        }
        player.resetFallDistance();

        // The maneuver is visual-only, but the server owns its progress and
        // broadcasts the remaining ticks so every client stays in sync.
        boolean maneuverWasActive = state.getManeuverTicksRemaining() > 0;
        state.tickManeuver();
        if (maneuverWasActive) {
            FlightNetwork.syncState(serverPlayer);
        }

        if (state.getLastInputGameTick() == Long.MIN_VALUE
                || player.level().getGameTime() - state.getLastInputGameTick() > INPUT_TIMEOUT_TICKS) {
            state.clearInput();
        }
        if (!player.onGround()) {
            state.setHasLeftGround(true);
        } else if (state.hasLeftGround()
                && (state.getInputMask() & FlightState.ASCEND) == 0
                && player.getXRot() >= -10.0F
                && Math.abs(player.getDeltaMovement().y) <= FlightTuning.LANDING_SPEED.get()) {
            deactivate(player, state, FlightState.END_LANDED);
            FlightNetwork.syncState(serverPlayer);
            return;
        }

        player.setDeltaMovement(nextVelocity(player, state.getInputMask()));
    }

    public static void tickClient(Player player) {
        FlightState state = getState(player);
        if (state != null && state.isActive() && player.isLocalPlayer()) {
            player.setNoGravity(true);
            player.resetFallDistance();
            player.setDeltaMovement(nextVelocity(player, state.getInputMask()));
        }
    }

    public static void deactivateForLifecycle(Player player) {
        FlightState state = getState(player);
        if (state != null && state.isActive()) {
            deactivate(player, state, FlightState.END_LIFECYCLE);
        }
    }

    /**
     * Deactivates flight and sends the updated state to the player's client.
     * Used when a power is revoked externally (e.g. via command).
     */
    public static void stopAndSyncForRevoke(ServerPlayer player) {
        FlightState state = getState(player);
        if (state != null && state.isActive()) {
            deactivate(player, state, FlightState.END_REVOKED);
        }
        FlightNetwork.syncState(player);
    }

    /**
     * Sends the current flight state to the player's client.
     * Used after lifecycle events (login, respawn) to ensure client consistency.
     */
    public static void syncToClient(ServerPlayer player) {
        FlightNetwork.syncState(player);
    }

    public static FlightState getState(Player player) {
        return player.getCapability(FlightStateProvider.CAPABILITY).orElse(null);
    }

    /**
     * Applies the shared vertical takeoff impulse. The server uses it for its
     * own copy of the player and the owning client applies the same value from
     * the synced state packet, so dedication servers and prediction agree.
     */
    public static void applyTakeoffImpulse(Player player, double impulse) {
        if (impulse <= 0.0D || !player.onGround()) {
            return;
        }
        Vec3 current = player.getDeltaMovement();
        player.setDeltaMovement(current.x, Math.max(current.y, impulse), current.z);
    }

    public static boolean canMaintainFlight(Player player) {
        return player.isAlive() && !player.isSpectator() && !player.isPassenger()
                && !player.isSleeping() && !player.isInWater() && !player.isInLava()
                && !player.getAbilities().mayfly
                && PowerRuntimeEngine.hasPowerAccess(player, FLIGHT_POWER);
    }

    private static Vec3 nextVelocity(Player player, int inputMask) {
        return FlightPhysics.nextVelocity(
                player.getDeltaMovement(),
                player.getYRot(),
                player.getXRot(),
                inputMask,
                player.isSprinting(),
                FlightTuning.MAX_CRUISE_SPEED.get(),
                FlightTuning.MAX_BOOST_SPEED.get(),
                FlightTuning.ACCELERATION.get(),
                FlightTuning.BOOST_ACCELERATION.get(),
                FlightTuning.STEERING_FACTOR.get(),
                FlightTuning.DRAG.get(),
                FlightTuning.BRAKING.get(),
                FlightTuning.HOVER_STABILITY.get()
        );
    }

    private static void deactivate(Player player, FlightState state, int reason) {
        boolean restoreNoGravity = state.hadNoGravity();
        state.clear();
        // clear() wipes the session, so the presentation reason is recorded
        // afterwards and travels in the same state packet as the deactivation.
        state.setLastEndReason(reason);
        player.setNoGravity(restoreNoGravity);
        player.resetFallDistance();

        // Safe residual velocity transition: prevent unexpected fling or sliding
        Vec3 current = player.getDeltaMovement();
        if (player.onGround()) {
            player.setDeltaMovement(current.multiply(0.2D, 0.0D, 0.2D));
        } else {
            double hSpeed = Math.sqrt(current.x * current.x + current.z * current.z);
            double newX = current.x;
            double newZ = current.z;
            if (hSpeed > SAFE_TRANSITION_MAX_HORIZONTAL) {
                double scale = SAFE_TRANSITION_MAX_HORIZONTAL / hSpeed;
                newX *= scale;
                newZ *= scale;
            }
            double newY = Math.max(current.y, -0.2D);
            player.setDeltaMovement(new Vec3(newX, newY, newZ));
        }
    }
}
