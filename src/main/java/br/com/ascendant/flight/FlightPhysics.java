package br.com.ascendant.flight;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Server-authoritative flight physics with two explicit control modes.
 *
 * <p><strong>Normal flight</strong> is camera-relative and deliberately
 * readable: WASD moves along the horizontal look direction, jump / sneak adds
 * a direct vertical command, and looking up or down while pressing forward
 * biases the vertical target in the same way the reference superhero flight
 * does. Releasing every control lets the horizontal velocity decay and folds
 * the vertical velocity into a small idle bob, which is the real hover state
 * rather than a pose-only flag.</p>
 *
 * <p><strong>Boost</strong> (sprint) follows the full look vector, including
 * pitch. The trajectory heading and pitch converge towards the crosshair at a
 * bounded angular rate instead of snapping. A 180-degree turn is therefore an
 * ordinary player manoeuvre: the velocity arcs through the turn over several
 * ticks and the presentation layer can show the body leading the camera.</p>
 *
 * <p>All state stays on the server. This class is pure so the same maths can
 * run in GameTests and in the owning client's prediction without giving the
 * client any authority.</p>
 */
public final class FlightPhysics {
    /** Fraction of cruise speed used for the direct vertical command. */
    private static final double VERTICAL_CRUISE_FACTOR = 0.72D;
    /** Smallest cosine factor when forward input is projected through pitch. */
    private static final double MIN_PITCH_HORIZONTAL_FACTOR = 0.20D;
    /** Horizontal speed below which an idle flight starts its hover bob. */
    private static final double HOVER_BOB_MAX_HORIZONTAL = 0.10D;
    /** Speed below which a boost starts directly on the look vector. */
    private static final double BOOST_ALIGN_FROM_REST = 0.05D;

    private FlightPhysics() {
    }

    /**
     * Immutable physical tuning. The values are supplied by
     * {@link FlightTuning} on the server and travel to clients through Forge's
     * config synchronisation.
     */
    public record Params(double maxCruiseSpeed, double maxBoostSpeed,
                         double acceleration, double boostAcceleration,
                         double drag, double braking, double hoverStability,
                         double hoverBob, double boostTurnRateDegrees) {
        public Params {
            maxCruiseSpeed = positive(maxCruiseSpeed, 0.65D);
            maxBoostSpeed = Math.max(maxCruiseSpeed, positive(maxBoostSpeed, 1.25D));
            acceleration = positive(acceleration, 0.045D);
            boostAcceleration = positive(boostAcceleration, 0.080D);
            drag = clamp(drag, 0.50D, 0.995D);
            braking = Math.max(0.0D, finite(braking, 0.12D));
            hoverStability = Math.max(0.0D, finite(hoverStability, 0.12D));
            hoverBob = Math.max(0.0D, finite(hoverBob, 0.025D));
            boostTurnRateDegrees = clamp(boostTurnRateDegrees, 1.0D, 45.0D);
        }
    }

    /**
     * Calculates the next velocity for one tick. {@code gameTime} is the level
     * game time and only drives the deterministic hover bob.
     */
    public static Vec3 nextVelocity(Vec3 current, float yawDegrees, float pitchDegrees,
                                    int inputMask, boolean sprinting, long gameTime,
                                    Params params) {
        int mask = inputMask & FlightState.VALID_INPUT_MASK;
        if ((mask & FlightState.BRAKE) != 0) {
            return brake(current, params.braking());
        }
        if (sprinting) {
            return boost(current, yawDegrees, pitchDegrees, params);
        }
        return normal(current, yawDegrees, pitchDegrees, mask, gameTime, params);
    }

    private static Vec3 normal(Vec3 current, float yawDegrees, float pitchDegrees,
                               int mask, long gameTime, Params params) {
        double forward = bit(mask, FlightState.FORWARD) - bit(mask, FlightState.BACK);
        double strafe = bit(mask, FlightState.RIGHT) - bit(mask, FlightState.LEFT);
        double verticalInput = bit(mask, FlightState.ASCEND) - bit(mask, FlightState.DESCEND);
        boolean idle = forward == 0.0D && strafe == 0.0D && verticalInput == 0.0D;

        if (idle) {
            return idleHover(current, gameTime, params);
        }

        double inputLength = Math.sqrt(forward * forward + strafe * strafe);
        double directionX = 0.0D;
        double directionZ = 0.0D;
        if (inputLength > 1.0E-6D) {
            double yaw = Math.toRadians(yawDegrees);
            directionX = (-Math.sin(yaw) * forward + Math.cos(yaw) * strafe) / inputLength;
            directionZ = (Math.cos(yaw) * forward + Math.sin(yaw) * strafe) / inputLength;
        }

        // Looking up or down while moving forward is a vertical command, as in
        // the reference hover flight. Direct jump / sneak input wins when held.
        double pitch = Math.toRadians(pitchDegrees);
        double pitchVertical = -Math.sin(pitch) * forward;
        double verticalCommand = verticalInput != 0.0D ? verticalInput : pitchVertical;
        verticalCommand = clamp(verticalCommand, -1.0D, 1.0D);

        double horizontalFactor = 1.0D;
        if (verticalInput == 0.0D && forward != 0.0D) {
            horizontalFactor = Math.max(MIN_PITCH_HORIZONTAL_FACTOR, Math.cos(pitch));
        }

        double targetHorizontal = params.maxCruiseSpeed() * horizontalFactor;
        double targetX = directionX * targetHorizontal;
        double targetZ = directionZ * targetHorizontal;
        double targetY = verticalCommand * params.maxCruiseSpeed() * VERTICAL_CRUISE_FACTOR;

        double response = clamp(params.acceleration() * 4.0D, 0.14D, 0.32D);
        double verticalResponse = clamp(params.hoverStability() * 1.6D, 0.10D, 0.35D);
        double x = current.x + (targetX - current.x) * response;
        double y = current.y + (targetY - current.y) * verticalResponse;
        double z = current.z + (targetZ - current.z) * response;

        // Keep the combined vector bounded. The limit is slightly above cruise
        // so a climb at full forward input still reads as a climb instead of
        // silently losing all forward speed.
        double maximum = params.maxCruiseSpeed() * 1.15D;
        double speed = Math.sqrt(x * x + y * y + z * z);
        if (speed > maximum) {
            double scale = maximum / speed;
            x *= scale;
            y *= scale;
            z *= scale;
        }
        return new Vec3(x, y, z);
    }

