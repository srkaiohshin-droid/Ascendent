package br.com.ascendant.power.component;

import com.google.gson.JsonObject;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Emits a radial kinetic wave that throws back nearby hostile entities.
 */
public final class KnockbackActionComponent implements IPowerComponent {
    public static final String TYPE = "ascendant:knockback_action";
    private final double radius;
    private final double strength;

    public KnockbackActionComponent(double radius, double strength) {
        this.radius = radius;
        this.strength = strength;
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

        AABB box = player.getBoundingBox().inflate(radius);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && e.isAlive() && !e.isAlliedTo(player));

        for (LivingEntity target : targets) {
            Vec3 diff = target.position().subtract(player.position());
            double dist = Math.max(0.1D, diff.horizontalDistance());
            Vec3 push = new Vec3(diff.x / dist * strength, 0.4D * strength, diff.z / dist * strength);
            target.setDeltaMovement(push);
            target.hurtMarked = true;
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.8F, 1.8F);
        level.sendParticles(ParticleTypes.SONIC_BOOM, player.getX(), player.getY() + 0.8D, player.getZ(),
                1, 0.0D, 0.0D, 0.0D, 0.0D);
        return true;
    }

    @Override
    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", TYPE);
        obj.addProperty("radius", radius);
        obj.addProperty("strength", strength);
        return obj;
    }

    public static KnockbackActionComponent fromJson(JsonObject json) {
        double rad = json.has("radius") ? json.get("radius").getAsDouble() : 5.0D;
        double str = json.has("strength") ? json.get("strength").getAsDouble() : 1.5D;
        return new KnockbackActionComponent(rad, str);
    }
}
