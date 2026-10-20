package br.com.ascendant.flight;

/**
 * Discrete flight animation states. The state only selects a target pose; the
 * actual joints are produced by {@link FlightPoseLibrary} and blended over time
 * by the client animation controller.
 */
public enum FlightAnimationState {
    /** Hovering with an almost upright, relaxed posture. */
    HOVER,
    /** Initial launch, blending from a forward lean into the heroic pose. */
    TAKEOFF,
    /** Main horizontal superhero pose. */
    HEROIC_FLIGHT,
    /** Stable high-speed cruise, sharing the heroic silhouette without sway. */
    CRUISE,
    /** Ascending trajectory; the chest opens and the gaze lifts. */
    CLIMB,
    /** Descending trajectory; the body follows the dive angle. */
    DIVE,
    /** Horizontal flight with a bank into a turn. */
    TURNING,
    /** Active deceleration with the arms flared back. */
    BRAKING,
    /** Flight has ended; the controller blends the pose back to vanilla. */
    EXIT
}
