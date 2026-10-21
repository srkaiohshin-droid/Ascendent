package br.com.ascendant.power.component;

import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Pulls nearby non-allied living entities toward the player while sustained. */
public final class VortexActionComponent implements IPowerComponent {
    public static final String TYPE = "ascendant:vortex_action";
    private final double radius;
    private final double pullStrength;

    public VortexActionComponent(double radius, double pullStrength) {
        this.radius = Math.max(1.0D, Math.min(32.0D, radius));
        this.pullStrength = Math.max(0.01D, Math.min(0.5D, pullStrength));
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
    public void onPlayerTick(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        AABB area = player.getBoundingBox().inflate(radius);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, area,
                entity -> entity != player && entity.isAlive() && !entity.isAlliedTo(player));
        Vec3 center = player.position();
        for (LivingEntity target : targets) {
            Vec3 offset = center.subtract(target.position());
            double distance = offset.length();
            if (distance < 0.75D || distance > radius) {
                continue;
            }
            Vec3 pull = offset.normalize().scale(pullStrength);
            Vec3 current = target.getDeltaMovement();
            Vec3 resulting = current.add(pull.x, Math.min(0.04D, pull.y), pull.z);
            if (resulting.lengthSqr() > 2.25D) {
                resulting = resulting.normalize().scale(1.5D);
            }
            target.setDeltaMovement(resulting);
            target.hurtMarked = true;
        }
    }

    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", TYPE);
        json.addProperty("radius", radius);
        json.addProperty("pullStrength", pullStrength);
        return json;
    }

    public static VortexActionComponent fromJson(JsonObject json) {
        return new VortexActionComponent(
                json.has("radius") ? json.get("radius").getAsDouble() : 10.0D,
                json.has("pullStrength") ? json.get("pullStrength").getAsDouble() : 0.08D);
    }
}
