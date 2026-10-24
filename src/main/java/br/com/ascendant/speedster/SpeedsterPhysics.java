package br.com.ascendant.speedster;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Pure horizontal movement model used by both the server and local prediction.
 * Provides both the legacy parameter-based method (preserved for existing tests)
 * and a new profile-based method that drives per-level speedster behavior.
 */
public final class SpeedsterPhysics {
    private SpeedsterPhysics() {
    }

    /**
     * Legacy parameter-based velocity calculation. Preserved for backward
     * compatibility with existing GameTests.
     */
    public static Vec3 nextVelocity(Vec3 current, float yawDegrees, int inputMask, int level,
                                    double minSpeed, double maxSpeed, double acceleration,
                                    double steering, double coastDrag, double emergencyBraking,
                                    double sprintMultiplier) {
        return nextVelocity(current, yawDegrees, inputMask, SpeedsterLevel.byId(level),
                minSpeed, maxSpeed, acceleration, steering, coastDrag, emergencyBraking,
                sprintMultiplier, 0.0D, false);
    }

    /**
     * Profile-based velocity calculation. Uses the level's physics profile for
     * acceleration, steering, coast drag, braking and sprint multiplier.
     * Also applies kinetic energy buildup when sprinting forward.
     *
     * @param current       current velocity
     * @param yawDegrees    player yaw
     * @param inputMask     speedster input bits
     * @param level         current speedster level
     * @param kinetic       current kinetic energy (0.0 to 1.0)
     * @param isDashing     whether the player is currently dashing
     * @return next velocity with vertical component preserved
     */
    public static Vec3 nextVelocity(Vec3 current, float yawDegrees, int inputMask,
                                    SpeedsterLevel level, double kinetic,
                                    boolean isDashing) {
        SpeedsterProfile profile = SpeedsterProfile.forLevel(level);
        return nextVelocity(current, yawDegrees, inputMask, level,
                profile.minSpeed(), profile.maxSpeed(), profile.acceleration(),
                profile.steering(), profile.coastDrag(), profile.emergencyBraking(),
                profile.sprintMultiplier(), kinetic, isDashing);
    }

