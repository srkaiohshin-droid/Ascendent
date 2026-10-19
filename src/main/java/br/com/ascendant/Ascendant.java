package br.com.ascendant;

import com.mojang.logging.LogUtils;
import br.com.ascendant.command.CommandEvents;
import br.com.ascendant.flight.FlightEvents;
import br.com.ascendant.flight.FlightGameTests;
import br.com.ascendant.flight.FlightNetwork;
import br.com.ascendant.flight.FlightTuning;
import br.com.ascendant.powers.PlayerPowerDataEvents;
import br.com.ascendant.powers.PlayerPowerDataGameTests;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterGameTestsEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.ModLoadingContext;
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
        FlightNetwork.register();
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, FlightTuning.SPEC);
        MinecraftForge.EVENT_BUS.register(PlayerPowerDataEvents.class);
        MinecraftForge.EVENT_BUS.register(FlightEvents.class);
        MinecraftForge.EVENT_BUS.register(CommandEvents.class);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(Ascendant::registerGameTests);
        LOGGER.info("Ascendant - Superhuman Framework initialized.");
    }

    private static void registerGameTests(RegisterGameTestsEvent event) {
        event.register(PlayerPowerDataGameTests.class);
        event.register(FlightGameTests.class);
    }
}
