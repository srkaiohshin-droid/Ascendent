package br.com.ascendant.ui.client;

import br.com.ascendant.Ascendant;
import br.com.ascendant.flight.FlightController;
import br.com.ascendant.network.AscendantNetwork;
import br.com.ascendant.speedster.SpeedsterController;
import br.com.ascendant.speedster.SpeedsterState;
import br.com.ascendant.speedster.SpeedsterStateProvider;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Ascendant.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class SpeedsterClientInput {
    private static final KeyMapping TOGGLE = new KeyMapping(
            "key.ascendant.speedster_toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z,
            "key.categories.ascendant.speedster");
    private static final KeyMapping LEVEL_UP = new KeyMapping(
            "key.ascendant.speedster_level_up", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_BRACKET,
            "key.categories.ascendant.speedster");
    private static final KeyMapping LEVEL_DOWN = new KeyMapping(
            "key.ascendant.speedster_level_down", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_BRACKET,
            "key.categories.ascendant.speedster");
    private static final KeyMapping EMERGENCY_BRAKE = new KeyMapping(
            "key.ascendant.speedster_brake", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT,
            "key.categories.ascendant.speedster");

    private static boolean listenerRegistered;
    private static int lastSentInput = -1;
    private static long lastSentTick;

    private SpeedsterClientInput() {
    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE);
        event.register(LEVEL_UP);
        event.register(LEVEL_DOWN);
        event.register(EMERGENCY_BRAKE);
        if (!listenerRegistered) {
            MinecraftForge.EVENT_BUS.addListener(SpeedsterClientInput::onClientTick);
            MinecraftForge.EVENT_BUS.addListener(SpeedsterClientInput::onPlayerTick);
            listenerRegistered = true;
        }
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            resetInputSync();
            drainClicks();
            return;
        }

        if (minecraft.screen == null) {
            while (TOGGLE.consumeClick()) {
                AscendantNetwork.sendSpeedsterControl(SpeedsterController.CONTROL_TOGGLE);
            }
            while (LEVEL_UP.consumeClick()) {
                AscendantNetwork.sendSpeedsterControl(SpeedsterController.CONTROL_LEVEL_UP);
            }
            while (LEVEL_DOWN.consumeClick()) {
                AscendantNetwork.sendSpeedsterControl(SpeedsterController.CONTROL_LEVEL_DOWN);
            }
        } else {
            drainClicks();
        }

        SpeedsterState state = player.getCapability(SpeedsterStateProvider.CAPABILITY).orElse(null);
        var flightState = FlightController.getState(player);
        if (state == null || !state.isActive() || flightState != null && flightState.isActive()) {
            resetInputSync();
            return;
        }
        int input = minecraft.screen == null ? readInput(minecraft) : 0;
        state.setInputMask(input);
        long tick = player.tickCount;
        if (input != lastSentInput || tick - lastSentTick >= 5L) {
            AscendantNetwork.sendSpeedsterInput(input);
            lastSentInput = input;
            lastSentTick = tick;
        }
    }

    private static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.phase == TickEvent.Phase.START && event.player == minecraft.player
                && minecraft.player != null) {
            SpeedsterState state = playerState(minecraft.player);
            if (state != null && state.isActive()) {
                state.setInputMask(minecraft.screen == null ? readInput(minecraft) : 0);
            }
            SpeedsterController.tickClient(minecraft.player);
        }
    }

    private static int readInput(Minecraft minecraft) {
        int input = 0;
        if (minecraft.options.keyUp.isDown()) input |= SpeedsterState.FORWARD;
        if (minecraft.options.keyDown.isDown()) input |= SpeedsterState.BACK;
        if (minecraft.options.keyLeft.isDown()) input |= SpeedsterState.LEFT;
        if (minecraft.options.keyRight.isDown()) input |= SpeedsterState.RIGHT;
        if (minecraft.options.keySprint.isDown()) input |= SpeedsterState.BOOST;
        if (EMERGENCY_BRAKE.isDown()) input |= SpeedsterState.BRAKE;
        return input;
    }

    private static SpeedsterState playerState(LocalPlayer player) {
        return player.getCapability(SpeedsterStateProvider.CAPABILITY).orElse(null);
    }

    private static void resetInputSync() {
        lastSentInput = -1;
        lastSentTick = 0L;
    }

    private static void drainClicks() {
        while (TOGGLE.consumeClick()) { }
        while (LEVEL_UP.consumeClick()) { }
        while (LEVEL_DOWN.consumeClick()) { }
    }
}
