package br.com.ascendant.flight;

/**
 * Discrete flight animation states. The state only selects a target pose; the
 * actual joints are produced by {@link FlightPoseLibrary} and blended over time
 * by the client animation controller.
 *
 * <p>The states are a classification of the real flight situation, not an
 * exclusive script. Several of them (climb, dive, turning, accelerating) share
 * the same underlying heroic silhouette and only tilt or articulate the joints
 * that the manoeuvre needs.</p>
 */
public enum FlightAnimationState {
    /** Hovering with an almost upright, relaxed posture and a live balance cycle. */
    HOVER,
    /** Initial launch: crouched preparation, then a forward dive into flight. */
    TAKEOFF,
    /** Main horizontal superhero pose. */
    HEROIC_FLIGHT,
    /** Low forward speed, between hover and full flight. */
    SLOW_FLIGHT,
    /** Stable high-speed cruise, sharing the heroic silhouette without sway. */
    CRUISE,
    /** Ascending trajectory; the chest opens and the gaze lifts. */
    CLIMB,
    /** Descending trajectory; the body follows the dive angle. */
    DIVE,
    /** Horizontal flight with a bank into a turn. */
    TURNING,
    /** Positive longitudinal acceleration; arms sweep back and the body stretches. */
    ACCELERATING,
    /** Coasting deceleration without the brake key; body sits up and limbs flare. */
    DECELERATING,
    /** Active deceleration with the arms flared back. */
    BRAKING,
    /** A server-authorised loop, full roll or half roll is running. */
    MANEUVER,
    /** Flight has ended near the ground; the pose folds into a landing. */
    LANDING,
    /** Flight has ended; the controller blends the pose back to vanilla. */
    EXIT
}
