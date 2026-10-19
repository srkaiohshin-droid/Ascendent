package br.com.ascendant.flight;

import br.com.ascendant.powers.IPlayerPowerData;
import br.com.ascendant.powers.PlayerPowerDataProvider;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
public final class FlightGameTests {
    private FlightGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void physicsAcceleratesAndEnforcesSpeedCaps(GameTestHelper helper) {
        Vec3 velocity = Vec3.ZERO;
        for (int tick = 0; tick < 100; tick++) {
            velocity = FlightPhysics.nextVelocity(velocity, 0.0F,
                    FlightState.FORWARD | FlightState.ASCEND,
                    0.55D, 0.38D, 0.035D, 0.025D, 0.09D, 0.16D);
        }
        helper.assertTrue(Math.abs(velocity.z - 0.55D) < 1.0E-8D,
                "Forward acceleration should reach the configured horizontal cap");
        helper.assertTrue(Math.abs(velocity.y - 0.38D) < 1.0E-8D,
                "Ascent should reach the configured vertical cap");
        helper.assertTrue(Math.abs(velocity.x) < 1.0E-8D,
                "Forward flight at zero yaw should not add sideways velocity");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void opposingVerticalControlsCancelAndHoverSettles(GameTestHelper helper) {
        Vec3 conflicting = FlightPhysics.nextVelocity(new Vec3(0.0D, 0.0D, 0.0D),
                0.0F, FlightState.ASCEND | FlightState.DESCEND,
                0.55D, 0.38D, 0.035D, 0.025D, 0.09D, 0.16D);
        helper.assertTrue(Math.abs(conflicting.y) < 1.0E-8D,
                "Simultaneous ascent and descent must not create vertical input");

        Vec3 hovering = new Vec3(0.0D, 0.3D, 0.0D);
        for (int tick = 0; tick < 3; tick++) {
            hovering = FlightPhysics.nextVelocity(hovering, 0.0F, 0,
                    0.55D, 0.38D, 0.035D, 0.025D, 0.09D, 0.16D);
        }
        helper.assertTrue(Math.abs(hovering.y) < 1.0E-8D,
                "Neutral vertical input should settle vertical velocity to a hover");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void diagonalInputIsNormalizedAndBrakeReducesSpeed(GameTestHelper helper) {
        Vec3 diagonal = FlightPhysics.nextVelocity(Vec3.ZERO, 0.0F,
                FlightState.FORWARD | FlightState.RIGHT,
                0.55D, 0.38D, 0.035D, 0.025D, 0.09D, 0.16D);
        helper.assertTrue(Math.sqrt(diagonal.x * diagonal.x + diagonal.z * diagonal.z) <= 0.0350001D,
                "Diagonal acceleration must not exceed one acceleration step");

        Vec3 braking = FlightPhysics.nextVelocity(new Vec3(0.3D, 0.0D, 0.0D), 0.0F,
                FlightState.BRAKE, 0.55D, 0.38D, 0.035D, 0.025D, 0.09D, 0.16D);
        helper.assertTrue(braking.x * braking.x + braking.z * braking.z < 0.3D * 0.3D,
                "Brake input should reduce horizontal speed");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void inputRejectsInvalidAndDuplicateTickRequests(GameTestHelper helper) {
        PlayerFlightState state = new PlayerFlightState();
        helper.assertTrue(state.acceptInput(FlightState.FORWARD, 4L), "First input for a tick should be accepted");
        helper.assertTrue(!state.acceptInput(FlightState.BACK, 4L), "A second input in the same tick should be rejected");
        helper.assertTrue(!state.acceptInput(1 << 7, 5L), "Unknown input bits should be rejected");
        state.clearInput();
        helper.assertTrue(state.getInputMask() == 0, "Clearing stale input should stop all commanded movement");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void entityMovementStillCollidesWithSolidBlocks(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        helper.getLevel().setBlockAndUpdate(origin.offset(2, 1, 0), Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(origin.offset(2, 2, 0), Blocks.STONE.defaultBlockState());
        Player player = helper.makeMockPlayer();
        player.setPos(origin.getX() + 0.5D, origin.getY() + 1.0D, origin.getZ() + 0.5D);

        Vec3 fastStep = new Vec3(0.55D, 0.0D, 0.0D).scale(10.0D);
        player.move(MoverType.SELF, fastStep);

        helper.assertTrue(player.getX() < origin.getX() + 2.0D,
                "Vanilla entity movement must clip a fast flight step against a block wall");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void activationRequiresUnlockedFlightPower(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        IPlayerPowerData powers = player.getCapability(PlayerPowerDataProvider.CAPABILITY)
                .orElseThrow(() -> new AssertionError("Player power capability is unavailable"));
        helper.assertTrue(!FlightController.canActivate(player), "Flight must be denied without its power");
        powers.unlockPower(FlightController.FLIGHT_POWER);
        helper.assertTrue(FlightController.canActivate(player), "An eligible player with unlocked flight should be accepted");
        powers.revokePower(FlightController.FLIGHT_POWER);
        helper.assertTrue(!FlightController.canActivate(player), "Revoking flight must remove activation permission");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void flightStateIsInactiveAfterLifecycleClear(GameTestHelper helper) {
        PlayerFlightState state = new PlayerFlightState();
        state.setActive(true);
        state.setHasLeftGround(true);
        state.acceptInput(FlightState.FORWARD | FlightState.ASCEND, 1L);
        helper.assertTrue(state.isActive(), "State must be active before clear");

        state.clear();
        helper.assertTrue(!state.isActive(), "State must be inactive after clear");
        helper.assertTrue(state.getInputMask() == 0, "Input mask must be zero after clear");
        helper.assertTrue(!state.hasLeftGround(), "hasLeftGround must be false after clear");
        helper.assertTrue(state.getLastInputGameTick() == Long.MIN_VALUE,
                "Last input game tick must be reset after clear");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void deactivateForLifecycleStopsActiveFlightState(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        IPlayerPowerData powers = player.getCapability(PlayerPowerDataProvider.CAPABILITY)
                .orElseThrow(() -> new AssertionError("Player power capability is unavailable"));
        powers.unlockPower(FlightController.FLIGHT_POWER);

        // Manually activate the flight state.
        FlightState state = FlightController.getState(player);
        helper.assertTrue(state != null, "Player must have a flight state capability");
        state.setHadNoGravity(player.isNoGravity());
        state.setActive(true);
        state.setHasLeftGround(true);
        player.setNoGravity(true);

        helper.assertTrue(state.isActive(), "State must be active after manual activation");

        // Now deactivate via the lifecycle method (used on death/dimension change/revoke).
        FlightController.deactivateForLifecycle(player);

        helper.assertTrue(!state.isActive(), "State must be inactive after deactivateForLifecycle");
        helper.assertTrue(state.getInputMask() == 0, "Input mask must be cleared after deactivation");
        // noGravity should be restored to what it was before flight (false for a normal player).
        helper.assertTrue(!player.isNoGravity(), "noGravity must be restored after deactivation");
        helper.succeed();
    }
}
