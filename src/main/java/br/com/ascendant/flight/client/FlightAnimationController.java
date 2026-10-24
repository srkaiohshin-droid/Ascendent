package br.com.ascendant.flight.client;

import br.com.ascendant.Ascendant;
import br.com.ascendant.flight.FlightAnimationSelector;
import br.com.ascendant.flight.FlightAnimationState;
import br.com.ascendant.flight.FlightController;
import br.com.ascendant.flight.FlightManeuvers;
import br.com.ascendant.flight.FlightMotionSample;
import br.com.ascendant.flight.FlightPose;
import br.com.ascendant.flight.FlightPoseLibrary;
import br.com.ascendant.flight.FlightPoseSample;
import br.com.ascendant.flight.FlightState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Client-only flight animation state machine.
 *
 * <p>One rig per player is advanced exactly once per client tick. Rendering
 * interpolates between the previous and current tick with the renderer partial
 * tick, so the pose is independent of the frame rate and never accumulates
 * rotation from frame to frame. The bank comes from the real change of the
 * horizontal trajectory, not from the camera yaw. When flight stops, a landed
 * session plays contact/recovery; other sessions blend back to vanilla.</p>
 */
@Mod.EventBusSubscriber(modid = Ascendant.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FlightAnimationController {
    private static final Map<Player, Rig> RIGS = new WeakHashMap<>();
    private static final float POSE_SMOOTHING = 0.32F;
    private static final float WEIGHT_STEP = 0.28F;
    private static final float BANK_SMOOTHING = 0.22F;
    private static final float ACCELERATION_SMOOTHING = 0.30F;
    private static final float LANDING_PROGRESS_SMOOTHING = 0.28F;
    private static final float LANDING_PROBE_HEIGHT = 1.6F;
    private static final float MAX_REMOTE_STEP = 3.0F;
    private static final int EXIT_MODE_NONE = 0;
    private static final int EXIT_MODE_BLEND = 1;
    private static final int EXIT_MODE_LANDING = 2;

    private static ClientLevel lastLevel;

    private FlightAnimationController() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            RIGS.clear();
            lastLevel = null;
            return;
        }
        if (level != lastLevel) {
            RIGS.clear();
            lastLevel = level;
        }

        Set<Player> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (AbstractClientPlayer player : level.players()) {
            FlightState state = FlightController.getState(player);
            if (state == null) {
                continue;
            }
            seen.add(player);
            Rig rig = RIGS.get(player);
            if (rig == null) {
                if (!state.isActive()) {
                    continue;
                }
                rig = new Rig();
                RIGS.put(player, rig);
            }
            rig.tick(player, state, minecraft);
        }
        RIGS.entrySet().removeIf(entry -> !seen.contains(entry.getKey())
                || entry.getValue().finished());
    }

    /** Returns the interpolated pose for the current render frame, or null for vanilla. */
    public static FlightPoseSample sample(Player player, float partialTick) {
        Rig rig = RIGS.get(player);
        if (rig == null) {
            return null;
        }
        float delta = Mth.clamp(partialTick, 0.0F, 1.0F);
        float weight = Mth.lerp(delta, rig.previousWeight, rig.weight);
        if (weight <= 1.0E-4F) {
            return null;
        }
        FlightPose pose = FlightPose.lerp(rig.previous, rig.current, delta);
        float maneuverPitch = FlightPose.lerpAngleDegrees(
                rig.previousManeuverPitch, rig.currentManeuverPitch, delta);
        float maneuverRoll = FlightPose.lerpAngleDegrees(
                rig.previousManeuverRoll, rig.currentManeuverRoll, delta);
        return new FlightPoseSample(pose, weight, maneuverPitch, maneuverRoll);
    }

    /** Snapshot used by the camera and FOV effects for the active maneuver. */
    public record ManeuverView(int kind, int direction, float progress) {
    }

    public static ManeuverView maneuverView(Player player, float partialTick) {
        Rig rig = RIGS.get(player);
        if (rig == null || rig.maneuverKind == FlightState.MANEUVER_NONE) {
            return null;
        }
        float progress = Mth.lerp(Mth.clamp(partialTick, 0.0F, 1.0F),
                rig.previousManeuverProgress, rig.currentManeuverProgress);
        return new ManeuverView(rig.maneuverKind, rig.maneuverDirection, progress);
    }

    /**
     * Bank used by the optional flight camera. It is the same smoothed value
     * that poses the body, so camera and model bank together instead of adding
     * two independent rolls.
     */
    public static float cameraBank(Player player, float partialTick) {
        Rig rig = RIGS.get(player);
        if (rig == null) {
            return 0.0F;
        }
        return Mth.lerp(Mth.clamp(partialTick, 0.0F, 1.0F), rig.previousBank, rig.currentBank);
    }

    private static final class Rig {
        private FlightPose previous = FlightPose.VANILLA;
        private FlightPose current = FlightPose.VANILLA;
        private FlightPose target = FlightPose.VANILLA;
        private float previousWeight;
        private float weight;
        private float previousManeuverRoll;
        private float currentManeuverRoll;
        private float previousManeuverPitch;
        private float currentManeuverPitch;
        private float previousManeuverProgress;
        private float currentManeuverProgress;
        private float maneuverRoll;
        private float maneuverPitch;
        private float maneuverProgress;
        private int maneuverKind = FlightState.MANEUVER_NONE;
        private int maneuverDirection;
        private int maneuverDuration;
        private int maneuverElapsed;
        private int completedKind = FlightState.MANEUVER_NONE;
        private int completedDirection;
        private boolean finishAfterTick;
        private float previousBank;
        private float currentBank;
        private float smoothedBank;
        private float smoothedAcceleration;
        private float smoothedLanding;
        private float landingImpact;
        private float lastSpeed;
        private float lastHeading = Float.NaN;
        private int activeTicks;
        private int stateTicks;
        private int exitTicks;
        private int recoveryTicks;
        private int exitMode = EXIT_MODE_NONE;
        private FlightAnimationState animationState = FlightAnimationState.EXIT;
        private boolean wasActive;
        private boolean finished;

        private void tick(Player player, FlightState state, Minecraft minecraft) {
            if (finished) {
                return;
            }
            boolean local = player == minecraft.player;
            Vec3 velocity = sampleVelocity(player, local);
            float speed = (float) velocity.length();
            float horizontalSpeed = (float) Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
            float verticalSpeed = (float) velocity.y;
            float trajectoryPitch = trajectoryPitch(velocity, horizontalSpeed);
            float turnRate = turnRate(horizontalSpeed, velocity);
            float aimError = aimError(player, velocity, horizontalSpeed);
            float acceleration = speed - lastSpeed;

            updateManeuver(state);
            boolean maneuvering = maneuverKind != FlightState.MANEUVER_NONE;

            smoothedAcceleration += (acceleration - smoothedAcceleration) * ACCELERATION_SMOOTHING;
            // A scripted trajectory owns its own bank/pitch. Letting the
            // measured turn rate add another bank here would double the roll
            // and produce a false spike at the top of a loop.
            float bankTarget = state.isActive() && !maneuvering && horizontalSpeed > 0.04F
                    ? Mth.clamp(turnRate * 2.4F,
                    -FlightPoseLibrary.MAX_BODY_ROLL, FlightPoseLibrary.MAX_BODY_ROLL)
                    : 0.0F;
            previousBank = currentBank;
            smoothedBank += (bankTarget - smoothedBank) * BANK_SMOOTHING;
            currentBank = smoothedBank;

            boolean braking = (state.getInputMask() & FlightState.BRAKE) != 0
                    || (!local && acceleration < -0.060F && speed > 0.12F);
            // Sprint is the boost input, but the streamlined boost pose must
            // follow real movement: holding sprint while hovering must not look
            // like a high-speed pass.
            boolean boosting = player.isSprinting()
                    && speed >= FlightAnimationSelector.SLOW_ENTER_SPEED * 0.5F;
            int takeoffTicks = state.wasLaunchedFromGround()
                    ? FlightPoseLibrary.TAKEOFF_GROUND_TICKS : FlightPoseLibrary.TAKEOFF_AIR_TICKS;

            if (state.isActive()) {
                if (!wasActive) {
                    activeTicks = 0;
                    exitTicks = 0;
                    recoveryTicks = 0;
                    exitMode = EXIT_MODE_NONE;
                    animationState = FlightAnimationState.EXIT;
                    stateTicks = 0;
                    finished = false;
                    smoothedBank = 0.0F;
                    currentBank = 0.0F;
                    previousBank = 0.0F;
                    smoothedLanding = 0.0F;
                    landingImpact = 0.0F;
                    smoothedAcceleration = 0.0F;
                    lastSpeed = 0.0F;
                    lastHeading = Float.NaN;
                }
                wasActive = true;
                activeTicks++;

                float groundDistance = distanceToGround(player);
                float rawLanding = 0.0F;
                boolean descending = verticalSpeed < -0.02F;
                boolean nearContact = groundDistance < 0.55F;
                if (activeTicks > takeoffTicks && groundDistance < LANDING_PROBE_HEIGHT
                        && verticalSpeed <= 0.02F
                        && (braking || descending || (horizontalSpeed < 0.30F && nearContact))) {
                    rawLanding = 1.0F - clamp01((groundDistance - 0.05F)
                            / (LANDING_PROBE_HEIGHT - 0.05F));
                }
                smoothedLanding += (rawLanding - smoothedLanding) * LANDING_PROGRESS_SMOOTHING;
                if (smoothedLanding < 0.002F) {
                    smoothedLanding = 0.0F;
                }
                landingImpact = clamp01(-verticalSpeed / 0.25F + horizontalSpeed / 0.90F);

                FlightMotionSample sample = FlightMotionSample.builder()
                        .active(true)
                        .activeTicks(activeTicks)
                        .launchedFromGround(state.wasLaunchedFromGround())
                        .speed(speed)
                        .horizontalSpeed(horizontalSpeed)
                        .verticalSpeed(verticalSpeed)
                        .trajectoryPitch(trajectoryPitch)
                        .acceleration(smoothedAcceleration)
                        // The scripted path owns the heading; measured turn
                        // and aim noise must not twist the neck during it.
                        .turnRate(maneuvering ? 0.0F : turnRate)
                        .aimError(maneuvering ? 0.0F : aimError)
                        .bank(smoothedBank)
                        .braking(braking)
                        .boosting(boosting)
                        .maneuvering(maneuvering)
                        .landing(smoothedLanding, landingImpact)
                        .build();

                FlightAnimationState next = FlightAnimationSelector.select(
                        animationState, stateTicks, sample);
                if (next != animationState) {
                    animationState = next;
                    stateTicks = 0;
                } else {
                    stateTicks++;
                }
                target = FlightPoseLibrary.target(animationState, sample);

                previousWeight = weight;
                weight = Math.min(1.0F, weight + WEIGHT_STEP);
            } else {
                if (wasActive) {
                    wasActive = false;
                    beginExit(player, state);
                } else if (exitMode == EXIT_MODE_NONE) {
                    finished = true;
                    return;
                }

                if (exitMode == EXIT_MODE_LANDING) {
                    tickLandingRecovery(player);
                } else {
                    tickExitBlend();
                }
            }

            previousManeuverRoll = currentManeuverRoll;
            currentManeuverRoll = maneuverRoll;
            previousManeuverPitch = currentManeuverPitch;
            currentManeuverPitch = maneuverPitch;
            previousManeuverProgress = currentManeuverProgress;
            currentManeuverProgress = maneuverProgress;

            previous = current;
            current = FlightPose.lerp(current, target, POSE_SMOOTHING);
            lastSpeed = speed;
            if (finishAfterTick) {
                finishAfterTick = false;
                finishPlayback(true);
            }
        }

        /**
         * Client-side playback of the server-authorised maneuver. It starts
         * only from synced state, advances once per client tick and is
         * independent of the frame rate. A normal completion packet lets the
         * remaining local steps finish; an abort stops them immediately.
         */
        private void updateManeuver(FlightState state) {
            if (!state.isActive()) {
                completedKind = FlightState.MANEUVER_NONE;
                completedDirection = 0;
                stopManeuver();
                return;
            }
            int kind = state.getManeuverKind();
            int direction = kind == FlightState.MANEUVER_NONE
                    ? 0 : state.getManeuverDirection();
            boolean aborted = state.wasManeuverAborted();

            if (kind == FlightState.MANEUVER_NONE) {
                completedKind = FlightState.MANEUVER_NONE;
                completedDirection = 0;
                if (maneuverKind != FlightState.MANEUVER_NONE) {
                    if (aborted) {
                        finishPlayback(false);
                    } else {
                        advancePlayback();
                    }
                }
                return;
            }

            if (maneuverKind == FlightState.MANEUVER_NONE) {
                if (kind == completedKind && direction == completedDirection) {
                    return;
                }
                maneuverKind = kind;
                maneuverDirection = direction;
                maneuverDuration = Math.max(1, FlightManeuvers.duration(kind));
                int remaining = Mth.clamp(state.getManeuverTicksRemaining(),
                        0, maneuverDuration);
                maneuverElapsed = maneuverDuration - remaining;
            }
            if (aborted) {
                finishPlayback(false);
                return;
            }
            advancePlayback();
        }

        private void advancePlayback() {
            if (maneuverKind == FlightState.MANEUVER_NONE) {
                return;
            }
            if (maneuverElapsed > maneuverDuration) {
                finishPlayback(true);
                return;
            }
            maneuverProgress = Mth.clamp(maneuverElapsed / (float) maneuverDuration,
                    0.0F, 1.0F);
            maneuverPitch = FlightManeuvers.bodyPitchOffset(maneuverKind, maneuverProgress);
            maneuverRoll = maneuverKind == FlightState.MANEUVER_ROLL_360
                    ? FlightPoseLibrary.maneuverRollProgress(
                    maneuverDirection, maneuverProgress)
                    : FlightManeuvers.bodyRollOffset(
                    maneuverKind, maneuverDirection, maneuverProgress);
            if (maneuverElapsed >= maneuverDuration) {
                // Render the exact end orientation once, then stop.
                finishAfterTick = true;
            } else {
                maneuverElapsed++;
            }
        }

        private void finishPlayback(boolean completed) {
            if (completed && maneuverKind != FlightState.MANEUVER_NONE) {
                completedKind = maneuverKind;
                completedDirection = maneuverDirection;
            }
            stopManeuver();
        }

        private void stopManeuver() {
            maneuverKind = FlightState.MANEUVER_NONE;
            maneuverDirection = 0;
            maneuverDuration = 0;
            maneuverElapsed = 0;
            maneuverProgress = 0.0F;
            maneuverPitch = 0.0F;
            maneuverRoll = 0.0F;
            finishAfterTick = false;
        }

        private void beginExit(Player player, FlightState state) {
            boolean landed = state.getLastEndReason() == FlightState.END_LANDED
                    || player.onGround() && distanceToGround(player) < 0.8F;
            if (landed) {
                exitMode = EXIT_MODE_LANDING;
                recoveryTicks = FlightPoseLibrary.LANDING_RECOVERY_TICKS;
                smoothedLanding = 1.0F;
                landingImpact = clamp01(0.5F + (float) player.getDeltaMovement().length());
                target = FlightPoseLibrary.target(FlightAnimationState.LANDING,
                        FlightMotionSample.builder()
                                .active(false)
                                .activeTicks(activeTicks)
                                .speed((float) player.getDeltaMovement().length())
                                .verticalSpeed((float) player.getDeltaMovement().y)
                                .bank(smoothedBank)
                                .landing(1.0F, landingImpact)
                                .recovery(0.0F)
                                .build());
            } else {
                exitMode = EXIT_MODE_BLEND;
                exitTicks = FlightPoseLibrary.EXIT_TICKS;
                target = FlightPose.VANILLA;
            }
            previousWeight = weight;
        }

        private void tickLandingRecovery(Player player) {
            recoveryTicks--;
            float progress = 1.0F - recoveryTicks
                    / (float) FlightPoseLibrary.LANDING_RECOVERY_TICKS;
            progress = clamp01(progress);
            float speed = (float) player.getDeltaMovement().length();
            FlightMotionSample sample = FlightMotionSample.builder()
                    .active(false)
                    .activeTicks(activeTicks)
                    .speed(speed)
                    .verticalSpeed((float) player.getDeltaMovement().y)
                    .bank(smoothedBank)
                    .landing(1.0F, landingImpact)
                    .recovery(progress)
                    .build();
            target = FlightPoseLibrary.target(FlightAnimationState.LANDING, sample);
            previousWeight = weight;
            if (recoveryTicks <= 0) {
                weight = 0.0F;
                finished = true;
            } else if (recoveryTicks < 4) {
                weight = recoveryTicks / 4.0F;
            } else {
                weight = Math.max(weight, 0.65F);
            }
            // Keep the bank target neutral while recovering.
            smoothedBank += (0.0F - smoothedBank) * BANK_SMOOTHING;
        }

        private void tickExitBlend() {
            previousWeight = weight;
            if (exitTicks > 0) {
                exitTicks--;
                weight = exitTicks / (float) FlightPoseLibrary.EXIT_TICKS;
            } else {
                weight = 0.0F;
                finished = true;
            }
            smoothedBank += (0.0F - smoothedBank) * BANK_SMOOTHING;
        }

        private boolean finished() {
            return finished && weight <= 1.0E-4F;
        }

        private static Vec3 sampleVelocity(Player player, boolean local) {
            Vec3 motion = player.getDeltaMovement();
            if (local) {
                return motion;
            }
            // Interpolated remote entities often report zero deltaMovement. The
            // per-tick position step is already a velocity; it must not be
            // multiplied by a guessed packet interval or the animation runs
            // three times too fast.
            Vec3 step = new Vec3(player.getX() - player.xOld, player.getY() - player.yOld,
                    player.getZ() - player.zOld);
            if (step.lengthSqr() > 1.0E-8D) {
                double length = step.length();
                return length > MAX_REMOTE_STEP ? step.scale(MAX_REMOTE_STEP / length) : step;
            }
            return motion;
        }

        private float turnRate(float horizontalSpeed, Vec3 velocity) {
            if (horizontalSpeed < 0.04F) {
                lastHeading = Float.NaN;
                return 0.0F;
            }
            float heading = (float) Math.toDegrees(Math.atan2(velocity.x, velocity.z));
            float rate = Float.isNaN(lastHeading) ? 0.0F : Mth.wrapDegrees(heading - lastHeading);
            lastHeading = heading;
            // atan2(x,z) decreases for a right-hand turn; expose positive as right.
            return Mth.clamp(-rate, -60.0F, 60.0F);
        }

        private static float aimError(Player player, Vec3 velocity, float horizontalSpeed) {
            if (horizontalSpeed < 0.04F) {
                return 0.0F;
            }
            Vec3 look = player.getViewVector(1.0F);
            double lookHorizontal = Math.sqrt(look.x * look.x + look.z * look.z);
            if (lookHorizontal < 1.0E-4D) {
                return 0.0F;
            }
            float velocityHeading = (float) Math.toDegrees(Math.atan2(velocity.x, velocity.z));
            float lookHeading = (float) Math.toDegrees(Math.atan2(look.x, look.z));
            return Mth.clamp(Mth.wrapDegrees(lookHeading - velocityHeading), -90.0F, 90.0F);
        }

        private static float trajectoryPitch(Vec3 velocity, float horizontalSpeed) {
            if (horizontalSpeed > 0.02F) {
                return (float) Math.toDegrees(Math.atan2(velocity.y, horizontalSpeed));
            }
            if (velocity.y > 0.02D) {
                return 90.0F;
            }
            if (velocity.y < -0.02D) {
                return -90.0F;
            }
            return 0.0F;
        }

        private static float distanceToGround(Player player) {
            double startY = player.getY() + 0.05D;
            Vec3 from = new Vec3(player.getX(), startY, player.getZ());
            Vec3 to = new Vec3(player.getX(), player.getY() - 3.0D, player.getZ());
            BlockHitResult hit = player.level().clip(new ClipContext(from, to,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (hit.getType() == HitResult.Type.MISS) {
                return 4.0F;
            }
            return (float) Math.max(0.0D, startY - hit.getLocation().y);
        }

        private static float clamp01(float value) {
            return Math.max(0.0F, Math.min(1.0F, value));
        }
    }
}
