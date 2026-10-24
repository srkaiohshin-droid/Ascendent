package br.com.ascendant.speedster;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Habilidades avançadas de percepção e sobrecarga do velocista.
 * Toda a lógica roda no servidor; o cliente apenas recebe o estado sincronizado.
 */
public final class SpeedsterPerception {
    private static final int PERCEPTION_DURATION_TICKS = 40;
    private static final int PERCEPTION_COOLDOWN_TICKS = 100;
    private static final int OVERLOAD_DURATION_TICKS = 60;
    private static final int OVERLOAD_COOLDOWN_TICKS = 200;
    private static final double OVERLOAD_SPEED_MULTIPLIER = 1.35D;

    private SpeedsterPerception() {
    }

    /**
     * Ativa a percepção acelerada por uma janela curta. Durante a percepção,
     * entidades próximas são destacadas e o jogador reage mais rapidamente a
     * projéteis. Não pausa o servidor nem afeta outros jogadores.
     */
    public static boolean activatePerception(Player player) {
        if (player.level().isClientSide || player.isDeadOrDying()) {
            return false;
        }
        SpeedsterState state = player.getCapability(SpeedsterStateProvider.CAPABILITY).orElse(null);
        if (state == null || !state.isActive() || state.getLevel() == SpeedsterLevel.NONE) {
            return false;
        }
        if (!state.profile().hasPerception()) {
            return false;
        }
        if (state.isPerceptionActive() || state.getPerceptionCooldown() > 0) {
            return false;
        }

        state.setPerceptionActive(true);
        state.setPerceptionCooldown(PERCEPTION_COOLDOWN_TICKS);
        state.setKineticEnergy(Math.max(0.0D, state.getKineticEnergy() - 0.2D));
        return true;
    }

    /**
     * Tick da percepção: destaca entidades próximas como ameaças.
     * Chamado no servidor durante a janela ativa.
     */
    public static void tickPerception(Player player) {
        SpeedsterState state = player.getCapability(SpeedsterStateProvider.CAPABILITY).orElse(null);
        if (state == null || !state.isPerceptionActive()) {
            return;
        }

        // Reduz duração através do cooldown como contador regressivo
        int remaining = state.getPerceptionCooldown();
        if (remaining > PERCEPTION_COOLDOWN_TICKS - PERCEPTION_DURATION_TICKS) {
            // Ainda na janela ativa
        } else {
            state.setPerceptionActive(false);
        }
        state.setPerceptionCooldown(Math.max(0, remaining - 1));
    }

    /**
     * Ativa a sobrecarga de velocidade: aumento temporário da velocidade máxima
     * com consumo de energia cinética. Depois do uso, entra em cooldown.
     */
    public static boolean activateOverload(Player player) {
        if (player.level().isClientSide || player.isDeadOrDying()) {
            return false;
        }
        SpeedsterState state = player.getCapability(SpeedsterStateProvider.CAPABILITY).orElse(null);
        if (state == null || !state.isActive() || state.getLevel() == SpeedsterLevel.NONE) {
            return false;
        }
        if (state.isOverloadActive() || state.getOverloadCooldown() > 0) {
            return false;
        }
        if (state.getKineticEnergy() < 0.5D) {
            return false;
        }

        state.setOverloadActive(true);
        state.setOverloadCooldown(OVERLOAD_COOLDOWN_TICKS);
        state.setKineticEnergy(Math.max(0.0D, state.getKineticEnergy() - 0.5D));
        return true;
    }

    /**
     * Tick da sobrecarga: consome energia cinética enquanto ativa.
     * Quando acaba, o jogador recebe uma breve penalidade de controle.
     */
    public static void tickOverload(Player player) {
        SpeedsterState state = player.getCapability(SpeedsterStateProvider.CAPABILITY).orElse(null);
        if (state == null || !state.isOverloadActive()) {
            return;
        }

        int remaining = state.getOverloadCooldown();
        if (remaining > OVERLOAD_COOLDOWN_TICKS - OVERLOAD_DURATION_TICKS) {
            // Ainda na janela ativa: consome energia cinética
            state.setKineticEnergy(Math.max(0.0D, state.getKineticEnergy() - 0.02D));
            if (state.getKineticEnergy() <= 0.0D) {
                state.setOverloadActive(false);
            }
        } else {
            state.setOverloadActive(false);
        }
        state.setOverloadCooldown(Math.max(0, remaining - 1));
    }

    /**
     * Retorna o multiplicador de velocidade enquanto a sobrecarga está ativa.
     */
    public static double overloadSpeedMultiplier(SpeedsterState state) {
        return state.isOverloadActive() ? OVERLOAD_SPEED_MULTIPLIER : 1.0D;
    }

    /**
     * Detecta entidades próximas que podem ser ameaças durante a percepção.
     * Retorna a entidade mais próxima na direção do olhar.
     */
    public static LivingEntity detectThreat(Player player) {
        if (player.level().isClientSide) {
            return null;
        }
        Vec3 start = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = start.add(look.scale(8.0D));
        AABB box = new AABB(start, end).inflate(1.0D);
        return player.level().getEntitiesOfClass(LivingEntity.class, box).stream()
                .filter(e -> e != player && !e.isDeadOrDying())
                .findFirst().orElse(null);
    }
}
