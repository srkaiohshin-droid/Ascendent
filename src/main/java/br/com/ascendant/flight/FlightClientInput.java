package br.com.ascendant.flight;

import br.com.ascendant.Ascendant;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
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
            "key.ascendant.flight_brake", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K,
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
        MinecraftForge.EVENT_BUS.addListener(FlightClientInput::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(FlightClientInput::onPlayerTick);
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
            return;
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

    private static int readInput(Minecraft minecraft) {
        int input = 0;
        if (minecraft.options.keyUp.isDown()) input |= FlightState.FORWARD;
        if (minecraft.options.keyDown.isDown()) input |= FlightState.BACK;
        if (minecraft.options.keyLeft.isDown()) input |= FlightState.LEFT;
        if (minecraft.options.keyRight.isDown()) input |= FlightState.RIGHT;
        if (minecraft.options.keyJump.isDown()) input |= FlightState.ASCEND;
        if (minecraft.options.keyShift.isDown()) input |= FlightState.DESCEND;
        if (BRAKE.isDown()) input |= FlightState.BRAKE;
        return input;
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

    static void onServerState(boolean active) {
        requestPending = false;
        lastSentInput = -1;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            FlightState state = FlightController.getState(minecraft.player);
            if (state != null) {
                state.setActive(active);
                if (!active) {
                    state.clearInput();
                }
            }
        }
    }
}
