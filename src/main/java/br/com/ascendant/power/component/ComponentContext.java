package br.com.ascendant.power.component;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Execution context provided when a power component is triggered.
 */
public final class ComponentContext {
    private final ServerPlayer player;
    private final ServerLevel level;
    private final ResourceLocation sourceId;
    @Nullable
    private final Entity targetEntity;
    @Nullable
    private final BlockPos targetBlock;
    private final Vec3 lookDirection;

    public ComponentContext(ServerPlayer player, ResourceLocation sourceId,
                            @Nullable Entity targetEntity, @Nullable BlockPos targetBlock) {
        this.player = Objects.requireNonNull(player, "player");
        this.level = player.serverLevel();
        this.sourceId = Objects.requireNonNull(sourceId, "sourceId");
        this.targetEntity = targetEntity;
        this.targetBlock = targetBlock;
        this.lookDirection = player.getLookAngle();
    }

    public static ComponentContext of(ServerPlayer player, ResourceLocation sourceId) {
        return new ComponentContext(player, sourceId, null, null);
    }

    public static ComponentContext of(ServerPlayer player, ResourceLocation sourceId, Entity targetEntity) {
        return new ComponentContext(player, sourceId, targetEntity, null);
    }

    public static ComponentContext of(ServerPlayer player, ResourceLocation sourceId, BlockPos targetBlock) {
        return new ComponentContext(player, sourceId, null, targetBlock);
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public ServerLevel getLevel() {
        return level;
    }

    public ResourceLocation getSourceId() {
        return sourceId;
    }

    @Nullable
    public Entity getTargetEntity() {
        return targetEntity;
    }

    @Nullable
    public BlockPos getTargetBlock() {
        return targetBlock;
    }

    public Vec3 getLookDirection() {
        return lookDirection;
    }
}
