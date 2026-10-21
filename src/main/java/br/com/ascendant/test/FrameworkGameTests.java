package br.com.ascendant.test;

import br.com.ascendant.flight.FlightController;
import br.com.ascendant.pack.PackSecurity;
import br.com.ascendant.power.AbilityRegistry;
import br.com.ascendant.power.PowerRegistry;
import br.com.ascendant.power.component.ComponentContext;
import br.com.ascendant.power.component.DashComponent;
import br.com.ascendant.power.component.HealActionComponent;
import br.com.ascendant.power.component.ShieldActionComponent;
import br.com.ascendant.power.runtime.PowerRuntimeEngine;
import br.com.ascendant.powers.IPlayerPowerData;
import br.com.ascendant.powers.PlayerPowerDataProvider;
import br.com.ascendant.suit.SuitManager;
import br.com.ascendant.tools.ContentValidator;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.file.Path;

@PrefixGameTestTemplate(false)
public final class FrameworkGameTests {

    private FrameworkGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void catalogIntegrityAndCountValidation(GameTestHelper helper) {
        ContentValidator.ValidationReport report = ContentValidator.validate();
        helper.assertTrue(report.isValid(), "Validation should have 0 errors, but had: " + report.errors());
        helper.assertTrue(PowerRegistry.size() == 500,
                "PowerRegistry must contain exactly 500 powers, found: " + PowerRegistry.size());
        helper.assertTrue(PowerRegistry.getFunctionalCount() >= 100,
                "PowerRegistry must contain at least 100 functional powers, found: " + PowerRegistry.getFunctionalCount());
        helper.assertTrue(AbilityRegistry.size() == 500,
                "AbilityRegistry must contain exactly 500 abilities, found: " + AbilityRegistry.size());
        helper.assertTrue(AbilityRegistry.getFunctionalCount() >= 100,
                "AbilityRegistry must contain at least 100 functional abilities, found: " + AbilityRegistry.getFunctionalCount());
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void coreComponentsExecution(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        if (!(player instanceof ServerPlayer serverPlayer)) {
            helper.succeed();
            return;
        }

        // Test DashComponent
        DashComponent dash = new DashComponent(1.8D, 0.2D);
        boolean dashExecuted = dash.execute(ComponentContext.of(serverPlayer, new ResourceLocation("ascendant", "test_dash")));
        helper.assertTrue(dashExecuted, "DashComponent should execute successfully");
        helper.assertTrue(serverPlayer.getDeltaMovement().lengthSqr() > 0.1D, "Dash should apply movement velocity");

        // Test HealActionComponent
        serverPlayer.setHealth(4.0F);
        HealActionComponent heal = new HealActionComponent(6.0F, true);
        boolean healExecuted = heal.execute(ComponentContext.of(serverPlayer, new ResourceLocation("ascendant", "test_heal")));
        helper.assertTrue(healExecuted, "HealActionComponent should execute successfully");
        helper.assertTrue(serverPlayer.getHealth() >= 9.9F, "Player health should increase after healing");

        // Test ShieldActionComponent
        ShieldActionComponent shield = new ShieldActionComponent(8.0F, 100);
        boolean shieldExecuted = shield.execute(ComponentContext.of(serverPlayer, new ResourceLocation("ascendant", "test_shield")));
        helper.assertTrue(shieldExecuted, "ShieldActionComponent should execute successfully");
        helper.assertTrue(serverPlayer.getAbsorptionAmount() >= 8.0F, "Player should have absorption hearts");

        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void abilityActivationAndCooldownEnforcement(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        if (!(player instanceof ServerPlayer serverPlayer)) {
            helper.succeed();
            return;
        }

        ResourceLocation strengthPower = new ResourceLocation("ascendant", "titan_strength");
        ResourceLocation punchAbility = new ResourceLocation("ascendant", "thunder_punch");

        // Unlock power first
        IPlayerPowerData data = serverPlayer.getCapability(PlayerPowerDataProvider.CAPABILITY)
                .orElseThrow(() -> new AssertionError("Power capability missing"));
        data.unlockPower(strengthPower);

        // First activation should succeed and apply cooldown
        boolean activated = PowerRuntimeEngine.activateAbility(serverPlayer, punchAbility);
        helper.assertTrue(activated, "Ability activation should succeed when player owns power");
        helper.assertTrue(PowerRuntimeEngine.isOnCooldown(serverPlayer, punchAbility),
                "Ability should be on cooldown after activation");

        // Immediate subsequent activation must fail because of cooldown
        boolean reactivated = PowerRuntimeEngine.activateAbility(serverPlayer, punchAbility);
        helper.assertTrue(!reactivated, "Ability activation should be rejected while on cooldown");

        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void suitEquipGrantsAssociatedPowers(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        if (!(player instanceof ServerPlayer serverPlayer)) {
            helper.succeed();
            return;
        }

        ResourceLocation suitId = new ResourceLocation("ascendant", "classic_ascendant");
        helper.assertTrue(!SuitManager.grantsPower(serverPlayer, FlightController.FLIGHT_POWER),
                "Player without suit should not have suit powers");

        boolean equipped = SuitManager.equipSuit(serverPlayer, suitId);
        helper.assertTrue(equipped, "Equipping suit should succeed");
        helper.assertTrue(SuitManager.grantsPower(serverPlayer, FlightController.FLIGHT_POWER),
                "Equipped suit should grant flight power to player");

        boolean unequipped = SuitManager.unequipSuit(serverPlayer);
        helper.assertTrue(unequipped, "Unequipping suit should succeed");
        helper.assertTrue(!SuitManager.grantsPower(serverPlayer, FlightController.FLIGHT_POWER),
                "Unequipped suit should revoke power grant");

        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void packSecurityPathTraversalAndExecutableRejection(GameTestHelper helper) {
        Path dummyDir = Path.of("ascendant_packs").toAbsolutePath();

        // Path traversal test
        boolean threwTraversal = false;
        try {
            PackSecurity.validateEntryName("../malicious.json", dummyDir);
        } catch (SecurityException e) {
            threwTraversal = true;
        }
        helper.assertTrue(threwTraversal, "PackSecurity must reject directory traversal");

        // Executable rejection test
        boolean threwExecutable = false;
        try {
            PackSecurity.validateEntryName("evil.class", dummyDir);
        } catch (SecurityException e) {
            threwExecutable = true;
        }
        helper.assertTrue(threwExecutable, "PackSecurity must reject executable .class files");

        // Valid file test
        boolean validPassed = false;
        try {
            PackSecurity.validateEntryName("powers/kinetic_strike.json", dummyDir);
            validPassed = true;
        } catch (SecurityException e) {
            validPassed = false;
        }
        helper.assertTrue(validPassed, "PackSecurity must accept valid content json path");

        helper.succeed();
    }
}
