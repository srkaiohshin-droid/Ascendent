package br.com.ascendant.flight;

/**
 * Immutable target pose for a player model. All values are in degrees.
 *
 * <p>Body values are applied to the whole-model render transform and follow the
 * conventions of the 1.20.1 player renderer (the renderer flips the model after
 * {@code setupRotations}). Limb values are absolute local rotations for the
 * vanilla joints: 0 keeps an arm or leg hanging down, values near -170 extend an
 * arm forward once the body is horizontal. The player model has no elbow or knee
 * joint, so the asymmetric arm and leg values are what suggest a natural bend.</p>
 *
 * <p>Sign conventions:</p>
 * <ul>
 *   <li>{@code bodyPitchDegrees == -90} is horizontal with the head forward;
 *       larger negative values dive, values closer to 0 climb.</li>
 *   <li>{@code bodyRollDegrees > 0} banks to the player's right.</li>
 *   <li>{@code headXRotDegrees} is the absolute local head rotation. A
 *       horizontal body needs roughly -90 degrees so the face looks along the
 *       flight direction instead of at the ground.</li>
 *   <li>{@code bodyLift} is measured in the pre-flip model frame of
 *       {@code setupRotations}; positive values lower the horizontal body.</li>
 * </ul>
 */
public final class FlightPose {
    public static final FlightPose VANILLA = new FlightPose(
            0.0F, 0.0F, 0.0F, 0.0F,
            0.0F, 0.0F, 0.0F,
            0.0F, 0.0F, 0.0F,
            0.0F, 0.0F, 0.0F,
            0.0F, 0.0F, 0.0F);

    public final float bodyPitchDegrees;
    public final float bodyRollDegrees;
    public final float bodyLift;
    public final float headXRotDegrees;

    public final float rightArmXRot;
    public final float rightArmYRot;
    public final float rightArmZRot;
    public final float leftArmXRot;
    public final float leftArmYRot;
    public final float leftArmZRot;
    public final float rightLegXRot;
    public final float rightLegYRot;
    public final float rightLegZRot;
    public final float leftLegXRot;
    public final float leftLegYRot;
    public final float leftLegZRot;

    public FlightPose(float bodyPitchDegrees, float bodyRollDegrees, float bodyLift,
                      float headXRotDegrees,
                      float rightArmXRot, float rightArmYRot, float rightArmZRot,
                      float leftArmXRot, float leftArmYRot, float leftArmZRot,
                      float rightLegXRot, float rightLegYRot, float rightLegZRot,
                      float leftLegXRot, float leftLegYRot, float leftLegZRot) {
        this.bodyPitchDegrees = bodyPitchDegrees;
        this.bodyRollDegrees = bodyRollDegrees;
        this.bodyLift = bodyLift;
        this.headXRotDegrees = headXRotDegrees;
        this.rightArmXRot = rightArmXRot;
        this.rightArmYRot = rightArmYRot;
        this.rightArmZRot = rightArmZRot;
        this.leftArmXRot = leftArmXRot;
        this.leftArmYRot = leftArmYRot;
        this.leftArmZRot = leftArmZRot;
        this.rightLegXRot = rightLegXRot;
        this.rightLegYRot = rightLegYRot;
        this.rightLegZRot = rightLegZRot;
        this.leftLegXRot = leftLegXRot;
        this.leftLegYRot = leftLegYRot;
        this.leftLegZRot = leftLegZRot;
    }

    /** Returns a copy with a different whole-body bank. */
    public FlightPose withRoll(float rollDegrees) {
        return new FlightPose(bodyPitchDegrees, rollDegrees, bodyLift, headXRotDegrees,
                rightArmXRot, rightArmYRot, rightArmZRot,
                leftArmXRot, leftArmYRot, leftArmZRot,
                rightLegXRot, rightLegYRot, rightLegZRot,
                leftLegXRot, leftLegYRot, leftLegZRot);
    }

    /** Linear interpolation of every joint. Used for frame-rate independent sampling. */
    public static FlightPose lerp(FlightPose from, FlightPose to, float delta) {
        if (delta <= 0.0F) {
            return from;
        }
        if (delta >= 1.0F) {
            return to;
        }
        return new FlightPose(
                lerp(from.bodyPitchDegrees, to.bodyPitchDegrees, delta),
                lerp(from.bodyRollDegrees, to.bodyRollDegrees, delta),
                lerp(from.bodyLift, to.bodyLift, delta),
                lerp(from.headXRotDegrees, to.headXRotDegrees, delta),
                lerp(from.rightArmXRot, to.rightArmXRot, delta),
                lerp(from.rightArmYRot, to.rightArmYRot, delta),
                lerp(from.rightArmZRot, to.rightArmZRot, delta),
                lerp(from.leftArmXRot, to.leftArmXRot, delta),
                lerp(from.leftArmYRot, to.leftArmYRot, delta),
                lerp(from.leftArmZRot, to.leftArmZRot, delta),
                lerp(from.rightLegXRot, to.rightLegXRot, delta),
                lerp(from.rightLegYRot, to.rightLegYRot, delta),
                lerp(from.rightLegZRot, to.rightLegZRot, delta),
                lerp(from.leftLegXRot, to.leftLegXRot, delta),
                lerp(from.leftLegYRot, to.leftLegYRot, delta),
                lerp(from.leftLegZRot, to.leftLegZRot, delta));
    }

    private static float lerp(float from, float to, float delta) {
        return from + (to - from) * delta;
    }
}
