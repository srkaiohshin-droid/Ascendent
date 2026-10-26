package br.com.ascendant.flight;

import net.minecraft.world.phys.Vec3;

/**
 * Pure math for the reference boost camera roll.
 *
 * <p>Kept in the common source set so the deterministic GameTests can exercise
 * the exact formula without loading client classes. The camera controller only
 * applies the returned angle to the viewport.</p>
 */
public final class FlightCameraMath {
    public static final float MAX_ROLL_DEGREES = 90.0F;
    public static final float ROLL_DIVISOR = 2.5F;

    private FlightCameraMath() {
    }

    /**
     * Reference roll target. A perfect head-on reversal has a zero cross
     * product and therefore reports zero; the roll appears as soon as the
     * trajectory and crosshair are slightly off-axis.
     */
    public static float rollTarget(Vec3 motion, Vec3 look) {
        if (motion == null || look == null) {
            return 0.0F;
        }
        double speedSquared = motion.x * motion.x + motion.z * motion.z;
        double lookSquared = look.x * look.x + look.z * look.z;
        if (speedSquared <= 1.0E-9D || lookSquared <= 1.0E-9D) {
            return 0.0F;
        }
        double dot = (motion.x * look.x + motion.z * look.z)
                / Math.sqrt(speedSquared * lookSquared);
        dot = Math.max(-1.0D, Math.min(1.0D, dot));
        double cross = motion.x * look.z - motion.z * look.x;
        double angle = Math.signum(cross) * Math.acos(dot) * 180.0D / Math.PI;
        double target = angle * Math.min(speedSquared, 1.0D) / ROLL_DIVISOR;
        return (float) Math.max(-MAX_ROLL_DEGREES, Math.min(MAX_ROLL_DEGREES, target));
    }

    /** Reference smoothing: {@code (target + 2 * previous) / 3}. */
    public static float smooth(float previousRoll, float target) {
        return (target + previousRoll * 2.0F) / 3.0F;
    }
}
