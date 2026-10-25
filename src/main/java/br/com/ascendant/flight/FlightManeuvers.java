package br.com.ascendant.flight;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Deterministic geometry and entry validation for trajectory maneuvers.
 *
 * <p>The vertical loop is a real server-owned path, not a camera or pose trick:
 * the controller advances the player by the delta of the analytic circle and
 * the complete path is swept against block, fluid, height and world-border
 * collision before it is allowed to start. The same geometry feeds client
 * prediction and the presentation layer, so no client decides where the player
 * goes.</p>
 *
 * <p>The 180-degree heading change is intentionally not a scripted trajectory
 * anymore. It is ordinary player control: the look direction turns freely and
 * the physics curves the velocity towards it. This keeps the full manoeuvre in
 * the player's hands instead of replaying a fixed animation.</p>
 */
public final class FlightManeuvers {
    /** Ticks in one full vertical loop. */
    public static final int LOOP_TICKS = 28;

    /** Minimum horizontal speed to enter a loop, in blocks per tick. */
    public static final double LOOP_MIN_SPEED = 0.50D;
    /** Upper bound that keeps the swept volume reviewable. */
    public static final double MAX_ENTRY_SPEED = 1.25D;
    /** Maximum trajectory pitch at entry, in degrees. */
    public static final float MAX_ENTRY_PITCH_DEGREES = 24.0F;
    /** Maximum angle between the crosshair and the actual trajectory. */
    public static final float MAX_AIM_ERROR_DEGREES = 32.0F;
    /** Largest accepted path radius. */
    public static final double MAX_RADIUS = 8.0D;
    /** Extra samples between two tick positions in the pre-flight sweep. */
    public static final int COLLISION_SUBSTEPS = 4;
    /**
     * Padding added to the swept bounding box. Vanilla still applies a small
     * camera-relative input acceleration on the owning client, so the predicted
     * path can differ from the authoritative one by a fraction of a block.
     * Requiring this clearance keeps the reviewed path safe in both copies.
     */
    public static final double SWEEP_CLEARANCE = 0.35D;

    public static final int PROBLEM_NONE = 0;
    public static final int PROBLEM_INVALID = 1;
    public static final int PROBLEM_TOO_SLOW = 2;
    public static final int PROBLEM_TOO_FAST = 3;
    public static final int PROBLEM_PITCH = 4;
    public static final int PROBLEM_AIM = 5;
    public static final int PROBLEM_OBSTRUCTED = 6;
    public static final int PROBLEM_FLUID = 7;
    public static final int PROBLEM_BORDER = 8;
    public static final int PROBLEM_HEIGHT = 9;
    public static final int PROBLEM_UNLOADED = 10;

    private static final double TWO_PI = Math.PI * 2.0D;

    private FlightManeuvers() {
    }

    /** Ticks for a maneuver kind, or zero when the kind has no controller entry. */
    public static int duration(int kind) {
        return switch (kind) {
            case FlightState.MANEUVER_ROLL_360 -> FlightPoseLibrary.MANEUVER_TICKS;
            case FlightState.MANEUVER_LOOP -> LOOP_TICKS;
            default -> 0;
        };
    }

    /** True for maneuvers that move the player along a checked trajectory. */
    public static boolean isTrajectory(int kind) {
        return kind == FlightState.MANEUVER_LOOP;
    }

    public static double minimumSpeed(int kind) {
        return kind == FlightState.MANEUVER_LOOP ? LOOP_MIN_SPEED : 0.0D;
    }

    public static double horizontalSpeed(Vec3 velocity) {
        return Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
    }

    public static double trajectoryPitchDegrees(Vec3 velocity, double horizontalSpeed) {
        if (horizontalSpeed <= 0.02D) {
            return velocity.y > 0.02D ? 90.0D : velocity.y < -0.02D ? -90.0D : 0.0D;
        }
        return Math.toDegrees(Math.atan2(velocity.y, horizontalSpeed));
    }

