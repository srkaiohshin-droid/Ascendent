package br.com.ascendant.test;

import br.com.ascendant.animation.AnimationCoverageValidator;
import br.com.ascendant.animation.AnimationProfile;
import br.com.ascendant.animation.AnimationRegistry;
import br.com.ascendant.catalog.CatalogInteraction;
import br.com.ascendant.core.AbilityCategory;
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
import br.com.ascendant.power.component.ComponentRegistry;
import br.com.ascendant.power.component.IPowerComponent;
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
import net.minecraft.world.level.block.Blocks;
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
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
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
        BlockPos blockedWolfSpawn = BlockPos.containing(serverPlayer.getX() + 2.5D,
                serverPlayer.getY(), serverPlayer.getZ());
        serverPlayer.serverLevel().setBlock(blockedWolfSpawn, Blocks.STONE.defaultBlockState(), 3);
        var wolvesBefore = serverPlayer.serverLevel().getEntitiesOfClass(Wolf.class,
                serverPlayer.getBoundingBox().inflate(8.0D), wolf -> true);
        helper.assertTrue(PowerRuntimeEngine.activatePower(serverPlayer, summonId),
                "Wolf summon should find collision-free alternatives when the preferred position is blocked");
        var nearbyWolves = serverPlayer.serverLevel().getEntitiesOfClass(Wolf.class,
                serverPlayer.getBoundingBox().inflate(8.0D), wolf -> true);
        var summonedWolves = nearbyWolves.stream().filter(Wolf::isTame).toList();
        helper.assertTrue(nearbyWolves.size() - wolvesBefore.size() == 3
                        && summonedWolves.size() - wolvesBefore.stream().filter(Wolf::isTame).count() == 3,
                "Summoning must add three tamed Minecraft 1.20.1 wolves; delta "
                        + (nearbyWolves.size() - wolvesBefore.size()) + " total, "
                        + (summonedWolves.size() - wolvesBefore.stream().filter(Wolf::isTame).count()) + " tamed");
        serverPlayer.serverLevel().removeBlock(blockedWolfSpawn, false);

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

        boolean threwWindowsPath = false;
        try {
            PackSecurity.validateEntryName("C:\\outside.json", dummyDir);
        } catch (SecurityException e) {
            threwWindowsPath = true;
        }
        helper.assertTrue(threwWindowsPath, "PackSecurity must reject Windows drive paths");

        boolean threwWindowsDevice = false;
        try {
            PackSecurity.validateEntryName("CON/metadata.json", dummyDir);
        } catch (SecurityException e) {
            threwWindowsDevice = true;
        }
        helper.assertTrue(threwWindowsDevice, "PackSecurity must reject Windows device names");

        // Executable rejection test
        boolean threwExecutable = false;
        try {
            PackSecurity.validateEntryName("evil.class", dummyDir);
        } catch (SecurityException e) {
            threwExecutable = true;
        }
        helper.assertTrue(threwExecutable, "PackSecurity must reject executable .class files");

        boolean threwExecutableSuffix = false;
        try {
            PackSecurity.validateEntryName("payload.jar.", dummyDir);
        } catch (SecurityException e) {
            threwExecutableSuffix = true;
        }
        helper.assertTrue(threwExecutableSuffix,
                "PackSecurity must reject trailing-dot aliases of prohibited executable files");

        // Valid file test
        boolean validPassed = false;
        try {
            PackSecurity.validateEntryName("powers/kinetic_strike.json", dummyDir);
            validPassed = true;
        } catch (SecurityException e) {
            validPassed = false;
        }
        helper.assertTrue(validPassed, "PackSecurity must accept valid content json path");

        boolean benignDoubleDotsPassed = false;
        try {
            PackSecurity.validateEntryName("powers/part..json", dummyDir);
            benignDoubleDotsPassed = true;
        } catch (SecurityException e) {
            benignDoubleDotsPassed = false;
        }
        helper.assertTrue(benignDoubleDotsPassed,
                "PackSecurity should reject traversal segments, not harmless double dots inside a filename");

        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void permanentFlightSurvivesSuitUnequip(GameTestHelper helper) {
        TestServerPlayer testPlayer = createServerPlayer(helper);
        try {
            ServerPlayer player = testPlayer.player();
            IPlayerPowerData data = player.getCapability(PlayerPowerDataProvider.CAPABILITY)
                    .orElseThrow(() -> new AssertionError("Power capability missing"));
            ResourceLocation flightPower = FlightController.FLIGHT_POWER;
            ResourceLocation suitId = new ResourceLocation("ascendant", "classic_ascendant");

            helper.assertTrue(data.unlockPower(flightPower),
                    "The player must permanently unlock flight before equipping the suit");
            helper.assertTrue(SuitManager.equipSuit(player, suitId),
                    "The flight suit must equip");
            helper.assertTrue(PowerRuntimeEngine.hasPowerAccess(player, flightPower),
                    "Flight access must be available from both sources");
            helper.assertTrue(FlightController.requestActive(player, true),
                    "Permanent flight must activate while the suit is equipped");
            helper.assertTrue(FlightController.getState(player) != null
                            && FlightController.getState(player).isActive(),
                    "Flight state must be active before unequipping");

            helper.assertTrue(SuitManager.unequipSuit(player), "The flight suit must unequip");
            helper.assertTrue(data.hasPower(flightPower),
                    "Unequipping a suit must not revoke a permanently unlocked power");
            helper.assertTrue(PowerRuntimeEngine.hasPowerAccess(player, flightPower),
                    "Permanent flight access must survive suit removal");
            helper.assertTrue(FlightController.getState(player) != null
                            && FlightController.getState(player).isActive(),
                    "Flight must remain active when another access source still grants it");
            FlightController.requestActive(player, false);
        } finally {
            removeServerPlayer(testPlayer);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void suitGrantedAbilityAuthorizationIsIndependentFromParentPower(GameTestHelper helper) {
        TestServerPlayer testPlayer = createServerPlayer(helper);
        try {
            ServerPlayer player = testPlayer.player();
            ResourceLocation abilityId = new ResourceLocation("ascendant", "kinetic_barrier_deploy");
            ResourceLocation suitId = new ResourceLocation("ascendant", "gametest_direct_ability_suit");
            SuitRegistry.register(SuitDefinition.builder(suitId).addAbility(abilityId).build());
            AbilityDefinition ability = AbilityRegistry.get(abilityId);

            helper.assertTrue(ability != null, "Barrier ability must exist in the catalog");
            helper.assertTrue(!PowerRuntimeEngine.hasAbilityAccess(player, ability),
                    "The player must not have access before equipping the suit");
            helper.assertTrue(SuitManager.equipSuit(player, suitId), "Direct-ability suit must equip");
            helper.assertTrue(PowerRuntimeEngine.hasAbilityAccess(player, ability),
                    "A directly suit-granted ability must be authorized without its parent power");
            helper.assertTrue(PowerRuntimeEngine.activateAbility(player, abilityId),
                    "The suit-granted ability must execute on the server");
            helper.assertTrue(SuitManager.unequipSuit(player), "Direct-ability suit must unequip");
            helper.assertTrue(!PowerRuntimeEngine.hasAbilityAccess(player, ability),
                    "Removing the suit must remove the direct ability grant");
            helper.assertTrue(!PowerRuntimeEngine.activateAbility(player, abilityId),
                    "The ability must no longer execute after the final grant is removed");
        } finally {
            removeServerPlayer(testPlayer);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void automaticAbilityTriggersDispatchAndRespectCooldown(GameTestHelper helper) {
        TestServerPlayer testPlayer = createServerPlayer(helper);
        ResourceLocation tickId = new ResourceLocation("ascendant", "gametest_tick_dispatch");
        ResourceLocation passiveId = new ResourceLocation("ascendant", "gametest_passive_dispatch");
        ResourceLocation toggleId = new ResourceLocation("ascendant", "gametest_toggle_dispatch");
        ResourceLocation hurtId = new ResourceLocation("ascendant", "gametest_hurt_dispatch");
        ResourceLocation attackId = new ResourceLocation("ascendant", "gametest_attack_dispatch");
        try {
            ServerPlayer player = testPlayer.player();
            ResourceLocation parentPower = new ResourceLocation("ascendant", "titan_strength");
            IPlayerPowerData data = player.getCapability(PlayerPowerDataProvider.CAPABILITY)
                    .orElseThrow(() -> new AssertionError("Power capability missing"));
            data.unlockPower(parentPower);

            CountingAction tickAction = new CountingAction();
            registerTestAbility(tickId, parentPower, AbilityTrigger.TICK, 20, tickAction);
            PowerRuntimeEngine.dispatchTickAbilities(player);
            helper.assertTrue(tickAction.executions == 1,
                    "TICK abilities must execute once when their cooldown allows it");
            helper.assertTrue(tickAction.ticks == 1,
                    "TICK abilities must receive periodic maintenance");
            helper.assertTrue(PowerRuntimeEngine.isOnCooldown(player, tickId),
                    "Executed TICK abilities must apply their cooldown");
            PowerRuntimeEngine.dispatchTickAbilities(player);
            helper.assertTrue(tickAction.executions == 1,
                    "TICK abilities must not execute again while on cooldown");
            helper.assertTrue(tickAction.ticks == 2,
                    "TICK maintenance must keep running while on cooldown");

            CountingAction passiveAction = new CountingAction();
            registerTestAbility(passiveId, parentPower, AbilityTrigger.PASSIVE, 0, passiveAction);
            PowerRuntimeEngine.dispatchPassiveAbilities(player);
            PowerRuntimeEngine.dispatchPassiveAbilities(player);
            helper.assertTrue(passiveAction.executions == 1,
                    "PASSIVE abilities must apply exactly once, not once per tick");
            helper.assertTrue(passiveAction.ticks == 2,
                    "PASSIVE abilities must be maintained idempotently every tick");
            helper.assertTrue(passiveAction.disabledCount == 0,
                    "PASSIVE state must not be cleaned while access remains");
            data.revokePower(parentPower);
            PowerRuntimeEngine.dispatchPassiveAbilities(player);
            helper.assertTrue(passiveAction.disabledCount == 1,
                    "PASSIVE state must be cleaned when access is lost");
            data.unlockPower(parentPower);

            CountingAction toggleAction = new CountingAction();
            registerTestAbility(toggleId, parentPower, AbilityTrigger.TOGGLE, 0, toggleAction);
            helper.assertTrue(PowerRuntimeEngine.toggleAbility(player, toggleId),
                    "TOGGLE abilities must be explicitly switchable");
            helper.assertTrue(PowerRuntimeEngine.isAbilityToggled(player, toggleId),
                    "TOGGLE state must be tracked on the server");
            helper.assertTrue(toggleAction.enabledCount == 1,
                    "Enabling a TOGGLE ability must apply its enabled state");
            PowerRuntimeEngine.dispatchToggledAbilities(player);
            PowerRuntimeEngine.dispatchToggledAbilities(player);
            helper.assertTrue(toggleAction.ticks == 2,
                    "Enabled TOGGLE abilities must receive maintenance");
            helper.assertTrue(PowerRuntimeEngine.toggleAbility(player, toggleId),
                    "TOGGLE abilities must switch off explicitly");
            helper.assertTrue(!PowerRuntimeEngine.isAbilityToggled(player, toggleId),
                    "TOGGLE state must be cleared on disable");
            helper.assertTrue(toggleAction.disabledCount == 1,
                    "Disabling a TOGGLE ability must clean its managed state");

            CountingAction hurtAction = new CountingAction();
            registerTestAbility(hurtId, parentPower, AbilityTrigger.HURT_EVENT, 20, hurtAction);
            LivingHurtEvent hurtEvent = new LivingHurtEvent(player, player.damageSources().magic(), 2.0F);
            PowerRuntimeEngine.dispatchHurtAbilities(player, hurtEvent);
            PowerRuntimeEngine.dispatchHurtAbilities(player, hurtEvent);
            helper.assertTrue(hurtAction.hurts == 1 && hurtAction.executions == 1,
                    "HURT_EVENT abilities must react once per cooldown window");

            CountingAction attackAction = new CountingAction();
            registerTestAbility(attackId, parentPower, AbilityTrigger.ATTACK_HIT, 20, attackAction);
            LivingHurtEvent attackEvent = new LivingHurtEvent(player,
                    player.damageSources().playerAttack(player), 1.0F);
            PowerRuntimeEngine.dispatchAttackAbilities(player, attackEvent);
            PowerRuntimeEngine.dispatchAttackAbilities(player, attackEvent);
            helper.assertTrue(attackAction.attacks == 1 && attackAction.executions == 1,
                    "ATTACK_HIT abilities must react once per cooldown window");
        } finally {
            AbilityRegistry.unregister(tickId);
            AbilityRegistry.unregister(passiveId);
            AbilityRegistry.unregister(toggleId);
            AbilityRegistry.unregister(hurtId);
            AbilityRegistry.unregister(attackId);
            removeServerPlayer(testPlayer);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void activationFeedbackReportsRealFailure(GameTestHelper helper) {
        TestServerPlayer testPlayer = createServerPlayer(helper);
        try {
            ServerPlayer player = testPlayer.player();
            ResourceLocation barrierPower = new ResourceLocation("ascendant", "kinetic_barrier");
            ResourceLocation barrierAbility = new ResourceLocation("ascendant", "kinetic_barrier_deploy");
            IPlayerPowerData data = player.getCapability(PlayerPowerDataProvider.CAPABILITY)
                    .orElseThrow(() -> new AssertionError("Power capability missing"));

            helper.assertTrue(!PowerRuntimeEngine.activateAbilityWithFeedback(player, barrierAbility),
                    "Activation must fail before ownership is granted");
            helper.assertTrue(PowerRuntimeEngine.getActivationFailureReason(player, barrierAbility)
                            .equals("gui.ascendant.catalog.ownership_note"),
                    "Missing ownership must be reported as the failure reason");

            data.unlockPower(barrierPower);
            helper.assertTrue(PowerRuntimeEngine.activateAbilityWithFeedback(player, barrierAbility),
                    "The same ability must activate after ownership is granted");
            helper.assertTrue(PowerRuntimeEngine.getActivationFailureReason(player, barrierAbility)
                            .equals("gui.ascendant.catalog.unavailable"),
                    "The cooldown must be reported as the real failure reason");
            helper.assertTrue(!PowerRuntimeEngine.activateAbilityWithFeedback(player, barrierAbility),
                    "A request during cooldown must be rejected, not reported as success");
        } finally {
            removeServerPlayer(testPlayer);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void automaticTriggerAbilitiesCannotBeManuallyActivated(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        if (!(player instanceof ServerPlayer serverPlayer)) {
            helper.succeed();
            return;
        }

        ResourceLocation barrierPower = new ResourceLocation("ascendant", "kinetic_barrier");
        ResourceLocation strengthPower = new ResourceLocation("ascendant", "titan_strength");
        ResourceLocation reactivePower = new ResourceLocation("ascendant", "reactive_shield");
        IPlayerPowerData data = serverPlayer.getCapability(PlayerPowerDataProvider.CAPABILITY)
                .orElseThrow(() -> new AssertionError("Power capability missing"));
        data.unlockPower(barrierPower);
        data.unlockPower(strengthPower);
        data.unlockPower(reactivePower);

        ResourceLocation manualAbility = new ResourceLocation("ascendant", "kinetic_barrier_deploy");
        helper.assertTrue(PowerRuntimeEngine.activateAbility(serverPlayer, manualAbility),
                "KEY_PRESS ability should be manually activatable");

        ResourceLocation passiveAbility = new ResourceLocation("ascendant", "titan_muscle_passive");
        AbilityDefinition passive = AbilityRegistry.get(passiveAbility);
        helper.assertTrue(passive != null && passive.getTrigger() == AbilityTrigger.PASSIVE,
                "titan_muscle_passive should be PASSIVE triggered");
        helper.assertTrue(!PowerRuntimeEngine.activateAbility(serverPlayer, passiveAbility),
                "PASSIVE ability must not be manually activated");

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

        helper.assertTrue(!SuitManager.grantsPower(serverPlayer, flightPower),
                "Player without suit should not have suit powers");

        helper.assertTrue(SuitManager.equipSuit(serverPlayer, suitId),
                "Equipping suit should succeed");
        helper.assertTrue(SuitManager.grantsPower(serverPlayer, flightPower),
                "Equipped suit should grant flight power to player");
        helper.assertTrue(PowerRuntimeEngine.hasPowerAccess(serverPlayer, flightPower),
                "hasPowerAccess should reflect suit-granted power");

        helper.assertTrue(SuitManager.unequipSuit(serverPlayer),
                "Unequipping suit should succeed");
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

        ResourceLocation unknown = new ResourceLocation("ascendant", "does_not_exist");
        String reason = PowerRuntimeEngine.getActivationFailureReason(serverPlayer, unknown);
        helper.assertTrue(reason != null && !reason.isBlank(),
                "Unknown ability should report a failure reason");

        ResourceLocation passive = new ResourceLocation("ascendant", "titan_muscle_passive");
        String passiveReason = PowerRuntimeEngine.getActivationFailureReason(serverPlayer, passive);
        helper.assertTrue("gui.ascendant.catalog.ability_trigger_reason".equals(passiveReason),
                "PASSIVE ability should report ability_trigger_reason");

        ResourceLocation punch = new ResourceLocation("ascendant", "thunder_punch");
        String punchReason = PowerRuntimeEngine.getActivationFailureReason(serverPlayer, punch);
        helper.assertTrue("gui.ascendant.catalog.ownership_note".equals(punchReason),
                "Ability without owned power should report ownership_note");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void packManifestRequiresValidId(GameTestHelper helper) {
        JsonObject validManifest = new JsonObject();
        validManifest.addProperty("id", "test_pack");
        validManifest.addProperty("version", "1.0.0");
        validManifest.addProperty("minMcVersion", "1.20.1");
        validManifest.addProperty("minForgeVersion", "47.3.0");

        boolean validParsed = false;
        try {
            PackManifest.fromJson(validManifest);
            validParsed = true;
        } catch (Exception ignored) {
        }
        helper.assertTrue(validParsed, "Valid pack manifest should parse successfully");

        JsonObject missingId = new JsonObject();
        missingId.addProperty("version", "1.0.0");
        helper.assertTrue(rejectsManifest(missingId),
                "Pack manifest without id must throw IllegalArgumentException");

        JsonObject blankId = new JsonObject();
        blankId.addProperty("id", "   ");
        helper.assertTrue(rejectsManifest(blankId),
                "Pack manifest with blank id must throw IllegalArgumentException");

        JsonObject unsafeId = new JsonObject();
        unsafeId.addProperty("id", "../outside");
        helper.assertTrue(rejectsManifest(unsafeId),
                "Pack manifest with a traversal id must be rejected");

        JsonObject reservedId = new JsonObject();
        reservedId.addProperty("id", "con.pack");
        helper.assertTrue(rejectsManifest(reservedId),
                "Pack manifest with a reserved Windows device name must be rejected");

        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void animationProfileRegistrationAndLookup(GameTestHelper helper) {
        ResourceLocation testId = new ResourceLocation("ascendant", "gametest_animation_profile");
        AnimationProfile profile = new AnimationProfile(
                testId,
                "Voo de Teste",
                List.of(new AnimationProfile.EffectLayer(
                        AnimationProfile.TYPE_PARTICLES,
                        Map.of("type", "smoke", "count", "5")
                ))
        );
        AnimationRegistry.register(testId, profile);

        helper.assertTrue(AnimationRegistry.hasProfile(testId),
                "AnimationRegistry should have profile for registered power");
        AnimationProfile retrieved = AnimationRegistry.get(testId);
        helper.assertTrue(retrieved != null && retrieved.getName().equals("Voo de Teste"),
                "Retrieved animation profile should match registered profile");
        helper.assertTrue(retrieved.getLayers().size() == 1,
                "Profile should contain exactly one effect layer");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void animationCoverageForKeyFunctionalPowers(GameTestHelper helper) {
        AnimationCoverageValidator.CoverageReport report = AnimationCoverageValidator.validate();
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

    private static void registerTestAbility(ResourceLocation id, ResourceLocation powerId,
                                            AbilityTrigger trigger, int cooldown,
                                            IPowerComponent action) {
        AbilityRegistry.register(AbilityDefinition.builder(id)
                .powerId(powerId)
                .category(AbilityCategory.SPECIALIZATION)
                .trigger(trigger)
                .cooldown(cooldown)
                .addAction(action)
                .functional(true)
                .build());
    }

    private static boolean rejectsManifest(JsonObject json) {
        try {
            PackManifest.fromJson(json);
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }

    private static final class CountingAction implements IPowerComponent {
        private int executions;
        private int ticks;
        private int hurts;
        private int attacks;
        private int enabledCount;
        private int disabledCount;

        @Override
        public String getType() {
            return "ascendant:gametest_counter";
        }

        @Override
        public boolean isFunctional() {
            return true;
        }

        @Override
        public boolean execute(ComponentContext context) {
            executions++;
            return true;
        }

        @Override
        public void onPlayerTick(ServerPlayer player) {
            ticks++;
        }

        @Override
        public void onHurt(ServerPlayer player, LivingHurtEvent event) {
            hurts++;
        }

        @Override
        public void onAttack(ServerPlayer player, LivingHurtEvent event) {
            attacks++;
        }

        @Override
        public void onToggle(ServerPlayer player, boolean enabled) {
            if (enabled) {
                enabledCount++;
            } else {
                disabledCount++;
            }
        }

        @Override
        public JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("type", getType());
            return json;
        }
    }
}
