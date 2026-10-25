package br.com.ascendant.flight;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Pre-flight collision sweep for trajectory maneuvers.
 *
 * <p>The complete analytic path is checked before the server commits to it,
 * including intermediate samples between tick positions. The player's real
 * bounding box is used at every sample, so a loop that would clip a block, a
 * liquid, the world border or the build-height limits is refused instead of
 * being "fixed" by collision later. Unloaded chunks are refused because their
 * geometry cannot be verified.</p>
 */
public final class FlightManeuverSafety {
    private FlightManeuverSafety() {
    }

    /**
     * Returns {@link FlightManeuvers#PROBLEM_NONE} when the whole path is
     * clear, otherwise the first blocking reason found.
     */
    public static int sweepProblem(Player player, int kind, int direction, float yawDegrees,
                                   double speed) {
        int duration = FlightManeuvers.duration(kind);
        if (!FlightManeuvers.isTrajectory(kind) || duration <= 0
                || !Double.isFinite(speed) || !Float.isFinite(yawDegrees)) {
            return FlightManeuvers.PROBLEM_INVALID;
        }
        if (FlightManeuvers.radius(kind, speed) > FlightManeuvers.MAX_RADIUS) {
            return FlightManeuvers.PROBLEM_TOO_FAST;
        }

        Level level = player.level();
        // Use the standing box regardless of the current pose: releasing a
        // crouch or leaving a gliding pose mid-maneuver must not make the
        // reviewed volume smaller than the real hitbox.
        EntityDimensions dimensions = player.getDimensions(Pose.STANDING);
        Vec3 origin = player.position();
        Vec3 previous = origin;
        for (int step = 0; step < duration; step++) {
            double progress = (step + 1) / (double) duration;
            Vec3 point = origin.add(FlightManeuvers.offset(
                    kind, direction, yawDegrees, speed, progress));
            for (int sub = 1; sub <= FlightManeuvers.COLLISION_SUBSTEPS; sub++) {
                float fraction = sub / (float) FlightManeuvers.COLLISION_SUBSTEPS;
                Vec3 sample = previous.lerp(point, fraction);
                int problem = sampleProblem(level, player, dimensions, sample);
                if (problem != FlightManeuvers.PROBLEM_NONE) {
                    return problem;
                }
            }
            previous = point;
        }
        return FlightManeuvers.PROBLEM_NONE;
    }

    private static int sampleProblem(Level level, Player player, EntityDimensions dimensions,
                                     Vec3 sample) {
        AABB box = dimensions.makeBoundingBox(sample)
                .inflate(FlightManeuvers.SWEEP_CLEARANCE);
        if (!chunksLoaded(level, box)) {
            return FlightManeuvers.PROBLEM_UNLOADED;
        }
        if (level.getWorldBorder() != null && !level.getWorldBorder().isWithinBounds(box)) {
            return FlightManeuvers.PROBLEM_BORDER;
        }
        if (box.minY < level.getMinBuildHeight() || box.maxY >= level.getMaxBuildHeight()) {
            return FlightManeuvers.PROBLEM_HEIGHT;
        }
        if (level.containsAnyLiquid(box)) {
            return FlightManeuvers.PROBLEM_FLUID;
        }
        for (VoxelShape shape : level.getBlockCollisions(player, box)) {
            if (!shape.isEmpty()) {
                return FlightManeuvers.PROBLEM_OBSTRUCTED;
            }
        }
        return FlightManeuvers.PROBLEM_NONE;
    }

    private static boolean chunksLoaded(Level level, AABB box) {
        return level.hasChunkAt(BlockPos.containing(box.minX, box.minY, box.minZ))
                && level.hasChunkAt(BlockPos.containing(box.maxX, box.minY, box.minZ))
                && level.hasChunkAt(BlockPos.containing(box.minX, box.minY, box.maxZ))
                && level.hasChunkAt(BlockPos.containing(box.maxX, box.minY, box.maxZ))
                && level.hasChunkAt(BlockPos.containing(box.getCenter()));
    }
}
