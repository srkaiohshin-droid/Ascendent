package br.com.ascendant.test;

import br.com.ascendant.animation.AnimationCoverageValidator;
import br.com.ascendant.animation.AnimationProfile;
import br.com.ascendant.animation.AnimationRegistry;
import br.com.ascendant.catalog.CatalogEntryState;
import br.com.ascendant.catalog.CatalogInteraction;
import br.com.ascendant.core.AbilityCategory;
import br.com.ascendant.core.AbilityTrigger;
import br.com.ascendant.flight.FlightController;
import br.com.ascendant.flight.FlightState;
import br.com.ascendant.lore.LoreAccess;
import br.com.ascendant.lore.LoreEntry;
import br.com.ascendant.lore.LoreRegistry;
import br.com.ascendant.lore.LoreUnlock;
import br.com.ascendant.pack.PackManifest;
import br.com.ascendant.pack.PackSecurity;
import br.com.ascendant.power.AbilityDefinition;
import br.com.ascendant.power.AbilityRegistry;
import br.com.ascendant.power.PowerDefinition;
import br.com.ascendant.power.PowerRegistry;
import br.com.ascendant.power.component.ComponentContext;
import br.com.ascendant.power.component.DashComponent;
import br.com.ascendant.power.component.ComponentRegistry;
import br.com.ascendant.power.component.EnergyBeamComponent;
import br.com.ascendant.power.component.GravityWellComponent;
import br.com.ascendant.power.component.ShockwaveComponent;
import br.com.ascendant.power.component.StunComponent;
import br.com.ascendant.power.component.IPowerComponent;
import br.com.ascendant.power.component.InvisibilityComponent;
import br.com.ascendant.power.component.LevitationComponent;
import br.com.ascendant.power.component.PhaseComponent;
import br.com.ascendant.power.component.SummonEntityComponent;
import br.com.ascendant.power.component.VortexActionComponent;
import br.com.ascendant.power.component.HealActionComponent;
import br.com.ascendant.power.component.AttributeModifierComponent;
import br.com.ascendant.power.component.FireWardComponent;
import br.com.ascendant.power.component.NightVisionComponent;
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
import net.minecraft.world.effect.MobEffectInstance;
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
        helper.assertTrue(PowerRegistry.getFunctionalCount() == 121,
                "PowerRegistry must contain exactly 121 functional powers, found: " + PowerRegistry.getFunctionalCount());
        helper.assertTrue(PowerRegistry.size() - PowerRegistry.getFunctionalCount() == 379,
                "PowerRegistry must retain 379 honest templates");
        helper.assertTrue(report.functionalPowersWithoutExecutableComponent() == 0,
                "Every functional power must expose an executable component, found: "
                        + report.functionalPowersWithoutExecutableComponent());
        helper.assertTrue(PowerRegistry.getAll().stream()
                        .filter(power -> !power.isFunctional())
                        .allMatch(power -> power.getComponents().stream()
                                .allMatch(component -> component instanceof br.com.ascendant.power.component.TemplateComponent)),
                "Non-functional powers may only carry honest template components");
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
        PowerDefinition speedsterToggle = PowerRegistry.get(new ResourceLocation("ascendant", "kinetic_sprint"));
        PowerDefinition template = PowerRegistry.get(new ResourceLocation("ascendant", "orbital_ascent"));
        var activeAbility = AbilityRegistry.get(new ResourceLocation("ascendant", "thunder_punch"));
        var passiveAbility = AbilityRegistry.get(new ResourceLocation("ascendant", "night_vision_passive"));
        var toggleAbility = AbilityRegistry.get(new ResourceLocation("ascendant", "infrared_sweep"));

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
        helper.assertTrue(CatalogInteraction.forPower(speedsterToggle).type()
                        == CatalogInteraction.ActionType.TOGGLE_POWER,
                "Super-speed must be exposed as a real toggle instead of a hidden passive");
        helper.assertTrue(CatalogInteraction.forPower(template).type() == CatalogInteraction.ActionType.NONE,
                "Template powers must be visibly non-actionable");
        helper.assertTrue(CatalogInteraction.forAbility(activeAbility).type()
                        == CatalogInteraction.ActionType.ACTIVATE_ABILITY
                && CatalogInteraction.forAbility(passiveAbility).type() == CatalogInteraction.ActionType.NONE
                && CatalogInteraction.forAbility(toggleAbility).type()
                == CatalogInteraction.ActionType.TOGGLE_ABILITY,
        "Manual and toggle abilities map to their distinct actions; automatic triggers remain non-manual");
        helper.assertTrue(CatalogInteraction.radialSector(0.0D, -100.0D, 8, 12.0D) == 0
                        && CatalogInteraction.radialSector(100.0D, 0.0D, 8, 12.0D) == 2
                        && CatalogInteraction.radialSector(0.0D, 0.0D, 8, 12.0D) == -1
                        && CatalogInteraction.radialSector(100.0D, 0.0D, 9, 12.0D) == -1,
                "Radial selection must map clockwise sectors and reject the dead zone and invalid counts");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void catalogEntryStateExplainsRefusalReasons(GameTestHelper helper) {
        CatalogEntryState.ContentKind power = CatalogEntryState.ContentKind.POWER;
        CatalogEntryState.ContentKind ability = CatalogEntryState.ContentKind.ABILITY;

        CatalogEntryState.Assessment ready = CatalogEntryState.assess(new CatalogEntryState.Facts(
                power, true, true, true, false, 0, true, false, false));
        helper.assertTrue(ready.state() == CatalogEntryState.State.READY && ready.actionable()
                        && !ready.hasReason(),
                "An owned manual power with no cooldown must be ready and actionable");

        CatalogEntryState.Assessment template = CatalogEntryState.assess(new CatalogEntryState.Facts(
                power, false, true, true, false, 0, false, false, false));
        helper.assertTrue(template.state() == CatalogEntryState.State.TEMPLATE && !template.actionable()
                        && "gui.ascendant.catalog.template_reason".equals(template.reasonKey()),
                "Template entries must be refused with the template reason instead of pretending to work");

        CatalogEntryState.Assessment loading = CatalogEntryState.assess(new CatalogEntryState.Facts(
                power, true, false, true, false, 0, true, false, false));
        helper.assertTrue(loading.state() == CatalogEntryState.State.LOADING && !loading.actionable()
                        && "gui.ascendant.catalog.loading_state".equals(loading.reasonKey()),
                "Entries must not be usable before authoritative state arrives");

        CatalogEntryState.Assessment locked = CatalogEntryState.assess(new CatalogEntryState.Facts(
                power, true, true, false, false, 0, true, false, false));
        helper.assertTrue(locked.state() == CatalogEntryState.State.LOCKED && !locked.actionable()
                        && "gui.ascendant.catalog.not_owned".equals(locked.reasonKey()),
                "Unowned powers must report the ownership requirement");

        CatalogEntryState.Assessment cooling = CatalogEntryState.assess(new CatalogEntryState.Facts(
                ability, true, true, true, false, 40, true, false, false));
        helper.assertTrue(cooling.state() == CatalogEntryState.State.COOLDOWN && !cooling.actionable()
                        && cooling.reasonArg() == 40
                        && "gui.ascendant.catalog.cooldown_remaining".equals(cooling.reasonKey()),
                "Cooldowns must expose the remaining ticks in the refusal reason");

        CatalogEntryState.Assessment active = CatalogEntryState.assess(new CatalogEntryState.Facts(
                power, true, true, true, true, 120, true, false, false));
        helper.assertTrue(active.state() == CatalogEntryState.State.ACTIVE && active.actionable()
                        && active.reasonKey() == null,
                "An enabled toggle must remain usable even while its next activation cools down");

        CatalogEntryState.Assessment passive = CatalogEntryState.assess(new CatalogEntryState.Facts(
                power, true, true, true, false, 0, false, false, false));
        CatalogEntryState.Assessment automatic = CatalogEntryState.assess(new CatalogEntryState.Facts(
                ability, true, true, true, false, 0, false, false, false));
        helper.assertTrue(passive.state() == CatalogEntryState.State.PASSIVE
                        && passive.reasonKey().equals("gui.ascendant.catalog.power_type_reason")
                        && automatic.state() == CatalogEntryState.State.AUTOMATIC
                        && automatic.reasonKey().equals("gui.ascendant.catalog.ability_trigger_reason"),
                "Passive powers and automatic abilities must be distinguished from manual actions");

        CatalogEntryState.Assessment equipped = CatalogEntryState.assess(new CatalogEntryState.Facts(
                CatalogEntryState.ContentKind.SUIT, true, true, true, false, 0, true, true, false));
        CatalogEntryState.Assessment available = CatalogEntryState.assess(new CatalogEntryState.Facts(
                CatalogEntryState.ContentKind.SUIT, true, true, true, false, 0, true, false, false));
        helper.assertTrue(equipped.state() == CatalogEntryState.State.EQUIPPED && !equipped.actionable()
                        && available.state() == CatalogEntryState.State.AVAILABLE && available.actionable(),
                "Suit cards must distinguish the equipped loadout from an available one");

        CatalogEntryState.Assessment metadata = CatalogEntryState.assess(new CatalogEntryState.Facts(
                CatalogEntryState.ContentKind.PACK, true, true, true, false, 0, false, false, true));
        helper.assertTrue(metadata.state() == CatalogEntryState.State.METADATA && !metadata.actionable()
                        && metadata.reasonKey().equals("gui.ascendant.catalog.pack_not_actionable"),
                "Pack cards must stay metadata-only");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void loreRegistryUnlocksAndTranslationCoverage(GameTestHelper helper) {
        helper.assertTrue(LoreRegistry.size() >= 12,
                "The native lore collection must stay a substantial archive, found: " + LoreRegistry.size());

        LoreEntry protocol = LoreRegistry.get(new ResourceLocation("ascendant", "ascendant_protocol"));
        LoreEntry firstFlight = LoreRegistry.get(new ResourceLocation("ascendant", "first_flight"));
        LoreEntry speedsterBrief = LoreRegistry.get(
                new ResourceLocation("ascendant", "kinetic_dynamics_brief"));
        helper.assertTrue(protocol != null && firstFlight != null && speedsterBrief != null,
                "Native lore ids must be registered");

        LoreAccess none = LoreAccess.none();
        helper.assertTrue(protocol.isUnlocked(none),
                "Always entries must be readable without any power");
        helper.assertTrue(!firstFlight.isUnlocked(none),
                "Power-gated lore must stay locked without the power");
        helper.assertTrue(!speedsterBrief.isUnlocked(none),
                "Suit-gated lore must stay locked without the suit");

        LoreAccess flightOnly = new LoreAccess() {
            @Override
            public boolean hasPower(ResourceLocation powerId) {
                return new ResourceLocation("ascendant", "flight").equals(powerId);
            }

            @Override
            public boolean hasAbility(ResourceLocation abilityId) {
                return false;
            }

            @Override
            public boolean hasSuit(ResourceLocation suitId) {
                return false;
            }
        };
        LoreAccess speedsterSuit = new LoreAccess() {
            @Override
            public boolean hasPower(ResourceLocation powerId) {
                return false;
            }

            @Override
            public boolean hasAbility(ResourceLocation abilityId) {
                return false;
            }

            @Override
            public boolean hasSuit(ResourceLocation suitId) {
                return new ResourceLocation("ascendant", "speedster_mk1").equals(suitId);
            }
        };
        helper.assertTrue(firstFlight.isUnlocked(flightOnly)
                        && !speedsterBrief.isUnlocked(flightOnly)
                        && speedsterBrief.isUnlocked(speedsterSuit)
                        && !firstFlight.isUnlocked(speedsterSuit),
                "Lore unlocks must follow the exact power or suit requirement");

        for (LoreEntry entry : LoreRegistry.getAll()) {
            helper.assertTrue(!entry.getTitleKey().isBlank() && !entry.getSummaryKey().isBlank()
                            && !entry.getBodyKey().isBlank(),
                    "Lore entry " + entry.id() + " must derive non-empty translation keys");
            for (ResourceLocation powerId : entry.relatedPowers()) {
                helper.assertTrue(PowerRegistry.contains(powerId),
                        "Lore " + entry.id() + " references unknown power " + powerId);
            }
            for (ResourceLocation suitId : entry.relatedSuits()) {
                helper.assertTrue(SuitRegistry.contains(suitId),
                        "Lore " + entry.id() + " references unknown suit " + suitId);
            }
            if (entry.unlock().type() == LoreUnlock.Type.POWER) {
                helper.assertTrue(PowerRegistry.contains(entry.unlock().id()),
                        "Lore " + entry.id() + " unlocks through an unknown power");
            }
            if (entry.unlock().type() == LoreUnlock.Type.ABILITY) {
                helper.assertTrue(AbilityRegistry.contains(entry.unlock().id()),
                        "Lore " + entry.id() + " unlocks through an unknown ability");
            }
            if (entry.unlock().type() == LoreUnlock.Type.SUIT) {
                helper.assertTrue(SuitRegistry.contains(entry.unlock().id()),
                        "Lore " + entry.id() + " unlocks through an unknown suit");
            }
        }

        ContentValidator.ValidationReport report = ContentValidator.validate();
        helper.assertTrue(report.isValid(), "Content validation must stay error-free: " + report.errors());
        helper.assertTrue(report.totalLore() == LoreRegistry.size(),
                "The validation report must count every registered lore entry");
        helper.assertTrue(report.translations().enUsAvailable() && report.translations().ptBrAvailable(),
                "Translation resources must be readable for coverage validation");
        helper.assertTrue(report.translations().curatedKeys() >= 100,
                "Curated coverage must include suits, their granted content and lore, found: "
                        + report.translations().curatedKeys());
        helper.assertTrue(report.translations().missingCuratedKeys().isEmpty(),
                "Curated content must be fully localized: " + report.translations().missingCuratedKeys());
        helper.assertTrue(report.translations().missingPowerDescriptions() > 0,
                "The report must keep disclosing catalog descriptions that are still missing");
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
    public static void convertedCatalogPowersExposeRealMechanics(GameTestHelper helper) {
        assertPowerComponent(helper, powerId("tachyon_pulse"), EnergyBeamComponent.TYPE);
        assertPowerComponent(helper, powerId("plasma_cutter"), EnergyBeamComponent.TYPE);
        assertPowerComponent(helper, powerId("radiant_nova"), ShockwaveComponent.TYPE);
        assertPowerComponent(helper, powerId("event_horizon_pulse"), GravityWellComponent.TYPE);
        assertPowerComponent(helper, powerId("taser_touch"), StunComponent.TYPE);
        assertPowerComponent(helper, powerId("concussion_flash"), StunComponent.TYPE);
        assertPowerComponent(helper, powerId("micro_black_hole"), GravityWellComponent.TYPE);
        assertPowerComponent(helper, powerId("zero_gravity_field"), GravityWellComponent.TYPE);
        helper.assertTrue(PowerRegistry.get(powerId("micro_black_hole")).getType()
                        == br.com.ascendant.core.PowerType.SUSTAINED
                        && PowerRegistry.get(powerId("zero_gravity_field")).getType()
                        == br.com.ascendant.core.PowerType.SUSTAINED,
                "Sustained gravity fields must use the sustained runtime so they can be toggled off");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void energyBeamHitsVisibleTargetsAndStopsAtBlocks(GameTestHelper helper) {
        TestServerPlayer testPlayer = createServerPlayer(helper);
        ServerPlayer serverPlayer = testPlayer.player();
        BlockPos spawn = helper.absolutePos(new BlockPos(1, 1, 1));
        serverPlayer.moveTo(spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D, 0.0F, 0.0F);
        serverPlayer.setYRot(0.0F);
        serverPlayer.setXRot(0.0F);

        ServerLevel level = serverPlayer.serverLevel();
        Mob visible = EntityType.ZOMBIE.create(level);
        Mob shielded = EntityType.ZOMBIE.create(level);
        helper.assertTrue(visible != null && shielded != null, "Beam test requires vanilla zombies");
        visible.moveTo(serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ() + 2.0D, 0.0F, 0.0F);
        shielded.moveTo(serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ() + 4.5D, 0.0F, 0.0F);
        level.addFreshEntity(visible);
        level.addFreshEntity(shielded);

        BlockPos wall = BlockPos.containing(serverPlayer.getX(), serverPlayer.getY() + 1.0D,
                serverPlayer.getZ() + 3.0D);
        level.setBlock(wall, Blocks.STONE.defaultBlockState(), 3);

        EnergyBeamComponent beam = new EnergyBeamComponent(24.0D, 6.0F, 4, "magic");
        float visibleBefore = visible.getHealth();
        float shieldedBefore = shielded.getHealth();
        helper.assertTrue(beam.execute(ComponentContext.of(serverPlayer, powerId("tachyon_pulse"))),
                "The beam should report a hit when a visible target is in front of it");
        helper.assertTrue(visible.getHealth() < visibleBefore,
                "The beam must damage the visible target");
        helper.assertTrue(Math.abs(shielded.getHealth() - shieldedBefore) < 1.0E-4F,
                "The beam must stop at the first solid block and never reach targets behind it");
        helper.assertTrue(ComponentRegistry.deserialize(beam.toJson()).isFunctional()
                        && ComponentRegistry.deserialize(beam.toJson()).getType().equals(EnergyBeamComponent.TYPE),
                "The beam component must round-trip through the component registry");

        visible.discard();
        shielded.discard();
        level.removeBlock(wall, false);
        serverPlayer.setXRot(-90.0F);
        helper.assertTrue(!beam.execute(ComponentContext.of(serverPlayer, powerId("tachyon_pulse"))),
                "A beam with no target must not report success, so whiffs cannot start cooldowns");
        removeServerPlayer(testPlayer);
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void shockwaveDamagesAndKnocksBackOnlyVisibleTargets(GameTestHelper helper) {
        TestServerPlayer testPlayer = createServerPlayer(helper);
        ServerPlayer serverPlayer = testPlayer.player();
        BlockPos spawn = helper.absolutePos(new BlockPos(1, 1, 1));
        serverPlayer.moveTo(spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D, 0.0F, 0.0F);
        serverPlayer.setYRot(0.0F);
        serverPlayer.setXRot(0.0F);

        ServerLevel level = serverPlayer.serverLevel();
        Mob exposed = EntityType.ZOMBIE.create(level);
        Mob behindWall = EntityType.ZOMBIE.create(level);
        helper.assertTrue(exposed != null && behindWall != null, "Shockwave test requires vanilla zombies");
        exposed.moveTo(serverPlayer.getX() + 2.0D, serverPlayer.getY(), serverPlayer.getZ() + 1.0D, 0.0F, 0.0F);
        behindWall.moveTo(serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ() + 4.0D, 0.0F, 0.0F);
        level.addFreshEntity(exposed);
        level.addFreshEntity(behindWall);

        BlockPos wallBase = BlockPos.containing(serverPlayer.getX(), serverPlayer.getY(),
                serverPlayer.getZ() + 3.0D);
        for (int dy = 0; dy <= 2; dy++) {
            level.setBlock(wallBase.offset(0, dy, 0), Blocks.STONE.defaultBlockState(), 3);
        }

        ShockwaveComponent shockwave = new ShockwaveComponent(6.0D, 8.0F, 1.2D, 8);
        float exposedBefore = exposed.getHealth();
        float hiddenBefore = behindWall.getHealth();
        helper.assertTrue(shockwave.execute(ComponentContext.of(serverPlayer, powerId("radiant_nova"))),
                "The shockwave should report a hit when a visible target is in range");
        helper.assertTrue(exposed.getHealth() < exposedBefore,
                "The shockwave must damage visible targets inside its radius");
        helper.assertTrue(exposed.getDeltaMovement().lengthSqr() > 0.0D,
                "The shockwave must apply real knockback to visible targets");
        helper.assertTrue(Math.abs(behindWall.getHealth() - hiddenBefore) < 1.0E-4F,
                "The shockwave must not damage targets sealed behind terrain");
        helper.assertTrue(behindWall.getDeltaMovement().lengthSqr() < 1.0E-6D,
                "The shockwave must not push targets sealed behind terrain");

        for (int dy = 0; dy <= 2; dy++) {
            level.removeBlock(wallBase.offset(0, dy, 0), false);
        }
        removeServerPlayer(testPlayer);
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void stunAppliesBoundedEffectsAndClearsAggro(GameTestHelper helper) {
        TestServerPlayer testPlayer = createServerPlayer(helper);
        ServerPlayer serverPlayer = testPlayer.player();
        BlockPos spawn = helper.absolutePos(new BlockPos(1, 1, 1));
        serverPlayer.moveTo(spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D, 0.0F, 0.0F);

        ServerLevel level = serverPlayer.serverLevel();
        Mob target = EntityType.ZOMBIE.create(level);
        helper.assertTrue(target != null, "Stun test requires a vanilla zombie");
        target.moveTo(serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ() + 2.0D, 0.0F, 0.0F);
        level.addFreshEntity(target);
        target.setTarget(serverPlayer);

        StunComponent stun = new StunComponent(5.0D, 40, 4);
        helper.assertTrue(stun.execute(ComponentContext.of(serverPlayer, powerId("taser_touch"))),
                "Stun should report success when a visible hostile is in range");
        MobEffectInstance slow = target.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        helper.assertTrue(slow != null && slow.getDuration() == 40,
                "Stun must apply the configured bounded duration");
        helper.assertTrue(target.hasEffect(MobEffects.WEAKNESS)
                        && target.hasEffect(MobEffects.DIG_SLOWDOWN)
                        && target.hasEffect(MobEffects.CONFUSION),
                "Stun must apply observable vanilla effects instead of a hidden frozen state");
        helper.assertTrue(target.getTarget() == null,
                "Stun must clear mob aggression so the effect is mechanically real");
        helper.assertTrue(new StunComponent(99.0D, 99_999, 999).getDurationTicks() == 240,
                "Stun duration must be clamped to the documented maximum");
        target.discard();
        removeServerPlayer(testPlayer);
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void gravityWellPullsAndPushesWithClampedVelocity(GameTestHelper helper) {
        TestServerPlayer testPlayer = createServerPlayer(helper);
        ServerPlayer serverPlayer = testPlayer.player();
        BlockPos spawn = helper.absolutePos(new BlockPos(1, 1, 1));
        serverPlayer.moveTo(spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D, 0.0F, 0.0F);

        ServerLevel level = serverPlayer.serverLevel();
        Mob target = EntityType.ZOMBIE.create(level);
        helper.assertTrue(target != null, "Gravity test requires a vanilla zombie");
        target.moveTo(serverPlayer.getX() + 3.0D, serverPlayer.getY(), serverPlayer.getZ(), 0.0F, 0.0F);
        level.addFreshEntity(target);

        Vec3 toward = new Vec3(serverPlayer.getX() - target.getX(), 0.0D,
                serverPlayer.getZ() - target.getZ()).normalize();
        GravityWellComponent pull = new GravityWellComponent(8.0D, 0.2D,
                GravityWellComponent.MODE_PULL, 8);
        helper.assertTrue(pull.execute(ComponentContext.of(serverPlayer, powerId("micro_black_hole"))),
                "The pull field should affect a visible hostile");
        helper.assertTrue(target.getDeltaMovement().dot(toward) > 0.0D,
                "The pull field must move targets toward the caster");
        helper.assertTrue(target.getDeltaMovement().length() <= 1.1D + 1.0E-6D,
                "The pull field must clamp velocity so it cannot launch entities arbitrarily");

        target.setDeltaMovement(Vec3.ZERO);
        GravityWellComponent push = new GravityWellComponent(8.0D, 0.2D,
                GravityWellComponent.MODE_PUSH, 8);
        helper.assertTrue(push.execute(ComponentContext.of(serverPlayer, powerId("zero_gravity_field"))),
                "The push field should affect a visible hostile");
        helper.assertTrue(target.getDeltaMovement().dot(toward.scale(-1.0D)) > 0.0D,
                "The push field must move targets away from the caster");
        helper.assertTrue(target.getDeltaMovement().length() <= 1.4D + 1.0E-6D,
                "The push field must clamp velocity so it cannot launch entities arbitrarily");

        IPlayerPowerData data = serverPlayer.getCapability(PlayerPowerDataProvider.CAPABILITY)
                .orElseThrow(() -> new AssertionError("Power capability missing"));
        data.unlockPower(powerId("micro_black_hole"));
        data.unlockPower(powerId("zero_gravity_field"));
        helper.assertTrue(PowerRuntimeEngine.togglePower(serverPlayer, powerId("micro_black_hole"), true)
                        && PowerRuntimeEngine.isPowerToggled(serverPlayer, powerId("micro_black_hole")),
                "A sustained gravity field must toggle on through the server runtime");
        helper.assertTrue(PowerRuntimeEngine.togglePower(serverPlayer, powerId("micro_black_hole"), false)
                        && !PowerRuntimeEngine.isPowerToggled(serverPlayer, powerId("micro_black_hole")),
                "A sustained gravity field must toggle off cleanly");
        helper.assertTrue(CatalogInteraction.forPower(PowerRegistry.get(powerId("micro_black_hole"))).type()
                        == CatalogInteraction.ActionType.TOGGLE_POWER,
                "The catalog must expose the gravity field through the real toggle path");

        target.discard();
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
        helper.assertTrue(PowerRuntimeEngine.togglePower(serverPlayer, speedId, true),
                "Suit-granted super-speed should toggle on");
        double boostedMovementSpeed = serverPlayer.getAttributeValue(Attributes.MOVEMENT_SPEED);
        helper.assertTrue(boostedMovementSpeed > baseMovementSpeed,
                "Toggled super-speed should apply its attribute modifier");
        helper.assertTrue(br.com.ascendant.speedster.SpeedsterController.isModeActive(serverPlayer),
                "Toggling super-speed must activate the movement controller");
        helper.assertTrue(SuitManager.unequipSuit(serverPlayer), "Test suit should unequip");
        helper.assertTrue(!PowerRuntimeEngine.isPowerToggled(serverPlayer, infraredId),
                "Removing the final access source must clear the toggle");
        helper.assertTrue(!serverPlayer.hasEffect(MobEffects.NIGHT_VISION),
                "Removing the final access source must clean the toggle-owned effect");
        helper.assertTrue(Math.abs(serverPlayer.getAttributeValue(Attributes.MOVEMENT_SPEED) - baseMovementSpeed) < 1.0E-6D,
                "Removing the final access source must remove its transient attribute modifier");

        helper.assertTrue(SuitManager.equipSuit(serverPlayer, suitId), "Test suit should re-equip");
        helper.assertTrue(PowerRuntimeEngine.togglePower(serverPlayer, speedId, true),
                "Super-speed should toggle on again after re-equipping");
        helper.assertTrue(Math.abs(serverPlayer.getAttributeValue(Attributes.MOVEMENT_SPEED) - boostedMovementSpeed) < 1.0E-6D,
                "Re-toggling must not duplicate the attribute modifier");
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

    private static ResourceLocation powerId(String path) {
        return new ResourceLocation("ascendant", path);
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
    public static void abilityTriggerAuthorizationAndSuitGrant(GameTestHelper helper) {
        TestServerPlayer testPlayer = createServerPlayer(helper);
        ServerPlayer serverPlayer = testPlayer.player();
        ResourceLocation punchId = new ResourceLocation("ascendant", "first_aid_touch");
        ResourceLocation passiveId = new ResourceLocation("ascendant", "night_vision_passive");
        ResourceLocation templateId = new ResourceLocation("ascendant", "offensive_tech_11");
        ResourceLocation suitId = new ResourceLocation("ascendant", "gametest_ability_grant");

        helper.assertTrue(PowerRuntimeEngine.activateAbilityWithResult(serverPlayer, punchId)
                        == PowerRuntimeEngine.AbilityResult.NOT_AUTHORIZED,
                "An ability must not execute without its power or a direct suit grant");
        helper.assertTrue(PowerRuntimeEngine.activateAbilityWithResult(serverPlayer, passiveId)
                        == PowerRuntimeEngine.AbilityResult.WRONG_TRIGGER,
                "Passive abilities cannot be manually activated");
        helper.assertTrue(PowerRuntimeEngine.activateAbilityWithResult(serverPlayer, templateId)
                        == PowerRuntimeEngine.AbilityResult.NOT_FUNCTIONAL,
                "Templates must be rejected before trying to execute their actions");

        SuitRegistry.register(SuitDefinition.builder(suitId).addAbility(punchId).build());
        helper.assertTrue(SuitManager.equipSuit(serverPlayer, suitId)
                        && !PowerRuntimeEngine.hasPowerAccess(serverPlayer,
                        new ResourceLocation("ascendant", "healing_touch"))
                        && PowerRuntimeEngine.hasAbilityAccess(serverPlayer, AbilityRegistry.get(punchId)),
                "A suit may grant an ability directly without implicitly granting its associated power");
        serverPlayer.setHealth(4.0F);
        helper.assertTrue(PowerRuntimeEngine.activateAbilityWithResult(serverPlayer, punchId)
                        == PowerRuntimeEngine.AbilityResult.ACTIVATED
                        && PowerRuntimeEngine.isOnCooldown(serverPlayer, punchId),
                "A directly suit-granted ability should execute and set its own cooldown");
        helper.assertTrue(SuitManager.unequipSuit(serverPlayer)
                        && !PowerRuntimeEngine.hasAbilityAccess(serverPlayer, AbilityRegistry.get(punchId))
                        && !PowerRuntimeEngine.isOnCooldown(serverPlayer, punchId),
                "Losing the final suit grant should revoke the ability and clear its cooldown");

        ResourceLocation visionSuitId = new ResourceLocation("ascendant", "gametest_vision_grant");
        ResourceLocation visionAbilityId = new ResourceLocation("ascendant", "infrared_sweep");
        SuitRegistry.register(SuitDefinition.builder(visionSuitId).addAbility(visionAbilityId).build());
        serverPlayer.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 200, 0));
        helper.assertTrue(SuitManager.equipSuit(serverPlayer, visionSuitId)
                        && PowerRuntimeEngine.toggleAbility(serverPlayer, visionAbilityId, true)
                        == PowerRuntimeEngine.AbilityResult.ENABLED
                        && SuitManager.unequipSuit(serverPlayer)
                        && serverPlayer.hasEffect(MobEffects.NIGHT_VISION),
                "Disabling a suit-granted toggle must preserve an effect that was already supplied externally");
        serverPlayer.removeEffect(MobEffects.NIGHT_VISION);
        helper.assertTrue(SuitManager.equipSuit(serverPlayer, visionSuitId)
                        && PowerRuntimeEngine.toggleAbility(serverPlayer, visionAbilityId, true)
                        == PowerRuntimeEngine.AbilityResult.ENABLED
                        && SuitManager.unequipSuit(serverPlayer)
                        && !serverPlayer.hasEffect(MobEffects.NIGHT_VISION),
                "Disabling a suit-granted toggle must clean its own managed effect");
        removeServerPlayer(testPlayer);
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void abilityTickHurtAndAttackTriggersExecute(GameTestHelper helper) {
        TestServerPlayer testPlayer = createServerPlayer(helper);
        ServerPlayer serverPlayer = testPlayer.player();
        ResourceLocation tickPower = new ResourceLocation("ascendant", "sonar_pulse");
        ResourceLocation tickAbilityId = new ResourceLocation("ascendant", "gametest_tick_ability");
        ResourceLocation attackAbilityId = new ResourceLocation("ascendant", "gametest_attack_ability");
        ResourceLocation lowHealthPower = new ResourceLocation("ascendant", "emergency_teleport");
        IPlayerPowerData data = serverPlayer.getCapability(PlayerPowerDataProvider.CAPABILITY)
                .orElseThrow(() -> new AssertionError("Power capability missing"));
        data.unlockPower(tickPower);
        data.unlockPower(lowHealthPower);
        data.unlockPower(new ResourceLocation("ascendant", "fire_immunity"));
        AbilityRegistry.register(AbilityDefinition.builder(tickAbilityId).powerId(tickPower)
                .category(AbilityCategory.PERCEPTION).trigger(AbilityTrigger.TICK)
                .addAction(new NightVisionComponent(100)).functional(true).build());
        AbilityRegistry.register(AbilityDefinition.builder(attackAbilityId)
                .powerId(new ResourceLocation("ascendant", "titan_strength"))
                .category(AbilityCategory.OFFENSIVE).trigger(AbilityTrigger.ATTACK_HIT)
                .cooldown(5).addAction(new HealActionComponent(4.0F, true)).functional(true).build());
        data.unlockPower(new ResourceLocation("ascendant", "titan_strength"));

        try {
            PowerRuntimeEngine.tickAbilities(serverPlayer);
            helper.assertTrue(serverPlayer.hasEffect(MobEffects.NIGHT_VISION),
                    "Eligible TICK abilities must execute through the server tick dispatcher");

            LivingHurtEvent ordinaryDamage = new LivingHurtEvent(serverPlayer,
                    serverPlayer.damageSources().generic(), 1.0F);
            serverPlayer.setHealth(serverPlayer.getMaxHealth());
            PowerRuntimeEngine.onLivingHurt(ordinaryDamage);
            helper.assertTrue(!PowerRuntimeEngine.isOnCooldown(serverPlayer,
                            new ResourceLocation("ascendant", "low_hp_emergency_warp")),
                    "Low-health reactions must not execute above their configured threshold");

            serverPlayer.setHealth(4.0F);
            Vec3 before = serverPlayer.position();
            LivingHurtEvent lowHealthDamage = new LivingHurtEvent(serverPlayer,
                    serverPlayer.damageSources().generic(), 1.0F);
            PowerRuntimeEngine.onLivingHurt(lowHealthDamage);
            helper.assertTrue(PowerRuntimeEngine.isOnCooldown(serverPlayer,
                            new ResourceLocation("ascendant", "low_hp_emergency_warp"))
                            && serverPlayer.position().distanceToSqr(before) > 1.0D,
                    "A qualifying low-health hurt event should execute and cool down the emergency warp");

            Mob target = EntityType.ZOMBIE.create(serverPlayer.serverLevel());
            helper.assertTrue(target != null, "Attack target must be a registered entity");
            serverPlayer.setHealth(4.0F);
            LivingHurtEvent attack = new LivingHurtEvent(target,
                    serverPlayer.damageSources().playerAttack(serverPlayer), 1.0F);
            PowerRuntimeEngine.onLivingHurt(attack);
            helper.assertTrue(serverPlayer.getHealth() > 4.0F
                            && PowerRuntimeEngine.isOnCooldown(serverPlayer, attackAbilityId),
                    "ATTACK_HIT abilities should execute once for their owning player");

            LivingHurtEvent fireDamage = new LivingHurtEvent(serverPlayer,
                    serverPlayer.damageSources().onFire(), 2.0F);
            PowerRuntimeEngine.onLivingHurt(fireDamage);
            helper.assertTrue(fireDamage.isCanceled(),
                    "HURT_EVENT abilities should dispatch their registered mitigation callback");
        } finally {
            AbilityRegistry.unregister(tickAbilityId);
            AbilityRegistry.unregister(attackAbilityId);
            removeServerPlayer(testPlayer);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void toggleLifecycleAndAttributeModifierIdentity(GameTestHelper helper) {
        TestServerPlayer testPlayer = createServerPlayer(helper);
        ServerPlayer serverPlayer = testPlayer.player();
        ResourceLocation massPowerId = new ResourceLocation("ascendant", "titan_mass_form");
        ResourceLocation growthAbilityId = new ResourceLocation("ascendant", "titan_growth_boost");
        IPlayerPowerData data = serverPlayer.getCapability(PlayerPowerDataProvider.CAPABILITY)
                .orElseThrow(() -> new AssertionError("Power capability missing"));
        data.unlockPower(massPowerId);
        double baseDamage = serverPlayer.getAttributeValue(Attributes.ATTACK_DAMAGE);

        helper.assertTrue(PowerRegistry.get(massPowerId).getType()
                        == br.com.ascendant.core.PowerType.TOGGLE
                        && PowerRuntimeEngine.togglePower(serverPlayer, massPowerId, true),
                "Titan mass form must use the toggle callback path");
        helper.assertTrue(PowerRuntimeEngine.toggleAbility(serverPlayer, growthAbilityId, true)
                        == PowerRuntimeEngine.AbilityResult.ENABLED,
                "Titan growth boost must be an authorized toggle ability");
        AttributeModifierComponent powerModifier = (AttributeModifierComponent)
                PowerRegistry.get(massPowerId).getComponents().get(0);
        AttributeModifierComponent abilityModifier = (AttributeModifierComponent)
                AbilityRegistry.get(growthAbilityId).getActions().get(0);
        helper.assertTrue(!powerModifier.getModifierId().equals(abilityModifier.getModifierId())
                        && serverPlayer.getAttributeValue(Attributes.ATTACK_DAMAGE) >= baseDamage + 19.9D,
                "Distinct power and ability modifiers must stack without UUID collisions");

        helper.assertTrue(PowerRuntimeEngine.togglePower(serverPlayer, massPowerId, false)
                        && !PowerRuntimeEngine.isPowerToggled(serverPlayer, massPowerId)
                        && PowerRuntimeEngine.isOnCooldown(serverPlayer, massPowerId)
                        && serverPlayer.getAttributeValue(Attributes.ATTACK_DAMAGE) >= baseDamage + 9.9D,
                "A toggle must always turn off during cooldown and remove only its own modifier");
        PowerRuntimeEngine.clearActiveToggles(serverPlayer);
        helper.assertTrue(!PowerRuntimeEngine.isAbilityToggled(serverPlayer, growthAbilityId)
                        && Math.abs(serverPlayer.getAttributeValue(Attributes.ATTACK_DAMAGE) - baseDamage) < 1.0E-6D,
                "Lifecycle cleanup must be idempotent and remove remaining toggle-owned modifiers");
        removeServerPlayer(testPlayer);
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void fireWardUsesFiniteOwnedEffectAndSuitCleanup(GameTestHelper helper) {
        TestServerPlayer testPlayer = createServerPlayer(helper);
        ServerPlayer serverPlayer = testPlayer.player();
        ResourceLocation abilityId = new ResourceLocation("ascendant", "fire_ward");
        ResourceLocation suitId = new ResourceLocation("ascendant", "gametest_fire_ward_grant");
        SuitRegistry.register(SuitDefinition.builder(suitId).addAbility(abilityId).build());
        helper.assertTrue(SuitManager.equipSuit(serverPlayer, suitId)
                        && PowerRuntimeEngine.activateAbilityWithResult(serverPlayer, abilityId)
                        == PowerRuntimeEngine.AbilityResult.ACTIVATED,
                "Fire Ward should activate when a suit directly grants the ability");
        helper.assertTrue(serverPlayer.getEffect(MobEffects.FIRE_RESISTANCE) != null
                        && serverPlayer.getEffect(MobEffects.FIRE_RESISTANCE).getDuration() == 100
                        && ComponentRegistry.deserialize(AbilityRegistry.get(abilityId).getActions().get(0).toJson())
                        instanceof FireWardComponent,
                "Fire Ward must apply its finite configured effect through its registered executor");
        helper.assertTrue(SuitManager.unequipSuit(serverPlayer)
                        && !serverPlayer.hasEffect(MobEffects.FIRE_RESISTANCE),
                "Revoking the last suit grant should remove the effect owned by Fire Ward");
        removeServerPlayer(testPlayer);
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
        ResourceLocation zeroTickId = new ResourceLocation("ascendant", "gametest_zero_tick_dispatch");
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

            CountingAction zeroTickAction = new CountingAction();
            registerTestAbility(zeroTickId, parentPower, AbilityTrigger.TICK, 0, zeroTickAction);
            PowerRuntimeEngine.dispatchTickAbilities(player);
            PowerRuntimeEngine.dispatchTickAbilities(player);
            helper.assertTrue(zeroTickAction.executions == 2,
                    "TICK abilities without a cooldown must execute every tick");
            helper.assertTrue(zeroTickAction.ticks == 2,
                    "TICK abilities without a cooldown must also receive maintenance");

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
            AbilityRegistry.unregister(zeroTickId);
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
    public static void suitGrantedFlightCanActivateAndMaintain(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        if (!(player instanceof ServerPlayer serverPlayer)) {
            helper.succeed();
            return;
        }
        ResourceLocation suitId = new ResourceLocation("ascendant", "gametest_flight_suit");
        ResourceLocation flightPower = new ResourceLocation("ascendant", "flight");
        SuitRegistry.register(SuitDefinition.builder(suitId).addPower(flightPower).build());

        helper.assertTrue(!FlightController.canActivate(serverPlayer),
                "Flight must stay locked without the power or a granting suit");
        helper.assertTrue(SuitManager.equipSuit(serverPlayer, suitId), "Flight suit should equip");
        helper.assertTrue(FlightController.canActivate(serverPlayer),
                "A suit-granted flight power must authorise flight activation");
        helper.assertTrue(FlightController.canMaintainFlight(serverPlayer),
                "A suit-granted flight power must authorise flight maintenance");
        helper.assertTrue(FlightController.requestActive(serverPlayer, true),
                "Suit-granted flight should actually start");
        FlightController.requestActive(serverPlayer, false);
        FlightState flightState = FlightController.getState(serverPlayer);
        helper.assertTrue(flightState != null && !flightState.isActive(),
                "Stopping suit-granted flight must clear the active state");
        helper.assertTrue(SuitManager.unequipSuit(serverPlayer), "Flight suit should unequip");
        helper.assertTrue(!FlightController.canActivate(serverPlayer),
                "Revoking the suit must revoke flight activation");
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
