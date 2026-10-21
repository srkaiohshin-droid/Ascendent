package br.com.ascendant.power.component;

import com.google.gson.JsonObject;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Powerful vertical and directional leap with kinetic shockwave on takeoff and landing safety.
 */
public final class LeapComponent implements IPowerComponent {
    public static final String TYPE = "ascendant:leap";
    private final double horizontalPower;
    private final double verticalPower;

    public LeapComponent(double horizontalPower, double verticalPower) {
        this.horizontalPower = horizontalPower;
        this.verticalPower = verticalPower;
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

        Vec3 velocity = new Vec3(look.x * horizontalPower, verticalPower, look.z * horizontalPower);
        player.setDeltaMovement(velocity);
        player.hurtMarked = true;
        player.resetFallDistance();

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1.0F, 0.8F);
        level.sendParticles(ParticleTypes.EXPLOSION, player.getX(), player.getY(), player.getZ(),
                5, 0.4D, 0.1D, 0.4D, 0.05D);
        return true;
    }

    @Override
    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", TYPE);
        obj.addProperty("horizontalPower", horizontalPower);
        obj.addProperty("verticalPower", verticalPower);
        return obj;
    }

    public static LeapComponent fromJson(JsonObject json) {
        double horiz = json.has("horizontalPower") ? json.get("horizontalPower").getAsDouble() : 1.2D;
        double vert = json.has("verticalPower") ? json.get("verticalPower").getAsDouble() : 1.5D;
        return new LeapComponent(horiz, vert);
    }
}
