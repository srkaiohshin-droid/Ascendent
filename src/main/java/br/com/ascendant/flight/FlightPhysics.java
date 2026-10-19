package br.com.ascendant.flight;

import net.minecraft.world.phys.Vec3;

/** Deterministic, collision-safe velocity controller; entity movement remains vanilla. */
public final class FlightPhysics {
    private FlightPhysics() {
    }

    public static Vec3 nextVelocity(Vec3 current, float yawDegrees, int inputMask,
                                    double maxHorizontalSpeed, double maxVerticalSpeed,
                                    double horizontalAcceleration, double verticalAcceleration,
                                    double braking, double hoverStability) {
        if ((inputMask & ~FlightState.VALID_INPUT_MASK) != 0) {
            inputMask &= FlightState.VALID_INPUT_MASK;
        }

        double forward = bit(inputMask, FlightState.FORWARD) - bit(inputMask, FlightState.BACK);
        double strafe = bit(inputMask, FlightState.RIGHT) - bit(inputMask, FlightState.LEFT);
        double inputLength = Math.sqrt(forward * forward + strafe * strafe);
        if (inputLength > 1.0D) {
            forward /= inputLength;
            strafe /= inputLength;
        }

        double yaw = Math.toRadians(yawDegrees);
        double directionX = -Math.sin(yaw) * forward + Math.cos(yaw) * strafe;
        double directionZ = Math.cos(yaw) * forward + Math.sin(yaw) * strafe;
        double horizontalX = current.x;
        double horizontalZ = current.z;

        if ((inputMask & FlightState.BRAKE) != 0) {
            double speed = Math.sqrt(horizontalX * horizontalX + horizontalZ * horizontalZ);
            double nextSpeed = Math.max(0.0D, speed - braking);
            if (speed > 1.0E-8D) {
                horizontalX *= nextSpeed / speed;
                horizontalZ *= nextSpeed / speed;
            }
        } else if (inputLength > 0.0D) {
            horizontalX += directionX * horizontalAcceleration;
            horizontalZ += directionZ * horizontalAcceleration;
        } else {
            double speed = Math.sqrt(horizontalX * horizontalX + horizontalZ * horizontalZ);
            double nextSpeed = Math.max(0.0D, speed - horizontalAcceleration * 0.5D);
            if (speed > 1.0E-8D) {
                horizontalX *= nextSpeed / speed;
                horizontalZ *= nextSpeed / speed;
            }
        }

        double horizontalSpeed = Math.sqrt(horizontalX * horizontalX + horizontalZ * horizontalZ);
        if (horizontalSpeed > maxHorizontalSpeed) {
            double scale = maxHorizontalSpeed / horizontalSpeed;
            horizontalX *= scale;
            horizontalZ *= scale;
        }

        double verticalInput = bit(inputMask, FlightState.ASCEND) - bit(inputMask, FlightState.DESCEND);
        double vertical = current.y;
        if (verticalInput != 0.0D) {
            vertical = approach(vertical, verticalInput * maxVerticalSpeed, verticalAcceleration);
        } else {
            vertical = approach(vertical, 0.0D, hoverStability);
        }
        vertical = clamp(vertical, -maxVerticalSpeed, maxVerticalSpeed);

        return new Vec3(horizontalX, vertical, horizontalZ);
    }

    private static double bit(int mask, int bit) {
        return (mask & bit) == 0 ? 0.0D : 1.0D;
    }

    private static double approach(double value, double target, double amount) {
        return value < target ? Math.min(value + amount, target) : Math.max(value - amount, target);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