    /**
     * Heading used as the maneuver basis. The real trajectory wins when the
     * player is already moving horizontally; the look yaw is only a fallback
     * for a nearly stationary hover.
     */
    public static float basisYaw(Vec3 velocity, float lookYawDegrees) {
        double horizontal = horizontalSpeed(velocity);
        if (horizontal < 0.08D) {
            return Mth.wrapDegrees(lookYawDegrees);
        }
        return (float) Math.toDegrees(Math.atan2(-velocity.x, velocity.z));
    }

    /** Entry checks shared by the server and the tests. */
    public static int entryProblem(Player player, int kind) {
        if (!isTrajectory(kind) || duration(kind) <= 0) {
            return PROBLEM_INVALID;
        }
        Vec3 motion = player.getDeltaMovement();
        double horizontal = horizontalSpeed(motion);
        if (horizontal < minimumSpeed(kind)) {
            return PROBLEM_TOO_SLOW;
        }
        if (horizontal > MAX_ENTRY_SPEED) {
            return PROBLEM_TOO_FAST;
        }
        double pitch = trajectoryPitchDegrees(motion, horizontal);
        if (Math.abs(pitch) > MAX_ENTRY_PITCH_DEGREES) {
            return PROBLEM_PITCH;
        }
        float yaw = basisYaw(motion, player.getYRot());
        float aim = Mth.wrapDegrees(player.getYRot() - yaw);
        if (Math.abs(aim) > MAX_AIM_ERROR_DEGREES) {
            return PROBLEM_AIM;
        }
        return PROBLEM_NONE;
    }

    public static double radius(int kind, double speed) {
        double clamped = Math.max(0.0D, speed);
        return kind == FlightState.MANEUVER_LOOP ? clamped * LOOP_TICKS / TWO_PI : 0.0D;
    }

    /** Horizontal unit vector for a Minecraft yaw. */
    public static Vec3 forward(float yawDegrees) {
        double radians = Math.toRadians(yawDegrees);
        return new Vec3(-Math.sin(radians), 0.0D, Math.cos(radians));
    }

    /**
     * Position offset from the entry point at a normalized progress.
     * Progress is clamped, so the final sample is exactly the end of the path.
     */
    public static Vec3 offset(int kind, int direction, float yawDegrees,
                              double speed, double progress) {
        if (kind != FlightState.MANEUVER_LOOP) {
            return Vec3.ZERO;
        }
        double p = clamp(progress, 0.0D, 1.0D);
        double radius = radius(kind, speed);
        double angle = TWO_PI * p;
        Vec3 forward = forward(yawDegrees);
        double horizontal = radius * Math.sin(angle);
        return new Vec3(forward.x * horizontal, radius * (1.0D - Math.cos(angle)),
                forward.z * horizontal);
    }

    /** Delta for one server tick. Step index zero is the first step after entry. */
    public static Vec3 stepDelta(int kind, int direction, float yawDegrees,
                                 double speed, int stepIndex, int durationTicks) {
        if (!isTrajectory(kind) || durationTicks <= 0) {
            return Vec3.ZERO;
        }
        double start = clamp(stepIndex / (double) durationTicks, 0.0D, 1.0D);
        double end = clamp((stepIndex + 1) / (double) durationTicks, 0.0D, 1.0D);
        return offset(kind, direction, yawDegrees, speed, end)
                .subtract(offset(kind, direction, yawDegrees, speed, start));
    }

    /** Extra body pitch for the loop; a full 360 turn about the lateral axis. */
    public static float bodyPitchOffset(int kind, float progress) {
        if (kind != FlightState.MANEUVER_LOOP) {
            return 0.0F;
        }
        return 360.0F * Mth.clamp(progress, 0.0F, 1.0F);
    }

    /** Human-readable key used by the server refusal message. */
    public static String problemKey(int problem) {
        return switch (problem) {
            case PROBLEM_TOO_SLOW -> "message.ascendant.flight.maneuver_too_slow";
            case PROBLEM_PITCH -> "message.ascendant.flight.maneuver_level";
            case PROBLEM_AIM -> "message.ascendant.flight.maneuver_aim";
            case PROBLEM_OBSTRUCTED, PROBLEM_FLUID, PROBLEM_BORDER,
                    PROBLEM_HEIGHT, PROBLEM_UNLOADED -> "message.ascendant.flight.maneuver_blocked";
            default -> "message.ascendant.flight.maneuver_unavailable";
        };
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
