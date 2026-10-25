package br.com.ascendant.flight;

import br.com.ascendant.command.AscendantCommands;
import br.com.ascendant.powers.IPlayerPowerData;
import br.com.ascendant.powers.PlayerPowerDataProvider;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import io.netty.buffer.Unpooled;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@PrefixGameTestTemplate(false)
public final class FlightGameTests {
    private static final double CRUISE = 0.65D;
    private static final double BOOST = 1.25D;
    private static final double ACCELERATION = 0.045D;
    private static final double BOOST_ACCELERATION = 0.080D;
    private static final double DRAG = 0.94D;
    private static final double BRAKING = 0.12D;
    private static final double HOVER_STABILITY = 0.12D;
    private static final double HOVER_BOB = 0.025D;
    private static final double BOOST_TURN_RATE = 9.0D;
    private static final float VERTICAL_CRUISE = (float) (CRUISE * 0.72D);

    private static final FlightPhysics.Params PARAMS = new FlightPhysics.Params(
            CRUISE, BOOST, ACCELERATION, BOOST_ACCELERATION, DRAG, BRAKING,
            HOVER_STABILITY, HOVER_BOB, BOOST_TURN_RATE);

    private FlightGameTests() {
    }

    private static Vec3 step(Vec3 velocity, float yaw, float pitch, int inputMask,
                             boolean sprinting, long gameTime) {
        return FlightPhysics.nextVelocity(velocity, yaw, pitch, inputMask,
                sprinting, gameTime, PARAMS);
    }

    private static Vec3 step(Vec3 velocity, float yaw, int inputMask) {
        return step(velocity, yaw, 0.0F, inputMask, false, 0L);
    }

    /** Minecraft heading in degrees for a horizontal velocity, matching yaw. */
    private static float heading(Vec3 velocity) {
        return (float) Math.toDegrees(Math.atan2(-velocity.x, velocity.z));
    }

