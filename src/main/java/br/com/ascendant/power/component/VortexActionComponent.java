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
 * Creates an inward gravitational or air vortex, pulling all surrounding hostiles towards the center.
 */
public final class VortexActionComponent implements IPowerComponent {
    public static final String TYPE = "ascendant:vortex_action";
    private final double radius;
    private final double suctionStrength;

    public VortexActionComponent(double radius, double suctionStrength) {
        this.radius = radius;
        this.suctionStrength = suctionStrength;
    }

    public VortexActionComponent(double radius) {
        this(radius, 1.2D);
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

        Vec3 center = player.position();
        for (LivingEntity target : targets) {
            Vec3 toCenter = center.subtract(target.position()).normalize().scale(suctionStrength);
            target.setDeltaMovement(new Vec3(toCenter.x, 0.25D * suctionStrength, toCenter.z));
            target.hurtMarked = true;
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.CONDUIT_DEACTIVATE, SoundSource.PLAYERS, 1.2F, 0.7F);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1.0D, player.getZ(),
                30, radius * 0.5D, 0.5D, radius * 0.5D, 0.05D);
        return true;
    }

    @Override
    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", TYPE);
        obj.addProperty("radius", radius);
        obj.addProperty("suctionStrength", suctionStrength);
        return obj;
    }

    public static VortexActionComponent fromJson(JsonObject json) {
        double rad = json.has("radius") ? json.get("radius").getAsDouble() : 8.0D;
        double strength = json.has("suctionStrength") ? json.get("suctionStrength").getAsDouble() : 1.2D;
        return new VortexActionComponent(rad, strength);
    }
}
