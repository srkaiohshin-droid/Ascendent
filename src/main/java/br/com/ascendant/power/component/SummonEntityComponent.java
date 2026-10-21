package br.com.ascendant.power.component;

import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/** Summons vanilla tamed wolves that obey and defend their owner. */
public final class SummonEntityComponent implements IPowerComponent {
    public static final String TYPE = "ascendant:summon_entity";
    private final int count;
    private final double spread;

    public SummonEntityComponent(int count, double spread) {
        this.count = Math.max(1, Math.min(8, count));
        this.spread = Math.max(1.0D, Math.min(8.0D, spread));
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
        ServerPlayer owner = context.getPlayer();
        ServerLevel level = context.getLevel();
        List<Wolf> wolves = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            Wolf wolf = EntityType.WOLF.create(level);
            if (wolf == null) {
                return false;
            }
            double angle = (Math.PI * 2.0D * index) / count;
            wolf.moveTo(owner.getX() + Math.cos(angle) * spread, owner.getY(),
                    owner.getZ() + Math.sin(angle) * spread, owner.getYRot(), 0.0F);
            wolf.setTame(true);
            wolf.setOwnerUUID(owner.getUUID());
            wolf.setOrderedToSit(false);
            if (!level.noCollision(wolf)) {
                return false;
            }
            wolves.add(wolf);
        }

        AABB targetArea = owner.getBoundingBox().inflate(12.0D);
        LivingEntity target = level.getEntitiesOfClass(LivingEntity.class, targetArea,
                        entity -> entity != owner && entity.isAlive() && !entity.isAlliedTo(owner))
                .stream().findFirst().orElse(null);
        int spawned = 0;
        for (Wolf wolf : wolves) {
            if (target != null) {
                wolf.setTarget(target);
            }
            if (!level.addFreshEntity(wolf)) {
                for (int rollback = 0; rollback < spawned; rollback++) {
                    wolves.get(rollback).discard();
                }
                return false;
            }
            spawned++;
        }
        return true;
    }

    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", TYPE);
        json.addProperty("count", count);
        json.addProperty("spread", spread);
        return json;
    }

    public static SummonEntityComponent fromJson(JsonObject json) {
        return new SummonEntityComponent(
                json.has("count") ? json.get("count").getAsInt() : 3,
                json.has("spread") ? json.get("spread").getAsDouble() : 2.5D);
    }
}
