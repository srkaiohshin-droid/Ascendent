package br.com.ascendant.flight;

import net.minecraft.util.Mth;

/**
 * Turns flight telemetry into a target pose. All angles are in degrees and all
 * distances are in model/render units, matching the vanilla 1.20.1 humanoid
 * joints.
 *
 * <p>The silhouette follows the reference flight animation:</p>
 * <ul>
 *   <li><strong>Hover</strong> is upright with the arms down and a small
 *       3-degree outward arm offset. The whole body leans up to 15 degrees into
 *       the raw horizontal velocity, scaled by {@code min(1.5 * speed, 1)}, so
 *       releasing boost produces the reference deceleration lean. The head
 *       keeps the player's real look pitch and adds the speed-scaled forward
 *       projection, exactly like the reference layers its hover offset over the
 *       vanilla head angle. Arms and legs receive the reference's
 *       velocity-scaled radian offsets, converted to degrees, so the pose
 *       trails naturally instead of staying rigid. A second low-speed layer
 *       adds the reference idle sway, asymmetric arm/leg tuck and small torso
 *       pitch; its head and body-yaw terms are deliberately omitted so the
 *       neck keeps the real crosshair.</li>
 *   <li><strong>Boost</strong> is the reference horizontal pose:
 *       {@code -(viewPitch + 80)} body pitch, the header's -40-degree neck
 *       lift plus the boost animation's -10-degree offset, arms at the sides
 *       and slightly back, and trailing legs. The reference turn layer adds
 *       asymmetric arm/leg offsets, a body bank, a neck counter-roll and a
 *       small vertical body drop; those details are reproduced here, and the
 *       hover/boost silhouettes are blended over the reference eight-tick
 *       boost timer.</li>
 *   <li><strong>Maneuver</strong> reuses the blended ordinary silhouette; the
 *       server-synced loop or roll is applied by the renderer outside the pose
 *       blend. The roll also adds the reference barrel-roll arm/leg/pitch
 *       overlay.</li>
 * </ul>
 *
 * <p>There is deliberately no motion-headed yaw or scripted takeoff, landing,
 * cruise, turn, climb or dive pose. The renderer counter-rotates the vanilla
 * movement yaw so the body faces the player's real look yaw; only the look and
 * the reference velocity offsets orient the pose.</p>
 */
public final class FlightPoseLibrary {
    /** Ticks used to return to vanilla when flight stops. */
    public static final int EXIT_TICKS = 8;
    /** Ticks in one full 360-degree aerial roll. */
    public static final int MANEUVER_TICKS = 10;
    /** Reference idle loop length in ticks: {@code entity.loop(20 * pi)}. */
    public static final float IDLE_LOOP_TICKS = (float) (Math.PI * 20.0D);
    /** Reference idle phase offset, applied on top of the tick counter. */
    public static final float IDLE_PHASE_OFFSET = 0.4F;
    /** Reference idle sway amplitude as a fraction of the gate. */
    public static final float IDLE_SWAY_SCALE = 0.2F;

    /** Maximum hover lean towards the horizontal velocity. */
    public static final float HOVER_LEAN_DEGREES = 15.0F;
    /** Reference boost body pitch at a level view: {@code -(viewPitch + 80)}. */
    public static final float BOOST_BASE_PITCH = 80.0F;
    /**
     * Local neck lift used by the horizontal boost silhouette. The reference
     * header interpolates the head to -40 degrees while the boost timer fills,
     * and the boost animation subtracts another 10 degrees.
     */
    public static final float BOOST_HEAD_PITCH = -50.0F;
    /** Reference hover arms sit 3 degrees outward from the vanilla pose. */
    public static final float HOVER_ARM_BASELINE_Z = 3.0F;
    /** Divisor that converts the turn angle into the reference boost offsets. */
    public static final float BOOST_TURN_OFFSET_DIVISOR = 2.5F;
    /** Reference tick ramp used by the boost timer. */
    public static final int BOOST_BLEND_TICKS = 8;
    /** Fraction of the reference turn bank kept for third-person readability. */
    public static final float BOOST_TURN_BANK_SCALE = 0.55F;
    /** Maximum body bank during a boost turn. */
    public static final float BOOST_TURN_BANK_MAX_DEGREES = 28.0F;
    /** Fraction of the bank counter-rotated into the neck. */
    public static final float BOOST_HEAD_COUNTER_ROLL = 0.5F;
    /**
     * Vertical component of the reference boost translation. The reference
     * rotates around the feet and then shifts the body along its local axis;
     * this renderer already rotates around the torso centre, so only the small
     * world-space drop is left to reproduce.
     */
    public static final float BOOST_BODY_LIFT = 0.14F;

