package br.com.ascendant;

import com.mojang.logging.LogUtils;
import br.com.ascendant.animation.AnimationCatalogData;
import br.com.ascendant.animation.AnimationCoverageValidator;
import br.com.ascendant.command.CommandEvents;
import br.com.ascendant.flight.FlightClientTuning;
import br.com.ascendant.flight.FlightEvents;
import br.com.ascendant.flight.FlightGameTests;
import br.com.ascendant.flight.FlightNetwork;
import br.com.ascendant.flight.FlightTuning;
import br.com.ascendant.network.AscendantNetwork;
import br.com.ascendant.pack.PackManager;
import br.com.ascendant.pack.PackManagerGameTests;
import br.com.ascendant.power.catalog.AbilityCatalogData;
import br.com.ascendant.power.catalog.PowerCatalogData;
import br.com.ascendant.power.runtime.PowerRuntimeEngine;
import br.com.ascendant.powers.PlayerPowerDataEvents;
import br.com.ascendant.powers.PlayerPowerDataGameTests;
import br.com.ascendant.suit.BuiltinSuitData;
import br.com.ascendant.suit.SuitManager;
import br.com.ascendant.speedster.SpeedsterEvents;
import br.com.ascendant.speedster.SpeedsterGameTests;
import br.com.ascendant.speedster.SpeedsterTuning;
import br.com.ascendant.test.FrameworkGameTests;
import br.com.ascendant.tools.ContentValidator;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterGameTestsEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

/**
 * Main entry point for Ascendant — Superhuman Framework.
 * Integrates flight, modular power engine, ability engine, suit catalog, and pack manager.
 */
@Mod(Ascendant.MOD_ID)
public final class Ascendant {
    public static final String MOD_ID = "ascendant";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Ascendant() {
        // Networking
        FlightNetwork.register();
        AscendantNetwork.register();

        // Configs
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, FlightTuning.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SpeedsterTuning.SPEC,
                "ascendant-speedster.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, FlightClientTuning.SPEC,
                "ascendant-client.toml");

        // Core Event Handlers
        MinecraftForge.EVENT_BUS.register(PlayerPowerDataEvents.class);
        MinecraftForge.EVENT_BUS.register(FlightEvents.class);
        MinecraftForge.EVENT_BUS.register(CommandEvents.class);
        MinecraftForge.EVENT_BUS.register(PowerRuntimeEngine.class);
        MinecraftForge.EVENT_BUS.register(SuitManager.class);
        MinecraftForge.EVENT_BUS.register(SpeedsterEvents.class);

        // Catalogs Initialization (500 powers, 500 abilities, modular suits)
        PowerCatalogData.registerAll();
        AbilityCatalogData.registerAll();
        BuiltinSuitData.registerAll();

        // Animation profiles for powers and abilities (visual-only, common-safe)
        AnimationCatalogData.registerAll();

        // Pack Manager
        PackManager.init(FMLPaths.GAMEDIR.get());
        PackManager.loadAllInstalledPacks();

        // Automated Validation Report on Startup
        ContentValidator.ValidationReport contentReport = ContentValidator.validate();
        LOGGER.info("[ASC] Content initialized. {}", contentReport.summary());

        // Animation Coverage Report on Startup
        AnimationCoverageValidator.CoverageReport animationReport = AnimationCoverageValidator.validate();
        LOGGER.info("[ASC] Animation coverage. {}", animationReport.summary());

        // GameTests
        FMLJavaModLoadingContext.get().getModEventBus().addListener(Ascendant::registerGameTests);
        LOGGER.info("Ascendant - Modular Superhuman Framework initialized successfully.");
    }

    private static void registerGameTests(RegisterGameTestsEvent event) {
        event.register(PlayerPowerDataGameTests.class);
        event.register(FlightGameTests.class);
        event.register(FrameworkGameTests.class);
        event.register(PackManagerGameTests.class);
        event.register(SpeedsterGameTests.class);
    }
}
