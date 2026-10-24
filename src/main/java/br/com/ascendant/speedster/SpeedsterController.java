package br.com.ascendant.speedster;

import br.com.ascendant.flight.FlightController;
import br.com.ascendant.flight.FlightState;
import br.com.ascendant.network.AscendantNetwork;
import br.com.ascendant.power.runtime.PowerRuntimeEngine;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class SpeedsterController {
    public static final ResourceLocation REQUIRED_POWER = new ResourceLocation("ascendant", "kinetic_sprint");
    public static final int CONTROL_TOGGLE = 0;
    public static final int CONTROL_LEVEL_UP = 1;
    public static final int CONTROL_LEVEL_DOWN = 2;
    public static final int CONTROL_DASH = 3;
    public static final int CONTROL_ATTACK = 4;
    public static final int CONTROL_PERCEPTION = 5;
    public static final int CONTROL_OVERLOAD = 6;
    private static final long INPUT_TIMEOUT_TICKS = 20L;

    private SpeedsterController() {
    }

    public static SpeedsterState getState(Player player) {
        return player.getCapability(SpeedsterStateProvider.CAPABILITY).orElse(null);
    }

    public static boolean canActivate(ServerPlayer player) {
        SpeedsterState state = getState(player);
        return SpeedsterTuning.ENABLED.get() && state != null
                && player.isAlive() && !player.isSpectator() && !player.isPassenger()
                && !player.isSleeping() && !player.isInWater() && !player.isInLava()
                && PowerRuntimeEngine.hasPowerAccess(player, REQUIRED_POWER)
                && !isFlightActive(player);
    }

    public static boolean toggle(ServerPlayer player) {
        return PowerRuntimeEngine.togglePower(player, REQUIRED_POWER, !isModeActive(player));
    }

    public static boolean isModeActive(Player player) {
        SpeedsterState state = getState(player);
        return state != null && state.isActive();
    }

    public static void setModeEnabled(ServerPlayer player, boolean enabled) {
        SpeedsterState state = getState(player);
        if (state == null) {
            return;
        }
        if (!enabled) {
            stop(player, state, true);
            return;
        }
        if (state.isActive() || !canActivate(player)) {
            return;
        }
        state.setLevel(SpeedsterLevel.REFLEXES);
        state.setActive(true);
        syncState(player);
        player.displayClientMessage(Component.translatable(
                "message.ascendant.speedster.enabled", state.getLevel().translationKey()), true);
    }

    public static boolean handleControl(ServerPlayer player, int action) {
        SpeedsterState state = getState(player);
        if (state == null || !state.canAcceptControl(player.level().getGameTime())) {
            return false;
        }
        if (action != CONTROL_TOGGLE && action != CONTROL_LEVEL_UP
                && action != CONTROL_LEVEL_DOWN && action != CONTROL_DASH
                && action != CONTROL_ATTACK && action != CONTROL_PERCEPTION
                && action != CONTROL_OVERLOAD) {
            return false;
        }
        if (action != CONTROL_TOGGLE && (!state.isActive() || !canActivate(player))) {
            return false;
        }
        state.markControlAccepted(player.level().getGameTime());
        if (action == CONTROL_TOGGLE) {
            return toggle(player);
        }
        if (action == CONTROL_LEVEL_UP) {
            state.setLevel(nextLevel(state.getLevel()));
            syncState(player);
            player.displayClientMessage(Component.translatable("message.ascendant.speedster.level", state.getLevel().translationKey()), true);
            return true;
        }
        if (action == CONTROL_LEVEL_DOWN) {
            state.setLevel(prevLevel(state.getLevel()));
            syncState(player);
            player.displayClientMessage(Component.translatable("message.ascendant.speedster.level", state.getLevel().translationKey()), true);
            return true;
        }
        if (action == CONTROL_DASH) {
            return attemptDash(player, state);
        }
        if (action == CONTROL_ATTACK) {
            return attemptSpeedPunch(player, state);
        }
        if (action == CONTROL_PERCEPTION) {
            return SpeedsterPerception.activatePerception(player);
        }
        if (action == CONTROL_OVERLOAD) {
            return SpeedsterPerception.activateOverload(player);
        }
        return false;
    }

    private static boolean attemptDash(ServerPlayer player, SpeedsterState state) {
        SpeedsterProfile profile = state.profile();
        if (state.getDashCooldown() > 0 || !profile.hasAfterimages()) {
            return false;
        }
        Vec3 look = player.getLookAngle();
        Vec3 dashMotion = new Vec3(look.x * profile.dashSpeed(), look.y * 0.3D, look.z * profile.dashSpeed());
        player.setDeltaMovement(dashMotion);
        player.hurtMarked = true;
        player.resetFallDistance();
        state.setDashing(true);
        state.setDashCooldown((int) profile.dashCooldownTicks());
        state.setKineticEnergy(Math.max(0.0D, state.getKineticEnergy() - 0.3D));
        syncState(player);
        return true;
    }

    private static boolean attemptSpeedPunch(ServerPlayer player, SpeedsterState state) {
        return SpeedsterCombat.speedPunch(player, findTargetInFront(player));
    }

    private static LivingEntity findTargetInFront(ServerPlayer player) {
        Vec3 start = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = start.add(look.scale(3.5D));
        AABB box = new AABB(start, end).inflate(0.6D);
        return player.level().getEntitiesOfClass(LivingEntity.class, box).stream()
                .filter(e -> e != player && !e.isDeadOrDying())
                .findFirst().orElse(null);
    }

    public static void acceptInput(ServerPlayer player, int inputMask) {
        SpeedsterState state = getState(player);
        if (state == null || !state.isActive()) {
            return;
        }
        if (!canMaintain(player)) {
            stop(player, state, true);
            return;
        }
        state.acceptInput(inputMask, player.level().getGameTime());
    }

    public static void tickServer(ServerPlayer player) {
        SpeedsterState state = getState(player);
        if (state == null || !state.isActive()) {
            return;
        }
        if (!canMaintain(player)) {
            stop(player, state, true);
            return;
        }

        long gameTick = player.level().getGameTime();
        if (state.getLastInputGameTick() == Long.MIN_VALUE
                || gameTick - state.getLastInputGameTick() > INPUT_TIMEOUT_TICKS) {
            state.clearInput();
        }

        if (state.getDashCooldown() > 0) {
            state.setDashCooldown(state.getDashCooldown() - 1);
        }
        boolean wasDashing = state.isDashing();
        if (wasDashing && state.getDashCooldown() == 0) {
            state.setDashing(false);
            SpeedsterCombat.dashExitExplosion(player);
        }

        // Braking shockwave at high speed
        if ((state.getInputMask() & SpeedsterState.BRAKE) != 0) {
            double speed = new Vec3(player.getDeltaMovement().x, 0.0D, player.getDeltaMovement().z).length();
            if (speed > 0.6D) {
                SpeedsterCombat.brakingShockwave(player);
            }
        }

        SpeedsterPerception.tickPerception(player);
        SpeedsterPerception.tickOverload(player);

        boolean movingForwardSprinting = (state.getInputMask() & (SpeedsterState.FORWARD | SpeedsterState.BOOST))
                == (SpeedsterState.FORWARD | SpeedsterState.BOOST);
        state.setKineticEnergy(SpeedsterPhysics.nextKinetic(
                state.getKineticEnergy(), movingForwardSprinting, state.getLevel()));

        Vec3 current = player.getDeltaMovement();
        Vec3 next = SpeedsterPhysics.nextVelocity(current, player.getYRot(),
                state.getInputMask(), state.getLevel(), state.getKineticEnergy(), state.isDashing());
        // Overload temporarily increases speed
        if (state.isOverloadActive()) {
            next = next.scale(SpeedsterPerception.overloadSpeedMultiplier(state));
        }
        // Collision check: never tunnel through blocks
        next = SpeedsterPhysics.checkCollision(player.level(), player, current, next);
        player.setDeltaMovement(next);
        if (next.distanceToSqr(current) > 1.0E-6D) {
            player.hurtMarked = true;
        }
    }

    public static void tickClient(Player player) {
        SpeedsterState state = getState(player);
        if (state == null || !state.isActive() || isFlightActive(player)
                || !player.isAlive() || player.isPassenger() || player.isSleeping()
                || player.isInWater() || player.isInLava()) {
            return;
        }
        player.setDeltaMovement(SpeedsterPhysics.nextVelocity(
                player.getDeltaMovement(), player.getYRot(), state.getInputMask(),
                state.getLevel(), state.getKineticEnergy(), state.isDashing()));
    }

    public static void clearForLifecycle(Player player, boolean sync) {
        SpeedsterState state = getState(player);
        if (state == null) {
            return;
        }
        boolean wasActive = state.isActive();
        state.clear();
        if (player instanceof ServerPlayer serverPlayer) {
            PowerRuntimeEngine.setPowerToggleSilently(serverPlayer, REQUIRED_POWER, false);
            if (sync && wasActive) {
                syncState(serverPlayer);
            }
        }
    }

    public static void deactivateForFlight(ServerPlayer player) {
        SpeedsterState state = getState(player);
        if (state != null && state.isActive()) {
            stop(player, state, true);
        }
    }

    public static void deactivateIfPowerWasRevoked(ServerPlayer player) {
        if (PowerRuntimeEngine.hasPowerAccess(player, REQUIRED_POWER)) {
            return;
        }
        SpeedsterState state = getState(player);
        if (state != null && state.isActive()) {
            stop(player, state, true);
        }
    }

    public static void syncState(ServerPlayer player) {
        SpeedsterState state = getState(player);
        if (state != null) {
            AscendantNetwork.syncSpeedsterState(player, state.isActive(), state.getLevel().id());
        }
    }

    private static SpeedsterLevel nextLevel(SpeedsterLevel current) {
        SpeedsterLevel[] values = SpeedsterLevel.values();
        int idx = current.id();
        if (idx >= values.length - 1) {
            return values[0];
        }
        return values[idx + 1];
    }

    private static SpeedsterLevel prevLevel(SpeedsterLevel current) {
        SpeedsterLevel[] values = SpeedsterLevel.values();
        int idx = current.id();
        if (idx <= 0) {
            return values[values.length - 1];
        }
        return values[idx - 1];
    }

    private static boolean canMaintain(ServerPlayer player) {
        return SpeedsterTuning.ENABLED.get() && player.isAlive() && !player.isSpectator() && !player.isPassenger()
                && !player.isSleeping() && !player.isInWater() && !player.isInLava()
                && PowerRuntimeEngine.hasPowerAccess(player, REQUIRED_POWER)
                && !isFlightActive(player);
    }

    private static boolean isFlightActive(Player player) {
        FlightState flightState = FlightController.getState(player);
        return flightState != null && flightState.isActive();
    }

    private static void stop(ServerPlayer player, SpeedsterState state, boolean sync) {
        if (state.isActive()) {
            state.setActive(false);
            state.setKineticEnergy(0.0D);
            state.setDashing(false);
            PowerRuntimeEngine.setPowerToggleSilently(player, REQUIRED_POWER, false);
        }
        if (sync) {
            syncState(player);
            AscendantNetwork.syncCatalogState(player);
            player.displayClientMessage(Component.translatable("message.ascendant.speedster.disabled"), true);
        }
    }
}
