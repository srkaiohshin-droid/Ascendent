package br.com.ascendant.speedster.client;

import br.com.ascendant.speedster.SpeedsterLevel;
import br.com.ascendant.speedster.SpeedsterState;
import br.com.ascendant.speedster.SpeedsterStateProvider;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Random;

/**
 * Client-side visual effects for speedster electricity, trails and afterimages.
 * Runs only on the logical client; never touches server-authoritative state.
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = "ascendant")
public final class SpeedsterVisuals {
    private static final Random RANDOM = new Random();
    private static long lastEffectTick = -1L;
    private static int trailTickAccumulator = 0;

    private SpeedsterVisuals() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }
        SpeedsterState state = player.getCapability(SpeedsterStateProvider.CAPABILITY).orElse(null);
        if (state == null || !state.isActive()) {
            return;
        }
        SpeedsterLevel level = state.getLevel();
        if (level == SpeedsterLevel.NONE || !level.hasEffects()) {
            return;
        }
        long gameTick = minecraft.level.getGameTime();
        if (gameTick == lastEffectTick) {
            return;
        }
        lastEffectTick = gameTick;

        double speed = new Vec3(player.getDeltaMovement().x, 0.0D, player.getDeltaMovement().z).length();
        boolean movingForward = (state.getInputMask() & SpeedsterState.FORWARD) != 0;
        double kinetic = state.getKineticEnergy();

        renderLightning(player, level, speed, kinetic, movingForward);
        renderFootSparks(player, level, speed, movingForward);
        renderBodyArcs(player, level, speed, kinetic);
        renderTrail(player, level, speed, kinetic);

        trailTickAccumulator++;
    }

    /**
     * Lightning arcs that climb from feet toward the torso. Intensity scales with
     * speed and kinetic energy. Uses a per-tick random seed so arcs change shape
     * without chaotic flickering.
     */
    private static void renderLightning(Player player, SpeedsterLevel level, double speed, double kinetic, boolean movingForward) {
        int count = arcCountForLevel(level, speed, kinetic);
        if (count <= 0) {
            return;
        }
        double y0 = player.getY();
        double y1 = player.getY() + 0.8D + kinetic * 0.6D;
        double x0 = player.getX();
        double z0 = player.getZ();
        long seed = player.level().getGameTime() * 31 + player.getId();
        RANDOM.setSeed(seed);

        for (int i = 0; i < count; i++) {
            double angle = RANDOM.nextDouble() * Math.PI * 2.0D;
            double offset = 0.3D + kinetic * 0.4D;
            double sx = x0 + Math.cos(angle) * offset;
            double sz = z0 + Math.sin(angle) * offset;
            int steps = 4 + RANDOM.nextInt(3);
            double px = sx;
            double py = y0;
            double pz = sz;
            for (int s = 0; s < steps; s++) {
                double nx = px + (RANDOM.nextDouble() - 0.5D) * 0.15D;
                double ny = py + (y1 - y0) / steps + (RANDOM.nextDouble() - 0.5D) * 0.08D;
                double nz = pz + (RANDOM.nextDouble() - 0.5D) * 0.15D;
                player.level().addParticle(ParticleTypes.ELECTRIC_SPARK, nx, ny, nz, 0.0D, 0.0D, 0.0D);
                px = nx;
                py = ny;
                pz = nz;
            }
        }
    }

    /**
     * Small sparks near the feet when moving, more frequent at high speed or
     * during braking/skidding.
     */
    private static void renderFootSparks(Player player, SpeedsterLevel level, double speed, boolean movingForward) {
        if (!movingForward || speed < 0.25D) {
            return;
        }
        int count = sparkCountForLevel(level, speed);
        if (count <= 0) {
            return;
        }
        long seed = player.level().getGameTime() * 17 + player.getId();
        RANDOM.setSeed(seed);
        double y = player.getY() + 0.1D;
        for (int i = 0; i < count; i++) {
            double angle = RANDOM.nextDouble() * Math.PI * 2.0D;
            double dist = 0.2D + RANDOM.nextDouble() * 0.4D;
            double x = player.getX() + Math.cos(angle) * dist;
            double z = player.getZ() + Math.sin(angle) * dist;
            player.level().addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z, 0.0D, -0.05D, 0.0D);
        }
    }

    /**
     * Arcs between arms and torso at higher levels, more visible during
     * acceleration and direction changes.
     */
    private static void renderBodyArcs(Player player, SpeedsterLevel level, double speed, double kinetic) {
        if (level == SpeedsterLevel.REFLEXES || level == SpeedsterLevel.KINETIC) {
            return;
        }
        int count = bodyArcCountForLevel(level, speed, kinetic);
        if (count <= 0) {
            return;
        }
        long seed = player.level().getGameTime() * 7 + player.getId();
        RANDOM.setSeed(seed);
        double y = player.getY() + 1.2D + kinetic * 0.3D;
        for (int i = 0; i < count; i++) {
            double angle = RANDOM.nextDouble() * Math.PI * 2.0D;
            double offset = 0.4D + RANDOM.nextDouble() * 0.3D;
            double x = player.getX() + Math.cos(angle) * offset;
            double z = player.getZ() + Math.sin(angle) * offset;
            player.level().addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, player.getZ(), 0.0D, 0.05D, 0.0D);
        }
    }

    /**
     * Short-lived trail behind the player at higher speeds. Uses a tick
     * accumulator to limit density.
     */
    private static void renderTrail(Player player, SpeedsterLevel level, double speed, double kinetic) {
        if (level == SpeedsterLevel.REFLEXES) {
            return;
        }
        if (speed < 0.30D) {
            return;
        }
        trailTickAccumulator++;
        int interval = trailIntervalForLevel(level);
        if (trailTickAccumulator < interval) {
            return;
        }
        trailTickAccumulator = 0;

        // Place a particle slightly behind the player's movement vector
        Vec3 movement = new Vec3(player.getDeltaMovement().x, 0.0D, player.getDeltaMovement().z);
        if (movement.length() < 1.0E-6D) {
            return;
        }
        Vec3 back = movement.normalize().scale(-0.5D);
        double x = player.getX() + back.x;
        double y = player.getY() + 0.2D;
        double z = player.getZ() + back.z;
        player.level().addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z, 0.0D, 0.0D, 0.0D);
    }

    private static int arcCountForLevel(SpeedsterLevel level, double speed, double kinetic) {
        return switch (level) {
            case REFLEXES -> 0;
            case KINETIC -> speed > 0.4D ? 1 : 0;
            case SUPERSONIC -> 2 + (int) (kinetic * 2.0D);
            case ELECTRIC -> 4 + (int) (kinetic * 4.0D);
            case EXTREME -> 6 + (int) (kinetic * 6.0D);
            case TRANSCENDENTAL -> 8 + (int) (kinetic * 8.0D);
            default -> 0;
        };
    }

    private static int sparkCountForLevel(SpeedsterLevel level, double speed) {
        return switch (level) {
            case REFLEXES -> speed > 0.35D ? 1 : 0;
            case KINETIC -> speed > 0.30D ? 2 : 0;
            case SUPERSONIC -> 3;
            case ELECTRIC -> 5;
            case EXTREME -> 7;
            case TRANSCENDENTAL -> 9;
            default -> 0;
        };
    }

    private static int bodyArcCountForLevel(SpeedsterLevel level, double speed, double kinetic) {
        return switch (level) {
            case REFLEXES, KINETIC -> 0;
            case SUPERSONIC -> speed > 0.7D ? 1 : 0;
            case ELECTRIC -> 2 + (int) (kinetic * 2.0D);
            case EXTREME -> 3 + (int) (kinetic * 3.0D);
            case TRANSCENDENTAL -> 4 + (int) (kinetic * 4.0D);
            default -> 0;
        };
    }

    private static int trailIntervalForLevel(SpeedsterLevel level) {
        return switch (level) {
            case REFLEXES -> 999;
            case KINETIC -> 6;
            case SUPERSONIC -> 4;
            case ELECTRIC -> 2;
            case EXTREME -> 1;
            case TRANSCENDENTAL -> 1;
            default -> 999;
        };
    }
}
