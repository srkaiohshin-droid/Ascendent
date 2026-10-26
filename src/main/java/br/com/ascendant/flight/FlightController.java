package br.com.ascendant.flight;

import br.com.ascendant.power.runtime.PowerRuntimeEngine;
import br.com.ascendant.speedster.SpeedsterController;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Server-authoritative flight controller.
 *
 * <p>Ordinary flight movement is executed from the player's {@code travel}
 * method by {@link FlightPhysics}, which reproduces the reference hover and
 * boost kernels. {@link #tickServer(Player)} only performs housekeeping,
 * collision-checked trajectory setup and landing detection; it does not set a
 * velocity target or a takeoff impulse.</p>
 *
 * <p>Scripted loops remain real server-owned paths. Their analytic step is
 * prepared here and moved from {@code travel} so vanilla gravity and friction
 * never alter the reviewed curve.</p>
 */
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
            // Turning off an already inactive flight must not clear another
            // system's noGravity flag or rewrite the player's velocity.
            if (state.isActive()) {
                deactivate(player, state, FlightState.END_MANUAL);
                FlightNetwork.syncState(player);
            }
            return true;
        }
        if (!canActivate(player)) {
            if (state.isActive()) {
                deactivate(player, state, FlightState.END_REVOKED);
                FlightNetwork.syncState(player);
            }
            return false;
        }
        SpeedsterController.deactivateForFlight(player);
        if (!state.isActive()) {
            // Flight owns movement from this tick on. Leaving the elytra flag
            // set would keep the fall-flying pose and hitbox active on top of
            // the flight pose, producing the double body rotation this system
            // explicitly avoids.
            if (player.isFallFlying()) {
                player.stopFallFlying();
            }
            boolean groundLaunch = player.onGround();
            state.setHadNoGravity(player.isNoGravity());
            state.setActive(true);
            state.setLaunchedFromGround(groundLaunch);
            state.setHasLeftGround(!groundLaunch);
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
     * <p>Rolls change only the rendered pose. Loops are real trajectories: the
     * server validates the entry state, sweeps the complete path against
     * collision and only then commits the maneuver. A refused trajectory never
     * moves the player and reports the reason on the action bar.</p>
     *
     * <p>The 180-degree heading change is not a maneuver. The player performs
     * it with ordinary look and movement input, so there is no fixed turn
     * radius, forced camera yaw or pre-programmed animation to trigger.</p>
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

    /**
     * Pre-travel housekeeping and scripted-path setup. Ordinary movement is
     * performed by {@link FlightPhysics#travel}.
     */
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
        // Another source can start fall flying mid-session; the flight kernel
        // owns travel, so the elytra flag must not stay set.
        if (player.isFallFlying()) {
            player.stopFallFlying();
        }
        // Keep the standing hitbox and camera height while flying. Crouching is
        // not a vertical control in the reference flight.
        maintainStandingPose(player);

        int kind = state.getManeuverKind();
        int remaining = state.getManeuverTicksRemaining();
        if (FlightManeuvers.isTrajectory(kind) && remaining > 0) {
            int duration = FlightManeuvers.duration(kind);
            int stepIndex = duration - remaining;
            // A collision on the previous move means the swept path did not
            // hold. Stop the script immediately instead of pushing through.
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
            player.setDeltaMovement(delta);
            // travel() moves this exact step and advances the maneuver.
            return;
        }

        if (state.getLastInputGameTick() == Long.MIN_VALUE
                || player.level().getGameTime() - state.getLastInputGameTick() > INPUT_TIMEOUT_TICKS) {
            state.clearInput();
        }
        if (!player.onGround()) {
            state.setHasLeftGround(true);
        } else if (state.hasLeftGround()) {
            // The reference flight ends as soon as a landed player touches the
            // ground again. There is no landing pose and no vertical input that
            // could keep it active.
            deactivate(player, state, FlightState.END_LANDED);
            FlightNetwork.syncState(serverPlayer);
        }
    }

    /** Client-side housekeeping only; movement happens in {@link #travel(Player)}. */
    public static void tickClient(Player player) {
        FlightState state = getState(player);
        if (state != null && state.isActive() && player.isLocalPlayer()) {
            player.setNoGravity(true);
            player.resetFallDistance();
            if (player.isFallFlying()) {
                player.stopFallFlying();
            }
            maintainStandingPose(player);
        } else {
            releaseStandingPose(player);
            if (clientManeuverKind != FlightState.MANEUVER_NONE) {
                clearClientManeuver();
            }
        }
    }

    /**
     * Executes the authoritative movement for one tick. Called from the
     * {@code Player#travel} mixin on both sides. Returns {@code false} to let
     * vanilla handle players without active flight.
     */
    public static boolean travel(Player player) {
        FlightState state = getState(player);
        if (state == null || !state.isActive()) {
            return false;
        }

        if (player.level().isClientSide) {
            if (!player.isLocalPlayer()) {
                return false;
            }
            if (clientManeuverKind != FlightState.MANEUVER_NONE
                    && travelClientManeuver(player)) {
                return true;
            }
        } else if (player instanceof ServerPlayer serverPlayer
                && FlightManeuvers.isTrajectory(state.getManeuverKind())
                && state.getManeuverTicksRemaining() > 0) {
            Vec3 delta = player.getDeltaMovement();
            player.move(MoverType.SELF, delta);
            advanceManeuver(serverPlayer, state);
            return true;
        }

        boolean rolling = isActiveRoll(state);
        FlightPhysics.travel(player, params(), state.getInputMask(),
                player.isSprinting(), player.tickCount, barrelRoll(state));
        if (rolling) {
            // The roll owns the current tick's motion, then advances. Doing it
            // here rather than in tickServer keeps the first step at progress
            // zero and the last at 0.9, matching the reference ten-tick timer.
            if (player instanceof ServerPlayer serverPlayer) {
                advanceManeuver(serverPlayer, state);
            } else if (player.isLocalPlayer()) {
                state.tickManeuver();
            }
        }
        return true;
    }

    /** Current barrel-roll phase, or {@link FlightPhysics.BarrelRoll#NONE}. */
    private static FlightPhysics.BarrelRoll barrelRoll(FlightState state) {
        if (!isActiveRoll(state)) {
            return FlightPhysics.BarrelRoll.NONE;
        }
        int duration = FlightManeuvers.duration(FlightState.MANEUVER_ROLL_360);
        int remaining = state.getManeuverTicksRemaining();
        if (duration <= 0 || remaining > duration) {
            return FlightPhysics.BarrelRoll.NONE;
        }
        float progress = 1.0F - remaining / (float) duration;
        return new FlightPhysics.BarrelRoll(state.getManeuverDirection(), progress);
    }

    private static boolean isActiveRoll(FlightState state) {
        return state.getManeuverKind() == FlightState.MANEUVER_ROLL_360
                && state.getManeuverTicksRemaining() > 0;
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

    /** Returns false when the local playback reached its end and physics may resume. */
    private static boolean travelClientManeuver(Player player) {
        int duration = Math.max(clientManeuverDuration,
                FlightManeuvers.duration(clientManeuverKind));
        if (duration <= 0 || clientManeuverStep >= duration) {
            clearClientManeuver();
            return false;
        }
        Vec3 delta = FlightManeuvers.stepDelta(clientManeuverKind, clientManeuverDirection,
                clientManeuverYaw, clientManeuverSpeed, clientManeuverStep, duration);
        player.setDeltaMovement(delta);
        player.move(MoverType.SELF, delta);
        clientManeuverStep++;
        return true;
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

    public static boolean canMaintainFlight(Player player) {
        return FlightTuning.ENABLED.get()
                && player.isAlive() && !player.isSpectator() && !player.isPassenger()
                && !player.isSleeping() && !player.isInWater() && !player.isInLava()
                && !player.getAbilities().mayfly
                && PowerRuntimeEngine.hasPowerAccess(player, FLIGHT_POWER);
    }

    private static FlightPhysics.Params params() {
        return new FlightPhysics.Params(
                FlightTuning.SPEED.get(),
                FlightTuning.BOOST_SPEED.get(),
                FlightTuning.HOVER_DRAG.get(),
                FlightTuning.HOVER_GRAVITY.get(),
                FlightTuning.HOVER_BOB.get());
    }

    /**
     * Keeps the standing pose while flight is active. Crouching must not shrink
     * the hitbox or lower the camera because it is not a flight control. The
     * pose is only forced when the standing box is actually clear; a low
     * ceiling still falls back to vanilla collision handling.
     */
    private static void maintainStandingPose(Player player) {
        if (player.isFallFlying() || !hasRoomForStandingPose(player)) {
            releaseStandingPose(player);
        } else {
            player.setForcedPose(Pose.STANDING);
        }
    }

    /**
     * {@code Entity.canEnterPose} is protected, so the same vanilla check is
     * reproduced here with the public dimension and collision API.
     */
    private static boolean hasRoomForStandingPose(Player player) {
        EntityDimensions dimensions = player.getDimensions(Pose.STANDING);
        AABB box = dimensions.makeBoundingBox(player.position()).deflate(1.0E-7D);
        return player.level().noCollision(player, box);
    }

    private static void releaseStandingPose(Player player) {
        if (player.getForcedPose() == Pose.STANDING) {
            player.setForcedPose(null);
        }
    }

    private static void deactivate(Player player, FlightState state, int reason) {
        boolean restoreNoGravity = state.hadNoGravity();
        releaseStandingPose(player);
        state.clear();
        // clear() wipes the session, so the presentation reason is recorded
        // afterwards and travels in the same state packet as the deactivation.
        state.setLastEndReason(reason);
        player.setNoGravity(restoreNoGravity);
        player.resetFallDistance();

        // Safe residual velocity transition: prevent unexpected fling or sliding.
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
