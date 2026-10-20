package br.com.ascendant.flight;

import net.minecraft.world.phys.Vec3;

/**
 * 3D directional physics controller inspired by superhero flight dynamics.
 * Supports full pitch and yaw trajectory guidance, inertial gliding, aerodynamic
 * curve convergence, boost sprinting, and hovering stabilization.
 */
public final class FlightPhysics {
    private FlightPhysics() {
    }

    /**
     * Calculates the next 3D velocity vector based on look orientation (yaw + pitch),
     * directional input mask, sprint boost, and aerodynamic parameters.
     */
    public static Vec3 nextVelocity(Vec3 current, float yawDegrees, float pitchDegrees,
                                    int inputMask, boolean isSprinting,
                                    double maxCruiseSpeed, double maxBoostSpeed,
                                    double acceleration, double boostAcceleration,
                                    double steeringFactor, double drag,
                                    double braking, double hoverStability) {
        if ((inputMask & ~FlightState.VALID_INPUT_MASK) != 0) {
            inputMask &= FlightState.VALID_INPUT_MASK;
        }

        // Active air brake
        if ((inputMask & FlightState.BRAKE) != 0) {
            double currentSpeed = current.length();
            if (currentSpeed <= 1.0E-5D) {
                return Vec3.ZERO;
            }
            double nextSpeed = Math.max(0.0D, currentSpeed - braking);
            return current.scale(nextSpeed / currentSpeed);
        }

        double forward = bit(inputMask, FlightState.FORWARD) - bit(inputMask, FlightState.BACK);
        double strafe = bit(inputMask, FlightState.RIGHT) - bit(inputMask, FlightState.LEFT);
        double verticalInput = bit(inputMask, FlightState.ASCEND) - bit(inputMask, FlightState.DESCEND);

        double yaw = Math.toRadians(yawDegrees);
        double pitch = Math.toRadians(pitchDegrees);
        double cosPitch = Math.cos(pitch);
        double sinPitch = Math.sin(pitch);
        double cosYaw = Math.cos(yaw);
        double sinYaw = Math.sin(yaw);

        // 3D forward look vector in Minecraft coordinates
        double lookX = -sinYaw * cosPitch;
        double lookY = -sinPitch; // Looking down has pitch > 0, so -sin(pitch) is negative Y
        double lookZ = cosYaw * cosPitch;

        // Horizontal strafe vector (perpendicular to horizontal yaw)
        double strafeX = cosYaw;
        double strafeZ = sinYaw;

        // Compose 3D thrust vector
        double thrustX = lookX * forward + strafeX * strafe;
        double thrustY = lookY * forward + verticalInput;
        double thrustZ = lookZ * forward + strafeZ * strafe;

        double thrustLength = Math.sqrt(thrustX * thrustX + thrustY * thrustY + thrustZ * thrustZ);
        if (thrustLength > 1.0D) {
            thrustX /= thrustLength;
            thrustY /= thrustLength;
            thrustZ /= thrustLength;
            thrustLength = 1.0D;
        }

        double maxSpeed = isSprinting ? maxBoostSpeed : maxCruiseSpeed;
        double accel = isSprinting ? boostAcceleration : acceleration;

        if (thrustLength > 1.0E-5D) {
            // Apply thrust acceleration
            double vx = current.x + thrustX * accel;
            double vy = current.y + thrustY * accel;
            double vz = current.z + thrustZ * accel;

            // Aerodynamic steering: when flying forward, smoothly rotate trajectory toward target
            if (forward > 0.0D) {
                double speed = Math.sqrt(vx * vx + vy * vy + vz * vz);
                if (speed > 0.03D) {
                    double targetVx = thrustX * speed;
                    double targetVy = thrustY * speed;
                    double targetVz = thrustZ * speed;

                    vx += (targetVx - vx) * steeringFactor;
                    vy += (targetVy - vy) * steeringFactor;
                    vz += (targetVz - vz) * steeringFactor;

                    double newSpeed = Math.sqrt(vx * vx + vy * vy + vz * vz);
                    if (newSpeed > 1.0E-6D) {
                        double scale = speed / newSpeed;
                        vx *= scale;
                        vy *= scale;
                        vz *= scale;
                    }
                }
            }

            // Enforce total 3D speed cap
            double finalSpeed = Math.sqrt(vx * vx + vy * vy + vz * vz);
            if (finalSpeed > maxSpeed) {
                double scale = maxSpeed / finalSpeed;
                vx *= scale;
                vy *= scale;
                vz *= scale;
            }

            return new Vec3(vx, vy, vz);
        } else {
            // Neutral flight / gliding inertia: decay with aerodynamic drag
            double vx = current.x * drag;
            double vy = current.y * drag;
            double vz = current.z * drag;

            double hSpeed = Math.sqrt(vx * vx + vz * vz);
            if (hSpeed < 0.02D) {
                vx = 0.0D;
                vz = 0.0D;
            }
            vy = approach(vy, 0.0D, hoverStability);
            if (Math.abs(vy) < 0.01D) {
                vy = 0.0D;
            }

            return new Vec3(vx, vy, vz);
        }
    }

    /**
     * Backward-compatible 2D-planar physics calculation with independent vertical axis.
     */
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