    private static Vec3 idleHover(Vec3 current, long gameTime, Params params) {
        double x = current.x * params.drag();
        double z = current.z * params.drag();
        double horizontal = Math.sqrt(x * x + z * z);
        if (horizontal < 0.02D) {
            x = 0.0D;
            z = 0.0D;
            horizontal = 0.0D;
        }

        double y = approach(current.y, 0.0D, params.hoverStability());
        if (Math.abs(y) < 0.01D) {
            y = 0.0D;
        }
        // A real hover is not a frozen player: once the residual glide is gone
        // the vertical velocity follows a slow deterministic sine. The server
        // and every client use the same game time, so the motion stays synced.
        if (horizontal < HOVER_BOB_MAX_HORIZONTAL && Math.abs(y) < 0.02D) {
            y = Math.sin(gameTime / 10.0D) * params.hoverBob();
        }
        return new Vec3(x, y, z);
    }

    private static Vec3 boost(Vec3 current, float yawDegrees, float pitchDegrees, Params params) {
        Vec3 look = lookVector(yawDegrees, pitchDegrees);
        double speed = current.length();
        if (speed < BOOST_ALIGN_FROM_REST) {
            double next = Math.min(params.maxBoostSpeed(),
                    speed * params.drag() + params.boostAcceleration());
            return look.scale(next);
        }

        double horizontal = Math.sqrt(current.x * current.x + current.z * current.z);
        float trajectoryYaw = horizontal > 0.02D
                ? (float) Math.toDegrees(Math.atan2(-current.x, current.z))
                : yawDegrees;
        float trajectoryPitch = (float) Math.toDegrees(Math.atan2(current.y, horizontal));

        float maximumStep = (float) params.boostTurnRateDegrees();
        // Minecraft pitch is positive downwards; the trajectory pitch used here
        // is positive upwards, so the look pitch is negated once.
        float lookPitch = -pitchDegrees;
        float yawStep = Mth.clamp(Mth.wrapDegrees(yawDegrees - trajectoryYaw),
                -maximumStep, maximumStep);
        float pitchStep = Mth.clamp(lookPitch - trajectoryPitch, -maximumStep, maximumStep);

        float nextYaw = trajectoryYaw + yawStep;
        float nextPitch = Mth.clamp(trajectoryPitch + pitchStep, -90.0F, 90.0F);
        double nextSpeed = Math.min(params.maxBoostSpeed(),
                speed * params.drag() + params.boostAcceleration());
        return direction(nextYaw, nextPitch).scale(nextSpeed);
    }

    /** Unit look vector for a Minecraft yaw / pitch pair. */
    public static Vec3 lookVector(float yawDegrees, float pitchDegrees) {
        double yaw = Math.toRadians(yawDegrees);
        double pitch = Math.toRadians(pitchDegrees);
        double cosPitch = Math.cos(pitch);
        return new Vec3(-Math.sin(yaw) * cosPitch, -Math.sin(pitch), Math.cos(yaw) * cosPitch);
    }

    private static Vec3 direction(float yawDegrees, float trajectoryPitchDegrees) {
        double yaw = Math.toRadians(yawDegrees);
        double pitch = Math.toRadians(trajectoryPitchDegrees);
        double cosPitch = Math.cos(pitch);
        return new Vec3(-Math.sin(yaw) * cosPitch, Math.sin(pitch), Math.cos(yaw) * cosPitch);
    }

    private static Vec3 brake(Vec3 current, double braking) {
        double speed = current.length();
        if (speed <= 1.0E-5D) {
            return Vec3.ZERO;
        }
        double nextSpeed = Math.max(0.0D, speed - braking);
        return current.scale(nextSpeed / speed);
    }

    private static double bit(int mask, int bit) {
        return (mask & bit) == 0 ? 0.0D : 1.0D;
    }

    private static double approach(double value, double target, double amount) {
        return value < target ? Math.min(value + amount, target)
                : Math.max(value - amount, target);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double positive(double value, double fallback) {
        return Double.isFinite(value) && value > 0.0D ? value : fallback;
    }

    private static double finite(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }
}
