package br.com.ascendant.power.component;

import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Summons a lightning bolt at the player's crosshair target location.
 */
public final class LightningActionComponent implements IPowerComponent {
    public static final String TYPE = "ascendant:lightning_action";
    private final double range;

    public LightningActionComponent(double range) {
        this.range = range;
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
        Vec3 endPos = eyePos.add(look.scale(range));

        BlockHitResult hit = level.clip(new ClipContext(eyePos, endPos,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));

        Vec3 targetPos = (hit.getType() == HitResult.Type.BLOCK) ? hit.getLocation() : endPos;

        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(targetPos.x, targetPos.y, targetPos.z);
            bolt.setCause(player);
            level.addFreshEntity(bolt);
            return true;
        }
        return false;
    }

    @Override
    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", TYPE);
        obj.addProperty("range", range);
        return obj;
    }

    public static LightningActionComponent fromJson(JsonObject json) {
        double rng = json.has("range") ? json.get("range").getAsDouble() : 24.0D;
        return new LightningActionComponent(rng);
    }
}
