package br.com.ascendant.power.component;

import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Moves the player through solid blocks to the furthest collision-free point in a bounded step. */
public final class PhaseComponent implements IPowerComponent {
    public static final String TYPE = "ascendant:phase";
    private final double maxDistance;

    public PhaseComponent(double maxDistance) {
        this.maxDistance = Math.max(1.0D, Math.min(32.0D, maxDistance));
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public boolean isFunctional() {
        return true;
    }

    @Override
    public boolean execute(ComponentContext context) {
        ServerPlayer player = context.getPlayer();
        ServerLevel level = context.getLevel();
        Vec3 start = player.position();
        Vec3 direction = context.getLookDirection();
        Vec3 destination = null;
        double step = 0.5D;
        for (double distance = step; distance <= maxDistance; distance += step) {
            Vec3 candidate = start.add(direction.scale(distance));
            AABB candidateBounds = player.getBoundingBox().move(candidate.subtract(start));
            if (!level.getWorldBorder().isWithinBounds(candidateBounds)
                    || !level.hasChunkAt(net.minecraft.core.BlockPos.containing(candidate))
                    || !level.noCollision(player, candidateBounds)) {
                continue;
            }
            destination = candidate;
        }
        if (destination == null || destination.distanceToSqr(start) < 1.0D) {
            return false;
        }

        player.teleportTo(destination.x, destination.y, destination.z);
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
        return true;
    }

    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", TYPE);
        json.addProperty("maxDistance", maxDistance);
        return json;
    }

    public static PhaseComponent fromJson(JsonObject json) {
        return new PhaseComponent(json.has("maxDistance") ? json.get("maxDistance").getAsDouble() : 20.0D);
    }
}
