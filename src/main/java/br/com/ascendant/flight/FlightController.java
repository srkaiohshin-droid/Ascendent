package br.com.ascendant.flight;

import br.com.ascendant.powers.IPlayerPowerData;
import br.com.ascendant.powers.PlayerPowerDataProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class FlightController {
    public static final ResourceLocation FLIGHT_POWER = new ResourceLocation("ascendant", "flight");
    private static final long INPUT_TIMEOUT_TICKS = 20L;
    private static final long TOGGLE_RATE_LIMIT_TICKS = 2L;
    private static final double SAFE_TRANSITION_MAX_HORIZONTAL = 0.28D;

    private FlightController() {
    }

    public static boolean requestActive(ServerPlayer player, boolean requestedActive) {
        FlightState state = getState(player);
        if (state == null) {
            return false;
        }

        long currentTick = player.level().getGameTime();
        if (state.getLastToggleGameTick() != Long.MIN_VALUE
                && currentTick - state.getLastToggleGameTick() < TOGGLE_RATE_LIMIT_TICKS) {
            return false;
        }

        if (!requestedActive) {
            if (state.isActive()) {
                state.setLastToggleGameTick(currentTick);
                deactivate(player, state);
                FlightNetwork.syncState(player);
                return true;
            }
            return false;
        }

        if (!canActivate(player)) {
            if (state.isActive()) {
                state.setLastToggleGameTick(currentTick);
                deactivate(player, state);
                FlightNetwork.syncState(player);
            } else {
                FlightNetwork.syncToClient(player);
            }
            return false;
        }

        if (!state.isActive()) {
            state.setLastToggleGameTick(currentTick);
            state.setHadNoGravity(player.isNoGravity());
            state.setActive(true);
            state.setHasLeftGround(!player.onGround());
            player.setNoGravity(true);
            player.resetFallDistance();
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
        return player.getCapability(PlayerPowerDataProvider.CAPABILITY)
                .map((IPlayerPowerData data) -> data.hasPower(FLIGHT_POWER)).orElse(false);
    }

    public static void acceptInput(ServerPlayer player, int inputMask) {
        FlightState state = getState(player);
        if (state == null || !state.isActive() || !canMaintainFlight(player)) {
            if (state != null && state.isActive()) {
                deactivate(player, state);
                FlightNetwork.syncState(player);
            }
            return;
        }
        state.acceptInput(inputMask, player.level().getGameTime());
    }

    public static void tickServer(Player player) {
        FlightState state = getState(player);
        if (state == null || !state.isActive()) {
            return;
        }
        ServerPlayer serverPlayer = player instanceof ServerPlayer server ? server : null;
        if (serverPlayer == null || !canMaintainFlight(player)) {
            if (serverPlayer != null) {
                deactivate(player, state);
                FlightNetwork.syncState(serverPlayer);
            }
            return;
        }

        if (!player.isNoGravity()) {
            player.setNoGravity(true);
        }
        player.resetFallDistance();

        if (state.getLastInputGameTick() == Long.MIN_VALUE
                || player.level().getGameTime() - state.getLastInputGameTick() > INPUT_TIMEOUT_TICKS) {
            state.clearInput();
        }

        int inputMask = state.getInputMask();
        if (!player.onGround()) {
            state.setHasLeftGround(true);
        } else if (shouldLand(player, state, inputMask)) {
            deactivate(player, state);
            FlightNetwork.syncState(serverPlayer);
            return;
        }

        player.setDeltaMovement(nextVelocity(player, inputMask));
    }

    public static boolean shouldLand(Player player, FlightState state, int inputMask) {
        if (!player.onGround() || !state.hasLeftGround()) {
            return false;
        }
        if ((inputMask & FlightState.ASCEND) != 0) {
            return false;
        }
        if (Math.abs(player.getDeltaMovement().y) > FlightTuning.LANDING_SPEED.get()) {
            return false;
        }

        // Touchdown condition: deliberate descent, brake, neutral glide, steep downward pitch, or slow taxiing
        if ((inputMask & (FlightState.DESCEND | FlightState.BRAKE)) != 0) {
            return true;
        }
        if (inputMask == 0) {
            return true;
        }
        if (player.getXRot() > 25.0F) {
            return true;
        }

        Vec3 delta = player.getDeltaMovement();
        double hSpeedSq = delta.x * delta.x + delta.z * delta.z;
        double cruiseSpeed = FlightTuning.MAX_CRUISE_SPEED.get();
        return hSpeedSq <= (cruiseSpeed * 0.4D) * (cruiseSpeed * 0.4D);
    }

    public static void tickClient(Player player) {
        FlightState state = getState(player);
        if (state != null && state.isActive() && player.isLocalPlayer()) {
            if (!player.isAlive() || player.isInWater() || player.isInLava()
                    || player.isPassenger() || player.isSleeping()) {
                player.setNoGravity(false);
                return;
            }
            player.setNoGravity(true);
            player.resetFallDistance();
            player.setDeltaMovement(nextVelocity(player, state.getInputMask()));
        }
    }

    public static void deactivateForLifecycle(Player player) {
        FlightState state = getState(player);
        if (state != null && state.isActive()) {
            deactivate(player, state);
        }
    }

    /**
     * Deactivates flight and sends the updated state to the player's client.
     * Used when a power is revoked externally (e.g. via command).
     */
    public static void stopAndSyncForRevoke(ServerPlayer player) {
        deactivateForLifecycle(player);
        FlightNetwork.syncState(player);
    }

    /**
     * Sends the current flight state to the player's client.
     * Used after lifecycle events (login, respawn) to ensure client consistency.
     */
    public static void syncToClient(ServerPlayer player) {
        FlightNetwork.syncToClient(player);
    }

    public static FlightState getState(Player player) {
        return player.getCapability(FlightStateProvider.CAPABILITY).orElse(null);
    }

    public static boolean canMaintainFlight(Player player) {
        return FlightTuning.ENABLED.get() && player.isAlive() && !player.isSpectator()
                && !player.isPassenger() && !player.isSleeping() && !player.isInWater()
                && !player.isInLava() && !player.getAbilities().mayfly
                && player.getCapability(PlayerPowerDataProvider.CAPABILITY)
                .map(data -> data.hasPower(FLIGHT_POWER)).orElse(false);
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

    private static void deactivate(Player player, FlightState state) {
        boolean restoreNoGravity = state.hadNoGravity();
        state.clear();
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
