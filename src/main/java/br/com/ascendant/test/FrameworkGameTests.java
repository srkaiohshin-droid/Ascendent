package br.com.ascendant.test;

import br.com.ascendant.animation.AnimationCoverageValidator;
import br.com.ascendant.animation.AnimationProfile;
import br.com.ascendant.animation.AnimationRegistry;
import br.com.ascendant.core.AbilityTrigger;
import br.com.ascendant.flight.FlightController;
import br.com.ascendant.pack.PackManifest;
import br.com.ascendant.pack.PackSecurity;
import br.com.ascendant.power.AbilityDefinition;
import br.com.ascendant.power.AbilityRegistry;
import br.com.ascendant.power.PowerDefinition;
import br.com.ascendant.power.PowerRegistry;
import br.com.ascendant.power.component.ComponentContext;
import br.com.ascendant.power.component.DashComponent;
import br.com.ascendant.power.component.HealActionComponent;
import br.com.ascendant.power.component.ShieldActionComponent;
import br.com.ascendant.power.component.TemplateComponent;
import br.com.ascendant.power.runtime.PowerRuntimeEngine;
import br.com.ascendant.powers.IPlayerPowerData;
import br.com.ascendant.powers.PlayerPowerDataProvider;
import br.com.ascendant.suit.SuitManager;
import br.com.ascendant.tools.ContentValidator;
import com.google.gson.JsonObject;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

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

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void functionalPowersHaveExecutableComponents(GameTestHelper helper) {
        ContentValidator.ValidationReport report = ContentValidator.validate();
        // Every functional power must have at least one executable (non-template) component
        for (PowerDefinition power : PowerRegistry.getAll()) {
            if (power.isFunctional()) {
                boolean hasExecutable = power.getComponents().stream()
                        .anyMatch(comp -> comp.isFunctional() && !(comp instanceof TemplateComponent));
                helper.assertTrue(hasExecutable,
                        "Functional power " + power.getId() + " must have at least one executable component");
            }
        }
        helper.assertTrue(report.functionalPowersWithoutExecutableComponent() == 0,
                "No functional powers should lack executable components, found: " + report.functionalPowersWithoutExecutableComponent());
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void packManifestRequiresValidId(GameTestHelper helper) {
        JsonObject validManifest = new JsonObject();
        validManifest.addProperty("id", "ascendant:test_pack");
        validManifest.addProperty("version", "1.0.0");
        validManifest.addProperty("minMcVersion", "1.20.1");
        validManifest.addProperty("minForgeVersion", "47.3.0");

        // Valid manifest should parse
        boolean validParsed = false;
        try {
            PackManifest.fromJson(validManifest);
            validParsed = true;
        } catch (Exception e) {
            validParsed = false;
        }
        helper.assertTrue(validParsed, "Valid pack manifest should parse successfully");

        // Missing id should throw
        JsonObject missingId = new JsonObject();
        missingId.addProperty("version", "1.0.0");
        boolean threwMissingId = false;
        try {
            PackManifest.fromJson(missingId);
        } catch (IllegalArgumentException e) {
            threwMissingId = true;
        }
        helper.assertTrue(threwMissingId, "Pack manifest without id must throw IllegalArgumentException");

        // Blank id should throw
        JsonObject blankId = new JsonObject();
        blankId.addProperty("id", "   ");
        boolean threwBlankId = false;
        try {
            PackManifest.fromJson(blankId);
        } catch (IllegalArgumentException e) {
            threwBlankId = true;
        }
        helper.assertTrue(threwBlankId, "Pack manifest with blank id must throw IllegalArgumentException");

        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void animationProfileRegistrationAndLookup(GameTestHelper helper) {
        ResourceLocation flightId = new ResourceLocation("ascendant", "flight");
        AnimationProfile profile = new AnimationProfile(
                flightId,
                "Voo de Teste",
                List.of(new AnimationProfile.EffectLayer(
                        AnimationProfile.TYPE_PARTICLES,
                        Map.of("type", "smoke", "count", "5")
                ))
        );
        AnimationRegistry.register(flightId, profile);

        helper.assertTrue(AnimationRegistry.hasProfile(flightId),
                "AnimationRegistry should have profile for registered power");
        AnimationProfile retrieved = AnimationRegistry.get(flightId);
        helper.assertTrue(retrieved != null && retrieved.getName().equals("Voo de Teste"),
                "Retrieved animation profile should match registered profile");
        helper.assertTrue(retrieved.getLayers().size() == 1,
                "Profile should contain exactly one effect layer");

        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void animationCoverageForKeyFunctionalPowers(GameTestHelper helper) {
        AnimationCoverageValidator.CoverageReport report = AnimationCoverageValidator.validate();
        // Key powers must have dedicated animation profiles (not generic placeholders)
        String[] keyPowers = {
            "ascendant:flight", "ascendant:mach_dash", "ascendant:blink_step",
            "ascendant:titan_strength", "ascendant:thunder_punch", "ascendant:kinetic_barrier",
            "ascendant:invisibility", "ascendant:healing_touch"
        };
        for (String powerId : keyPowers) {
            ResourceLocation id = ResourceLocation.tryParse(powerId);
            helper.assertTrue(id != null, "Invalid power ID: " + powerId);
            helper.assertTrue(AnimationRegistry.hasProfile(id),
                    "Key power must have animation profile: " + powerId);
        }
        helper.assertTrue(report.powersWithProfile() > 0,
                "At least some functional powers should have animation profiles");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void automaticTriggerAbilitiesCannotBeManuallyActivated(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        if (!(player instanceof ServerPlayer serverPlayer)) {
            helper.succeed();
            return;
        }

        // Unlock the parent power so ownership is not the blocking factor
        ResourceLocation strengthPower = new ResourceLocation("ascendant", "titan_strength");
        IPlayerPowerData data = serverPlayer.getCapability(PlayerPowerDataProvider.CAPABILITY)
                .orElseThrow(() -> new AssertionError("Power capability missing"));
        data.unlockPower(strengthPower);

        // thunder_punch is KEY_PRESS -> manually activatable
        ResourceLocation punchAbility = new ResourceLocation("ascendant", "thunder_punch");
        AbilityDefinition punch = AbilityRegistry.get(punchAbility);
        helper.assertTrue(punch != null && punch.getTrigger() == AbilityTrigger.KEY_PRESS,
                "thunder_punch should be KEY_PRESS triggered");
        helper.assertTrue(PowerRuntimeEngine.activateAbility(serverPlayer, punchAbility),
                "KEY_PRESS ability should be manually activatable");

        // titan_muscle_passive is PASSIVE -> must NOT be manually activatable
        ResourceLocation passiveAbility = new ResourceLocation("ascendant", "titan_muscle_passive");
        AbilityDefinition passive = AbilityRegistry.get(passiveAbility);
        helper.assertTrue(passive != null && passive.getTrigger() == AbilityTrigger.PASSIVE,
                "titan_muscle_passive should be PASSIVE triggered");
        helper.assertTrue(!PowerRuntimeEngine.activateAbility(serverPlayer, passiveAbility),
                "PASSIVE ability must not be manually activated");

        // retaliation_blast is HURT_EVENT -> must NOT be manually activatable
        ResourceLocation hurtAbility = new ResourceLocation("ascendant", "retaliation_blast");
        AbilityDefinition hurt = AbilityRegistry.get(hurtAbility);
        helper.assertTrue(hurt != null && hurt.getTrigger() == AbilityTrigger.HURT_EVENT,
                "retaliation_blast should be HURT_EVENT triggered");
        helper.assertTrue(!PowerRuntimeEngine.activateAbility(serverPlayer, hurtAbility),
                "HURT_EVENT ability must not be manually activated");

        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void suitUnequipRevokesGrantedPowers(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        if (!(player instanceof ServerPlayer serverPlayer)) {
            helper.succeed();
            return;
        }

        ResourceLocation suitId = new ResourceLocation("ascendant", "classic_ascendant");
        ResourceLocation flightPower = new ResourceLocation("ascendant", "flight");

        // Player without suit should not have suit powers
        helper.assertTrue(!SuitManager.grantsPower(serverPlayer, flightPower),
                "Player without suit should not have suit powers");

        boolean equipped = SuitManager.equipSuit(serverPlayer, suitId);
        helper.assertTrue(equipped, "Equipping suit should succeed");
        helper.assertTrue(SuitManager.grantsPower(serverPlayer, flightPower),
                "Equipped suit should grant flight power to player");

        // The suit grants flight power; hasPowerAccess should reflect the grant
        helper.assertTrue(PowerRuntimeEngine.hasPowerAccess(serverPlayer, flightPower),
                "hasPowerAccess should reflect suit-granted power");

        boolean unequipped = SuitManager.unequipSuit(serverPlayer);
        helper.assertTrue(unequipped, "Unequipping suit should succeed");
        helper.assertTrue(!SuitManager.grantsPower(serverPlayer, flightPower),
                "Unequipped suit should revoke power grant");
        helper.assertTrue(!PowerRuntimeEngine.hasPowerAccess(serverPlayer, flightPower),
                "hasPowerAccess should no longer reflect revoked suit power");

        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void activationFailureReasonProvidesFeedback(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        if (!(player instanceof ServerPlayer serverPlayer)) {
            helper.succeed();
            return;
        }

        // Unknown ability -> unavailable reason
        ResourceLocation unknown = new ResourceLocation("ascendant", "does_not_exist");
        String reason = PowerRuntimeEngine.getActivationFailureReason(serverPlayer, unknown);
        helper.assertTrue(reason != null && !reason.isBlank(),
                "Unknown ability should report a failure reason");

        // PASSIVE ability -> ability_trigger_reason
        ResourceLocation passive = new ResourceLocation("ascendant", "titan_muscle_passive");
        String passiveReason = PowerRuntimeEngine.getActivationFailureReason(serverPlayer, passive);
        helper.assertTrue(passiveReason != null && passiveReason.equals(
                "gui.ascendant.catalog.ability_trigger_reason"),
                "PASSIVE ability should report ability_trigger_reason");

        // Functional KEY_PRESS ability with no power access -> ownership_note
        ResourceLocation punch = new ResourceLocation("ascendant", "thunder_punch");
        String punchReason = PowerRuntimeEngine.getActivationFailureReason(serverPlayer, punch);
        helper.assertTrue(punchReason != null && punchReason.equals(
                "gui.ascendant.catalog.ownership_note"),
                "Ability without owned power should report ownership_note");

        helper.succeed();
    }
}
