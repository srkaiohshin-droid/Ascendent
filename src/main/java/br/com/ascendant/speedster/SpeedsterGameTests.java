package br.com.ascendant.speedster;

import br.com.ascendant.flight.FlightController;
import br.com.ascendant.flight.FlightState;
import br.com.ascendant.power.runtime.PowerRuntimeEngine;
import br.com.ascendant.powers.IPlayerPowerData;
import br.com.ascendant.powers.PlayerPowerDataProvider;
import br.com.ascendant.suit.SuitDefinition;
import br.com.ascendant.suit.SuitManager;
import br.com.ascendant.suit.SuitRegistry;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.network.NetworkHooks;

import java.util.UUID;

@PrefixGameTestTemplate(false)
public final class SpeedsterGameTests {
    private SpeedsterGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void levelsAccelerateProgressivelyAndBrakingIsResponsive(GameTestHelper helper) {
        Vec3 levelOne = Vec3.ZERO;
        Vec3 levelTen = Vec3.ZERO;
        int forward = SpeedsterState.FORWARD;
        for (int tick = 0; tick < 40; tick++) {
            levelOne = SpeedsterPhysics.nextVelocity(levelOne, 0.0F, forward, 1,
                    0.22D, 1.0D, 0.035D, 0.16D, 0.94D, 0.18D, 1.12D);
            levelTen = SpeedsterPhysics.nextVelocity(levelTen, 0.0F, forward, 10,
                    0.22D, 1.0D, 0.035D, 0.16D, 0.94D, 0.18D, 1.12D);
        }
        helper.assertTrue(levelOne.z > 0.20D && levelOne.z < 0.23D,
                "Level 1 must accelerate to its lower configured cap");
        helper.assertTrue(levelTen.z > levelOne.z + 0.5D && levelTen.z <= 1.000001D,
                "Higher levels must unlock a materially higher bounded speed");

        Vec3 braking = SpeedsterPhysics.nextVelocity(levelTen, 0.0F, SpeedsterState.BRAKE, 10,
                0.22D, 1.0D, 0.035D, 0.16D, 0.94D, 0.18D, 1.12D);
        helper.assertTrue(braking.horizontalDistanceSqr() < levelTen.horizontalDistanceSqr(),
                "Emergency braking must reduce horizontal speed immediately");
        Vec3 coast = SpeedsterPhysics.nextVelocity(levelTen, 0.0F, 0, 10,
                0.22D, 1.0D, 0.035D, 0.16D, 0.94D, 0.18D, 1.12D);
        helper.assertTrue(coast.z > 0.0D && coast.z < levelTen.z,
                "Releasing directional input must preserve then gradually lose momentum");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void directionSteeringNormalizesInputAndPreservesVerticalMotion(GameTestHelper helper) {
        Vec3 diagonal = SpeedsterPhysics.nextVelocity(Vec3.ZERO, 0.0F,
                SpeedsterState.FORWARD | SpeedsterState.RIGHT, 10,
                0.22D, 1.0D, 0.035D, 0.16D, 0.94D, 0.18D, 1.12D);
        helper.assertTrue(Math.abs(diagonal.x - diagonal.z) < 1.0E-8D,
                "Diagonal input must be normalized and split evenly at zero yaw");

        Vec3 curved = SpeedsterPhysics.nextVelocity(new Vec3(0.0D, 0.4D, 0.8D), -90.0F,
                SpeedsterState.FORWARD, 10,
                0.22D, 1.0D, 0.035D, 0.16D, 0.94D, 0.18D, 1.12D);
        helper.assertTrue(curved.x > 0.0D && curved.z > 0.0D && curved.y == 0.4D,
                "Steering should curve existing momentum without taking over vertical movement");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void inputAndControlStateRejectReplayAndClearOnDeactivation(GameTestHelper helper) {
        PlayerSpeedsterState state = new PlayerSpeedsterState();
        state.setActive(true);
        state.setLevel(11);
        helper.assertTrue(state.getLevel() == SpeedsterState.MAX_LEVEL,
                "Speed level must be clamped to its supported range");
        helper.assertTrue(state.acceptInput(SpeedsterState.FORWARD | SpeedsterState.BOOST, 5L),
                "Valid input must be accepted once per server tick");
        helper.assertTrue(!state.acceptInput(SpeedsterState.BACK, 5L),
                "Duplicate input in one tick must be rejected");
        helper.assertTrue(!state.acceptInput(1 << 7, 6L),
                "Unknown input bits must be rejected");
        helper.assertTrue(state.canAcceptControl(8L), "A new tick may accept a control action");
        state.markControlAccepted(8L);
        helper.assertTrue(!state.canAcceptControl(8L), "Duplicate control actions in one tick must be rejected");

        state.setActive(false);
        helper.assertTrue(state.getInputMask() == 0, "Deactivation must clear held movement input");
        state.clear();
        helper.assertTrue(state.getLevel() == SpeedsterState.MIN_LEVEL && !state.isActive(),
                "Lifecycle cleanup must reset the transient state");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void activationRequiresPowerAndCannotOverlapFlight(GameTestHelper helper) {
        TestPlayer testPlayer = createServerPlayer(helper);
        try {
            ServerPlayer player = testPlayer.player();
            IPlayerPowerData powers = player.getCapability(PlayerPowerDataProvider.CAPABILITY)
                    .orElseThrow(() -> new AssertionError("Power capability is unavailable"));
            SpeedsterState speedster = SpeedsterController.getState(player);
            FlightState flight = FlightController.getState(player);
            helper.assertTrue(speedster != null && flight != null,
                    "Both transient movement states must be attached");
            helper.assertTrue(!SpeedsterController.canActivate(player),
                    "Super-speed must require the kinetic sprint power");
            powers.unlockPower(SpeedsterController.REQUIRED_POWER);
            helper.assertTrue(SpeedsterController.canActivate(player),
                    "An eligible power owner may activate super-speed");
            helper.assertTrue(SpeedsterController.toggle(player) && speedster.isActive(),
                    "The authorized player can activate the speedster controller");
            powers.unlockPower(FlightController.FLIGHT_POWER);
            helper.assertTrue(FlightController.requestActive(player, true),
                    "An eligible player can activate flight after requesting the mode change");
            helper.assertTrue(!speedster.isActive() && flight.isActive(),
                    "Activating flight must immediately deactivate speedster movement");
            FlightController.requestActive(player, false);
        } finally {
            removeServerPlayer(testPlayer);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void suitRevocationStopsSpeedsterUnlessAnotherSourceRemains(GameTestHelper helper) {
        TestPlayer testPlayer = createServerPlayer(helper);
        try {
            ServerPlayer player = testPlayer.player();
            IPlayerPowerData powers = player.getCapability(PlayerPowerDataProvider.CAPABILITY)
                    .orElseThrow(() -> new AssertionError("Power capability is unavailable"));
            SpeedsterState state = SpeedsterController.getState(player);
            ResourceLocation suitId = new ResourceLocation("ascendant", "gametest_speedster_suit");
            SuitRegistry.register(SuitDefinition.builder(suitId)
                    .addPower(SpeedsterController.REQUIRED_POWER).build());

            helper.assertTrue(SuitManager.equipSuit(player, suitId), "Test speedster suit must equip");
            helper.assertTrue(SpeedsterController.toggle(player) && state.isActive(),
                    "Suit-granted kinetic sprint must activate the speedster mode");
            helper.assertTrue(SuitManager.unequipSuit(player) && !state.isActive(),
                    "Losing the final kinetic sprint source must immediately stop speedster movement");

            helper.assertTrue(SuitManager.equipSuit(player, suitId), "Test speedster suit must re-equip");
            helper.assertTrue(SpeedsterController.toggle(player) && state.isActive(),
                    "Speedster mode must be reusable after re-equipping");
            powers.unlockPower(SpeedsterController.REQUIRED_POWER);
            helper.assertTrue(SuitManager.unequipSuit(player) && state.isActive(),
                    "Removing a suit must preserve speedster movement when the power remains intrinsic");
            helper.assertTrue(PowerRuntimeEngine
                            .isPowerToggled(player, SpeedsterController.REQUIRED_POWER),
                    "Active speedster movement must stay mirrored by the power toggle");
            SpeedsterController.clearForLifecycle(player, false);
            helper.assertTrue(!state.isActive()
                            && !PowerRuntimeEngine
                            .isPowerToggled(player, SpeedsterController.REQUIRED_POWER),
                    "Lifecycle cleanup must clear both the transient mode and its power toggle");
        } finally {
            removeServerPlayer(testPlayer);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void highSpeedMovementStillClipsAgainstSolidBlocks(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        helper.getLevel().setBlockAndUpdate(origin.offset(2, 1, 0), Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(origin.offset(2, 2, 0), Blocks.STONE.defaultBlockState());
        Player player = helper.makeMockPlayer();
        player.setPos(origin.getX() + 0.5D, origin.getY() + 1.0D, origin.getZ() + 0.5D);

        Vec3 velocity = Vec3.ZERO;
        for (int tick = 0; tick < 40; tick++) {
            velocity = SpeedsterPhysics.nextVelocity(velocity, -90.0F, SpeedsterState.FORWARD, 10,
                    0.22D, 1.0D, 0.035D, 0.16D, 0.94D, 0.18D, 1.12D);
        }
        player.move(MoverType.SELF, velocity);
        helper.assertTrue(player.getX() < origin.getX() + 2.0D,
                "Vanilla movement collision must stop the fast trajectory at the wall");
        helper.succeed();
    }

    /**
     * End-to-end check of the path the catalog and the radial menu use: the power runtime
     * must start the server-authoritative speedster mode, apply the movement speed modifier
     * of the same power, and remove both when the toggle is turned off again.
     */
    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void catalogTogglePathAppliesRealSpeedAndRestoresBaseline(GameTestHelper helper) {
        TestPlayer testPlayer = createServerPlayer(helper);
        try {
            ServerPlayer player = testPlayer.player();
            IPlayerPowerData powers = player.getCapability(PlayerPowerDataProvider.CAPABILITY)
                    .orElseThrow(() -> new AssertionError("Power capability is unavailable"));
            powers.unlockPower(SpeedsterController.REQUIRED_POWER);
            SpeedsterState state = SpeedsterController.getState(player);
            helper.assertTrue(state != null, "Speedster state capability must be available");
            double baseline = player.getAttributeValue(Attributes.MOVEMENT_SPEED);

            helper.assertTrue(PowerRuntimeEngine.togglePower(player, SpeedsterController.REQUIRED_POWER, true),
                    "The catalog toggle path must accept kinetic sprint");
            helper.assertTrue(PowerRuntimeEngine.isPowerToggled(player, SpeedsterController.REQUIRED_POWER)
                            && state.isActive(),
                    "The runtime toggle must start the server-authoritative speedster mode");
            helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.MOVEMENT_SPEED)
                            - (baseline + 0.15D)) < 1.0E-6D,
                    "The active power must add its 0.15 movement speed modifier");

            BlockPos origin = helper.absolutePos(BlockPos.ZERO);
            player.setPos(origin.getX() + 0.5D, origin.getY() + 1.0D, origin.getZ() + 0.5D);
            Vec3 stride = Vec3.ZERO;
            for (int tick = 0; tick < 40; tick++) {
                stride = SpeedsterPhysics.nextVelocity(stride, 0.0F, SpeedsterState.FORWARD,
                        state.getLevel(), 0.22D, 1.0D, 0.035D, 0.16D, 0.94D, 0.18D, 1.12D);
            }
            double startX = player.getX();
            double startZ = player.getZ();
            player.move(MoverType.SELF, stride);
            double dx = player.getX() - startX;
            double dz = player.getZ() - startZ;
            helper.assertTrue(Math.sqrt(dx * dx + dz * dz) > 0.12D,
                    "An active speedster stride must move further than a vanilla walking step");

            helper.assertTrue(PowerRuntimeEngine.togglePower(player, SpeedsterController.REQUIRED_POWER, false)
                            && !PowerRuntimeEngine.isPowerToggled(player, SpeedsterController.REQUIRED_POWER)
                            && !state.isActive()
                            && Math.abs(player.getAttributeValue(Attributes.MOVEMENT_SPEED) - baseline) < 1.0E-6D,
                    "Toggling the power off must stop the mode and restore the movement speed baseline");
        } finally {
            removeServerPlayer(testPlayer);
        }
        helper.succeed();
    }

    private static TestPlayer createServerPlayer(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID uuid = UUID.randomUUID();
        ServerPlayer player = new ServerPlayer(server, helper.getLevel(),
                new GameProfile(uuid, "ascspd_" + uuid.toString().substring(0, 8)));
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel();
        Connection.configureSerialization(channel.pipeline(), PacketFlow.SERVERBOUND);
        channel.pipeline().addLast("packet_handler", connection);
        channel.pipeline().fireChannelActive();
        NetworkHooks.registerClientLoginChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player);
        return new TestPlayer(player, channel);
    }

    private static void removeServerPlayer(TestPlayer testPlayer) {
        testPlayer.player().getServer().getPlayerList().remove(testPlayer.player());
        testPlayer.clientChannel().finishAndReleaseAll();
    }

    private record TestPlayer(ServerPlayer player, EmbeddedChannel clientChannel) {
    }
}
