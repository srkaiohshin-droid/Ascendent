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
 * Telekinetically pulls an entity or multiple entities towards the player.
 */
public final class PullActionComponent implements IPowerComponent {
    public static final String TYPE = "ascendant:pull_action";
    private final double range;
    private final double pullStrength;

    public PullActionComponent(double range, double pullStrength) {
        this.range = range;
        this.pullStrength = pullStrength;
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
        Vec3 look = context.getLookDirection();
        Vec3 eyePos = player.getEyePosition();

        AABB box = player.getBoundingBox().inflate(range);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && e.isAlive() && !e.isAlliedTo(player));

        LivingEntity closestTarget = null;
        double closestDot = 0.5D; // must be roughly in front

        for (LivingEntity target : targets) {
            Vec3 toTarget = target.getEyePosition().subtract(eyePos).normalize();
            double dot = look.dot(toTarget);
            if (dot > closestDot) {
                closestDot = dot;
                closestTarget = target;
            }
        }

        if (closestTarget == null) {
            return false;
        }

        Vec3 pullVec = player.position().subtract(closestTarget.position()).normalize().scale(pullStrength);
        closestTarget.setDeltaMovement(new Vec3(pullVec.x, 0.3D * pullStrength, pullVec.z));
        closestTarget.hurtMarked = true;

        level.playSound(null, closestTarget.getX(), closestTarget.getY(), closestTarget.getZ(),
                SoundEvents.CONDUIT_ATTACK_TARGET, SoundSource.PLAYERS, 1.0F, 1.5F);
        level.sendParticles(ParticleTypes.ENCHANTED_HIT, closestTarget.getX(), closestTarget.getY() + 1.0D, closestTarget.getZ(),
                15, 0.3D, 0.3D, 0.3D, 0.1D);
        return true;
    }

    @Override
    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", TYPE);
        obj.addProperty("range", range);
        obj.addProperty("pullStrength", pullStrength);
        return obj;
    }

    public static PullActionComponent fromJson(JsonObject json) {
        double rng = json.has("range") ? json.get("range").getAsDouble() : 12.0D;
        double str = json.has("pullStrength") ? json.get("pullStrength").getAsDouble() : 1.2D;
        return new PullActionComponent(rng, str);
    }
}
