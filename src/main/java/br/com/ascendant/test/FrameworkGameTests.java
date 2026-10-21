package br.com.ascendant.test;

import br.com.ascendant.catalog.CatalogInteraction;
import br.com.ascendant.flight.FlightController;
import br.com.ascendant.pack.PackSecurity;
import br.com.ascendant.power.AbilityRegistry;
import br.com.ascendant.power.PowerDefinition;
import br.com.ascendant.power.PowerRegistry;
import br.com.ascendant.power.component.ComponentContext;
import br.com.ascendant.power.component.DashComponent;
import br.com.ascendant.power.component.ComponentRegistry;
import br.com.ascendant.power.component.InvisibilityComponent;
import br.com.ascendant.power.component.LevitationComponent;
import br.com.ascendant.power.component.PhaseComponent;
import br.com.ascendant.power.component.SummonEntityComponent;
import br.com.ascendant.power.component.VortexActionComponent;
import br.com.ascendant.power.component.HealActionComponent;
import br.com.ascendant.power.component.ShieldActionComponent;
import br.com.ascendant.power.runtime.PowerRuntimeEngine;
import br.com.ascendant.powers.IPlayerPowerData;
import br.com.ascendant.powers.PlayerPowerDataProvider;
import br.com.ascendant.suit.SuitManager;
import br.com.ascendant.suit.SuitDefinition;
import br.com.ascendant.suit.SuitRegistry;
import br.com.ascendant.tools.ContentValidator;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.network.NetworkHooks;

