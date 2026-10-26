package br.com.ascendant.flight;

import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Server-authoritative flight kernel ported from the reference superhero flight.
 *
 * <p>The reference does not apply a velocity target or a turn-rate limiter. It
 * replaces the entity's {@code travel} method and moves the entity directly:</p>
 *
 * <ul>
 *   <li><strong>Hover</strong> adds a fixed {@code speed} impulse along the
 *       horizontal look direction, keeps the body upright and only uses the
 *       look pitch for vertical control while the forward key is held.</li>
 *   <li><strong>Boost</strong> (sprint) is a strong impulse along the full look
 *       vector. A horizontal realignment preserves the current horizontal speed
 *       while the heading catches up with the crosshair, so a 180-degree turn is
 *       an ordinary player-controlled arc and never a scripted trajectory.</li>
 * </ul>
 *
 * <p>The values below are the reference constants: hover impulse {@code 0.1},
 * hover drag {@code 0.91}, hover gravity {@code 0.08}, boost impulse
 * {@code 0.2}, boost drag {@code 0.96}, boost realignment {@code 0.1} and the
 * post-move drag {@code 0.99 / 0.98}. They are deliberately not capped: the
 * steady-state speeds fall out of the impulse and drag, exactly as in the
 * reference (about {@code 1.01} blocks/tick hovering and {@code 4.0} boosting).
 * </p>
 *
 * <p>{@link #travel(Player, Params, int, boolean, long)} performs the actual
 * entity move. It is called from a {@code Player#travel} mixin on both the
 * logical server and the owning client so the authoritative motion and the
 * local prediction run the same arithmetic. The client never gains authority:
 * the server still owns the flight state and every maneuver.</p>
 */
public final class FlightPhysics {
    /** Reference boost pre-drag applied to the previous velocity. */
    public static final double BOOST_DRAG = 0.96D;
    /** Reference horizontal realignment factor while boosting. */
    public static final double BOOST_ALIGNMENT = 0.10D;
    /** Reference boost post-move drag. */
    public static final double BOOST_POST_HORIZONTAL = 0.99D;
    public static final double BOOST_POST_VERTICAL = 0.98D;
    /** Reference hover post-move vertical drag. */
    public static final double HOVER_POST_VERTICAL = 0.98D;
    /** Reference barrel-roll speed scrub at the middle of the roll. */
    public static final double BARREL_ROLL_DRAG = 0.06D;
    /** Reference barrel-roll lateral impulse as a fraction of horizontal speed. */
    public static final double BARREL_ROLL_LATERAL = 0.15D;
    /** Horizontal retention of the optional Ascendant brake key. */
    public static final double BRAKE_RETENTION = 0.60D;

    private FlightPhysics() {
    }

    /**
     * Immutable physical tuning, supplied by {@link FlightTuning} on the server
     * and synchronized to clients by Forge's config system.
     */
    public record Params(double speed, double boostSpeed, double hoverDrag,
                         double hoverGravity, double hoverBob) {
        public Params {
            speed = clamp(positive(speed, 0.10D), 0.01D, 0.50D);
            boostSpeed = clamp(positive(boostSpeed, 0.20D), 0.02D, 1.00D);
            hoverDrag = clamp(hoverDrag, 0.50D, 0.995D);
            hoverGravity = clamp(finite(hoverGravity, 0.08D), 0.0D, 0.20D);
            hoverBob = clamp(finite(hoverBob, 0.025D), 0.0D, 0.10D);
        }
    }

    /**
     * One movement tick. {@code motion} is the velocity handed to
     * {@code Entity#move}; {@code velocity} is the post-drag velocity stored for
     * the next tick after an unobstructed move.
     */
    public record Step(Vec3 motion, Vec3 velocity) {
    }

    /**
     * Signed barrel-roll phase. {@code progress} runs from zero to one over the
     * ten-tick maneuvre. Ascendant direction {@code +1} is a right-hand roll,
     * which maps to the reference's negative barrel-roll timer.
     */
    public record BarrelRoll(int direction, float progress) {
        public static final BarrelRoll NONE = new BarrelRoll(0, 0.0F);

        public BarrelRoll {
            direction = direction == -1 || direction == 1 ? direction : 0;
            progress = (float) clamp(progress, 0.0D, 1.0D);
            if (direction == 0) {
                progress = 0.0F;
            }
        }

        public boolean active() {
            return direction != 0 && progress > 0.0F && progress < 1.0F;
        }
    }

    /**
     * Computes one tick without moving an entity. Used by the travel mixin and
     * by the deterministic GameTests.
     */
    public static Step simulate(Vec3 current, float yawDegrees, float pitchDegrees,
                                int inputMask, boolean boosting, long gameTime,
                                Params params) {
        return simulate(current, yawDegrees, pitchDegrees, inputMask, boosting,
                gameTime, params, BarrelRoll.NONE);
    }

    public static Step simulate(Vec3 current, float yawDegrees, float pitchDegrees,
                                int inputMask, boolean boosting, long gameTime,
                                Params params, BarrelRoll roll) {
        int mask = inputMask & FlightState.VALID_INPUT_MASK;
        boolean braking = (mask & FlightState.BRAKE) != 0;
        Vec3 motion;
        if (braking) {
            motion = current.scale(BRAKE_RETENTION);
        } else if (boosting) {
            motion = boostMotion(current, yawDegrees, pitchDegrees, params);
        } else {
            motion = hoverMotion(current, yawDegrees, pitchDegrees, mask, gameTime, params);
        }
        if (!braking) {
            motion = barrelRollMotion(motion, roll);
        }
        return new Step(motion, post(motion, boosting, braking, params));
    }

    /**
     * Executes the reference movement on a player: set the motion that the move
     * uses, let vanilla resolve collisions, then apply the reference post-move
     * gravity and drag to the actual (collision-adjusted) displacement.
     */
    public static Step travel(Player player, Params params, int inputMask,
                              boolean boosting, long gameTime) {
        return travel(player, params, inputMask, boosting, gameTime, BarrelRoll.NONE);
    }

    public static Step travel(Player player, Params params, int inputMask,
                              boolean boosting, long gameTime, BarrelRoll roll) {
        Step step = simulate(player.getDeltaMovement(), player.getYRot(), player.getXRot(),
                inputMask, boosting, gameTime, params, roll);
        player.setDeltaMovement(step.motion());
        player.move(MoverType.SELF, step.motion());
        Vec3 moved = player.getDeltaMovement();
        boolean braking = (inputMask & FlightState.BRAKE) != 0;
        Vec3 velocity = post(moved, boosting, braking, params);
        player.setDeltaMovement(velocity);
        return new Step(step.motion(), velocity);
    }

    private static Vec3 hoverMotion(Vec3 current, float yawDegrees, float pitchDegrees,
                                    int mask, long gameTime, Params params) {
        double forward = bit(mask, FlightState.FORWARD) - bit(mask, FlightState.BACK);
        double strafe = bit(mask, FlightState.RIGHT) - bit(mask, FlightState.LEFT);
        double y = current.y;

        if (forward != 0.0D) {
            // Minecraft pitch is positive downwards. The reference uses
            // -sin(pitch), so looking up produces a positive climb term.
            double pitch = -Math.sin(Math.toRadians(pitchDegrees));
            y += Math.abs(pitch) * pitch * forward * params.speed()
                    + params.hoverGravity();
            y *= 0.95D;
            // Looking up or down removes horizontal speed as the look tilts.
            forward *= 1.0D - Math.abs(pitch * pitch);
        } else {
            if (y < -0.11D) {
                y += 0.15D;
            } else if (y > 0.001D) {
                y -= 0.001D;
            } else {
                y = Math.sin(gameTime / 10.0D) * params.hoverBob();
            }
        }

        Vec3 impulse = inputVector(strafe, forward, params.speed(), yawDegrees);
        return new Vec3(current.x + impulse.x, y, current.z + impulse.z);
    }

    private static Vec3 boostMotion(Vec3 current, float yawDegrees, float pitchDegrees,
                                    Params params) {
        Vec3 look = lookVector(yawDegrees, pitchDegrees);
        Vec3 motion = current.scale(BOOST_DRAG).add(look.scale(params.boostSpeed()));

        double horizontal = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
        double cosPitch = Math.cos(Math.toRadians(pitchDegrees));
        if (cosPitch > 1.0E-4D) {
            // Preserve the horizontal speed while the heading converges on the
            // crosshair. There is no angular cap: a 180-degree request simply
            // takes several ticks and never snaps.
            double targetX = look.x / cosPitch * horizontal;
            double targetZ = look.z / cosPitch * horizontal;
            motion = new Vec3(
                    motion.x + (targetX - motion.x) * BOOST_ALIGNMENT,
                    motion.y,
                    motion.z + (targetZ - motion.z) * BOOST_ALIGNMENT);
        }

        double sinPitch = Math.sin(Math.toRadians(pitchDegrees));
        if (sinPitch > 0.0D) {
            // Looking down aligns the vertical speed with the downward look
            // without changing its magnitude, matching the reference.
            motion = new Vec3(motion.x,
                    motion.y + (look.y / sinPitch * Math.abs(motion.y) - motion.y)
                            * BOOST_ALIGNMENT,
                    motion.z);
        }
        return motion;
    }

    /**
     * Reference barrel-roll kernel: scrub the complete velocity by
     * {@code 1 - 0.06 * |sin(pi * timer)|}, then add
     * {@code 0.15 * sin(pi * timer) * horizontalSpeed} perpendicular to the
     * velocity. The perpendicular direction follows the roll sign, so a
     * right-hand roll deflects right while the body turns.
     */
    private static Vec3 barrelRollMotion(Vec3 motion, BarrelRoll roll) {
        if (roll == null || !roll.active()) {
            return motion;
        }
        double envelope = Math.sin(Math.PI * roll.progress());
        if (envelope <= 1.0E-6D) {
            return motion;
        }
        double horizontal = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
        double scrub = 1.0D - BARREL_ROLL_DRAG * envelope;
        Vec3 scrubbed = motion.scale(scrub);
        if (horizontal <= 1.0E-6D) {
            return scrubbed;
        }
        // Fisk computes the amount from the pre-scrub horizontal speed and the
        // left-perpendicular (z, -x). Ascendant direction +1 maps to Fisk's
        // negative timer, so the signed amount is negated.
        double amount = BARREL_ROLL_LATERAL * -roll.direction() * envelope * horizontal;
        return new Vec3(scrubbed.x + amount * motion.z / horizontal,
                scrubbed.y,
                scrubbed.z - amount * motion.x / horizontal);
    }

    /** Applies the reference post-move gravity and drag. */
    public static Vec3 post(Vec3 moved, boolean boosting, boolean braking, Params params) {
        if (braking) {
            return moved;
        }
        if (boosting) {
            return new Vec3(moved.x * BOOST_POST_HORIZONTAL,
                    moved.y * BOOST_POST_VERTICAL,
                    moved.z * BOOST_POST_HORIZONTAL);
        }
        return new Vec3(moved.x * params.hoverDrag(),
                (moved.y - params.hoverGravity()) * HOVER_POST_VERTICAL,
                moved.z * params.hoverDrag());
    }

    /** Unit look vector for a Minecraft yaw / pitch pair. */
    public static Vec3 lookVector(float yawDegrees, float pitchDegrees) {
        double yaw = Math.toRadians(yawDegrees);
        double pitch = Math.toRadians(pitchDegrees);
        double cosPitch = Math.cos(pitch);
        return new Vec3(-Math.sin(yaw) * cosPitch, -Math.sin(pitch), Math.cos(yaw) * cosPitch);
    }

    /**
     * Horizontal input impulse. The diagonal is normalized only when its length
     * exceeds one, which mirrors {@code moveFlying} in the reference.
     */
    private static Vec3 inputVector(double strafeRight, double forward, double speed,
                                    float yawDegrees) {
        double length = Math.sqrt(strafeRight * strafeRight + forward * forward);
        if (length < 1.0E-7D) {
            return Vec3.ZERO;
        }
        double scale = speed / Math.max(length, 1.0D);
        double strafe = strafeRight * scale;
        double move = forward * scale;
        double yaw = Math.toRadians(yawDegrees);
        double sin = Math.sin(yaw);
        double cos = Math.cos(yaw);
        // Positive strafe means the player's right. At yaw 0 the look vector is
        // +Z and the right vector is -X, so both signs are explicit here.
        return new Vec3(-move * sin - strafe * cos, 0.0D, move * cos - strafe * sin);
    }

    private static double bit(int mask, int bit) {
        return (mask & bit) == 0 ? 0.0D : 1.0D;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double finite(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }

    private static double positive(double value, double fallback) {
        return Double.isFinite(value) && value > 0.0D ? value : fallback;
    }
}
