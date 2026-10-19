package br.com.ascendant;

import com.mojang.logging.LogUtils;
import br.com.ascendant.powers.PlayerPowerDataEvents;
import br.com.ascendant.powers.PlayerPowerDataGameTests;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterGameTestsEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Main entry point for Ascendant.
 *
 * Gameplay systems will be registered through dedicated modules.
 */
@Mod(Ascendant.MOD_ID)
public final class Ascendant {
    public static final String MOD_ID = "ascendant";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Ascendant() {
        MinecraftForge.EVENT_BUS.register(PlayerPowerDataEvents.class);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(Ascendant::registerGameTests);
        LOGGER.info("Ascendant - Superhuman Framework initialized.");
    }

    private static void registerGameTests(RegisterGameTestsEvent event) {
        event.register(PlayerPowerDataGameTests.class);
    }
}
