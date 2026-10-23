package br.com.ascendant.power.component;

import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;

/** Sustained telekinetic lift applied to the living entity the player is looking at. */
public final class LevitationComponent implements IPowerComponent {
    public static final String TYPE = "ascendant:levitation";
    private final double range;
    private final double liftPerTick;

    public LevitationComponent(double range, double liftPerTick) {
        this.range = Math.max(1.0D, Math.min(32.0D, range));
        this.liftPerTick = Math.max(0.01D, Math.min(0.5D, liftPerTick));
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
        if (!(context.getTargetEntity() instanceof LivingEntity target)) {
            return false;
        }
        return applyLift(context.getPlayer(), target);
    }

    @Override
    public void onPlayerTick(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        LivingEntity target = level.getEntitiesOfClass(LivingEntity.class,
                        player.getBoundingBox().inflate(range),
                        entity -> entity != player && entity.isAlive() && !entity.isAlliedTo(player))
                .stream()
                .filter(entity -> {
                    Vec3 direction = entity.getEyePosition().subtract(eye).normalize();
                    return look.dot(direction) >= 0.85D;
                })
                .min(Comparator.comparingDouble(entity -> entity.distanceToSqr(player)))
                .orElse(null);
        if (target == null) {
            return;
        }

        applyLift(player, target);
    }

    private boolean applyLift(ServerPlayer player, LivingEntity target) {
        if (!target.isAlive() || target == player || target.isAlliedTo(player)
                || target.distanceToSqr(player) > range * range) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        Vec3 targetEye = target.getEyePosition();
        if (level.clip(new ClipContext(player.getEyePosition(), targetEye, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player)).getType() != HitResult.Type.MISS) {
            return false;
        }

        Vec3 movement = target.getDeltaMovement();
        target.setDeltaMovement(movement.x, Math.max(movement.y, liftPerTick), movement.z);
        target.hurtMarked = true;
        return true;
    }

    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", TYPE);
        json.addProperty("range", range);
        json.addProperty("liftPerTick", liftPerTick);
        return json;
    }

    public static LevitationComponent fromJson(JsonObject json) {
        return new LevitationComponent(
                json.has("range") ? json.get("range").getAsDouble() : 16.0D,
                json.has("liftPerTick") ? json.get("liftPerTick").getAsDouble() : 0.14D);
    }
}