    /** Pivot used by the running lean, kept at the legacy knee height. */
    public static final float BODY_PIVOT_Y = 6.0F / 16.0F;
    /**
     * Flight pivot in the pre-flip {@code setupRotations} frame. The vanilla
     * torso spans roughly 0.7..1.4 blocks above the feet; 1.0 is its centre.
     * Rotating here keeps the chest and hips together instead of swinging the
     * whole body around the knees.
     */
    public static final float FLIGHT_BODY_PIVOT_Y = 1.0F;

    private FlightPoseLibrary() {
    }

    public static FlightPose target(FlightAnimationState state, FlightMotionSample sample) {
        return switch (state) {
            case HOVER -> hover(sample);
            case BOOST -> boost(sample);
            case MANEUVER -> maneuver(sample);
            case EXIT -> FlightPose.VANILLA;
        };
    }

    /**
     * Eased boost weight from the reference timer. The reference computes
     * {@code curve(flight_boost_timer)}, where the timer fills or drains by
     * {@code 1 / 8} per tick and {@code curve(x) = (sin((2x - 1) * pi / 2) + 1) / 2}.
     * A value of zero is pure hover and one is pure boost.
     */
    public static float boostBlend(float timer) {
        float clamped = clamp(timer, 0.0F, 1.0F);
        return (Mth.sin((clamped * 2.0F - 1.0F) * (float) (Math.PI / 2.0D)) + 1.0F) * 0.5F;
    }

    /** Advances the reference eight-tick boost timer by one client tick. */
    public static float advanceBoostTimer(float timer, boolean boosting) {
        float step = 1.0F / BOOST_BLEND_TICKS;
        return clamp(timer + (boosting ? step : -step), 0.0F, 1.0F);
    }

    /**
     * Blends the ordinary hover and boost silhouettes using the reference boost
     * timer. Maneuvre pitch/roll is authoritative and is applied separately by
     * the renderer, so it is deliberately absent here.
     */
    public static FlightPose ordinaryPose(FlightMotionSample sample, float boostBlend) {
        float blend = clamp(boostBlend, 0.0F, 1.0F);
        if (blend <= 1.0E-4F) {
            return hover(sample);
        }
        if (blend >= 1.0F - 1.0E-4F) {
            return boost(sample);
        }
        return FlightPose.lerp(hover(sample), boost(sample), blend);
    }

    /** Reference boost body pitch for a Minecraft view pitch. */
    public static float boostBodyPitch(float viewPitchDegrees) {
        return -(viewPitchDegrees + BOOST_BASE_PITCH);
    }

    /**
     * Yaw applied on top of the vanilla movement-body yaw while the pose is
     * active. Vanilla already applied {@code 180 - bodyYaw}; adding
     * {@code bodyYaw - lookYaw} yields the {@code 180 - lookYaw} frame, so the
     * body pitch/roll axes follow the crosshair instead of the last movement
     * direction. The shortest arc keeps a 180-degree turn continuous.
     */
    public static float lookYawCorrectionDegrees(float bodyYawDegrees, float lookYawDegrees) {
        return Mth.wrapDegrees(bodyYawDegrees - lookYawDegrees);
    }

