package br.com.ascendant.speedster;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Mecânicas de combate baseadas na velocidade do velocista.
 * O dano é calculado pelo servidor com base na velocidade efetiva e no nível.
 * O cliente nunca declara acertos por conta própria.
 */
public final class SpeedsterCombat {
    private static final int ATTACK_COOLDOWN_TICKS = 6;
    private static final double MIN_SPEED_FOR_IMPACT = 0.35D;

    private SpeedsterCombat() {
    }

    /**
     * Executa um ataque corpo a corpo acelerado. O dano escala com a velocidade
     * atual do jogador e com o nível de velocista. O alvo recebe knockback
     * proporcional à velocidade.
     *
     * @return true se o ataque foi executado (alvo válido dentro de alcance)
     */
    public static boolean speedPunch(Player attacker, LivingEntity target) {
        if (attacker.level().isClientSide || attacker.isDeadOrDying() || target.isDeadOrDying()) {
            return false;
        }
        SpeedsterState state = attacker.getCapability(SpeedsterStateProvider.CAPABILITY).orElse(null);
        if (state == null || !state.isActive() || state.getLevel() == SpeedsterLevel.NONE) {
            return false;
        }
        if (state.getDashCooldown() > 0 && state.getDashCooldown() < ATTACK_COOLDOWN_TICKS) {
            return false;
        }

        double speed = new Vec3(attacker.getDeltaMovement().x, 0.0D, attacker.getDeltaMovement().z).length();
        if (speed < MIN_SPEED_FOR_IMPACT) {
            return false;
        }

        double damage = SpeedsterPhysics.impactDamageMultiplier(speed, state.getLevel());
        if (damage <= 0.0D) {
            return false;
        }

        // Consume kinetic energy on impact
        state.setKineticEnergy(Math.max(0.0D, state.getKineticEnergy() - 0.15D));
        state.setDashCooldown(ATTACK_COOLDOWN_TICKS);

        // Apply damage server-authoritative
        target.hurt(attacker.level().damageSources().playerAttack(attacker), (float) damage);

        // Knockback proportional to speed and level
        Vec3 knockbackDir = new Vec3(target.getX() - attacker.getX(), 0.0D, target.getZ() - attacker.getZ());
        if (knockbackDir.length() > 1.0E-6D) {
            knockbackDir = knockbackDir.normalize();
        }
        double knockbackStrength = Math.min(speed * state.profile().impactDamageMultiplier(), 2.0D);
        target.setDeltaMovement(target.getDeltaMovement().add(
                knockbackDir.x * knockbackStrength,
                0.25D,
                knockbackDir.z * knockbackStrength
        ));

        return true;
    }

    /**
     * Ataque de passagem: atinge entidades próximas ao trajetória do jogador
     * enquanto ele se move em alta velocidade, continuando o movimento.
     */
    public static boolean speedPass(Player attacker) {
        if (attacker.level().isClientSide || attacker.isDeadOrDying()) {
            return false;
        }
        SpeedsterState state = attacker.getCapability(SpeedsterStateProvider.CAPABILITY).orElse(null);
        if (state == null || !state.isActive() || state.getLevel() == SpeedsterLevel.NONE) {
            return false;
        }

        double speed = new Vec3(attacker.getDeltaMovement().x, 0.0D, attacker.getDeltaMovement().z).length();
        if (speed < MIN_SPEED_FOR_IMPACT * 1.5D) {
            return false;
        }

        double damage = SpeedsterPhysics.impactDamageMultiplier(speed, state.getLevel()) * 0.75D;
        if (damage <= 0.0D) {
            return false;
        }

        state.setKineticEnergy(Math.max(0.0D, state.getKineticEnergy() - 0.2D));
        state.setDashCooldown(ATTACK_COOLDOWN_TICKS);

        AABB area = attacker.getBoundingBox().inflate(1.2D, 0.6D, 1.2D);
        boolean hit = false;
        for (LivingEntity entity : attacker.level().getEntitiesOfClass(LivingEntity.class, area)) {
            if (entity == attacker || entity.isDeadOrDying()) {
                continue;
            }
            LivingEntity target = entity;
            target.hurt(attacker.level().damageSources().playerAttack(attacker), (float) damage);
            hit = true;
        }
        return hit;
    }

    /**
     * Onda de choque ao frear em alta velocidade. Afeta entidades em área ao
     * redor do jogador com dano leve e knockback para trás.
     */
    public static boolean brakingShockwave(Player attacker) {
        if (attacker.level().isClientSide || attacker.isDeadOrDying()) {
            return false;
        }
        SpeedsterState state = attacker.getCapability(SpeedsterStateProvider.CAPABILITY).orElse(null);
        if (state == null || !state.isActive() || state.getLevel() == SpeedsterLevel.NONE) {
            return false;
        }

        double speed = new Vec3(attacker.getDeltaMovement().x, 0.0D, attacker.getDeltaMovement().z).length();
        if (speed < 0.6D) {
            return false;
        }

        double damage = Math.min(speed * 0.5D, 3.0D);
        state.setKineticEnergy(0.0D);
        state.setDashCooldown(ATTACK_COOLDOWN_TICKS * 2);

        AABB area = attacker.getBoundingBox().inflate(3.0D, 1.0D, 3.0D);
        boolean hit = false;
        for (LivingEntity entity : attacker.level().getEntitiesOfClass(LivingEntity.class, area)) {
            if (entity == attacker || entity.isDeadOrDying()) {
                continue;
            }
            LivingEntity target = entity;
            target.hurt(attacker.level().damageSources().playerAttack(attacker), (float) damage);

            Vec3 dir = new Vec3(target.getX() - attacker.getX(), 0.0D, target.getZ() - attacker.getZ());
            if (dir.length() > 1.0E-6D) {
                dir = dir.normalize();
            }
            target.setDeltaMovement(target.getDeltaMovement().add(dir.x * 1.0D, 0.3D, dir.z * 1.0D));
            hit = true;
        }
        return hit;
    }

    /**
     * Explosão cinética ao sair de uma arrancada (dash). Libera energia acumulada
     * em área curta ao redor do jogador.
     */
    public static boolean dashExitExplosion(Player attacker) {
        if (attacker.level().isClientSide || attacker.isDeadOrDying()) {
            return false;
        }
        SpeedsterState state = attacker.getCapability(SpeedsterStateProvider.CAPABILITY).orElse(null);
        if (state == null || !state.isDashing()) {
            return false;
        }

        double kinetic = state.getKineticEnergy();
        double damage = kinetic * 4.0D;
        if (damage <= 0.0D) {
            return false;
        }

        state.setKineticEnergy(0.0D);
        state.setDashCooldown((int) state.profile().dashCooldownTicks());

        AABB area = attacker.getBoundingBox().inflate(2.5D, 1.0D, 2.5D);
        boolean hit = false;
        for (LivingEntity entity : attacker.level().getEntitiesOfClass(LivingEntity.class, area)) {
            if (entity == attacker || entity.isDeadOrDying()) {
                continue;
            }
            LivingEntity target = entity;
            target.hurt(attacker.level().damageSources().playerAttack(attacker), (float) damage);
            hit = true;
        }
        return hit;
    }
}
