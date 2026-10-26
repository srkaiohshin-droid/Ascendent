package br.com.ascendant.flight;

/**
 * Deterministic flight animation classifier.
 *
 * <p>The reference flight has exactly two ordinary silhouettes and one
 * scripted overlay, so there are no speed bands, hysteresis or turn-rate
 * thresholds to tune: a running maneuvre wins, sprinting selects the boost
 * pose, and every other active tick is a hover.</p>
 */
public final class FlightAnimationSelector {
    private FlightAnimationSelector() {
    }

    public static FlightAnimationState select(FlightAnimationState previous, int ticksInState,
                                              FlightMotionSample sample) {
        if (sample == null || !sample.active()) {
            return FlightAnimationState.EXIT;
        }
        if (sample.maneuvering()) {
            return FlightAnimationState.MANEUVER;
        }
        if (sample.boosting()) {
            return FlightAnimationState.BOOST;
        }
        return FlightAnimationState.HOVER;
    }
}