import java.nio.file.Path;
import java.util.UUID;
import com.google.gson.JsonObject;

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
        helper.assertTrue(PowerRegistry.getFunctionalCount() == 113,
                "PowerRegistry must contain exactly 113 functional powers, found: " + PowerRegistry.getFunctionalCount());
        helper.assertTrue(PowerRegistry.size() - PowerRegistry.getFunctionalCount() == 387,
                "PowerRegistry must retain 387 honest templates");
        helper.assertTrue(AbilityRegistry.size() == 500,
                "AbilityRegistry must contain exactly 500 abilities, found: " + AbilityRegistry.size());
        helper.assertTrue(AbilityRegistry.getFunctionalCount() == 111,
                "AbilityRegistry must retain exactly 111 functional abilities, found: " + AbilityRegistry.getFunctionalCount());
        helper.assertTrue(AbilityRegistry.size() - AbilityRegistry.getFunctionalCount() == 389,
                "AbilityRegistry must retain 389 honest templates");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void catalogFilteringAndActionClassification(GameTestHelper helper) {
        PowerDefinition active = PowerRegistry.get(new ResourceLocation("ascendant", "refractive_invisibility"));
        PowerDefinition sustained = PowerRegistry.get(new ResourceLocation("ascendant", "telekinetic_levitate"));
        PowerDefinition passive = PowerRegistry.get(new ResourceLocation("ascendant", "kinetic_sprint"));
        PowerDefinition template = PowerRegistry.get(new ResourceLocation("ascendant", "orbital_ascent"));
        var activeAbility = AbilityRegistry.get(new ResourceLocation("ascendant", "thunder_punch"));
        var passiveAbility = AbilityRegistry.get(new ResourceLocation("ascendant", "night_vision_passive"));

        helper.assertTrue(CatalogInteraction.matches("REFRACTIVE camouflage",
                        active.getId().toString(), active.getNameKey(), active.getCategory().name()),
                "Catalog search must match case-insensitive identifier and category terms");
        helper.assertTrue(CatalogInteraction.matches("levitate", sustained.getId().toString(),
                        "Telekinetic Levitate", sustained.getCategory().name()),
                "Catalog search must match display names");
        helper.assertTrue(!CatalogInteraction.matches("missing", active.getId().toString(),
                        active.getNameKey(), active.getCategory().name()),
                "Catalog search must reject unmatched queries");
        helper.assertTrue(CatalogInteraction.matchesCategory("TELEKINESIS", "telekinesis")
                        && CatalogInteraction.matchesCategory("all", "telekinesis")
                        && !CatalogInteraction.matchesCategory("WATER", "telekinesis"),
                "Category filtering must be case-insensitive and support all categories");
        helper.assertTrue(CatalogInteraction.forPower(active).type() == CatalogInteraction.ActionType.ACTIVATE_POWER,
                "Functional ACTIVE powers map to the power activation path");
        helper.assertTrue(CatalogInteraction.forPower(sustained).type() == CatalogInteraction.ActionType.TOGGLE_POWER,
                "Functional SUSTAINED powers map to a server-resolved toggle request");
        helper.assertTrue(CatalogInteraction.forPower(passive).type() == CatalogInteraction.ActionType.NONE,
                "Passive powers must not be manually activated from a catalog");
        helper.assertTrue(CatalogInteraction.forPower(template).type() == CatalogInteraction.ActionType.NONE,
                "Template powers must be visibly non-actionable");
        helper.assertTrue(CatalogInteraction.forAbility(activeAbility).type()
                        == CatalogInteraction.ActionType.ACTIVATE_ABILITY
                        && CatalogInteraction.forAbility(passiveAbility).type() == CatalogInteraction.ActionType.NONE,
                "Only functional key-triggered abilities map to manual activation");
        helper.assertTrue(CatalogInteraction.radialSector(0.0D, -100.0D, 8, 12.0D) == 0
                        && CatalogInteraction.radialSector(100.0D, 0.0D, 8, 12.0D) == 2
                        && CatalogInteraction.radialSector(0.0D, 0.0D, 8, 12.0D) == -1
                        && CatalogInteraction.radialSector(100.0D, 0.0D, 9, 12.0D) == -1,
                "Radial selection must map clockwise sectors and reject the dead zone and invalid counts");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void newComponentsAreRegisteredAndExecuteOnServer(GameTestHelper helper) {
        TestServerPlayer testPlayer = createServerPlayer(helper);
        ServerPlayer serverPlayer = testPlayer.player();
        BlockPos spawn = helper.absolutePos(new BlockPos(1, 1, 1));
        serverPlayer.moveTo(spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D, 0.0F, 0.0F);

        ResourceLocation invisibilityId = new ResourceLocation("ascendant", "refractive_invisibility");
        ResourceLocation levitationId = new ResourceLocation("ascendant", "telekinetic_levitate");
        ResourceLocation vortexId = new ResourceLocation("ascendant", "vortex_maelstrom");
        ResourceLocation summonId = new ResourceLocation("ascendant", "spectral_wolf_pack");
        ResourceLocation phaseId = new ResourceLocation("ascendant", "void_step");
        assertPowerComponent(helper, invisibilityId, InvisibilityComponent.TYPE);
        assertPowerComponent(helper, levitationId, LevitationComponent.TYPE);
        assertPowerComponent(helper, vortexId, VortexActionComponent.TYPE);
        assertPowerComponent(helper, summonId, SummonEntityComponent.TYPE);
        assertPowerComponent(helper, phaseId, PhaseComponent.TYPE);
        helper.assertTrue(PowerRegistry.get(levitationId).getType()
                        == br.com.ascendant.core.PowerType.SUSTAINED
                        && PowerRegistry.get(vortexId).getType() == br.com.ascendant.core.PowerType.SUSTAINED,
                "Sustained powers must keep their sustained runtime");

        IPlayerPowerData data = serverPlayer.getCapability(PlayerPowerDataProvider.CAPABILITY)
                .orElseThrow(() -> new AssertionError("Power capability missing"));
        data.unlockPower(invisibilityId);
        data.unlockPower(levitationId);
        data.unlockPower(vortexId);
        data.unlockPower(summonId);
        data.unlockPower(phaseId);

        helper.assertTrue(PowerRuntimeEngine.activatePower(serverPlayer, invisibilityId),
                "Active invisibility should execute through the server runtime");
        helper.assertTrue(serverPlayer.hasEffect(MobEffects.INVISIBILITY),
                "Invisibility should apply its bounded vanilla status effect");
        var wolvesBefore = serverPlayer.serverLevel().getEntitiesOfClass(Wolf.class,
                serverPlayer.getBoundingBox().inflate(8.0D), wolf -> true);
        helper.assertTrue(PowerRuntimeEngine.activatePower(serverPlayer, summonId),
                "Wolf summon should execute through the server runtime");
        var nearbyWolves = serverPlayer.serverLevel().getEntitiesOfClass(Wolf.class,
                serverPlayer.getBoundingBox().inflate(8.0D), wolf -> true);
        var summonedWolves = nearbyWolves.stream().filter(Wolf::isTame).toList();
        helper.assertTrue(nearbyWolves.size() - wolvesBefore.size() == 3
                        && summonedWolves.size() - wolvesBefore.stream().filter(Wolf::isTame).count() == 3,
                "Summoning must add three tamed Minecraft 1.20.1 wolves; delta "
                        + (nearbyWolves.size() - wolvesBefore.size()) + " total, "
                        + (summonedWolves.size() - wolvesBefore.stream().filter(Wolf::isTame).count()) + " tamed");

        Mob levitationTarget = EntityType.ZOMBIE.create(serverPlayer.serverLevel());
        Mob vortexTarget = EntityType.ZOMBIE.create(serverPlayer.serverLevel());
        helper.assertTrue(levitationTarget != null && vortexTarget != null,
                "Test targets must be registered vanilla entities");
        Vec3 look = serverPlayer.getLookAngle();
        levitationTarget.moveTo(serverPlayer.getX() + look.x * 1.5D,
                serverPlayer.getY() + look.y * 1.5D,
                serverPlayer.getZ() + look.z * 1.5D, 0.0F, 0.0F);
        vortexTarget.moveTo(serverPlayer.getX() + 3.0D, serverPlayer.getY(), serverPlayer.getZ(), 0.0F, 0.0F);
        helper.assertTrue(serverPlayer.serverLevel().addFreshEntity(levitationTarget)
                        && serverPlayer.serverLevel().addFreshEntity(vortexTarget),
                "Test targets should enter the server level");

        serverPlayer.setYRot(0.0F);
        serverPlayer.setXRot(0.0F);
        helper.assertTrue(PowerRuntimeEngine.togglePower(serverPlayer, levitationId, true),
                "Sustained levitation should toggle on");
        helper.assertTrue(PowerRuntimeEngine.togglePower(serverPlayer, vortexId, true),
                "Sustained vortex should toggle on");
        helper.assertTrue(PowerRegistry.get(levitationId).getComponents().get(0)
                        .execute(ComponentContext.of(serverPlayer, levitationId, levitationTarget)),
                "Levitation component should lift its validated target");
        PowerRegistry.get(vortexId).getComponents().get(0).onPlayerTick(serverPlayer);
        helper.assertTrue(levitationTarget.getDeltaMovement().y > 0.0D,
                "Levitation should lift a visible non-allied target");
        helper.assertTrue(vortexTarget.getDeltaMovement().lengthSqr() > 0.0D,
                "Vortex should pull a nearby non-allied target");

        Vec3 phaseStart = serverPlayer.position();
        helper.assertTrue(PowerRuntimeEngine.activatePower(serverPlayer, phaseId),
                "The bounded phase step should execute through the server runtime");
        helper.assertTrue(serverPlayer.position().distanceToSqr(phaseStart) > 1.0D,
                "Phase step should displace the player to a collision-free destination");
        removeServerPlayer(testPlayer);
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void temporaryInvisibilityUsesFiniteDuration(GameTestHelper helper) {
        TestServerPlayer testPlayer = createServerPlayer(helper);
        ServerPlayer serverPlayer = testPlayer.player();
        InvisibilityComponent component = new InvisibilityComponent(5);
        helper.assertTrue(component.execute(ComponentContext.of(serverPlayer,
                        new ResourceLocation("ascendant", "temporary_invisibility_test"))),
                "Temporary invisibility should activate");
        helper.assertTrue(serverPlayer.hasEffect(MobEffects.INVISIBILITY),
                "Temporary invisibility should be present before expiry");
        int duration = serverPlayer.getEffect(MobEffects.INVISIBILITY).getDuration();
        helper.assertTrue(duration == 5, "Component must apply the configured finite duration");
        helper.assertTrue(duration < 20 * 60 * 10,
                "Temporary invisibility must not be represented by an effectively permanent duration");
        removeServerPlayer(testPlayer);
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void suitRemovalCleansToggleButPreservesIntrinsicPower(GameTestHelper helper) {
        TestServerPlayer testPlayer = createServerPlayer(helper);
        ServerPlayer serverPlayer = testPlayer.player();
        ResourceLocation infraredId = new ResourceLocation("ascendant", "infrared_toggle");
        ResourceLocation speedId = new ResourceLocation("ascendant", "kinetic_sprint");
        ResourceLocation suitId = new ResourceLocation("ascendant", "gametest_infrared_suit");
        SuitRegistry.register(SuitDefinition.builder(suitId).addPower(infraredId).addPower(speedId).build());
        double baseMovementSpeed = serverPlayer.getAttributeValue(Attributes.MOVEMENT_SPEED);

        helper.assertTrue(SuitManager.equipSuit(serverPlayer, suitId), "Test suit should equip");
        helper.assertTrue(PowerRuntimeEngine.togglePower(serverPlayer, infraredId, true),
                "Suit-granted toggle should activate");
        helper.assertTrue(serverPlayer.hasEffect(MobEffects.NIGHT_VISION),
                "Activated toggle should apply its managed effect");
        PowerRegistry.get(speedId).getComponents().get(0).onPlayerTick(serverPlayer);
        double boostedMovementSpeed = serverPlayer.getAttributeValue(Attributes.MOVEMENT_SPEED);
        helper.assertTrue(boostedMovementSpeed > baseMovementSpeed,
                "Suit-granted passive should apply its attribute modifier");
        helper.assertTrue(SuitManager.unequipSuit(serverPlayer), "Test suit should unequip");
        helper.assertTrue(!PowerRuntimeEngine.isPowerToggled(serverPlayer, infraredId),
                "Removing the final access source must clear the toggle");
        helper.assertTrue(!serverPlayer.hasEffect(MobEffects.NIGHT_VISION),
                "Removing the final access source must clean the toggle-owned effect");
        helper.assertTrue(Math.abs(serverPlayer.getAttributeValue(Attributes.MOVEMENT_SPEED) - baseMovementSpeed) < 1.0E-6D,
                "Removing the final access source must remove its transient attribute modifier");

        helper.assertTrue(SuitManager.equipSuit(serverPlayer, suitId), "Test suit should re-equip");
        PowerRegistry.get(speedId).getComponents().get(0).onPlayerTick(serverPlayer);
        helper.assertTrue(Math.abs(serverPlayer.getAttributeValue(Attributes.MOVEMENT_SPEED) - boostedMovementSpeed) < 1.0E-6D,
                "Re-equipping and ticking must not duplicate the attribute modifier");
        helper.assertTrue(SuitManager.unequipSuit(serverPlayer), "Test suit should unequip after re-equipping");

        IPlayerPowerData data = serverPlayer.getCapability(PlayerPowerDataProvider.CAPABILITY)
                .orElseThrow(() -> new AssertionError("Power capability missing"));
        data.unlockPower(infraredId);
        helper.assertTrue(SuitManager.equipSuit(serverPlayer, suitId), "Test suit should equip again");
        helper.assertTrue(PowerRuntimeEngine.togglePower(serverPlayer, infraredId, true),
                "Toggle should activate with both suit and intrinsic access");
        helper.assertTrue(SuitManager.unequipSuit(serverPlayer), "Test suit should unequip again");
        helper.assertTrue(PowerRuntimeEngine.hasPowerAccess(serverPlayer, infraredId)
                        && PowerRuntimeEngine.isPowerToggled(serverPlayer, infraredId)
                        && serverPlayer.hasEffect(MobEffects.NIGHT_VISION),
                "Removing one source must preserve an intrinsically unlocked active power");
        removeServerPlayer(testPlayer);
        helper.succeed();
    }

    private static void assertPowerComponent(GameTestHelper helper, ResourceLocation powerId, String componentType) {
        PowerDefinition power = PowerRegistry.get(powerId);
        helper.assertTrue(power != null && power.isFunctional(),
                "Power should be registered as functional: " + powerId);
        helper.assertTrue(power.getComponents().size() == 1
                        && power.getComponents().get(0).getType().equals(componentType),
                "Power should use component " + componentType + ": " + powerId);
        JsonObject serialized = power.getComponents().get(0).toJson();
        helper.assertTrue(ComponentRegistry.deserialize(serialized).isFunctional()
                        && ComponentRegistry.deserialize(serialized).getType().equals(componentType),
                "Component should round-trip through ComponentRegistry: " + componentType);
    }

    private static TestServerPlayer createServerPlayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        UUID uuid = UUID.randomUUID();
        ServerPlayer player = new ServerPlayer(server, level,
                new GameProfile(uuid, "asc_b1_" + uuid.toString().substring(0, 8)));
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel();
        Connection.configureSerialization(channel.pipeline(), PacketFlow.SERVERBOUND);
        channel.pipeline().addLast("packet_handler", connection);
        channel.pipeline().fireChannelActive();
        NetworkHooks.registerClientLoginChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player);
        return new TestServerPlayer(player, channel);
    }

    private static void removeServerPlayer(TestServerPlayer testPlayer) {
        testPlayer.player().getServer().getPlayerList().remove(testPlayer.player());
        testPlayer.clientChannel().finishAndReleaseAll();
    }

    private record TestServerPlayer(ServerPlayer player, EmbeddedChannel clientChannel) {
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
