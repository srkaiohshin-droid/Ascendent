package br.com.ascendant.speedster;

import net.minecraft.world.phys.Vec3;

/** Pure horizontal movement model used by both the server and local prediction. */
public final class SpeedsterPhysics {
    private SpeedsterPhysics() {
    }

    public static double maxSpeedAtLevel(int level, double minSpeed, double maxSpeed) {
        int boundedLevel = Math.max(SpeedsterState.MIN_LEVEL, Math.min(SpeedsterState.MAX_LEVEL, level));
        double progress = (boundedLevel - SpeedsterState.MIN_LEVEL)
                / (double) (SpeedsterState.MAX_LEVEL - SpeedsterState.MIN_LEVEL);
        return minSpeed + (maxSpeed - minSpeed) * Math.pow(progress, 1.35D);
    }

    public static Vec3 nextVelocity(Vec3 current, float yawDegrees, int inputMask, int level,
                                    double minSpeed, double maxSpeed, double acceleration,
                                    double steering, double coastDrag, double emergencyBraking,
                                    double sprintMultiplier) {
        int input = inputMask & SpeedsterState.VALID_INPUT_MASK;
        Vec3 horizontal = new Vec3(current.x, 0.0D, current.z);
        double speed = horizontal.length();

        if ((input & SpeedsterState.BRAKE) != 0) {
            double reducedSpeed = Math.max(0.0D, speed - emergencyBraking);
            return withVertical(reducedSpeed <= 1.0E-8D ? Vec3.ZERO : horizontal.scale(reducedSpeed / speed), current.y);
        }

        double forward = bit(input, SpeedsterState.FORWARD) - bit(input, SpeedsterState.BACK);
        double strafe = bit(input, SpeedsterState.RIGHT) - bit(input, SpeedsterState.LEFT);
        double inputLength = Math.sqrt(forward * forward + strafe * strafe);
        if (inputLength <= 1.0E-8D) {
            double coastSpeed = Math.min(speed, maxSpeedAtLevel(level, minSpeed, maxSpeed)) * coastDrag;
            return withVertical(coastSpeed <= 1.0E-4D ? Vec3.ZERO : horizontal.scale(coastSpeed / speed), current.y);
        }

        forward /= inputLength;
        strafe /= inputLength;
        double yaw = Math.toRadians(yawDegrees);
        Vec3 desiredHeading = new Vec3(
                -Math.sin(yaw) * forward + Math.cos(yaw) * strafe,
                0.0D,
                Math.cos(yaw) * forward + Math.sin(yaw) * strafe
        ).normalize();

        double levelCap = maxSpeedAtLevel(level, minSpeed, maxSpeed);
        boolean sprinting = (input & SpeedsterState.BOOST) != 0;
        double targetSpeed = levelCap * (sprinting ? sprintMultiplier : 1.0D);
        double nextSpeed = Math.min(targetSpeed,
                approach(speed, targetSpeed, acceleration * (sprinting ? 1.35D : 1.0D)));

        Vec3 heading = speed <= 1.0E-8D
                ? desiredHeading
                : horizontal.scale(1.0D / speed).lerp(desiredHeading, steering).normalize();
        return withVertical(heading.scale(nextSpeed), current.y);
    }

    private static Vec3 withVertical(Vec3 horizontal, double vertical) {
        return new Vec3(horizontal.x, vertical, horizontal.z);
    }

    private static double bit(int mask, int bit) {
        return (mask & bit) == 0 ? 0.0D : 1.0D;
    }

    private static double approach(double value, double target, double amount) {
        return value < target ? Math.min(value + amount, target) : Math.max(value - amount, target);
    }
}
