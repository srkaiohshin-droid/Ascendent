package br.com.ascendant.power.component;

import com.google.gson.JsonObject;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Perception/thermal sense: scans living entities within radius and applies Glowing status.
 */
public final class ThermalVisionComponent implements IPowerComponent {
    public static final String TYPE = "ascendant:thermal_vision";
    private final double radius;
    private final int durationTicks;

    public ThermalVisionComponent(double radius, int durationTicks) {
        this.radius = radius;
        this.durationTicks = durationTicks;
    }

    public ThermalVisionComponent() {
        this(32.0D, 160);
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
        List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && e.isAlive());

        for (LivingEntity e : entities) {
            e.addEffect(new MobEffectInstance(MobEffects.GLOWING, durationTicks, 0, false, false, false));
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.7F, 1.8F);
        level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0D, player.getZ(),
                15, 0.5D, 0.5D, 0.5D, 0.1D);
        return true;
    }

    @Override
    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", TYPE);
        obj.addProperty("radius", radius);
        obj.addProperty("durationTicks", durationTicks);
        return obj;
    }

    public static ThermalVisionComponent fromJson(JsonObject json) {
        double rad = json.has("radius") ? json.get("radius").getAsDouble() : 32.0D;
        int dur = json.has("durationTicks") ? json.get("durationTicks").getAsInt() : 160;
        return new ThermalVisionComponent(rad, dur);
    }
}
