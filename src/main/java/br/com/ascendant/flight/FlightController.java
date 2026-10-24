package br.com.ascendant.flight;

import br.com.ascendant.power.runtime.PowerRuntimeEngine;
import br.com.ascendant.speedster.SpeedsterController;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class FlightController {
    public static final ResourceLocation FLIGHT_POWER = new ResourceLocation("ascendant", "flight");
    public static final int MANEUVER_COOLDOWN_TICKS = 24;
    private static final long INPUT_TIMEOUT_TICKS = 20L;
    private static final double SAFE_TRANSITION_MAX_HORIZONTAL = 0.28D;

    // Local playback of a server-authorised trajectory. Only the owning client
    // advances it, and it never grants authority: the server owns the actual
    // path and aborts it authoritatively.
    private static int clientManeuverKind = FlightState.MANEUVER_NONE;
    private static int clientManeuverDirection;
    private static int clientManeuverDuration;
    private static int clientManeuverStep;
    private static float clientManeuverYaw;
    private static float clientManeuverSpeed;

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
     * Requests a server-authorised maneuver.
     *
     * <p>Rolls change only the rendered pose. Loops and reversals are real
     * trajectories: the server validates the entry state, sweeps the complete
     * path against collision and only then commits the maneuver. A refused
     * trajectory never moves the player and reports the reason on the action
     * bar.</p>
     */
    public static boolean requestManeuver(ServerPlayer player, int kind, int direction) {
        if (direction != -1 && direction != 1) {
            return false;
        }
        FlightState state = getState(player);
        if (state == null || !state.isActive() || !canMaintainFlight(player)) {
            return false;
        }
        if (state.getManeuverKind() != FlightState.MANEUVER_NONE
                || state.getManeuverTicksRemaining() > 0
                || state.getManeuverCooldownTicks() > 0) {
            return false;
        }
        int duration = FlightManeuvers.duration(kind);
        if (duration <= 0) {
            return false;
        }

        float basisYaw = player.getYRot();
        double basisSpeed = player.getDeltaMovement().length();
        if (FlightManeuvers.isTrajectory(kind)) {
            int problem = FlightManeuvers.entryProblem(player, kind);
            basisYaw = FlightManeuvers.basisYaw(player.getDeltaMovement(), player.getYRot());
            basisSpeed = FlightManeuvers.horizontalSpeed(player.getDeltaMovement());
            if (problem == FlightManeuvers.PROBLEM_NONE) {
                problem = FlightManeuverSafety.sweepProblem(
                        player, kind, direction, basisYaw, basisSpeed);
            }
            if (problem != FlightManeuvers.PROBLEM_NONE) {
                player.displayClientMessage(
                        Component.translatable(FlightManeuvers.problemKey(problem)), true);
                return false;
            }
        }

        if (!state.startManeuver(kind, direction, duration)) {
            return false;
        }
        state.setManeuverBasis(basisYaw, (float) basisSpeed);
        state.setManeuverAborted(false);
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

        int kind = state.getManeuverKind();
        int remaining = state.getManeuverTicksRemaining();
        if (FlightManeuvers.isTrajectory(kind) && remaining > 0) {
            int duration = FlightManeuvers.duration(kind);
            int stepIndex = duration - remaining;
            // A collision on the previous vanilla move means the swept path did
            // not hold. Stop the script immediately instead of pushing through.
            if (stepIndex > 0 && (player.horizontalCollision || player.verticalCollision)) {
                abortManeuver(serverPlayer, state);
                return;
            }
            Vec3 delta = FlightManeuvers.stepDelta(kind, state.getManeuverDirection(),
                    state.getManeuverYaw(), state.getManeuverSpeed(), stepIndex, duration);
            if (delta.lengthSqr() <= 1.0E-9D) {
                abortManeuver(serverPlayer, state);
                return;
            }
            // The swept path assumes the analytic step is the only horizontal
            // acceleration. Server-side movement input arrives from the client
            // and would otherwise shift the real path away from the reviewed
            // one, so the script consumes and discards it while it runs.
            player.xxa = 0.0F;
            player.zza = 0.0F;
            player.yya = 0.0F;
            player.setSprinting(false);
            if (kind == FlightState.MANEUVER_REVERSAL) {
                float progress = (stepIndex + 1) / (float) duration;
                float yaw = FlightManeuvers.yawAt(kind, state.getManeuverDirection(),
                        state.getManeuverYaw(), progress);
                player.setYRot(yaw);
                player.setYHeadRot(yaw);
            }
            player.setDeltaMovement(delta);
            advanceManeuver(serverPlayer, state);
            return;
        }

        advanceManeuver(serverPlayer, state);

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
            if (clientManeuverKind != FlightState.MANEUVER_NONE
                    && !tickClientManeuver(player)) {
                return;
            }
            player.setDeltaMovement(nextVelocity(player, state.getInputMask()));
        } else if (clientManeuverKind != FlightState.MANEUVER_NONE) {
            clearClientManeuver();
        }
    }

    /**
     * Starts local prediction for an already server-authorised trajectory. A
     * normal completion lets the local playback run to its final pose even if
     * the completion packet arrives first; an abort clears it immediately.
     */
    public static void startClientManeuver(int kind, int direction, float yawDegrees,
                                           float speed, int remainingTicks) {
        if (!FlightManeuvers.isTrajectory(kind)) {
            return;
        }
        int duration = FlightManeuvers.duration(kind);
        if (duration <= 0) {
            return;
        }
        clientManeuverKind = kind;
        clientManeuverDirection = direction;
        clientManeuverDuration = duration;
        clientManeuverStep = Mth.clamp(duration - remainingTicks, 0, duration);
        clientManeuverYaw = yawDegrees;
        clientManeuverSpeed = speed;
    }

    public static void abortClientManeuver() {
        clearClientManeuver();
    }

    /** Returns false when this tick was already consumed by the local playback. */
    private static boolean tickClientManeuver(Player player) {
        int duration = FlightManeuvers.duration(clientManeuverKind);
        if (duration <= 0 || clientManeuverStep >= duration) {
            clearClientManeuver();
            return true;
        }
        Vec3 delta = FlightManeuvers.stepDelta(clientManeuverKind, clientManeuverDirection,
                clientManeuverYaw, clientManeuverSpeed, clientManeuverStep, duration);
        if (clientManeuverKind == FlightState.MANEUVER_REVERSAL) {
            float progress = (clientManeuverStep + 1) / (float) duration;
            float yaw = FlightManeuvers.yawAt(clientManeuverKind, clientManeuverDirection,
                    clientManeuverYaw, progress);
            player.setYRot(yaw);
            player.setYHeadRot(yaw);
        }
        player.setDeltaMovement(delta);
        clientManeuverStep++;
        return false;
    }

    private static void clearClientManeuver() {
        clientManeuverKind = FlightState.MANEUVER_NONE;
        clientManeuverDirection = 0;
        clientManeuverDuration = 0;
        clientManeuverStep = 0;
        clientManeuverYaw = 0.0F;
        clientManeuverSpeed = 0.0F;
    }

    private static void advanceManeuver(ServerPlayer player, FlightState state) {
        boolean wasActive = state.getManeuverTicksRemaining() > 0;
        state.tickManeuver();
        if (wasActive && state.getManeuverTicksRemaining() == 0) {
            // Completion is the only reliable signal for remote clients that
            // the scripted path ended normally. Absence of a maneuver without
            // this sync could also mean a disconnect, so send it explicitly.
            FlightNetwork.syncState(player);
        }
    }

    private static void abortManeuver(ServerPlayer player, FlightState state) {
        state.clearManeuver();
        state.setManeuverAborted(true);
        state.setManeuverCooldownTicks(MANEUVER_COOLDOWN_TICKS);
        Vec3 current = player.getDeltaMovement();
        player.setDeltaMovement(current.scale(0.25D));
        FlightNetwork.syncState(player);
        player.displayClientMessage(
                Component.translatable("message.ascendant.flight.maneuver_aborted"), true);
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
