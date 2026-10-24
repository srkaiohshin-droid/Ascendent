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
 * Instant hitscan energy beam.
 *
 * <p>Unlike {@link LightningActionComponent}, which spawns a vanilla bolt at a location, this
 * mechanic damages the entities whose hitboxes the player's aim actually crosses, in order,
 * and stops at the first solid block. Multiple targets are only hit when the component is
 * configured to pierce, which keeps a basic blast and a piercing cutter distinct.</p>
 */
public final class EnergyBeamComponent implements IPowerComponent {
    public static final String TYPE = "ascendant:energy_beam";
    private final double range;
    private final float damage;
    private final int maxTargets;
    private final String damageType;

    public EnergyBeamComponent(double range, float damage, int maxTargets, String damageType) {
        this.range = Math.max(2.0D, Math.min(64.0D, range));
        this.damage = Math.max(0.5F, Math.min(60.0F, damage));
        this.maxTargets = Math.max(1, Math.min(CombatTargeting.MAX_TARGETS, maxTargets));
        this.damageType = damageType == null ? "magic" : damageType;
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
        List<LivingEntity> targets = CombatTargeting.rayTargets(player, range, maxTargets);
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(range));

        for (LivingEntity target : targets) {
            target.hurt(CombatTargeting.damageSource(player, damageType), damage);
            if ("fire".equalsIgnoreCase(damageType)) {
                target.setSecondsOnFire(4);
            }
        }
        drawBeam(level, player, eye, end);
        return !targets.isEmpty();
    }

    private void drawBeam(ServerLevel level, ServerPlayer player, Vec3 start, Vec3 end) {
        Vec3 delta = end.subtract(start);
        double length = delta.length();
        if (length < 1.0E-4D) {
            return;
        }
        Vec3 step = delta.scale(1.0D / length);
        for (double travelled = 0.5D; travelled < length; travelled += 0.5D) {
            Vec3 point = start.add(step.scale(travelled));
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, point.x, point.y, point.z,
                    1, 0.02D, 0.02D, 0.02D, 0.0D);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.9F, 1.6F);
    }

    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", TYPE);
        json.addProperty("range", range);
        json.addProperty("damage", damage);
        json.addProperty("maxTargets", maxTargets);
        json.addProperty("damageType", damageType);
        return json;
    }

    public static EnergyBeamComponent fromJson(JsonObject json) {
        return new EnergyBeamComponent(
                json.has("range") ? json.get("range").getAsDouble() : 24.0D,
                json.has("damage") ? json.get("damage").getAsFloat() : 8.0F,
                json.has("maxTargets") ? json.get("maxTargets").getAsInt() : 1,
                json.has("damageType") ? json.get("damageType").getAsString() : "magic");
    }
}
