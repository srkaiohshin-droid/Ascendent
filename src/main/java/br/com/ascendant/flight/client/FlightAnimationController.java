package br.com.ascendant.flight.client;

import br.com.ascendant.Ascendant;
import br.com.ascendant.flight.FlightAnimationState;
import br.com.ascendant.flight.FlightController;
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
 * <p>One rig per player is advanced once per client tick. Rendering interpolates
 * between the previous and current tick with the renderer partial tick, so the
 * pose is independent of the frame rate and never accumulates rotation from
 * frame to frame. When flight stops, the last pose is held for a few ticks while
 * its weight decays to zero; the renderer then stops touching the model and the
 * vanilla animation takes over again.</p>
 */
@Mod.EventBusSubscriber(modid = Ascendant.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FlightAnimationController {
    private static final Map<Player, Rig> RIGS = new WeakHashMap<>();
    private static final float POSE_SMOOTHING = 0.30F;
    private static final float WEIGHT_STEP = 0.20F;
    private static final float REMOTE_INTERPOLATION_TICKS = 3.0F;

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
            if (!state.isActive() && !RIGS.containsKey(player)) {
                continue;
            }
            RIGS.computeIfAbsent(player, ignored -> new Rig()).tick(player, state, minecraft);
        }
        RIGS.keySet().retainAll(seen);
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
        return new FlightPoseSample(FlightPose.lerp(rig.previous, rig.current, delta), weight);
    }

    private static final class Rig {
        private FlightPose previous = FlightPose.VANILLA;
        private FlightPose current = FlightPose.VANILLA;
        private FlightPose target = FlightPose.VANILLA;
        private float previousWeight;
        private float weight;
        private int activeTicks;
        private int exitTicks;
        private boolean wasActive;
        private float lastSpeed;

        private void tick(Player player, FlightState state, Minecraft minecraft) {
            boolean local = player == minecraft.player;
            Vec3 velocity = sampleVelocity(player, local);
            float speed = (float) velocity.length();
            float verticalSpeed = (float) velocity.y;
            float turnDegrees = turnDegrees(player, velocity);
            boolean braking = (state.getInputMask() & FlightState.BRAKE) != 0
                    || (!local && speed - lastSpeed < -0.085F && speed > 0.12F);

            if (state.isActive()) {
                if (!wasActive) {
                    activeTicks = 0;
                    exitTicks = 0;
                }
                wasActive = true;
                activeTicks++;
                boolean maneuvering = state.getManeuverTicksRemaining() > 0
                        && state.getManeuverDirection() != 0;
                FlightAnimationState animation = FlightPoseLibrary.selectState(true, activeTicks,
                        speed, verticalSpeed, player.getXRot(), braking, turnDegrees, maneuvering);
                target = FlightPoseLibrary.target(animation, activeTicks, speed, verticalSpeed,
                        player.getXRot(), turnDegrees);
                if (maneuvering) {
                    target = target.withRoll(target.bodyRollDegrees + FlightPoseLibrary.maneuverRoll(
                            state.getManeuverDirection(), state.getManeuverTicksRemaining()));
                }
                previousWeight = weight;
                weight = Math.min(1.0F, weight + WEIGHT_STEP);
            } else {
                if (wasActive) {
                    wasActive = false;
                    exitTicks = FlightPoseLibrary.EXIT_TICKS;
                }
                previousWeight = weight;
                if (exitTicks > 0) {
                    exitTicks--;
                    weight = exitTicks / (float) FlightPoseLibrary.EXIT_TICKS;
                } else {
                    weight = 0.0F;
                }
            }

            previous = current;
            current = FlightPose.lerp(current, target, POSE_SMOOTHING);
            lastSpeed = speed;
        }

        private static Vec3 sampleVelocity(Player player, boolean local) {
            Vec3 motion = player.getDeltaMovement();
            if (local) {
                return motion;
            }
            // Other players are moved by interpolation packets; their deltaMovement
            // is usually zero. The per-tick position delta is a useful, bounded
            // estimate of the visible travel direction and speed.
            Vec3 step = new Vec3(player.getX() - player.xOld, player.getY() - player.yOld,
                    player.getZ() - player.zOld);
            if (step.lengthSqr() < 1.0E-8D) {
                return motion;
            }
            return step.scale(REMOTE_INTERPOLATION_TICKS);
        }

        private static float turnDegrees(Player player, Vec3 velocity) {
            double horizontalSqr = velocity.x * velocity.x + velocity.z * velocity.z;
            if (horizontalSqr < 0.0016D) {
                return 0.0F;
            }
            Vec3 look = player.getViewVector(1.0F);
            double lookHorizontalSqr = look.x * look.x + look.z * look.z;
            if (lookHorizontalSqr < 1.0E-4D) {
                return 0.0F;
            }
            double cross = velocity.x * look.z - velocity.z * look.x;
            double dot = velocity.x * look.x + velocity.z * look.z;
            return Mth.clamp((float) Math.toDegrees(Math.atan2(cross, dot)), -60.0F, 60.0F);
        }
    }
}
