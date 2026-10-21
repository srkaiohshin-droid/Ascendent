package br.com.ascendant.power.component;

import com.google.gson.JsonObject;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Applies a potion/status effect or freezing to living entities within a specified radius.
 */
public final class AreaEffectActionComponent implements IPowerComponent {
    public static final String TYPE = "ascendant:area_effect";
    private final String effectId;
    private final int durationTicks;
    private final int amplifier;
    private final double radius;

    public AreaEffectActionComponent(String effectId, int durationTicks, int amplifier, double radius) {
        this.effectId = effectId;
        this.durationTicks = durationTicks;
        this.amplifier = amplifier;
        this.radius = radius;
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

        if ("freeze".equalsIgnoreCase(effectId)) {
            for (LivingEntity target : targets) {
                target.setTicksFrozen(durationTicks);
            }
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.PLAYER_HURT_FREEZE, SoundSource.PLAYERS, 1.0F, 1.0F);
            level.sendParticles(ParticleTypes.SNOWFLAKE, player.getX(), player.getY() + 1.0D, player.getZ(),
                    25, radius * 0.5D, 0.5D, radius * 0.5D, 0.05D);
            return true;
        }

        ResourceLocation effectLoc = ResourceLocation.tryParse(effectId.contains(":") ? effectId : "minecraft:" + effectId);
        if (effectLoc == null) {
            return false;
        }

        MobEffect effect = BuiltInRegistries.MOB_EFFECT.get(effectLoc);
        if (effect == null) {
            return false;
        }

        for (LivingEntity target : targets) {
            target.addEffect(new MobEffectInstance(effect, durationTicks, amplifier, false, true, true));
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0F, 1.2F);
        level.sendParticles(ParticleTypes.ENTITY_EFFECT, player.getX(), player.getY() + 0.8D, player.getZ(),
                20, radius * 0.5D, 0.3D, radius * 0.5D, 0.1D);
        return true;
    }

    @Override
    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", TYPE);
        obj.addProperty("effectId", effectId);
        obj.addProperty("durationTicks", durationTicks);
        obj.addProperty("amplifier", amplifier);
        obj.addProperty("radius", radius);
        return obj;
    }

    public static AreaEffectActionComponent fromJson(JsonObject json) {
        String eff = json.has("effectId") ? json.get("effectId").getAsString() : "slowness";
        int dur = json.has("durationTicks") ? json.get("durationTicks").getAsInt() : 100;
        int amp = json.has("amplifier") ? json.get("amplifier").getAsInt() : 1;
        double rad = json.has("radius") ? json.get("radius").getAsDouble() : 6.0D;
        return new AreaEffectActionComponent(eff, dur, amp, rad);
    }
}
