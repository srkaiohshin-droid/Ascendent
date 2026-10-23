package br.com.ascendant.power.component;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.List;

/**
 * Shared server-side target resolution for power components.
 *
 * <p>Every combat component used to build its own entity query, which produced three
 * recurring defects: effects leaked through solid walls, target counts were unbounded, and
 * each component had a slightly different idea of who counts as a valid target. This helper
 * keeps those rules in one place so new mechanics cannot regress them.</p>
 *
 * <p>All queries run on the server and only read world state. They never trust client input
 * beyond the player's own rotation, which the server already knows.</p>
 */
public final class CombatTargeting {
    /** Hard cap so a single activation can never touch an arbitrary number of entities. */
    public static final int MAX_TARGETS = 32;

    private CombatTargeting() {
    }

    /**
     * Returns living, non-allied targets inside {@code radius} that the player can actually
     * see, ordered from nearest to farthest and capped by {@code maxTargets}.
     */
    public static List<LivingEntity> visibleHostiles(ServerPlayer player, double radius, int maxTargets) {
        double boundedRadius = Math.max(1.0D, Math.min(48.0D, radius));
        int boundedTargets = Math.max(1, Math.min(MAX_TARGETS, maxTargets));
        AABB area = player.getBoundingBox().inflate(boundedRadius);
        return player.serverLevel()
                .getEntitiesOfClass(LivingEntity.class, area, entity -> isValidHostile(player, entity)
                        && entity.distanceToSqr(player) <= boundedRadius * boundedRadius)
                .stream()
                .filter(entity -> hasLineOfSight(player, entity))
                .sorted(Comparator.comparingDouble(entity -> entity.distanceToSqr(player)))
                .limit(boundedTargets)
                .toList();
    }

    /**
     * Resolves the entity the player is aiming at. A block in the way always wins, so the
     * returned target is never behind cover.
     */
    @Nullable
    public static LivingEntity lookTarget(ServerPlayer player, double range) {
        double boundedRange = Math.max(1.0D, Math.min(64.0D, range));
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(boundedRange));

        BlockHitResult blockHit = level.clip(new ClipContext(eye, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double limitSqr = blockHit.getType() == HitResult.Type.BLOCK
                ? blockHit.getLocation().distanceToSqr(eye)
                : boundedRange * boundedRange;

        LivingEntity best = null;
        double bestDistance = limitSqr;
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(boundedRange), entity -> isValidHostile(player, entity))) {
            var hit = candidate.getBoundingBox().inflate(0.3D).clip(eye, end);
            if (hit.isEmpty()) {
                continue;
            }
            double distance = hit.get().distanceToSqr(eye);
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    /**
     * Returns the hostile entities whose hitbox the segment from the player's eye along the
     * look vector crosses before the first solid block, ordered by distance.
     *
     * <p>Used by hitscan mechanics: a beam cannot pass through terrain and the first solid
     * block clips the ray, which keeps the effect inside the world the player can see.</p>
     */
    public static List<LivingEntity> rayTargets(ServerPlayer player, double range, int maxTargets) {
        double boundedRange = Math.max(1.0D, Math.min(64.0D, range));
        int boundedTargets = Math.max(1, Math.min(MAX_TARGETS, maxTargets));
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(boundedRange));
        BlockHitResult blockHit = level.clip(new ClipContext(eye, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double limitSqr = blockHit.getType() == HitResult.Type.BLOCK
                ? blockHit.getLocation().distanceToSqr(eye)
                : boundedRange * boundedRange;

        record Hit(LivingEntity entity, double distance) {
        }
        return level.getEntitiesOfClass(LivingEntity.class,
                        player.getBoundingBox().inflate(boundedRange),
                        entity -> isValidHostile(player, entity))
                .stream()
                .map(entity -> entity.getBoundingBox().inflate(0.35D).clip(eye, end)
                        .map(point -> new Hit(entity, point.distanceToSqr(eye)))
                        .orElse(null))
                .filter(hit -> hit != null && hit.distance() <= limitSqr)
                .sorted(Comparator.comparingDouble(Hit::distance))
                .limit(boundedTargets)
                .map(Hit::entity)
                .toList();
    }

    /** True when no solid block blocks the view from the player's eyes to the target's body. */
    public static boolean hasLineOfSight(ServerPlayer player, Entity target) {
        ServerLevel level = player.serverLevel();
        Vec3 from = player.getEyePosition();
        Vec3 to = target.getBoundingBox().getCenter();
        if (from.distanceToSqr(to) > 64.0D * 64.0D) {
            return false;
        }
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS;
    }

    /**
     * A valid hostile is alive, not the caster, not on the caster's side and not a player in
     * a state where gameplay damage must never apply. Creative and spectator players are
     * excluded so powers cannot be used to harass build-mode sessions.
     */
    public static boolean isValidHostile(ServerPlayer player, Entity candidate) {
        if (candidate == player || !candidate.isAlive() || candidate.isSpectator()) {
            return false;
        }
        if (candidate.isAlliedTo(player)) {
            return false;
        }
        if (candidate instanceof Player other) {
            return !other.isCreative() && !other.isSpectator();
        }
        return true;
    }

    /** Central damage source mapping so new mechanics stay consistent with existing ones. */
    public static DamageSource damageSource(ServerPlayer player, String damageType) {
        if (damageType == null) {
            return player.damageSources().playerAttack(player);
        }
        return switch (damageType.toLowerCase()) {
            case "fire" -> player.damageSources().inFire();
            case "magic" -> player.damageSources().magic();
            case "lightning" -> player.damageSources().lightningBolt();
            case "explosion" -> player.damageSources().explosion(player, player);
            default -> player.damageSources().playerAttack(player);
        };
    }
}
