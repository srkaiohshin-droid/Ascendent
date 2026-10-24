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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

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
    public static void lookVectorPitchControlsVerticalClimbAndDive(GameTestHelper helper) {
        Vec3 climbVel = Vec3.ZERO;
        // Pitch = -45 (looking up 45 degrees in Minecraft)
        for (int i = 0; i < 50; i++) {
            climbVel = FlightPhysics.nextVelocity(climbVel, 0.0F, -45.0F,
                    FlightState.FORWARD, false, 0.65D, 1.25D,
                    0.045D, 0.080D, 0.12D, 0.94D, 0.12D, 0.12D);
        }
        helper.assertTrue(climbVel.y > 0.3D, "Looking up while holding forward must produce positive vertical climb");
        helper.assertTrue(climbVel.z > 0.3D, "Looking up while holding forward must maintain forward component");

        Vec3 diveVel = Vec3.ZERO;
        // Pitch = +45 (looking down 45 degrees in Minecraft)
        for (int i = 0; i < 50; i++) {
            diveVel = FlightPhysics.nextVelocity(diveVel, 0.0F, 45.0F,
                    FlightState.FORWARD, false, 0.65D, 1.25D,
                    0.045D, 0.080D, 0.12D, 0.94D, 0.12D, 0.12D);
        }
        helper.assertTrue(diveVel.y < -0.3D, "Looking down while holding forward must produce negative vertical dive");
        helper.assertTrue(diveVel.z > 0.3D, "Looking down while holding forward must maintain forward component");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void sprintBoostReachesHigherBoostCap(GameTestHelper helper) {
        Vec3 normalVel = Vec3.ZERO;
        Vec3 boostVel = Vec3.ZERO;
        for (int i = 0; i < 100; i++) {
            normalVel = FlightPhysics.nextVelocity(normalVel, 0.0F, 0.0F,
                    FlightState.FORWARD, false, 0.65D, 1.25D,
                    0.045D, 0.080D, 0.12D, 0.94D, 0.12D, 0.12D);
            boostVel = FlightPhysics.nextVelocity(boostVel, 0.0F, 0.0F,
                    FlightState.FORWARD, true, 0.65D, 1.25D,
                    0.045D, 0.080D, 0.12D, 0.94D, 0.12D, 0.12D);
        }
        helper.assertTrue(Math.abs(normalVel.z - 0.65D) < 0.01D, "Cruise speed must cap around cruise speed");
        helper.assertTrue(Math.abs(boostVel.z - 1.25D) < 0.01D, "Sprinting boost must accelerate to boost cap");
        helper.assertTrue(boostVel.z > normalVel.z + 0.5D, "Boost speed must be substantially faster than cruise");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void aerodynamicSteeringCurvesTrajectory(GameTestHelper helper) {
        // Start moving forward along Z (yaw = 0)
        Vec3 vel = new Vec3(0.0D, 0.0D, 0.60D);
        // Turn look 90 degrees to the right (yaw = -90) while holding forward
        for (int i = 0; i < 15; i++) {
            vel = FlightPhysics.nextVelocity(vel, -90.0F, 0.0F,
                    FlightState.FORWARD, false, 0.65D, 1.25D,
                    0.045D, 0.080D, 0.15D, 0.94D, 0.12D, 0.12D);
        }
        helper.assertTrue(vel.x > 0.2D, "Turning camera must curve trajectory smoothly toward the new facing angle");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void inertiaGlidingPreservesMomentumWithoutSuddenStop(GameTestHelper helper) {
        Vec3 vel = new Vec3(0.0D, 0.0D, 0.65D);
        // Release forward key (input = 0)
        Vec3 next = FlightPhysics.nextVelocity(vel, 0.0F, 0.0F,
                0, false, 0.65D, 1.25D,
                0.045D, 0.080D, 0.12D, 0.94D, 0.12D, 0.12D);
        helper.assertTrue(next.z > 0.55D, "Releasing keys must preserve momentum through aerodynamic glide");
        helper.assertTrue(next.z < vel.z, "Glide must experience gentle aerodynamic drag");
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
        helper.assertTrue(heroic.headYRotDegrees >= -FlightPoseLibrary.MAX_HEAD_YAW
                        && heroic.headYRotDegrees <= FlightPoseLibrary.MAX_HEAD_YAW,
                "The local head yaw must stay bounded");
        helper.assertTrue(heroic.headZRotDegrees >= -FlightPoseLibrary.MAX_HEAD_ROLL
                        && heroic.headZRotDegrees <= FlightPoseLibrary.MAX_HEAD_ROLL,
                "The local head roll must stay bounded");
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
        helper.assertTrue(pose.rightArmXRot > -30.0F && pose.leftArmXRot > -30.0F,
                "Boost must sweep the arms back along the body instead of pointing forward");
        helper.assertTrue(Math.abs(pose.rightLegXRot) < 6.0F && Math.abs(pose.leftLegXRot) < 6.0F,
                "Boost legs must trail close to the body");
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
    public static void trajectoryManeuversUseClosedAndReversedPaths(GameTestHelper helper) {
        helper.assertTrue(FlightManeuvers.duration(FlightState.MANEUVER_LOOP)
                        == FlightManeuvers.LOOP_TICKS,
                "The loop must own a fixed, server-timed duration");
        helper.assertTrue(FlightManeuvers.duration(FlightState.MANEUVER_REVERSAL)
                        == FlightManeuvers.REVERSAL_TICKS,
                "The reversal must own a fixed, server-timed duration");

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

        double reversalRadius = FlightManeuvers.radius(FlightState.MANEUVER_REVERSAL, speed);
        Vec3 side = FlightManeuvers.right(yaw);
        Vec3 reversalEnd = FlightManeuvers.offset(
                FlightState.MANEUVER_REVERSAL, 1, yaw, speed, 1.0D);
        helper.assertTrue(reversalEnd.distanceTo(side.scale(reversalRadius * 2.0D)) < 1.0E-6D,
                "A right reversal must finish one diameter to the right");
        Vec3 lastStep = FlightManeuvers.stepDelta(FlightState.MANEUVER_REVERSAL, 1, yaw,
                speed, FlightManeuvers.REVERSAL_TICKS - 1, FlightManeuvers.REVERSAL_TICKS);
        helper.assertTrue(lastStep.dot(FlightManeuvers.forward(yaw)) < 0.0D,
                "The last reversal step must point opposite the entry heading");
        float reversalYaw = FlightManeuvers.yawAt(
                FlightState.MANEUVER_REVERSAL, 1, yaw, 1.0F);
        helper.assertTrue(Math.abs(Mth.wrapDegrees(reversalYaw - (yaw + 180.0F))) < 1.0E-4F,
                "A right reversal must finish facing 180 degrees from entry");

        helper.assertTrue(FlightManeuvers.bodyPitchOffset(FlightState.MANEUVER_LOOP, 0.0F) == 0.0F
                        && FlightManeuvers.bodyPitchOffset(FlightState.MANEUVER_LOOP, 0.5F) == 180.0F
                        && FlightManeuvers.bodyPitchOffset(FlightState.MANEUVER_LOOP, 1.0F) == 360.0F,
                "The rendered body must complete one full loop pitch");
        helper.assertTrue(FlightManeuvers.bodyRollOffset(
                        FlightState.MANEUVER_REVERSAL, 1, 0.5F)
                        == FlightManeuvers.REVERSAL_BANK_DEGREES,
                "The reversal must bank into its turn");
        helper.assertTrue(Math.abs(FlightManeuvers.bodyRollOffset(
                        FlightState.MANEUVER_REVERSAL, 1, 1.0F)) < 1.0E-4F,
                "The reversal must level out at the exit");

        PlayerFlightState state = new PlayerFlightState();
        state.setActive(true);
        helper.assertTrue(state.startManeuver(FlightState.MANEUVER_REVERSAL, 1,
                        FlightManeuvers.REVERSAL_TICKS),
                "The reversal must be a startable maneuver kind, not only a duration");
        state.clearManeuver();
        state.setManeuverAborted(true);
        state.clear();
        helper.assertTrue(!state.wasManeuverAborted(),
                "Clearing the session must forget a stale abort flag");
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
}