    /**
     * Full parameter-based velocity calculation with kinetic energy and dash support.
     */
    private static Vec3 nextVelocity(Vec3 current, float yawDegrees, int inputMask, SpeedsterLevel level,
                                     double minSpeed, double maxSpeed, double acceleration,
                                     double steering, double coastDrag, double emergencyBraking,
                                     double sprintMultiplier, double kinetic,
                                     boolean isDashing) {
        int input = inputMask & SpeedsterState.VALID_INPUT_MASK;
        Vec3 horizontal = new Vec3(current.x, 0.0D, current.z);
        double speed = horizontal.length();

        if ((input & SpeedsterState.BRAKE) != 0) {
            double braking = emergencyBraking * (level == SpeedsterLevel.EXTREME || level == SpeedsterLevel.TRANSCENDENTAL ? 1.5D : 1.0D);
            double reducedSpeed = Math.max(0.0D, speed - braking);
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

        // Kinetic energy adds to top speed at higher levels
        double kineticBoost = 1.0D + (kinetic * 0.25D);
        double targetSpeed = levelCap * (sprinting ? sprintMultiplier : 1.0D) * kineticBoost;

        double accel = acceleration * (sprinting ? 1.35D : 1.0D);
        // Higher kinetic energy slightly improves acceleration response
        accel *= (1.0D + kinetic * 0.2D);

        double nextSpeed = Math.min(targetSpeed,
                approach(speed, targetSpeed, accel));

        Vec3 heading = speed <= 1.0E-8D
                ? desiredHeading
                : horizontal.scale(1.0D / speed).lerp(desiredHeading, steering).normalize();

        return withVertical(heading.scale(nextSpeed), current.y);
    }

    /**
     * Computes the kinetic energy buildup for the next tick.
     * Kinetic builds when moving forward with sprint, decays otherwise.
     * Higher levels build faster and decay slower.
     */
    public static double nextKinetic(double currentKinetic, boolean movingForwardSprinting,
                                     SpeedsterLevel level) {
        SpeedsterProfile profile = SpeedsterProfile.forLevel(level);
        if (movingForwardSprinting) {
            return Math.min(1.0D, currentKinetic + profile.kineticBuildupRate());
        } else {
            return Math.max(0.0D, currentKinetic - profile.kineticDecayRate());
        }
    }

    /**
     * Maximum horizontal speed for a given level, interpolated between min and max.
     */
    public static double maxSpeedAtLevel(SpeedsterLevel level, double minSpeed, double maxSpeed) {
        int boundedLevel = Math.min(Math.max(level.id(), 0), SpeedsterLevel.TRANSCENDENTAL.id());
        if (boundedLevel <= 0) {
            return minSpeed;
        }
        double progress = (boundedLevel - 1.0) / (double) (SpeedsterLevel.TRANSCENDENTAL.id() - 1);
        return minSpeed + (maxSpeed - minSpeed) * Math.pow(progress, 1.35D);
    }

    /**
     * Maximum horizontal speed for a given legacy level number (1-10 range).
     * Preserved for backward compatibility with existing GameTests.
     */
    public static double maxSpeedAtLevel(int level, double minSpeed, double maxSpeed) {
        int boundedLevel = Math.min(Math.max(level, 1), 10);
        double progress = (boundedLevel - 1.0) / 9.0;
        return minSpeed + (maxSpeed - minSpeed) * Math.pow(progress, 1.35D);
    }

    /**
     * Impact damage multiplier based on speed and level. Used for speed-based attacks.
     * Damage scales with current speed relative to level cap.
     */
    public static double impactDamageMultiplier(double currentSpeed, SpeedsterLevel level) {
        SpeedsterProfile profile = SpeedsterProfile.forLevel(level);
        double cap = profile.maxSpeed();
        if (cap <= 0.0D) {
            return 0.0D;
        }
        double ratio = Math.min(currentSpeed / cap, 1.0D);
        return profile.impactDamageMultiplier() * (0.5D + 0.5D * ratio);
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

    /**
     * Verifica colisão com blocos sólidos ao longo do trajeto de movimento.
     * Se houver um bloco sólido à frente, reduz ou interrompe a velocidade para
     * evitar tunelamento (atraveshar paredes) em alta velocidade.
     *
     * @param level  nível do mundo para checagem de blocos
     * @param player entidade do jogador (para hitbox e posição vertical)
     * @param current velocidade atual
     * @param next    velocidade candidata para o próximo tick
     * @return velocidade ajustada, sem atravessar blocos sólidos
     */
    public static Vec3 checkCollision(Level level, Player player, Vec3 current, Vec3 next) {
        if (next.distanceToSqr(current) < 1.0E-6D) {
            return next;
        }
        double speed = next.length();
        if (speed < 0.1D) {
            return next;
        }

        Vec3 horizontal = new Vec3(next.x, 0.0D, next.z);
        Vec3 dir = horizontal.normalize();
        AABB box = player.getBoundingBox();

        // Amostra pontos ao longo do trajeto para detectar blocos sólidos
        int steps = Math.max(2, (int) Math.ceil(speed * 8.0D));
        for (int i = 1; i <= steps; i++) {
            double t = i / (double) steps;
            Vec3 checkPos = player.position().add(
                    dir.x * speed * t * 1.2D,
                    0.0D,
                    dir.z * speed * t * 1.2D
            );
            AABB checkBox = box.move(checkPos.subtract(player.position()));
            BlockPos pos = new BlockPos(
                    (int) checkPos.x,
                    (int) (checkPos.y + player.getEyeHeight() / 2.0D),
                    (int) checkPos.z
            );
            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && state.blocksMotion()) {
                // Bloco sólido à frente: reduz a velocidade proporcionalmente
                double reducedSpeed = speed * (1.0D - t) * 0.5D;
                if (reducedSpeed < 0.05D) {
                    return new Vec3(0.0D, next.y, 0.0D);
                }
                return dir.scale(reducedSpeed).add(0.0D, next.y, 0.0D);
            }
        }
        return next;
    }
}
