package br.com.ascendant.power.component;

import com.google.gson.JsonObject;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Radial damaging shockwave with distance falloff and real knockback.
 *
 * <p>{@link KnockbackActionComponent} only moves entities and
 * {@link DamageActionComponent} only deals damage; this component combines both so an impact
 * power has a single, coherent effect. Targets need line of sight, so the wave cannot hurt
 * entities sealed behind terrain.</p>
 */
public final class ShockwaveComponent implements IPowerComponent {
    public static final String TYPE = "ascendant:shockwave";
    private final double radius;
    private final float damage;
    private final double knockback;
    private final int maxTargets;

    public ShockwaveComponent(double radius, float damage, double knockback, int maxTargets) {
        this.radius = Math.max(1.0D, Math.min(32.0D, radius));
        this.damage = Math.max(0.5F, Math.min(60.0F, damage));
        this.knockback = Math.max(0.0D, Math.min(4.0D, knockback));
        this.maxTargets = Math.max(1, Math.min(CombatTargeting.MAX_TARGETS, maxTargets));
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
        List<LivingEntity> targets = CombatTargeting.visibleHostiles(player, radius, maxTargets);
        if (targets.isEmpty()) {
            return false;
        }

        for (LivingEntity target : targets) {
            double distance = Math.sqrt(target.distanceToSqr(player));
            double falloff = 1.0D - Math.min(1.0D, distance / radius) * 0.5D;
            target.hurt(CombatTargeting.damageSource(player, "physical"), (float) (damage * falloff));
            Vec3 away = target.position().subtract(player.position());
            Vec3 flat = new Vec3(away.x, 0.0D, away.z);
            if (flat.lengthSqr() < 1.0E-4D) {
                flat = player.getLookAngle();
            }
            Vec3 push = flat.normalize().scale(knockback * falloff).add(0.0D, 0.35D * knockback, 0.0D);
            target.push(push.x, push.y, push.z);
            target.hurtMarked = true;
        }

        level.sendParticles(ParticleTypes.SWEEP_ATTACK, player.getX(), player.getY() + 0.8D,
                player.getZ(), 12, radius * 0.5D, 0.25D, radius * 0.5D, 0.0D);
        level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.2D,
                player.getZ(), 24, radius * 0.5D, 0.1D, radius * 0.5D, 0.05D);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.9F, 1.7F);
        return true;
    }

    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", TYPE);
        json.addProperty("radius", radius);
        json.addProperty("damage", damage);
        json.addProperty("knockback", knockback);
        json.addProperty("maxTargets", maxTargets);
        return json;
    }

    public static ShockwaveComponent fromJson(JsonObject json) {
        return new ShockwaveComponent(
                json.has("radius") ? json.get("radius").getAsDouble() : 6.0D,
                json.has("damage") ? json.get("damage").getAsFloat() : 8.0F,
                json.has("knockback") ? json.get("knockback").getAsDouble() : 1.2D,
                json.has("maxTargets") ? json.get("maxTargets").getAsInt() : 12);
    }
}
