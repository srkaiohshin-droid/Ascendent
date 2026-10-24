package br.com.ascendant.speedster.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = "ascendant", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class SpeedsterKeyBindings {

    private SpeedsterKeyBindings() {
    }

    public static final KeyMapping TOGGLE_SPRINT = new KeyMapping(
            "key.ascendant.speedster.toggle_sprint", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B,
            "key.categories.ascendant.controls");
    public static final KeyMapping DASH = new KeyMapping(
            "key.ascendant.speedster.dash", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V,
            "key.categories.ascendant.controls");
    public static final KeyMapping PERCEPTION = new KeyMapping(
            "key.ascendant.speedster.perception", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C,
            "key.categories.ascendant.controls");
    public static final KeyMapping OVERLOAD = new KeyMapping(
            "key.ascendant.speedster.overload", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X,
            "key.categories.ascendant.controls");
    public static final KeyMapping LEVEL_UP = new KeyMapping(
            "key.ascendant.speedster.level_up", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_PAGE_UP,
            "key.categories.ascendant.controls");
    public static final KeyMapping LEVEL_DOWN = new KeyMapping(
            "key.ascendant.speedster.level_down", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_PAGE_DOWN,
            "key.categories.ascendant.controls");

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_SPRINT);
        event.register(DASH);
        event.register(PERCEPTION);
        event.register(OVERLOAD);
        event.register(LEVEL_UP);
        event.register(LEVEL_DOWN);
    }
}
