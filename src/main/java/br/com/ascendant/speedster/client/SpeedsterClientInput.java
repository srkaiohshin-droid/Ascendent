package br.com.ascendant.speedster.client;

import br.com.ascendant.network.AscendantNetwork;
import br.com.ascendant.speedster.SpeedsterController;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = "ascendant", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class SpeedsterClientInput {

    private static boolean registered;

    private SpeedsterClientInput() {
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent ignored) {
        if (!registered) {
            MinecraftForge.EVENT_BUS.addListener(SpeedsterClientInput::onClientTick);
            registered = true;
        }
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (SpeedsterKeyBindings.TOGGLE_SPRINT.consumeClick()) {
            AscendantNetwork.sendSpeedsterControl(SpeedsterController.CONTROL_TOGGLE);
        }
        if (SpeedsterKeyBindings.LEVEL_UP.consumeClick()) {
            AscendantNetwork.sendSpeedsterControl(SpeedsterController.CONTROL_LEVEL_UP);
        }
        if (SpeedsterKeyBindings.LEVEL_DOWN.consumeClick()) {
            AscendantNetwork.sendSpeedsterControl(SpeedsterController.CONTROL_LEVEL_DOWN);
        }
        if (SpeedsterKeyBindings.DASH.consumeClick()) {
            AscendantNetwork.sendSpeedsterControl(SpeedsterController.CONTROL_DASH);
        }
        if (SpeedsterKeyBindings.PERCEPTION.consumeClick()) {
            AscendantNetwork.sendSpeedsterControl(SpeedsterController.CONTROL_PERCEPTION);
        }
        if (SpeedsterKeyBindings.OVERLOAD.consumeClick()) {
            AscendantNetwork.sendSpeedsterControl(SpeedsterController.CONTROL_OVERLOAD);
        }
    }
}
