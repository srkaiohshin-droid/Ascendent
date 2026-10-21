package br.com.ascendant.power.component;

import com.google.gson.JsonObject;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Deals typed damage to enemies within a conical or spherical radius.
 */
public final class DamageActionComponent implements IPowerComponent {
    public static final String TYPE = "ascendant:damage_action";
    private final float damage;
    private final double radius;
    private final String damageType;

    public DamageActionComponent(float damage, double radius, String damageType) {
        this.damage = damage;
        this.radius = radius;
        this.damageType = damageType;
    }

    public DamageActionComponent(float damage, double radius) {
        this(damage, radius, "physical");
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

        DamageSource source;
        if ("fire".equalsIgnoreCase(damageType)) {
            source = player.damageSources().inFire();
        } else if ("magic".equalsIgnoreCase(damageType)) {
            source = player.damageSources().magic();
        } else if ("lightning".equalsIgnoreCase(damageType)) {
            source = player.damageSources().lightningBolt();
        } else {
            source = player.damageSources().playerAttack(player);
        }

        for (LivingEntity target : targets) {
            target.hurt(source, damage);
            if ("fire".equalsIgnoreCase(damageType)) {
                target.setSecondsOnFire(5);
            }
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, player.getX(), player.getY() + 1.0D, player.getZ(),
                8, radius * 0.5D, 0.2D, radius * 0.5D, 0.0D);
        return true;
    }

    @Override
    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", TYPE);
        obj.addProperty("damage", damage);
        obj.addProperty("radius", radius);
        obj.addProperty("damageType", damageType);
        return obj;
    }

    public static DamageActionComponent fromJson(JsonObject json) {
        float dmg = json.has("damage") ? json.get("damage").getAsFloat() : 8.0F;
        double rad = json.has("radius") ? json.get("radius").getAsDouble() : 4.0D;
        String type = json.has("damageType") ? json.get("damageType").getAsString() : "physical";
        return new DamageActionComponent(dmg, rad, type);
    }
}