    /**
     * Reference low-speed hover phase in {@code [0, 1)}. Fisk derives it from
     * {@code entity.loop(20 * pi) + 0.4}; the tick counter is the same
     * deterministic clock on both sides.
     */
    public static float idlePhase(int tickCount) {
        float phase = tickCount / IDLE_LOOP_TICKS + IDLE_PHASE_OFFSET;
        return phase - (float) Math.floor(phase);
    }

    /**
     * Velocity gate for the reference idle layer:
     * {@code 1 - curve(min(1.5 * (horizontal + (0.3 * vertical)^2), 1))}.
     * The layer fades out once ordinary motion owns the silhouette.
     */
    static float idleGate(FlightMotionSample sample) {
        float vertical = 0.3F * sample.verticalSpeed();
        float motion = sample.horizontalSpeed() + vertical * vertical;
        return 1.0F - boostBlend(Math.min(1.5F * motion, 1.0F));
    }

    /**
     * Upright hover that leans with the real horizontal velocity. The velocity
     * direction is already expressed in the player's yaw frame, so a strafe or
     * a reversal tilts the body without any scripted yaw.
     *
     * <p>This reproduces the reference hover animation:</p>
     * <ul>
     *   <li>the whole-body lean is scaled by {@code min(1.5 * speed, 1)}, so a
     *       slow drift only tilts the torso slightly;</li>
     *   <li>the head keeps the real look pitch and adds the speed-scaled
     *       {@code -15 * forward} projection, with the same raw-velocity read as
     *       the body lean;</li>
     *   <li>arms and legs receive the small velocity-scaled offsets that stop
     *       the upright pose from reading as a rigid mannequin;</li>
     *   <li>a low-speed idle layer adds the reference's slow sway and
     *       asymmetric tuck. Its head pitch/yaw and body yaw offsets are
     *       deliberately omitted so the neck keeps the real crosshair.</li>
     * </ul>
     */
    private static FlightPose hover(FlightMotionSample sample) {
        float speed = sample.horizontalSpeed();
        float forward = clamp(sample.bodyForward(), -1.0F, 1.0F) * speed;
        float strafe = clamp(sample.bodyRight(), -1.0F, 1.0F) * speed;
        float bodyLeanScale = Math.min(1.5F * speed, 1.0F);
        float limbScale = Math.min(2.0F * speed, 1.0F);
        float limbForward = forward * limbScale;
        float limbStrafe = strafe * limbScale;
        float absForward = Math.abs(limbForward);
        // Reference idle layer. data_0 is the velocity gate and data_1 is the
        // slow secondary sine; x is their sum for the limbs and torso pitch.
        float idleGate = idleGate(sample);
        float idleSway = IDLE_SWAY_SCALE * idleGate
                * Mth.sin((float) (Math.PI * 2.0D) * sample.idlePhase());
        float idle = idleGate + idleSway;
        float bodyPitch = -HOVER_LEAN_DEGREES * bodyLeanScale * clamp(forward, -2.0F, 2.0F)
                - 3.5F * idleGate + 5.0F * idleSway;
        float bodyRoll = HOVER_LEAN_DEGREES * bodyLeanScale * clamp(strafe, -2.0F, 2.0F);
        // The reference hovers with an upright torso but lets the neck keep the
        // real look pitch; it then layers the speed-scaled forward offset on
        // top. A fixed local angle would make the head stare past the crosshair.
        //
        // The reference stores model joint angles in radians, while FlightPose
        // exposes degrees. The coefficients below are deliberately converted;
        // treating them as degrees would erase almost all of the reference's
        // limb motion and leave the hover looking like a rigid mannequin.
        float headPitch = sample.viewPitch()
                - HOVER_LEAN_DEGREES * clamp(limbForward, -2.0F, 2.0F);
        float rightArmX = Mth.RAD_TO_DEG * (0.1F * limbForward + 0.1F * limbStrafe)
                - (10.0F * idleGate + 0.1F * Mth.RAD_TO_DEG * idleSway);
        float leftArmX = Mth.RAD_TO_DEG * (0.1F * limbForward - 0.1F * limbStrafe)
                + 5.0F * idleGate + 0.08F * Mth.RAD_TO_DEG * idleSway;
        float rightArmZ = HOVER_ARM_BASELINE_Z
                + Mth.RAD_TO_DEG * (0.1F * absForward + 0.2F * limbStrafe)
                + 6.0F * idle;
        float leftArmZ = -(HOVER_ARM_BASELINE_Z
                + Mth.RAD_TO_DEG * (0.1F * absForward - 0.2F * limbStrafe))
                - 10.0F * idle;
        float rightLegX = Mth.RAD_TO_DEG * (0.3F * limbForward) + 20.0F * idle;
        float leftLegX = Mth.RAD_TO_DEG * (0.2F * limbForward)
                + 5.0F * idleGate - 15.0F * idleSway;
        float rightLegZ = Mth.RAD_TO_DEG * (0.05F * absForward + 0.4F * limbStrafe);
        float leftLegZ = -Mth.RAD_TO_DEG * (0.05F * absForward - 0.4F * limbStrafe);
        return new FlightPose(
                bodyPitch, bodyRoll, 0.0F,
                headPitch, 0.0F, 0.0F,
                rightArmX, 10.0F * idleGate, rightArmZ,
                leftArmX, -20.0F * idleGate, leftArmZ,
                rightLegX, 5.0F * idleGate, rightLegZ,
                leftLegX, -8.0F * idleGate, leftLegZ);
    }

