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
 * Suspends enemies in the air by applying Levitation.
 */
public final class LevitationComponent implements IPowerComponent {
    public static final String TYPE = "ascendant:levitation";
    private final double radius;
    private final int durationTicks;
    private final int amplifier;

    public LevitationComponent(double radius, int durationTicks, int amplifier) {
        this.radius = radius;
        this.durationTicks = durationTicks;
        this.amplifier = amplifier;
    }

    public LevitationComponent(double radius, int durationTicks) {
        this(radius, durationTicks, 1);
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

        if (targets.isEmpty()) {
            return false;
        }

        for (LivingEntity target : targets) {
            target.addEffect(new MobEffectInstance(MobEffects.LEVITATION, durationTicks, amplifier, false, true, true));
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SHULKER_SHOOT, SoundSource.PLAYERS, 1.0F, 1.2F);
        level.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 0.5D, player.getZ(),
                20, radius * 0.5D, 0.4D, radius * 0.5D, 0.1D);
        return true;
    }

    @Override
    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", TYPE);
        obj.addProperty("radius", radius);
        obj.addProperty("durationTicks", durationTicks);
        obj.addProperty("amplifier", amplifier);
        return obj;
    }

    public static LevitationComponent fromJson(JsonObject json) {
        double rad = json.has("radius") ? json.get("radius").getAsDouble() : 6.0D;
        int dur = json.has("durationTicks") ? json.get("durationTicks").getAsInt() : 60;
        int amp = json.has("amplifier") ? json.get("amplifier").getAsInt() : 1;
        return new LevitationComponent(rad, dur, amp);
    }
}
