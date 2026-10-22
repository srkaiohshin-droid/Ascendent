package br.com.ascendant.speedster;

import br.com.ascendant.flight.FlightController;
import br.com.ascendant.flight.FlightState;
import br.com.ascendant.network.AscendantNetwork;
import br.com.ascendant.power.runtime.PowerRuntimeEngine;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class SpeedsterController {
    public static final ResourceLocation REQUIRED_POWER = new ResourceLocation("ascendant", "kinetic_sprint");
    public static final int CONTROL_TOGGLE = 0;
    public static final int CONTROL_LEVEL_UP = 1;
    public static final int CONTROL_LEVEL_DOWN = 2;
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
        SpeedsterState state = getState(player);
        if (state == null) {
            return false;
        }
        if (state.isActive()) {
            stop(player, state, true);
            return true;
        }
        if (!canActivate(player)) {
            return false;
        }
        state.setActive(true);
        syncState(player);
        player.displayClientMessage(Component.translatable("message.ascendant.speedster.enabled", state.getLevel()), true);
        return true;
    }

    public static boolean handleControl(ServerPlayer player, int action) {
        SpeedsterState state = getState(player);
        if (state == null || !state.canAcceptControl(player.level().getGameTime())) {
            return false;
        }
        if (action != CONTROL_TOGGLE && action != CONTROL_LEVEL_UP && action != CONTROL_LEVEL_DOWN) {
            return false;
        }
        if (action != CONTROL_TOGGLE && (!state.isActive() || !canActivate(player))) {
            return false;
        }
        state.markControlAccepted(player.level().getGameTime());
        if (action == CONTROL_TOGGLE) {
            return toggle(player);
        }

        state.setLevel(state.getLevel() + (action == CONTROL_LEVEL_UP ? 1 : -1));
        syncState(player);
        player.displayClientMessage(Component.translatable("message.ascendant.speedster.level", state.getLevel()), true);
        return true;
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
        Vec3 current = player.getDeltaMovement();
        Vec3 next = calculateNextVelocity(current, player.getYRot(), state.getInputMask(), state.getLevel());
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
        player.setDeltaMovement(calculateNextVelocity(
                player.getDeltaMovement(), player.getYRot(), state.getInputMask(), state.getLevel()));
    }

    public static void clearForLifecycle(Player player, boolean sync) {
        SpeedsterState state = getState(player);
        if (state == null) {
            return;
        }
        boolean wasActive = state.isActive();
        state.clear();
        if (sync && wasActive && player instanceof ServerPlayer serverPlayer) {
            syncState(serverPlayer);
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
            AscendantNetwork.syncSpeedsterState(player, state.isActive(), state.getLevel());
        }
    }

    private static Vec3 calculateNextVelocity(Vec3 current, float yaw, int input, int level) {
        return SpeedsterPhysics.nextVelocity(current, yaw, input, level,
                SpeedsterTuning.minSpeed(), SpeedsterTuning.maxSpeed(),
                SpeedsterTuning.ACCELERATION.get(), SpeedsterTuning.STEERING.get(),
                SpeedsterTuning.COAST_DRAG.get(), SpeedsterTuning.EMERGENCY_BRAKING.get(),
                SpeedsterTuning.SPRINT_MULTIPLIER.get());
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
        state.setActive(false);
        if (sync) {
            syncState(player);
            player.displayClientMessage(Component.translatable("message.ascendant.speedster.disabled"), true);
        }
    }
}