    /** Reference horizontal boost pose, including its turn layer. */
    private static FlightPose boost(FlightMotionSample sample) {
        float turnOffset = boostTurnOffset(sample);
        float bank = boostTurnBank(sample, turnOffset);
        return horizontalPose(boostBodyPitch(sample.viewPitch()), bank, BOOST_BODY_LIFT,
                BOOST_HEAD_PITCH, -bank * BOOST_HEAD_COUNTER_ROLL,
                0.5F * Math.abs(turnOffset), 0.3F * turnOffset, -0.3F * turnOffset);
    }

    /**
     * Signed turn angle used by the reference boost animation, in degrees:
     * {@code sign(cross) * degrees(acos(dot)) * min(speed, 1) / 2.5}. The cross
     * product is expressed with the player's right vector, which is the mirror
     * of the reference's left-positive strafe axis.
     *
     * <p>The reference adds this value to radian model joints, so converting it
     * to degrees here is what gives the turn its visible arm and leg offsets.
     * A 90-degree velocity/crosshair mismatch produces a 36-degree offset at
     * full speed: 18 degrees on each arm and 10.8 degrees on each leg.</p>
     */
    static float boostTurnOffset(FlightMotionSample sample) {
        float speed = sample.horizontalSpeed();
        if (speed <= 1.0E-4F) {
            return 0.0F;
        }
        float forward = clamp(sample.bodyForward(), -1.0F, 1.0F);
        float right = clamp(sample.bodyRight(), -1.0F, 1.0F);
        double angleDegrees = Math.toDegrees(Math.acos(clamp(forward, -1.0F, 1.0F)));
        double sign = Math.signum(-right);
        return (float) (sign * angleDegrees * Math.min(speed, 1.0F)
                / BOOST_TURN_OFFSET_DIVISOR);
    }

    /**
     * Reference boost bank. The reference rotates the body by half of the raw
     * velocity/crosshair angle, gated by {@code min(speed / 2, 1)}. That can
     * exceed 45 degrees during an ordinary 90-degree turn, so Ascendant keeps a
     * readable fraction and caps it; the neck counter-rotates by half the bank
     * to keep the crosshair readable. The sign banks into the turn.
     */
    static float boostTurnBank(FlightMotionSample sample, float turnOffsetDegrees) {
        float speedGate = Math.min(sample.horizontalSpeed() / 2.0F, 1.0F);
        float reference = -turnOffsetDegrees * BOOST_TURN_OFFSET_DIVISOR * 0.5F * speedGate;
        return clamp(reference * BOOST_TURN_BANK_SCALE,
                -BOOST_TURN_BANK_MAX_DEGREES, BOOST_TURN_BANK_MAX_DEGREES);
    }

