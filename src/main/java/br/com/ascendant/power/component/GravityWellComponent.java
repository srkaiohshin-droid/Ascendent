package br.com.ascendant.power.component;

import com.google.gson.JsonObject;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Local gravity field around the caster.
 *
 * <p>Two distinct modes share the same physics: {@code pull} drags visible hostiles toward
 * the caster and {@code push} throws them away with an upward bias. The field is a sustained
 * mechanic, so the same component can be used by a toggle/sustained power (ticked every
 * player tick) or by an active power (one pulse on activation).</p>
 *
 * <p>Velocity is clamped per tick so the field cannot launch entities at arbitrary speed,
 * and {@link IPowerComponent#onToggle} does not need to undo anything because the effect is
 * pure velocity: it decays naturally once the field stops.</p>
 */
public final class GravityWellComponent implements IPowerComponent {
    public static final String TYPE = "ascendant:gravity_well";
    public static final String MODE_PULL = "pull";
    public static final String MODE_PUSH = "push";

    private final double radius;
    private final double strength;
    private final String mode;
    private final int maxTargets;

    public GravityWellComponent(double radius, double strength, String mode, int maxTargets) {
        this.radius = Math.max(1.0D, Math.min(24.0D, radius));
        this.strength = Math.max(0.01D, Math.min(0.35D, strength));
        this.mode = MODE_PUSH.equalsIgnoreCase(mode) ? MODE_PUSH : MODE_PULL;
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

    public String getMode() {
        return mode;
    }

    @Override
    public boolean execute(ComponentContext context) {
        return pulse(context.getPlayer());
    }

    @Override
    public void onPlayerTick(ServerPlayer player) {
        pulse(player);
    }

    private boolean pulse(ServerPlayer player) {
        List<LivingEntity> targets = CombatTargeting.visibleHostiles(player, radius, maxTargets);
        if (targets.isEmpty()) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        Vec3 center = player.position().add(0.0D, 0.5D, 0.0D);
        for (LivingEntity target : targets) {
            Vec3 offset = center.subtract(target.position());
            double distance = offset.length();
            if (distance < 1.0E-3D) {
                continue;
            }
            Vec3 direction = offset.scale(1.0D / distance);
            double falloff = 1.0D - Math.min(1.0D, distance / radius);
            double force = strength * (0.5D + 0.5D * falloff);
            Vec3 current = target.getDeltaMovement();
            Vec3 next;
            if (MODE_PULL.equals(mode)) {
                next = current.add(direction.scale(force)).add(0.0D, force * 0.25D, 0.0D);
            } else {
                next = current.add(direction.scale(-force)).add(0.0D, force, 0.0D);
            }
            double maxSpeed = MODE_PULL.equals(mode) ? 1.1D : 1.4D;
            if (next.lengthSqr() > maxSpeed * maxSpeed) {
                next = next.normalize().scale(maxSpeed);
            }
            target.setDeltaMovement(next);
            target.hurtMarked = true;
        }
        level.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 0.5D, player.getZ(),
                8, radius * 0.4D, 0.5D, radius * 0.4D, 0.02D);
        return true;
    }

    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", TYPE);
        json.addProperty("radius", radius);
        json.addProperty("strength", strength);
        json.addProperty("mode", mode);
        json.addProperty("maxTargets", maxTargets);
        return json;
    }

    public static GravityWellComponent fromJson(JsonObject json) {
        return new GravityWellComponent(
                json.has("radius") ? json.get("radius").getAsDouble() : 8.0D,
                json.has("strength") ? json.get("strength").getAsDouble() : 0.08D,
                json.has("mode") ? json.get("mode").getAsString() : MODE_PULL,
                json.has("maxTargets") ? json.get("maxTargets").getAsInt() : 12);
    }
}
