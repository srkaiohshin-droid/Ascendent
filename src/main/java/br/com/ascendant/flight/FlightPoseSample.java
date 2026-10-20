package br.com.ascendant.flight;

/** A sampled pose and the weight with which it should be applied over vanilla. */
public record FlightPoseSample(FlightPose pose, float weight) {
    public FlightPoseSample {
        if (pose == null) {
            throw new IllegalArgumentException("pose");
        }
        if (weight < 0.0F || weight > 1.0F) {
            throw new IllegalArgumentException("weight must be within [0, 1]");
        }
    }
}