    /**
     * Neutral silhouette under a scripted loop or roll. The loop pitch and roll
     * angle are supplied separately by {@link FlightPoseSample}, so the pose
     * itself stays constant for the whole maneuvre.
     */
    private static FlightPose maneuver(FlightMotionSample sample) {
        return horizontalPose(boostBodyPitch(sample.viewPitch()), 0.0F, BOOST_BODY_LIFT,
                BOOST_HEAD_PITCH, 0.0F, 0.0F, 0.0F, 0.0F);
    }

    private static FlightPose horizontalPose(float bodyPitch, float bodyRoll, float bodyLift,
                                             float headPitch, float headRoll, float armLift,
                                             float rightLegOffset, float leftLegOffset) {
        return new FlightPose(
                bodyPitch, bodyRoll, bodyLift,
                headPitch, 0.0F, headRoll,
                8.0F + armLift, 18.0F, 12.0F,
                8.0F + armLift, -18.0F, -12.0F,
                15.0F + rightLegOffset, 2.0F, 3.0F,
                15.0F + leftLegOffset, -2.0F, -3.0F);
    }

    /**
     * Full 360-degree roll offset for an aerial maneuvre. Progress is derived
     * from the server-synced ticks remaining, so every client sees the same
     * rotation without any client-side authority over the maneuvre.
     */
    public static float maneuverRoll(int direction, int ticksRemaining) {
        float progress = 1.0F - clamp(ticksRemaining / (float) MANEUVER_TICKS, 0.0F, 1.0F);
        return maneuverRollProgress(direction, progress);
    }

    /**
     * Progress-based version used by deterministic client playback. Kept
     * separate from the tick-count helper so a maneuvre can be replayed from a
     * synced phase without depending on local authority. Fisk's barrel roll
     * uses its eased {@code curve} rather than a linear turn.
     */
    public static float maneuverRollProgress(int direction, float progress) {
        if (direction != -1 && direction != 1) {
            return 0.0F;
        }
        return direction * 360.0F * boostBlend(clamp(progress, 0.0F, 1.0F));
    }

    /**
     * Additive limb and body-pitch layer from {@code barrel_roll.fsk}. The full
     * body rotation stays in the separate maneuvre channel; this layer only
     * accents the arms, legs and torso so the roll reads as a whole-body move.
     *
     * <p>Fisk evaluates {@code curved = curve(timer)} and
     * {@code envelope = sin(|curved| * pi)}, with a signed value that follows
     * the roll direction. Ascendant uses progress 0..1 directly, so the same
     * envelope is {@code sin(pi * curve(progress))}. The direction sign maps
     * Ascendant's +1 (right-hand roll) to Fisk's negative barrel-roll timer.</p>
     */
    public static FlightPose rollOverlay(int direction, float progress) {
        if (direction != -1 && direction != 1) {
            return FlightPose.VANILLA;
        }
        float curved = boostBlend(clamp(progress, 0.0F, 1.0F));
        float envelope = Mth.sin((float) Math.PI * curved);
        if (envelope <= 1.0E-4F) {
            return FlightPose.VANILLA;
        }
        // Fisk's signed y term: positive timer (Ascendant -1/left) uses +y.
        float signed = -direction * envelope;
        return new FlightPose(
                -10.0F * envelope, 0.0F, 0.0F,
                0.0F, 0.0F, 0.0F,
                -30.0F * signed - 10.0F * envelope, 0.0F, 0.0F,
                30.0F * signed - 10.0F * envelope, 0.0F, 0.0F,
                -10.0F * signed - 10.0F * envelope, 0.0F, 0.0F,
                10.0F * signed - 10.0F * envelope, 0.0F, 0.0F);
    }

    /** Package-visible for tests. */
    static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