    private static float separationDegrees(Vec3 first, Vec3 second) {
        double dot = first.dot(second);
        double lengths = first.length() * second.length();
        if (lengths < 1.0E-9D) {
            return 0.0F;
        }
        double cosine = Math.max(-1.0D, Math.min(1.0D, dot / lengths));
        return (float) Math.toDegrees(Math.acos(cosine));
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void normalFlightReachesCruiseAndVerticalCaps(GameTestHelper helper) {
        Vec3 forward = Vec3.ZERO;
        for (int tick = 0; tick < 100; tick++) {
            forward = step(forward, 0.0F, FlightState.FORWARD);
        }
        helper.assertTrue(Math.abs(forward.z - CRUISE) < 1.0E-6D,
                "Normal forward flight should converge on the cruise cap, got " + forward.z);
        helper.assertTrue(Math.abs(forward.x) < 1.0E-9D && Math.abs(forward.y) < 1.0E-9D,
                "Straight forward flight must not add lateral or vertical drift");

        Vec3 ascend = Vec3.ZERO;
        for (int tick = 0; tick < 100; tick++) {
            ascend = step(ascend, 0.0F, FlightState.ASCEND);
        }
        helper.assertTrue(Math.abs(ascend.y - VERTICAL_CRUISE) < 1.0E-6D,
                "Direct ascent should converge on the vertical cruise command, got " + ascend.y);
        helper.assertTrue(Math.sqrt(ascend.x * ascend.x + ascend.z * ascend.z) < 1.0E-9D,
                "Vertical-only input must not create horizontal movement");

        Vec3 combined = Vec3.ZERO;
        for (int tick = 0; tick < 100; tick++) {
            combined = step(combined, 0.0F, FlightState.FORWARD | FlightState.ASCEND);
        }
        double combinedSpeed = combined.length();
        helper.assertTrue(combinedSpeed <= CRUISE * 1.15D + 1.0E-6D,
                "A combined climb must respect the overall speed ceiling, got " + combinedSpeed);
        helper.assertTrue(combined.z > 0.55D && combined.y > 0.40D,
                "A combined climb must keep both forward and upward motion");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void opposingVerticalControlsCancelAndIdleHoverSettles(GameTestHelper helper) {
        Vec3 conflicting = step(Vec3.ZERO, 0.0F, 0.0F,
                FlightState.ASCEND | FlightState.DESCEND, false, 0L);
        helper.assertTrue(conflicting.length() < 1.0E-9D,
                "Simultaneous ascent and descent must not create movement");

        Vec3 hovering = new Vec3(0.0D, 0.3D, 0.0D);
        for (int tick = 0; tick < 40; tick++) {
            hovering = step(hovering, 0.0F, 0.0F, 0, false, tick);
        }
        helper.assertTrue(Math.abs(hovering.y) <= HOVER_BOB + 1.0E-6D,
                "An idle hover should settle vertical glide into the small bob, got " + hovering.y);
        helper.assertTrue(Math.sqrt(hovering.x * hovering.x + hovering.z * hovering.z) < 1.0E-4D,
                "An idle hover must not retain horizontal movement");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void diagonalInputIsNormalizedAndBrakeReducesSpeed(GameTestHelper helper) {
        Vec3 diagonal = step(Vec3.ZERO, 0.0F, FlightState.FORWARD | FlightState.RIGHT);
        double horizontal = Math.sqrt(diagonal.x * diagonal.x + diagonal.z * diagonal.z);
        helper.assertTrue(horizontal <= CRUISE * 0.18D + 1.0E-6D,
                "Diagonal input must not exceed one normal response step, got " + horizontal);
        helper.assertTrue(Math.abs(diagonal.x - diagonal.z) < 1.0E-6D,
                "Forward-right input must split evenly between both axes");

        Vec3 braking = step(new Vec3(0.3D, 0.0D, 0.0D), 0.0F, FlightState.BRAKE);
        helper.assertTrue(Math.abs(braking.length() - (0.3D - BRAKING)) < 1.0E-6D,
                "Brake input should remove the configured speed without changing direction");
        helper.assertTrue(braking.x > 0.0D && Math.abs(braking.z) < 1.0E-9D,
                "Braking must preserve the current heading");

        Vec3 stopped = step(Vec3.ZERO, 0.0F, 0.0F, FlightState.BRAKE, false, 0L);
        helper.assertTrue(stopped.equals(Vec3.ZERO),
                "Braking from rest must stay at rest");
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
    public static void inputMaskDirectUpdateClampsAndReflects(GameTestHelper helper) {
        PlayerFlightState state = new PlayerFlightState();
        state.setInputMask(FlightState.FORWARD | FlightState.ASCEND);
        helper.assertTrue(state.getInputMask() == (FlightState.FORWARD | FlightState.ASCEND),
                "setInputMask should directly set valid bits");
        state.setInputMask(FlightState.RIGHT | (1 << 9)); // invalid bit
        helper.assertTrue(state.getInputMask() == FlightState.RIGHT,
                "setInputMask must filter out invalid mask bits");
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

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void fallDistanceAndResidualVelocityHandledOnDeactivate(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        IPlayerPowerData powers = player.getCapability(PlayerPowerDataProvider.CAPABILITY)
                .orElseThrow(() -> new AssertionError("Player power capability is unavailable"));
        powers.unlockPower(FlightController.FLIGHT_POWER);

        FlightState state = FlightController.getState(player);
        helper.assertTrue(state != null, "Player must have flight state");
        state.setActive(true);
        player.setNoGravity(true);
        player.fallDistance = 25.0F;
        player.setDeltaMovement(new Vec3(0.55D, -0.4D, 0.0D));

        FlightController.deactivateForLifecycle(player);

        helper.assertTrue(player.fallDistance == 0.0F, "Fall distance must be reset to 0 upon deactivation");
        Vec3 velocity = player.getDeltaMovement();
        double hSpeed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        helper.assertTrue(hSpeed <= 0.28001D, "Excessive horizontal flight speed must be clamped during transition");
        helper.assertTrue(velocity.y >= -0.20001D, "Downward plunge speed must be dampened during transition");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void commandRegistrationParsesResourceLocation(GameTestHelper helper) {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        AscendantCommands.register(dispatcher);
        helper.assertTrue(dispatcher.getRoot().getChild("ascendant") != null,
                "Ascendant root command should be registered");
        helper.assertTrue(dispatcher.getRoot().getChild("ascendant").getChild("power") != null,
                "Ascendant power subcommand should be registered");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void lavaAndWaterDenyFlight(GameTestHelper helper) {
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "fluid_test")) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }

            @Override
            public boolean isInLava() {
                return true;
            }
        };
        IPlayerPowerData powers = player.getCapability(PlayerPowerDataProvider.CAPABILITY)
                .orElseThrow(() -> new AssertionError("Player power capability is unavailable"));
        powers.unlockPower(FlightController.FLIGHT_POWER);

        helper.assertTrue(!FlightController.canActivate(player), "Flight activation must be denied in lava");
        helper.assertTrue(!FlightController.canMaintainFlight(player), "Flight maintenance must be denied in lava");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void lookPitchBiasesTheVerticalCommandWithoutLosingForwardMotion(GameTestHelper helper) {
        Vec3 climbVel = Vec3.ZERO;
        // Minecraft pitch is positive downwards, so -45 is looking up.
        for (int tick = 0; tick < 50; tick++) {
            climbVel = step(climbVel, 0.0F, -45.0F, FlightState.FORWARD, false, tick);
        }
        helper.assertTrue(climbVel.y > 0.30D,
                "Looking up while holding forward must produce a real climb, got " + climbVel.y);
        helper.assertTrue(climbVel.z > 0.30D,
                "Looking up while holding forward must keep forward motion, got " + climbVel.z);

        Vec3 diveVel = Vec3.ZERO;
        for (int tick = 0; tick < 50; tick++) {
            diveVel = step(diveVel, 0.0F, 45.0F, FlightState.FORWARD, false, tick);
        }
        helper.assertTrue(diveVel.y < -0.30D,
                "Looking down while holding forward must produce a real dive, got " + diveVel.y);
        helper.assertTrue(diveVel.z > 0.30D,
                "Looking down while holding forward must keep forward motion, got " + diveVel.z);
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void sprintBoostReachesHigherBoostCapAndFollowsPitch(GameTestHelper helper) {
        Vec3 normalVel = Vec3.ZERO;
        Vec3 boostVel = Vec3.ZERO;
        for (int tick = 0; tick < 100; tick++) {
            normalVel = step(normalVel, 0.0F, FlightState.FORWARD);
            boostVel = step(boostVel, 0.0F, 0.0F, FlightState.FORWARD, true, tick);
        }
        helper.assertTrue(Math.abs(normalVel.z - CRUISE) < 0.01D,
                "Normal flight must cap around cruise speed, got " + normalVel.z);
        helper.assertTrue(Math.abs(boostVel.z - BOOST) < 0.01D,
                "Sprint boost must accelerate to the boost cap, got " + boostVel.z);
        helper.assertTrue(boostVel.z > normalVel.z + 0.5D,
                "Boost speed must be substantially faster than normal cruise");

        Vec3 pitchedBoost = Vec3.ZERO;
        for (int tick = 0; tick < 100; tick++) {
            pitchedBoost = step(pitchedBoost, 0.0F, -45.0F,
                    FlightState.FORWARD, true, tick);
        }
        helper.assertTrue(pitchedBoost.y > 0.75D && pitchedBoost.z > 0.75D,
                "Boost must follow the full look vector, including pitch, got " + pitchedBoost);
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void aerodynamicSteeringCurvesTrajectory(GameTestHelper helper) {
        // Start moving forward along +Z (yaw = 0).
        Vec3 velocity = new Vec3(0.0D, 0.0D, 0.60D);
        // Turn the look 90 degrees to the right (yaw = -90) while holding forward.
        for (int tick = 0; tick < 15; tick++) {
            velocity = step(velocity, -90.0F, FlightState.FORWARD);
        }
        helper.assertTrue(velocity.x > 0.2D,
                "Turning the camera must curve the trajectory toward the new facing angle");
        helper.assertTrue(velocity.z < 0.30D,
                "The old forward component must be replaced by the new heading");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void inertiaGlidingPreservesMomentumWithoutSuddenStop(GameTestHelper helper) {
        Vec3 velocity = new Vec3(0.0D, 0.0D, 0.65D);
        Vec3 next = step(velocity, 0.0F, 0);
        helper.assertTrue(next.z > 0.55D,
                "Releasing every key must preserve momentum through aerodynamic glide");
        helper.assertTrue(next.z < velocity.z,
                "The glide must experience gentle aerodynamic drag");
        helper.assertTrue(Math.abs(next.x) < 1.0E-9D,
                "A straight glide must not drift sideways");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void animationStateSelectionCoversEveryFlightRegime(GameTestHelper helper) {
        helper.assertTrue(FlightPoseLibrary.selectState(false, 20, 0.50F, 0.0F, 0.0F, false, 0.0F)
                        == FlightAnimationState.EXIT,
                "Inactive flight must select the exit blend");
        helper.assertTrue(FlightPoseLibrary.selectState(true, 1, 0.0F, 0.0F, 0.0F, false, 0.0F)
                        == FlightAnimationState.TAKEOFF,
                "The first ticks after activation must use takeoff");
        helper.assertTrue(FlightPoseLibrary.selectState(true, 20, 0.02F, 0.0F, 0.0F, false, 0.0F)
                        == FlightAnimationState.HOVER,
                "Slow active flight without input must hover");
        helper.assertTrue(FlightPoseLibrary.selectState(true, 20, 0.02F, 0.025F, 0.0F, false, 0.0F)
                        == FlightAnimationState.HOVER,
                "The rising half of the hover bob must not select climb");
        helper.assertTrue(FlightPoseLibrary.selectState(true, 20, 0.02F, -0.025F, 0.0F, false, 0.0F)
                        == FlightAnimationState.HOVER,
                "The falling half of the hover bob must not select dive");
        helper.assertTrue(FlightPoseLibrary.selectState(true, 20, 0.30F, 0.0F, 0.0F, false, 0.0F)
                        == FlightAnimationState.HEROIC_FLIGHT,
                "Moderate forward flight must use the heroic pose");
        helper.assertTrue(FlightPoseLibrary.selectState(true, 20, 0.50F, 0.0F, 0.0F, false, 0.0F)
                        == FlightAnimationState.CRUISE,
                "Stable high-speed flight must cruise");
        helper.assertTrue(FlightPoseLibrary.selectState(true, 20, 0.50F, 0.0F, 0.0F, false, 25.0F)
                        == FlightAnimationState.TURNING,
                "A sustained heading change must select turning");
        helper.assertTrue(FlightPoseLibrary.selectState(true, 20, 0.50F, 0.30F, 0.0F, false, 0.0F)
                        == FlightAnimationState.CLIMB,
                "Positive vertical velocity must select climb");
        helper.assertTrue(FlightPoseLibrary.selectState(true, 20, 0.50F, -0.30F, 0.0F, false, 0.0F)
                        == FlightAnimationState.DIVE,
                "Negative vertical velocity must select dive");
        helper.assertTrue(FlightPoseLibrary.selectState(true, 20, 0.50F, 0.0F, 0.0F, true, 0.0F)
                        == FlightAnimationState.BRAKING,
                "Brake input must select the braking pose");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void heroicPoseOrientsTheWholeBodyForHorizontalFlight(GameTestHelper helper) {
        FlightPose heroic = FlightPoseLibrary.target(FlightAnimationState.HEROIC_FLIGHT, 20,
                0.35F, 0.0F, 0.0F, 0.0F);

        helper.assertTrue(heroic.bodyPitchDegrees <= -72.0F && heroic.bodyPitchDegrees >= -95.0F,
                "The heroic torso must be pitched close to horizontal");
        helper.assertTrue(heroic.rightArmXRot < -150.0F && heroic.leftArmXRot < -150.0F,
                "Both arms must be extended forward");
        helper.assertTrue(heroic.rightLegXRot > -15.0F && heroic.rightLegXRot < 30.0F
                        && heroic.leftLegXRot > -15.0F && heroic.leftLegXRot < 30.0F,
                "Legs must trail behind the horizontal body");
        helper.assertTrue(Math.abs(heroic.bodyRollDegrees) < 0.001F,
                "Straight flight must not bank");
        helper.assertTrue(heroic.headXRotDegrees > heroic.bodyPitchDegrees,
                "The local neck angle must be less extreme than the torso pitch instead of copying it");
        helper.assertTrue(heroic.headXRotDegrees <= -30.0F && heroic.headXRotDegrees >= -55.0F,
                "A horizontal body should use the vanilla-style local neck angle, not fold the head into the chest");
        helper.assertTrue(heroic.headXRotDegrees >= FlightPoseLibrary.MIN_HEAD_PITCH
                        && heroic.headXRotDegrees <= FlightPoseLibrary.MAX_HEAD_PITCH,
                "The local head pitch must stay inside the configured neck limit");
        helper.assertTrue(heroic.headYRotDegrees == 0.0F && heroic.headZRotDegrees == 0.0F,
                "A moving flight pose must not twist or roll the head relative to the body");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void heroicArmsAreForwardButNotARigidSingleBar(GameTestHelper helper) {
        FlightPose heroic = FlightPoseLibrary.target(FlightAnimationState.HEROIC_FLIGHT, 20,
                0.35F, 0.0F, 0.0F, 0.0F);

        helper.assertTrue(Math.abs(heroic.rightArmXRot - heroic.leftArmXRot) > 3.0F,
                "The two arms must have a slight natural difference in forward angle");
        helper.assertTrue(Math.abs(heroic.rightArmYRot - heroic.leftArmYRot) > 8.0F,
                "The arms must be spread enough to avoid reading as one rigid bar");
        helper.assertTrue(Math.abs(heroic.rightArmZRot) + Math.abs(heroic.leftArmZRot) > 4.0F,
                "A small elbow-implying angle difference must be visible");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void climbAndDiveFollowTheVerticalTrajectory(GameTestHelper helper) {
        FlightPose heroic = FlightPoseLibrary.target(FlightAnimationState.HEROIC_FLIGHT, 20,
                0.80F, 0.0F, 0.0F, 0.0F);
        FlightPose climb = FlightPoseLibrary.target(FlightAnimationState.CLIMB, 20,
                0.80F, 0.60F, -30.0F, 0.0F);
        FlightPose dive = FlightPoseLibrary.target(FlightAnimationState.DIVE, 20,
                0.80F, -0.60F, 30.0F, 0.0F);

        helper.assertTrue(climb.bodyPitchDegrees > heroic.bodyPitchDegrees,
                "Climbing must lift the chest relative to horizontal flight");
        helper.assertTrue(dive.bodyPitchDegrees < heroic.bodyPitchDegrees,
                "Diving must point the body further down");
        helper.assertTrue(dive.bodyPitchDegrees >= FlightPoseLibrary.MIN_BODY_PITCH,
                "Dive pitch must respect the joint limit");
        helper.assertTrue(climb.bodyPitchDegrees <= -52.0F,
                "Climb pitch must respect the joint limit");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void turnRollBanksIntoTheTurnAndStaysBounded(GameTestHelper helper) {
        FlightPose right = FlightPoseLibrary.target(FlightAnimationState.TURNING, 20,
                0.50F, 0.0F, 0.0F, 25.0F);
        FlightPose left = FlightPoseLibrary.target(FlightAnimationState.TURNING, 20,
                0.50F, 0.0F, 0.0F, -25.0F);

        helper.assertTrue(right.bodyRollDegrees > 0.0F,
                "A right-hand turn must bank to the right");
        helper.assertTrue(left.bodyRollDegrees < 0.0F,
                "A left-hand turn must bank to the left");
        helper.assertTrue(Math.abs(right.bodyRollDegrees) <= FlightPoseLibrary.MAX_BODY_ROLL
                        && Math.abs(left.bodyRollDegrees) <= FlightPoseLibrary.MAX_BODY_ROLL,
                "Turn bank must stay within the configured limit");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void brakingPoseReactsWithoutReplacingFlightMechanics(GameTestHelper helper) {
        FlightPose heroic = FlightPoseLibrary.target(FlightAnimationState.HEROIC_FLIGHT, 20,
                0.50F, 0.0F, 0.0F, 0.0F);
        FlightPose braking = FlightPoseLibrary.target(FlightAnimationState.BRAKING, 20,
                0.50F, 0.0F, 0.0F, 0.0F);

        helper.assertTrue(braking.bodyPitchDegrees > heroic.bodyPitchDegrees,
                "Braking must raise the torso out of the horizontal cruise");
        helper.assertTrue(braking.rightArmXRot > -40.0F && braking.leftArmXRot > -40.0F,
                "Braking arms must sweep back instead of pointing forward");
        helper.assertTrue(Math.abs(braking.rightArmZRot) > 12.0F,
                "Braking arms must flare out for drag");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void takeoffProgressesTowardsTheHeroicSilhouette(GameTestHelper helper) {
        FlightPose first = FlightPoseLibrary.target(FlightAnimationState.TAKEOFF, 1,
                0.20F, 0.0F, 0.0F, 0.0F);
        FlightPose last = FlightPoseLibrary.target(FlightAnimationState.TAKEOFF,
                FlightPoseLibrary.TAKEOFF_TICKS, 0.20F, 0.0F, 0.0F, 0.0F);

        FlightPose heroic = FlightPoseLibrary.target(FlightAnimationState.HEROIC_FLIGHT, 20,
                0.20F, 0.0F, 0.0F, 0.0F);

        helper.assertTrue(last.bodyPitchDegrees < first.bodyPitchDegrees,
                "Takeoff must progressively lower the torso towards horizontal");
        helper.assertTrue(last.rightArmXRot < first.rightArmXRot,
                "Takeoff must progressively extend the arms forward");
        helper.assertTrue(Math.abs(last.bodyPitchDegrees - heroic.bodyPitchDegrees) < 0.001F,
                "Takeoff must finish exactly on the heroic body pitch");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void poseInterpolationKeepsJointsBetweenEndpoints(GameTestHelper helper) {
        FlightPose hover = FlightPoseLibrary.target(FlightAnimationState.HOVER, 20,
                0.0F, 0.0F, 0.0F, 0.0F);
        FlightPose heroic = FlightPoseLibrary.target(FlightAnimationState.HEROIC_FLIGHT, 20,
                0.35F, 0.0F, 0.0F, 0.0F);
        FlightPose middle = FlightPose.lerp(hover, heroic, 0.5F);

        helper.assertTrue(middle.bodyPitchDegrees < hover.bodyPitchDegrees
                        && middle.bodyPitchDegrees > heroic.bodyPitchDegrees,
                "Interpolated body pitch must stay between the two poses");
        helper.assertTrue(middle.rightArmXRot < hover.rightArmXRot
                        && middle.rightArmXRot > heroic.rightArmXRot,
                "Interpolated arm angle must stay between the two poses");
        helper.assertTrue(FlightPose.lerp(hover, heroic, 0.0F) == hover,
                "A zero delta must return the source pose");
        helper.assertTrue(FlightPose.lerp(hover, heroic, 1.0F) == heroic,
                "A full delta must return the target pose");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void aerialManeuverMakesOneFullTurnAndHonoursCooldown(GameTestHelper helper) {
        helper.assertTrue(FlightPoseLibrary.maneuverRoll(1, FlightPoseLibrary.MANEUVER_TICKS) == 0.0F,
                "A maneuver must start at the neutral roll");
        helper.assertTrue(FlightPoseLibrary.maneuverRoll(1, 0) == 360.0F,
                "A right-hand maneuver must complete one full clockwise turn");
        helper.assertTrue(FlightPoseLibrary.maneuverRoll(-1, 0) == -360.0F,
                "A left-hand maneuver must complete one full counter-clockwise turn");
        helper.assertTrue(FlightPoseLibrary.maneuverRoll(1, FlightPoseLibrary.MANEUVER_TICKS / 2) > 90.0F,
                "The maneuver must progress through a visible mid-roll");
        helper.assertTrue(FlightPoseLibrary.maneuverRoll(0, 3) == 0.0F,
                "An invalid direction must never rotate the body");

        PlayerFlightState state = new PlayerFlightState();
        state.setActive(true);
        helper.assertTrue(!state.startManeuver(0, FlightPoseLibrary.MANEUVER_TICKS),
                "Invalid maneuver directions must be rejected");
        helper.assertTrue(state.startManeuver(1, FlightPoseLibrary.MANEUVER_TICKS),
                "A valid maneuver must start while flight is active");
        helper.assertTrue(!state.startManeuver(-1, FlightPoseLibrary.MANEUVER_TICKS),
                "A second maneuver must not interrupt the first");
        state.setManeuverCooldownTicks(FlightController.MANEUVER_COOLDOWN_TICKS);
        for (int tick = 0; tick < FlightPoseLibrary.MANEUVER_TICKS; tick++) {
            state.tickManeuver();
        }
        helper.assertTrue(state.getManeuverTicksRemaining() == 0 && state.getManeuverDirection() == 0,
                "The maneuver must finish and release the direction");
        helper.assertTrue(!state.startManeuver(-1, FlightPoseLibrary.MANEUVER_TICKS),
                "The cooldown must prevent immediate chaining");
        for (int tick = 0; tick < FlightController.MANEUVER_COOLDOWN_TICKS; tick++) {
            state.tickManeuver();
        }
        helper.assertTrue(state.startManeuver(-1, FlightPoseLibrary.MANEUVER_TICKS),
                "The maneuver must be available again after the cooldown");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void exitTargetReleasesThePoseBackToVanilla(GameTestHelper helper) {
        FlightPose exit = FlightPoseLibrary.target(FlightAnimationState.EXIT, 20,
                0.50F, 0.0F, 0.0F, 0.0F);
        helper.assertTrue(exit.bodyPitchDegrees == 0.0F && exit.bodyRollDegrees == 0.0F,
                "Exit must target the vanilla body orientation");
        helper.assertTrue(exit.rightArmXRot == 0.0F && exit.leftArmXRot == 0.0F,
                "Exit must target vanilla arm rotations");
        helper.assertTrue(exit.rightLegXRot == 0.0F && exit.leftLegXRot == 0.0F,
                "Exit must target vanilla leg rotations");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void flightPivotIsAtTheTorsoAndHeadUsesALocalNeckAngle(GameTestHelper helper) {
        helper.assertTrue(FlightPoseLibrary.FLIGHT_BODY_PIVOT_Y > FlightPoseLibrary.BODY_PIVOT_Y + 0.2F,
                "Flight must pivot above the locomotion knee-height pivot");
        helper.assertTrue(FlightPoseLibrary.FLIGHT_BODY_PIVOT_Y >= 0.9F
                        && FlightPoseLibrary.FLIGHT_BODY_PIVOT_Y <= 1.1F,
                "The flight pivot should sit at the vanilla torso centre");

        float horizontalHead = FlightPoseLibrary.headPitchForBody(-90.0F);
        helper.assertTrue(horizontalHead >= -50.0F && horizontalHead <= -40.0F,
                "A horizontal torso must use the vanilla-style local neck angle near -45 degrees");
        helper.assertTrue(horizontalHead > -90.0F,
                "The head must not copy the full torso rotation");
        helper.assertTrue(horizontalHead >= FlightPoseLibrary.MIN_HEAD_PITCH
                        && horizontalHead <= FlightPoseLibrary.MAX_HEAD_PITCH,
                "The derived head pitch must respect the neck limit");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void selectorUsesHysteresisAndMinimumDwell(GameTestHelper helper) {
        FlightMotionSample hoverBand = FlightMotionSample.builder()
                .active(true).activeTicks(20).speed(0.10F).build();
        helper.assertTrue(FlightAnimationSelector.select(FlightAnimationState.HOVER, 5, hoverBand)
                        == FlightAnimationState.HOVER,
                "Hover must survive inside the exit band instead of flickering at the enter threshold");
        helper.assertTrue(FlightAnimationSelector.select(FlightAnimationState.SLOW_FLIGHT, 5,
                        hoverBand.withBank(0.0F)) == FlightAnimationState.SLOW_FLIGHT
                        || hoverBand.speed() >= FlightAnimationSelector.SLOW_EXIT_SPEED,
                "Hysteresis must keep the prior state while the sample remains in its band");

        FlightMotionSample cruiseBand = FlightMotionSample.builder()
                .active(true).activeTicks(20).speed(0.47F).build();
        helper.assertTrue(FlightAnimationSelector.select(FlightAnimationState.CRUISE, 5, cruiseBand)
                        == FlightAnimationState.CRUISE,
                "Cruise must survive above its lower exit threshold");
        helper.assertTrue(FlightAnimationSelector.select(FlightAnimationState.HEROIC_FLIGHT, 5, cruiseBand)
                        == FlightAnimationState.HEROIC_FLIGHT,
                "Heroic flight must not enter cruise below the higher enter threshold");

        FlightMotionSample slow = FlightMotionSample.builder()
                .active(true).activeTicks(20).speed(0.02F).build();
        helper.assertTrue(FlightAnimationSelector.select(FlightAnimationState.HEROIC_FLIGHT, 1, slow)
                        == FlightAnimationState.HEROIC_FLIGHT,
                "A lower-priority state must not replace the current state before the dwell expires");
        helper.assertTrue(FlightAnimationSelector.select(FlightAnimationState.HEROIC_FLIGHT, 3, slow)
                        == FlightAnimationState.HOVER,
                "The state may change once the minimum dwell has elapsed");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void criticalStatesInterruptOrdinaryOnes(GameTestHelper helper) {
        FlightMotionSample maneuver = FlightMotionSample.builder()
                .active(true).activeTicks(2).speed(0.60F).maneuvering(true).build();
        helper.assertTrue(FlightAnimationSelector.select(FlightAnimationState.TAKEOFF, 1, maneuver)
                        == FlightAnimationState.MANEUVER,
                "A server-authorised maneuver must override takeoff and ordinary poses");

        FlightMotionSample landing = FlightMotionSample.builder()
                .active(true).activeTicks(30).speed(0.60F).braking(true)
                .landing(0.8F, 0.6F).build();
        helper.assertTrue(FlightAnimationSelector.select(FlightAnimationState.BRAKING, 1, landing)
                        == FlightAnimationState.LANDING,
                "Ground approach must override braking when the landing pose is active");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void groundAndAirTakeoffUseDifferentDurationsAndBothReachHeroic(GameTestHelper helper) {
        FlightMotionSample groundFirst = FlightMotionSample.builder()
                .active(true).activeTicks(1).launchedFromGround(true).speed(0.20F).build();
        FlightMotionSample groundLast = FlightMotionSample.builder()
                .active(true).activeTicks(FlightPoseLibrary.TAKEOFF_GROUND_TICKS)
                .launchedFromGround(true).speed(0.20F).build();
        FlightMotionSample airFirst = FlightMotionSample.builder()
                .active(true).activeTicks(1).launchedFromGround(false).speed(0.20F).build();

        helper.assertTrue(FlightAnimationSelector.takeoffTicks(groundFirst)
                        == FlightPoseLibrary.TAKEOFF_GROUND_TICKS,
                "A grounded launch must use the longer anticipation window");
        helper.assertTrue(FlightAnimationSelector.takeoffTicks(airFirst)
                        == FlightPoseLibrary.TAKEOFF_AIR_TICKS,
                "An airborne activation must use the shorter extension window");

        FlightPose first = FlightPoseLibrary.target(FlightAnimationState.TAKEOFF, groundFirst);
        FlightPose last = FlightPoseLibrary.target(FlightAnimationState.TAKEOFF, groundLast);
        FlightPose heroic = FlightPoseLibrary.target(FlightAnimationState.HEROIC_FLIGHT, groundLast);
        helper.assertTrue(last.bodyPitchDegrees < first.bodyPitchDegrees,
                "Ground takeoff must progressively lower the torso towards horizontal");
        helper.assertTrue(Math.abs(last.bodyPitchDegrees - heroic.bodyPitchDegrees) < 0.001F,
                "Ground takeoff must finish exactly on the heroic body pitch");
        helper.assertTrue(airFirst.launchedFromGround()
                        == false,
                "The air sample must remain tagged as an airborne activation");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void landingApproachAndRecoveryEndAtVanillaExactly(GameTestHelper helper) {
        FlightMotionSample approach = FlightMotionSample.builder()
                .active(false).landing(0.0F, 0.4F).build();
        FlightMotionSample contact = FlightMotionSample.builder()
                .active(false).landing(1.0F, 0.8F).build();
        FlightMotionSample recovered = FlightMotionSample.builder()
                .active(false).landing(1.0F, 0.8F).recovery(1.0F).build();

        FlightPose approachPose = FlightPoseLibrary.target(FlightAnimationState.LANDING, approach);
        FlightPose contactPose = FlightPoseLibrary.target(FlightAnimationState.LANDING, contact);
        FlightPose recoveredPose = FlightPoseLibrary.target(FlightAnimationState.LANDING, recovered);

        helper.assertTrue(contactPose.bodyPitchDegrees > approachPose.bodyPitchDegrees,
                "Contact must raise the body from the descending approach");
        helper.assertTrue(contactPose.bodyPitchDegrees > -20.0F,
                "The contact pose must be nearly upright");
        helper.assertTrue(recoveredPose.bodyPitchDegrees == 0.0F
                        && recoveredPose.bodyRollDegrees == 0.0F
                        && recoveredPose.rightArmXRot == 0.0F
                        && recoveredPose.leftLegXRot == 0.0F,
                "A completed landing recovery must target vanilla exactly, with no exit snap");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void hoverIdleArticulatesLimbsAndHeadWithoutAccumulating(GameTestHelper helper) {
        FlightMotionSample early = FlightMotionSample.builder()
                .active(true).activeTicks(10).speed(0.01F).build();
        FlightMotionSample later = FlightMotionSample.builder()
                .active(true).activeTicks(40).speed(0.01F).build();
        FlightPose first = FlightPoseLibrary.target(FlightAnimationState.HOVER, early);
        FlightPose second = FlightPoseLibrary.target(FlightAnimationState.HOVER, later);

        helper.assertTrue(Math.abs(first.bodyPitchDegrees - second.bodyPitchDegrees) > 0.2F
                        || Math.abs(first.headYRotDegrees - second.headYRotDegrees) > 0.2F
                        || Math.abs(first.rightArmXRot - second.rightArmXRot) > 0.2F,
                "Hover must keep breathing with the body and limbs instead of freezing");
        helper.assertTrue(first.rightArmXRot >= -40.0F && first.rightArmXRot <= 20.0F,
                "Hover arms must remain in a relaxed bounded range");
        helper.assertTrue(Math.abs(first.headZRotDegrees) < 1.0E-6F,
                "Hover must never roll the head");
        helper.assertTrue(Math.abs(first.headYRotDegrees) <= FlightPoseLibrary.MAX_HEAD_YAW,
                "Hover head sway must stay inside the small neck limit");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void boostPoseAndSelectorReflectRealSprintSpeed(GameTestHelper helper) {
        FlightMotionSample boost = FlightMotionSample.builder()
                .active(true).activeTicks(30).speed(0.90F).horizontalSpeed(0.90F)
                .acceleration(0.05F).boosting(true).build();
        FlightPose pose = FlightPoseLibrary.target(FlightAnimationState.ACCELERATING, boost);

        helper.assertTrue(FlightAnimationSelector.select(FlightAnimationState.HEROIC_FLIGHT, 5, boost)
                        == FlightAnimationState.ACCELERATING,
                "Sprint boost must select the acceleration pose");
        helper.assertTrue(pose.bodyPitchDegrees < -80.0F,
                "Boost must stretch the body more horizontal than cruise");
        helper.assertTrue(pose.rightArmXRot < -150.0F && pose.leftArmXRot < -150.0F,
                "Boost arms must lead in front of the body");
        helper.assertTrue(Math.abs(pose.rightArmXRot - pose.leftArmXRot) > 2.0F,
                "The two boost arms must not read as a single rigid bar");
        helper.assertTrue(Math.abs(pose.rightLegXRot) < 12.0F
                        && Math.abs(pose.leftLegXRot) < 12.0F,
                "Boost legs must trail close to the body");
        helper.assertTrue(pose.headYRotDegrees == 0.0F && pose.headZRotDegrees == 0.0F,
                "Boost must not twist the neck toward the crosshair");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void flightStatePreservesGroundLaunchAndEndReason(GameTestHelper helper) {
        PlayerFlightState state = new PlayerFlightState();
        state.setActive(true);
        state.setLaunchedFromGround(true);
        state.setLastEndReason(FlightState.END_LANDED);
        helper.assertTrue(state.wasLaunchedFromGround(),
                "The ground-launch flag must survive until the session ends");
        helper.assertTrue(state.getLastEndReason() == FlightState.END_LANDED,
                "The landing reason must be exposed to the presentation layer");

        state.clear();
        helper.assertTrue(!state.wasLaunchedFromGround(),
                "Clearing the session must forget the previous launch origin");
        helper.assertTrue(state.getLastEndReason() == FlightState.END_NONE,
                "Clearing the session must reset the stored end reason");
        state.setLastEndReason(FlightState.END_LIFECYCLE);
        helper.assertTrue(state.getLastEndReason() == FlightState.END_LIFECYCLE,
                "The controller must be able to record a reason after clearing");
        state.setLastEndReason(99);
        helper.assertTrue(state.getLastEndReason() == FlightState.END_NONE,
                "Unknown end reasons must be ignored");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void flightStatePacketRoundTripsEveryField(GameTestHelper helper) {
        FlightNetwork.FlightStateUpdate original = new FlightNetwork.FlightStateUpdate(
                321, true, true, FlightState.MANEUVER_LOOP, -1,
                FlightManeuvers.LOOP_TICKS - 2, true, true,
                FlightState.END_LANDED, 0.32F, 47.5F, 0.62F, true);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            FlightNetwork.FlightStateUpdate.encode(original, buffer);
            FlightNetwork.FlightStateUpdate decoded = FlightNetwork.FlightStateUpdate.decode(buffer);
            helper.assertTrue(decoded.equals(original),
                    "The flight state packet must preserve activation, maneuver, launch and landing fields exactly");
            helper.assertTrue(buffer.readableBytes() == 0,
                    "The decoder must consume the complete state payload");
        } finally {
            buffer.release();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void angleInterpolationTakesTheShortestArc(GameTestHelper helper) {
        float wrapped = FlightPose.lerpAngleDegrees(350.0F, 10.0F, 0.5F);
        helper.assertTrue(wrapped > 355.0F && wrapped < 365.0F,
                "Interpolating across the wrap must continue forward instead of unwinding");
        helper.assertTrue(FlightPose.lerpAngleDegrees(0.0F, 360.0F, 0.5F) == 0.0F,
                "A full turn is the same orientation and must not interpolate backwards");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void trajectoryManeuversAreLimitedToTheLoopAndRoll(GameTestHelper helper) {
        helper.assertTrue(FlightManeuvers.duration(FlightState.MANEUVER_LOOP)
                        == FlightManeuvers.LOOP_TICKS,
                "The loop must own a fixed, server-timed duration");
        helper.assertTrue(FlightManeuvers.duration(FlightState.MANEUVER_ROLL_360)
                        == FlightPoseLibrary.MANEUVER_TICKS,
                "The pose-only roll must still have its fixed duration");
        helper.assertTrue(FlightManeuvers.isTrajectory(FlightState.MANEUVER_LOOP),
                "The loop must remain a real server trajectory");
        helper.assertTrue(!FlightManeuvers.isTrajectory(FlightState.MANEUVER_ROLL_360),
                "The roll must stay a pose-only maneuver");

        float yaw = 90.0F;
        double speed = 0.60D;
        double loopRadius = FlightManeuvers.radius(FlightState.MANEUVER_LOOP, speed);
        Vec3 loopStart = FlightManeuvers.offset(
                FlightState.MANEUVER_LOOP, 1, yaw, speed, 0.0D);
        Vec3 loopTop = FlightManeuvers.offset(
                FlightState.MANEUVER_LOOP, 1, yaw, speed, 0.5D);
        Vec3 loopEnd = FlightManeuvers.offset(
                FlightState.MANEUVER_LOOP, 1, yaw, speed, 1.0D);
        helper.assertTrue(loopStart.length() < 1.0E-6D && loopEnd.length() < 1.0E-6D,
                "A full loop must return exactly to its entry point");
        helper.assertTrue(Math.abs(loopTop.y - loopRadius * 2.0D) < 1.0E-6D,
                "The top of the loop must sit one diameter above the entry point");

        Vec3 loopSum = Vec3.ZERO;
        for (int step = 0; step < FlightManeuvers.LOOP_TICKS; step++) {
            loopSum = loopSum.add(FlightManeuvers.stepDelta(FlightState.MANEUVER_LOOP,
                    1, yaw, speed, step, FlightManeuvers.LOOP_TICKS));
        }
        helper.assertTrue(loopSum.length() < 1.0E-6D,
                "The per-tick loop steps must close the circle");

        helper.assertTrue(FlightManeuvers.bodyPitchOffset(FlightState.MANEUVER_LOOP, 0.0F) == 0.0F
                        && FlightManeuvers.bodyPitchOffset(FlightState.MANEUVER_LOOP, 0.5F) == 180.0F
                        && FlightManeuvers.bodyPitchOffset(FlightState.MANEUVER_LOOP, 1.0F) == 360.0F,
                "The rendered body must complete one full loop pitch");

        PlayerFlightState state = new PlayerFlightState();
        state.setActive(true);
        helper.assertTrue(state.startManeuver(FlightState.MANEUVER_ROLL_360, 1,
                        FlightPoseLibrary.MANEUVER_TICKS),
                "The roll must remain a startable maneuver");
        state.clearManeuver();
        state.setManeuverAborted(true);
        state.clear();
        helper.assertTrue(!state.wasManeuverAborted(),
                "Clearing the session must forget a stale abort flag");

        // Kind 4 was the retired scripted 180-degree reversal. It must be
        // rejected even if an old or modified client asks for it, because the
        // turn is now ordinary player-controlled flight.
        helper.assertTrue(FlightManeuvers.duration(4) == 0,
                "The retired reversal kind must have no controller duration");
        helper.assertTrue(!state.startManeuver(4, 1, 18),
                "The retired reversal kind must be rejected by the flight state");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void trajectoryEntriesRequireLevelAlignedSpeed(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        player.setPos(0.5D, 8.0D, 0.5D);
        player.setYRot(0.0F);
        player.setXRot(0.0F);

        player.setDeltaMovement(new Vec3(0.0D, 0.0D, 0.30D));
        helper.assertTrue(FlightManeuvers.entryProblem(player, FlightState.MANEUVER_LOOP)
                        == FlightManeuvers.PROBLEM_TOO_SLOW,
                "A loop must refuse a hover-speed entry");

        player.setDeltaMovement(new Vec3(0.0D, 0.0D, 0.80D));
        helper.assertTrue(FlightManeuvers.entryProblem(player, FlightState.MANEUVER_LOOP)
                        == FlightManeuvers.PROBLEM_NONE,
                "A level, aligned, fast enough entry must be accepted");

        player.setYRot(90.0F);
        helper.assertTrue(FlightManeuvers.entryProblem(player, FlightState.MANEUVER_LOOP)
                        == FlightManeuvers.PROBLEM_AIM,
                "A crosshair far from the trajectory must be refused");
        player.setYRot(0.0F);

        player.setDeltaMovement(new Vec3(0.0D, 0.40D, 0.50D));
        helper.assertTrue(FlightManeuvers.entryProblem(player, FlightState.MANEUVER_LOOP)
                        == FlightManeuvers.PROBLEM_PITCH,
                "A steeply climbing trajectory must be refused");

        player.setDeltaMovement(new Vec3(0.0D, 0.0D, 1.50D));
        helper.assertTrue(FlightManeuvers.entryProblem(player, FlightState.MANEUVER_LOOP)
                        == FlightManeuvers.PROBLEM_TOO_FAST,
                "An entry above the reviewed speed must be refused");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void trajectorySweepRejectsObstructedAirspace(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        BlockPos start = helper.absolutePos(new BlockPos(3, 6, 3));
        player.setPos(start.getX() + 0.5D, start.getY(), start.getZ() + 0.5D);
        player.setYRot(0.0F);

        int clear = FlightManeuverSafety.sweepProblem(
                player, FlightState.MANEUVER_LOOP, 1, 0.0F, 0.60D);
        helper.assertTrue(clear == FlightManeuvers.PROBLEM_NONE,
                "A loop in empty air must pass the pre-flight sweep, got " + clear);

        // At a quarter of the loop the path is one radius forward and one
        // radius above the entry point. With speed 0.60 the radius is 2.67, so
        // the quarter point lands in the block cell relative (3, 8, 6). The
        // helper resolves relative coordinates itself; passing an absolute
        // position here would offset the block twice.
        helper.setBlock(new BlockPos(3, 8, 6), Blocks.STONE);
        int blocked = FlightManeuverSafety.sweepProblem(
                player, FlightState.MANEUVER_LOOP, 1, 0.0F, 0.60D);
        helper.assertTrue(blocked == FlightManeuvers.PROBLEM_OBSTRUCTED,
                "A block on the loop must fail the sweep, got " + blocked);
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void idleHoverBobIsDeterministicAndBounded(GameTestHelper helper) {
        Vec3 first = step(Vec3.ZERO, 0.0F, 0.0F, 0, false, 7L);
        Vec3 sameTick = step(Vec3.ZERO, 0.0F, 0.0F, 0, false, 7L);
        Vec3 later = step(Vec3.ZERO, 0.0F, 0.0F, 0, false, 12L);

        helper.assertTrue(first.distanceTo(sameTick) < 1.0E-12D,
                "The hover bob must be deterministic for the same game tick");
        helper.assertTrue(Math.abs(first.y) <= HOVER_BOB + 1.0E-9D
                        && Math.abs(later.y) <= HOVER_BOB + 1.0E-9D,
                "The hover bob must stay inside its configured amplitude");
        helper.assertTrue(Math.abs(first.y - later.y) > 0.005D,
                "The hover bob must actually move over time");
        helper.assertTrue(Math.abs(first.y) > 0.005D,
                "An idle hover must not freeze the vertical axis at zero");
        helper.assertTrue(Math.abs(first.x) < 1.0E-12D && Math.abs(first.z) < 1.0E-12D,
                "The hover bob must be purely vertical");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void boostTurnsTowardTheCrosshairAtABoundedRate(GameTestHelper helper) {
        Vec3 start = new Vec3(0.0D, 0.0D, CRUISE);
        Vec3 first = step(start, 180.0F, 0.0F, FlightState.FORWARD, true, 0L);
        float firstTurn = separationDegrees(start, first);
        helper.assertTrue(firstTurn > 1.0F && firstTurn <= BOOST_TURN_RATE + 0.01F,
                "Boost must turn at a bounded rate, got " + firstTurn + " degrees");
        helper.assertTrue(first.z > 0.0D,
                "A 180-degree request must not snap the boost direction in one tick");
        helper.assertTrue(Math.abs(heading(first)) < 90.0F,
                "The first boosted tick must still point mostly forward");

        Vec3 velocity = start;
        for (int tick = 0; tick < 25; tick++) {
            velocity = step(velocity, 180.0F, 0.0F, FlightState.FORWARD, true, tick);
        }
        helper.assertTrue(Math.abs(Math.abs(heading(velocity)) - 180.0F) < 2.0F,
                "After the turn the trajectory must face the reversed crosshair, got "
                        + heading(velocity));
        helper.assertTrue(velocity.z < -1.0D,
                "The reversed boost must carry real speed in the new direction");
        helper.assertTrue(velocity.length() <= BOOST + 1.0E-6D,
                "The free turn must still respect the boost speed cap");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void normalFlightCanReverseOneHundredEightyDegreesWithoutAScript(GameTestHelper helper) {
        Vec3 start = new Vec3(0.0D, 0.0D, CRUISE);
        Vec3 first = step(start, 180.0F, FlightState.FORWARD);
        helper.assertTrue(first.z > 0.30D,
                "Normal flight must decelerate through the turn instead of teleporting backwards");
        helper.assertTrue(Math.abs(first.x) < 1.0E-9D,
                "A straight 180-degree turn must not add sideways drift");

        Vec3 velocity = start;
        double minimumSpeed = velocity.length();
        for (int tick = 0; tick < 30; tick++) {
            velocity = step(velocity, 180.0F, FlightState.FORWARD);
            minimumSpeed = Math.min(minimumSpeed, velocity.length());
        }
        helper.assertTrue(velocity.z < -0.60D,
                "The free 180 must finish moving in the new look direction, got " + velocity.z);
        helper.assertTrue(minimumSpeed < 0.20D,
                "The turn must pass through a real slow point instead of a scripted arc");
        helper.assertTrue(velocity.length() <= CRUISE + 1.0E-6D,
                "The free turn must respect the normal cruise cap");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void movingPosesKeepTheHeadAlignedWithTheBody(GameTestHelper helper) {
        FlightAnimationState[] states = {
                FlightAnimationState.HEROIC_FLIGHT,
                FlightAnimationState.CRUISE,
                FlightAnimationState.TURNING,
                FlightAnimationState.CLIMB,
                FlightAnimationState.DIVE,
                FlightAnimationState.ACCELERATING,
                FlightAnimationState.DECELERATING,
                FlightAnimationState.BRAKING,
                FlightAnimationState.TAKEOFF,
                FlightAnimationState.MANEUVER,
                FlightAnimationState.LANDING
        };
        FlightMotionSample neutral = FlightMotionSample.builder()
                .active(true).activeTicks(30).speed(0.55F).horizontalSpeed(0.55F)
                .bank(12.0F).build();
        FlightMotionSample noisy = FlightMotionSample.builder()
                .active(true).activeTicks(30).speed(0.55F).horizontalSpeed(0.55F)
                .bank(12.0F).aimError(45.0F).turnRate(30.0F).build();

        for (FlightAnimationState state : states) {
            FlightPose base = FlightPoseLibrary.target(state, neutral);
            FlightPose disturbed = FlightPoseLibrary.target(state, noisy);
            helper.assertTrue(disturbed.headYRotDegrees == 0.0F
                            && disturbed.headZRotDegrees == 0.0F,
                    state + " must not derive head yaw or roll from aim/turn telemetry");
            helper.assertTrue(base.headXRotDegrees == disturbed.headXRotDegrees,
                    state + " must not change head pitch when the crosshair turns");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void bodyYawOffsetFollowsTheTrajectoryAndFadesAtHover(GameTestHelper helper) {
        helper.assertTrue(Math.abs(FlightPoseLibrary.bodyYawOffset(
                        45.0F, 0.65F, FlightPoseLibrary.MAX_BODY_YAW_OFFSET) + 45.0F) < 1.0E-6F,
                "A crosshair 45 degrees left of the trajectory must turn the body 45 degrees right");
        helper.assertTrue(Math.abs(FlightPoseLibrary.bodyYawOffset(
                        -45.0F, 0.65F, FlightPoseLibrary.MAX_BODY_YAW_OFFSET) - 45.0F) < 1.0E-6F,
                "The body yaw offset must mirror the aim error sign");
        helper.assertTrue(Math.abs(FlightPoseLibrary.bodyYawOffset(
                        170.0F, 0.65F, FlightPoseLibrary.MAX_BODY_YAW_OFFSET) + 170.0F) < 1.0E-6F,
                "A half turn must rotate most of the body instead of clamping at a small angle");
        helper.assertTrue(FlightPoseLibrary.bodyYawOffset(
                        45.0F, 0.0F, FlightPoseLibrary.MAX_BODY_YAW_OFFSET) == 0.0F,
                "A stationary hover must keep the body facing the camera");
        float lowSpeed = FlightPoseLibrary.bodyYawOffset(
                45.0F, 0.10F, FlightPoseLibrary.MAX_BODY_YAW_OFFSET);
        helper.assertTrue(lowSpeed > -45.0F && lowSpeed < 0.0F,
                "The body lead must fade smoothly as the trajectory slows");
        helper.assertTrue(Math.abs(FlightPoseLibrary.bodyYawOffset(
                        180.0F, 0.65F, FlightPoseLibrary.MAX_BODY_YAW_OFFSET)) <= 180.0F,
                "The body yaw offset must remain a legal yaw");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void landingGateIgnoresHoverAndRequiresARealDescent(GameTestHelper helper) {
        helper.assertTrue(FlightPoseLibrary.landingProgress(
                        30, true, 0.40F, -0.010F, false) == 0.0F,
                "The hover bob must never trigger the landing pose");
        helper.assertTrue(FlightPoseLibrary.landingProgress(
                        30, true, 2.00F, -0.025F, false) == 0.0F,
                "A full hover-bob cycle near the ground must not land");
        helper.assertTrue(FlightPoseLibrary.landingProgress(
                        30, true, 3.00F, -0.200F, true) == 0.0F,
                "A descent far above the ground must not start the landing pose");
        helper.assertTrue(FlightPoseLibrary.landingProgress(
                        5, true, 0.30F, -0.200F, true) == 0.0F,
                "The takeoff window must not be mistaken for a landing");
        helper.assertTrue(FlightPoseLibrary.landingProgress(
                        30, true, 0.30F, 0.200F, true) == 0.0F,
                "A climb must not start the landing pose");
        float early = FlightPoseLibrary.landingProgress(
                30, true, 2.00F, -0.200F, false);
        helper.assertTrue(early > 0.0F && early < 0.25F,
                "A real descent must begin the landing flare blocks above the ground");
        float unbraked = FlightPoseLibrary.landingProgress(
                30, true, 0.90F, -0.200F, false);
        helper.assertTrue(unbraked > 0.0F && unbraked < 1.0F,
                "A non-braking descent must also get the landing pose before contact");
        float braking = FlightPoseLibrary.landingProgress(
                30, true, 0.90F, -0.200F, true);
        helper.assertTrue(braking > 0.0F && braking < 1.0F,
                "Braking may start the approach earlier, with partial progress");
        float close = FlightPoseLibrary.landingProgress(
                30, true, 0.20F, -0.200F, false);
        float far = FlightPoseLibrary.landingProgress(
                30, true, 1.00F, -0.200F, true);
        helper.assertTrue(close > far,
                "Landing progress must grow as the ground gets closer");
        helper.succeed();
    }
}
