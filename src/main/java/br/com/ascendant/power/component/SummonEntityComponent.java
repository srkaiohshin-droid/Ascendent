package br.com.ascendant.power.component;

import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

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
        List<Vec3> spawnPositions = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            Wolf wolf = EntityType.WOLF.create(level);
            if (wolf == null) {
                return false;
            }
            wolf.setTame(true);
            wolf.setOwnerUUID(owner.getUUID());
            wolf.setOrderedToSit(false);

            Vec3 spawn = findSpawnPosition(level, owner, wolf, index, spawnPositions);
            if (spawn == null) {
                return false;
            }
            wolf.moveTo(spawn.x, spawn.y, spawn.z, owner.getYRot(), 0.0F);
            spawnPositions.add(spawn);
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

    private Vec3 findSpawnPosition(ServerLevel level, ServerPlayer owner, Wolf wolf,
                                   int wolfIndex, List<Vec3> reservedPositions) {
        int angleSteps = Math.max(16, count * 4);
        for (int ring = 0; ring < 5; ring++) {
            double radius = spread + ring * 1.25D;
            for (int step = 0; step < angleSteps; step++) {
                double angle = (Math.PI * 2.0D * (step + wolfIndex)) / angleSteps;
                double x = owner.getX() + Math.cos(angle) * radius;
                double z = owner.getZ() + Math.sin(angle) * radius;
                for (int verticalOffset = 0; verticalOffset <= 2; verticalOffset++) {
                    double y = owner.getY() + verticalOffset;
                    if (y < level.getMinBuildHeight() || y + wolf.getBbHeight() > level.getMaxBuildHeight()) {
                        continue;
                    }
                    BlockPos blockPos = BlockPos.containing(x, y, z);
                    if (!level.hasChunkAt(blockPos)) {
                        continue;
                    }

                    wolf.moveTo(x, y, z, owner.getYRot(), 0.0F);
                    AABB bounds = wolf.getBoundingBox();
                    if (!level.getWorldBorder().isWithinBounds(bounds) || !level.noCollision(wolf)) {
                        continue;
                    }

                    boolean overlapsReserved = reservedPositions.stream()
                            .anyMatch(position -> position.distanceToSqr(x, y, z) < 1.0D);
                    if (!overlapsReserved) {
                        return new Vec3(x, y, z);
                    }
                }
            }
        }
        return null;
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
