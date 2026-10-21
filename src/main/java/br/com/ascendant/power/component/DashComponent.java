package br.com.ascendant.power.component;

import com.google.gson.JsonObject;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Launches the player rapidly along their view angle with a kinetic trail.
 */
public final class DashComponent implements IPowerComponent {
    public static final String TYPE = "ascendant:dash";
    private final double speed;
    private final double upwardBoost;

    public DashComponent(double speed, double upwardBoost) {
        this.speed = speed;
        this.upwardBoost = upwardBoost;
    }

    public DashComponent(double speed) {
        this(speed, 0.15D);
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

        Vec3 motion = new Vec3(look.x * speed, look.y * speed + upwardBoost, look.z * speed);
        player.setDeltaMovement(motion);
        player.hurtMarked = true;
        player.resetFallDistance();

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.2F, 1.5F);
        level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.5D, player.getZ(),
                15, 0.3D, 0.3D, 0.3D, 0.05D);
        return true;
    }

    @Override
    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", TYPE);
        obj.addProperty("speed", speed);
        obj.addProperty("upwardBoost", upwardBoost);
        return obj;
    }

    public static DashComponent fromJson(JsonObject json) {
        double speed = json.has("speed") ? json.get("speed").getAsDouble() : 1.5D;
        double upwardBoost = json.has("upwardBoost") ? json.get("upwardBoost").getAsDouble() : 0.15D;
        return new DashComponent(speed, upwardBoost);
    }
}
