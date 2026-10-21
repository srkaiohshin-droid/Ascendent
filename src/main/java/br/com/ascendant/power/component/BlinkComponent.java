package br.com.ascendant.power.component;

import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Instantly teleports the player forward along their gaze up to maxDistance.
 */
public final class BlinkComponent implements IPowerComponent {
    public static final String TYPE = "ascendant:blink";
    private final double maxDistance;

    public BlinkComponent(double maxDistance) {
        this.maxDistance = maxDistance;
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
        Vec3 eyePos = player.getEyePosition();
        Vec3 look = context.getLookDirection();
        Vec3 endPos = eyePos.add(look.scale(maxDistance));

        BlockHitResult hit = level.clip(new ClipContext(eyePos, endPos,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));

        Vec3 targetPos;
        if (hit.getType() == HitResult.Type.BLOCK) {
            // Step back slightly along look vector so player doesn't spawn inside block
            targetPos = hit.getLocation().subtract(look.scale(0.5D));
        } else {
            targetPos = endPos;
        }

        // Particle at departure
        level.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1.0D, player.getZ(),
                20, 0.4D, 0.6D, 0.4D, 0.1D);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.2F);

        // Safe landing
        BlockPos checkPos = BlockPos.containing(targetPos);
        double targetY = targetPos.y;
        if (!level.getBlockState(checkPos).isAir()) {
            targetY = checkPos.getY() + 1.0D;
        }

        player.teleportTo(targetPos.x, targetY, targetPos.z);
        player.resetFallDistance();

        // Particle and sound at arrival
        level.sendParticles(ParticleTypes.PORTAL, targetPos.x, targetY + 1.0D, targetPos.z,
                20, 0.4D, 0.6D, 0.4D, 0.1D);
        level.playSound(null, targetPos.x, targetY, targetPos.z,
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.4F);
        return true;
    }

    @Override
    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", TYPE);
        obj.addProperty("maxDistance", maxDistance);
        return obj;
    }

    public static BlinkComponent fromJson(JsonObject json) {
        double maxDist = json.has("maxDistance") ? json.get("maxDistance").getAsDouble() : 16.0D;
        return new BlinkComponent(maxDist);
    }
}
