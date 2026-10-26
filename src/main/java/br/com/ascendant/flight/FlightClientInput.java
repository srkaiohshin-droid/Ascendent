package br.com.ascendant.flight;

import br.com.ascendant.Ascendant;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = Ascendant.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class FlightClientInput {
    private static final KeyMapping TOGGLE_FLIGHT = new KeyMapping(
            "key.ascendant.toggle_flight", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R,
            "key.categories.ascendant.flight");
    private static final KeyMapping BRAKE = new KeyMapping(
            "key.ascendant.flight_brake", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X,
            "key.categories.ascendant.flight");
    private static final KeyMapping MANEUVER_LEFT = new KeyMapping(
            "key.ascendant.flight_maneuver_left", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C,
            "key.categories.ascendant.flight");
    private static final KeyMapping MANEUVER_RIGHT = new KeyMapping(
            "key.ascendant.flight_maneuver_right", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V,
            "key.categories.ascendant.flight");
    private static final KeyMapping MANEUVER_LOOP = new KeyMapping(
            "key.ascendant.flight_loop", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G,
            "key.categories.ascendant.flight");

    private static boolean requestPending;
    private static int lastSentInput = -1;
    private static long lastSentTick;

    private FlightClientInput() {
    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_FLIGHT);
        event.register(BRAKE);
        event.register(MANEUVER_LEFT);
        event.register(MANEUVER_RIGHT);
        event.register(MANEUVER_LOOP);
        MinecraftForge.EVENT_BUS.addListener(FlightClientInput::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(FlightClientInput::onPlayerTick);
        MinecraftForge.EVENT_BUS.addListener(FlightClientInput::onMovementInput);
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            requestPending = false;
            lastSentInput = -1;
            drainManeuverClicks();
            return;
        }

        while (TOGGLE_FLIGHT.consumeClick()) {
            if (!requestPending && minecraft.screen == null) {
                FlightState state = FlightController.getState(player);
                boolean desired = state == null || !state.isActive();
                if (desired && (!player.isAlive() || player.isInWater() || player.isInLava())) {
                    continue;
                }
                requestPending = true;
                FlightNetwork.requestActive(desired);
            }
        }

        FlightState state = FlightController.getState(player);
        if (state == null || !state.isActive()) {
            lastSentInput = -1;
            drainManeuverClicks();
            return;
        }

        if (minecraft.screen == null) {
            while (MANEUVER_LEFT.consumeClick()) {
                FlightNetwork.requestManeuver(FlightState.MANEUVER_ROLL_360, -1);
            }
            while (MANEUVER_RIGHT.consumeClick()) {
                FlightNetwork.requestManeuver(FlightState.MANEUVER_ROLL_360, 1);
            }
            while (MANEUVER_LOOP.consumeClick()) {
                FlightNetwork.requestManeuver(FlightState.MANEUVER_LOOP, 1);
            }
        } else {
            drainManeuverClicks();
        }

        int input = minecraft.screen == null ? readInput(minecraft) : 0;
        state.setInputMask(input);

        long tick = player.tickCount;
        if (input != lastSentInput || tick - lastSentTick >= 10L) {
            FlightNetwork.sendInput(input);
            lastSentInput = input;
            lastSentTick = tick;
        }
    }

    private static void drainManeuverClicks() {
        while (MANEUVER_LEFT.consumeClick()) { }
        while (MANEUVER_RIGHT.consumeClick()) { }
        while (MANEUVER_LOOP.consumeClick()) { }
    }

    private static int readInput(Minecraft minecraft) {
        int input = 0;
        if (minecraft.options.keyUp.isDown()) input |= FlightState.FORWARD;
        if (minecraft.options.keyDown.isDown()) input |= FlightState.BACK;
        if (minecraft.options.keyLeft.isDown()) input |= FlightState.LEFT;
        if (minecraft.options.keyRight.isDown()) input |= FlightState.RIGHT;
        if (BRAKE.isDown()) input |= FlightState.BRAKE;
        return input;
    }

    private static void onMovementInput(MovementInputUpdateEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.getEntity() != minecraft.player) {
            return;
        }
        FlightState state = FlightController.getState(event.getEntity());
        if (state == null || !state.isActive()) {
            return;
        }
        // Jump and sneak are not flight controls in the reference. Clearing
        // them keeps vanilla from starting a jump on landing, crouching the
        // hitbox or feeding a vertical impulse while the flight kernel owns
        // movement. Forward/strafe are preserved because the client uses them
        // to decide whether sprinting (and therefore boost) may continue.
        event.getInput().jumping = false;
        event.getInput().shiftKeyDown = false;
        if (!FlightManeuvers.isTrajectory(state.getManeuverKind())
                || state.getManeuverTicksRemaining() <= 0) {
            return;
        }
        // The maneuver path was swept assuming no camera-relative input
        // acceleration. Zero the vanilla impulses so the local move matches
        // the authoritative step instead of widening the reviewed curve.
        event.getInput().leftImpulse = 0.0F;
        event.getInput().forwardImpulse = 0.0F;
    }

    private static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.phase == TickEvent.Phase.START && event.player == minecraft.player && minecraft.player != null) {
            FlightState state = FlightController.getState(event.player);
            if (state != null && state.isActive()) {
                int input = minecraft.screen == null ? readInput(minecraft) : 0;
                state.setInputMask(input);
            }
            FlightController.tickClient(event.player);
        }
    }

    /**
     * Resets request latches after the authoritative packet has already applied
     * the state. Applying {@code setActive} here as well would clear the end
     * reason and the ground-launch flag that the same packet just carried.
     */
    static void onServerState(boolean active) {
        requestPending = false;
        lastSentInput = -1;
        if (!active) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                FlightState state = FlightController.getState(minecraft.player);
                if (state != null) {
                    state.clearInput();
                }
            }
        }
    }
}
