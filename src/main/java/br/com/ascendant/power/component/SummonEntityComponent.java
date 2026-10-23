package br.com.ascendant.power.component;

import com.google.gson.JsonObject;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.phys.Vec3;

/**
 * Spawns a temporary or loyal allied creature at the player's position.
 */
public final class SummonEntityComponent implements IPowerComponent {
    public static final String TYPE = "ascendant:summon_entity";
    private final String entityType;

    public SummonEntityComponent(String entityType) {
        this.entityType = entityType;
    }

    public SummonEntityComponent() {
        this("wolf");
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
        Vec3 pos = player.position().add(player.getLookAngle().scale(1.5D));

        if ("wolf".equalsIgnoreCase(entityType)) {
            Wolf wolf = EntityType.WOLF.create(level);
            if (wolf != null) {
                wolf.moveTo(pos.x, pos.y, pos.z, player.getYRot(), 0.0F);
                wolf.tame(player);
                level.addFreshEntity(wolf);
                level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.WOLF_HOWL, SoundSource.PLAYERS, 1.0F, 1.1F);
                level.sendParticles(ParticleTypes.HEART, pos.x, pos.y + 0.5D, pos.z, 8, 0.3D, 0.3D, 0.3D, 0.02D);
                return true;
            }
        }
        return false;
    }

    @Override
    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", TYPE);
        obj.addProperty("entityType", entityType);
        return obj;
    }

    public static SummonEntityComponent fromJson(JsonObject json) {
        String type = json.has("entityType") ? json.get("entityType").getAsString() : "wolf";
        return new SummonEntityComponent(type);
    }
}
