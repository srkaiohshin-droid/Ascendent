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
 * rotation from frame to frame.</p>
 *
 * <p>There is no velocity-headed body yaw: the model faces the player's look
 * direction at all times. A 180-degree reversal is a normal player-controlled
 * mouse turn. Hover leans with the real horizontal velocity, sprint blends into
 * the horizontal boost pose over the reference eight-tick timer, and a
 * server-authorised loop or roll overlays its own pitch/roll on that silhouette.
 * When flight stops, the whole pose fades back to vanilla.</p>
 */
@Mod.EventBusSubscriber(modid = Ascendant.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FlightAnimationController {
    private static final Map<Player, Rig> RIGS = new WeakHashMap<>();
    /**
     * Per-tick pose convergence. The reference has no extra smoothing beyond
     * its flight/boost timers, but a light client filter removes packet and
     * frame jitter. 0.35 settles in roughly half a second without lagging
     * behind the crosshair during a mouse turn; slower values made the boost
     * bank and pitch feel detached from the camera.
     */
    private static final float POSE_SMOOTHING = 0.35F;
    private static final float WEIGHT_STEP = 0.18F;
    private static final float MAX_REMOTE_STEP = 3.0F;
    private static final int EXIT_MODE_NONE = 0;
    private static final int EXIT_MODE_BLEND = 1;

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
        FlightPose overlay = FlightPose.lerp(
                rig.previousRollOverlay, rig.currentRollOverlay, delta);
        return new FlightPoseSample(pose, weight, maneuverPitch, maneuverRoll, overlay);
    }

    /** Snapshot used by the capture harness for maneuvre telemetry. */
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
        private FlightPose rollOverlay = FlightPose.VANILLA;
        private FlightPose previousRollOverlay = FlightPose.VANILLA;
        private FlightPose currentRollOverlay = FlightPose.VANILLA;
        private int maneuverKind = FlightState.MANEUVER_NONE;
        private int maneuverDirection;
        private int maneuverDuration;
        private int maneuverElapsed;
        private int completedKind = FlightState.MANEUVER_NONE;
        private int completedDirection;
        private boolean finishAfterTick;
        private int activeTicks;
        private float boostTimer;
        private int exitTicks;
        private int exitMode = EXIT_MODE_NONE;
        private FlightAnimationState animationState = FlightAnimationState.EXIT;
        private boolean wasActive;
        private boolean finished;

        private void tick(Player player, FlightState state, Minecraft minecraft) {
            if (finished) {
                return;
            }
            boolean local = player == minecraft.player;
            boolean active = state.isActive();
            boolean justActivated = active && !wasActive;
            if (justActivated) {
                resetSession();
            }

            updateManeuver(state);
            boolean maneuvering = maneuverKind != FlightState.MANEUVER_NONE;

            Vec3 velocity = sampleVelocity(player, local);
            float speed = (float) velocity.length();
            float horizontalSpeed = (float) Math.sqrt(
                    velocity.x * velocity.x + velocity.z * velocity.z);
            float verticalSpeed = (float) velocity.y;
            float bodyForward = bodyForward(player, velocity, horizontalSpeed);
            float bodyRight = bodyRight(player, velocity, horizontalSpeed);

            if (active) {
                wasActive = true;
                activeTicks++;
                FlightMotionSample sample = FlightMotionSample.builder()
                        .active(true)
                        .activeTicks(activeTicks)
                        .speed(speed)
                        .horizontalSpeed(horizontalSpeed)
                        .verticalSpeed(verticalSpeed)
                        .viewPitch(player.getXRot())
                        .bodyForward(bodyForward)
                        .bodyRight(bodyRight)
                        .boosting(player.isSprinting())
                        .maneuvering(maneuvering)
                        .idlePhase(FlightPoseLibrary.idlePhase(player.tickCount))
                        .build();
                animationState = FlightAnimationSelector.select(
                        animationState, activeTicks, sample);
                // The reference fills or drains the boost timer by one eighth
                // per tick and eases it before blending the boost layers. The
                // maneuvre overlay stays separate, so a roll or loop starts
                // from whatever ordinary silhouette the player is holding.
                boostTimer = FlightPoseLibrary.advanceBoostTimer(
                        boostTimer, sample.boosting());
                target = animationState == FlightAnimationState.EXIT
                        ? FlightPose.VANILLA
                        : FlightPoseLibrary.ordinaryPose(sample,
                        FlightPoseLibrary.boostBlend(boostTimer));
                previousWeight = weight;
                weight = Math.min(1.0F, weight + WEIGHT_STEP);
            } else {
                if (wasActive) {
                    wasActive = false;
                    beginExit();
                } else if (exitMode == EXIT_MODE_NONE) {
                    finished = true;
                    return;
                }
                tickExitBlend();
            }

            previousManeuverRoll = currentManeuverRoll;
            currentManeuverRoll = maneuverRoll;
            previousManeuverPitch = currentManeuverPitch;
            currentManeuverPitch = maneuverPitch;
            previousManeuverProgress = currentManeuverProgress;
            currentManeuverProgress = maneuverProgress;
            previousRollOverlay = currentRollOverlay;
            currentRollOverlay = rollOverlay;

            previous = current;
            current = FlightPose.lerp(current, target, POSE_SMOOTHING);
            if (finishAfterTick) {
                finishAfterTick = false;
                finishPlayback(true);
            }
        }

        private void resetSession() {
            activeTicks = 0;
            boostTimer = 0.0F;
            exitTicks = 0;
            exitMode = EXIT_MODE_NONE;
            animationState = FlightAnimationState.EXIT;
            finished = false;
            completedKind = FlightState.MANEUVER_NONE;
            completedDirection = 0;
            stopManeuver();
        }

        /**
         * Client-side playback of the server-authorised maneuvre. It starts
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
                    : 0.0F;
            rollOverlay = maneuverKind == FlightState.MANEUVER_ROLL_360
                    ? FlightPoseLibrary.rollOverlay(maneuverDirection, maneuverProgress)
                    : FlightPose.VANILLA;
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
            rollOverlay = FlightPose.VANILLA;
            finishAfterTick = false;
        }

        private void beginExit() {
            exitMode = EXIT_MODE_BLEND;
            exitTicks = FlightPoseLibrary.EXIT_TICKS;
            target = FlightPose.VANILLA;
            previousWeight = weight;
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
        }

        private boolean finished() {
            return finished && weight <= 1.0E-4F;
        }

        /**
         * Horizontal velocity expressed in the player's yaw frame. The player
         * right vector at yaw zero is -X, matching Minecraft's camera axes.
         */
        private static float bodyForward(Player player, Vec3 velocity, float horizontalSpeed) {
            if (horizontalSpeed < 1.0E-4F) {
                return 0.0F;
            }
            double yaw = Math.toRadians(player.getYRot());
            double forwardX = -Math.sin(yaw);
            double forwardZ = Math.cos(yaw);
            return (float) ((velocity.x * forwardX + velocity.z * forwardZ) / horizontalSpeed);
        }

        private static float bodyRight(Player player, Vec3 velocity, float horizontalSpeed) {
            if (horizontalSpeed < 1.0E-4F) {
                return 0.0F;
            }
            double yaw = Math.toRadians(player.getYRot());
            double rightX = -Math.cos(yaw);
            double rightZ = -Math.sin(yaw);
            return (float) ((velocity.x * rightX + velocity.z * rightZ) / horizontalSpeed);
        }

        private static Vec3 sampleVelocity(Player player, boolean local) {
            // The reference animation and camera use the real per-tick position
            // step, not the stored delta movement. Hover stores extra drag in
            // deltaMovement while the entity actually travels farther, so using
            // the stored value would undershoot the reference lean and head.
            Vec3 step = new Vec3(player.getX() - player.xOld, player.getY() - player.yOld,
                    player.getZ() - player.zOld);
            if (step.lengthSqr() > 1.0E-8D) {
                if (local) {
                    return step;
                }
                // Remote interpolation can report a multi-block correction.
                double length = step.length();
                return length > MAX_REMOTE_STEP ? step.scale(MAX_REMOTE_STEP / length) : step;
            }
            return local ? Vec3.ZERO : player.getDeltaMovement();
        }
    }
}
