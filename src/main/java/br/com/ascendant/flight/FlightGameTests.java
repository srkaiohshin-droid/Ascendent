package br.com.ascendant.flight;

import br.com.ascendant.command.AscendantCommands;
import br.com.ascendant.powers.IPlayerPowerData;
import br.com.ascendant.powers.PlayerPowerDataProvider;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
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
        helper.assertTrue(heroic.headXRotDegrees <= heroic.bodyPitchDegrees,
                "The head must counter-rotate the pitched body so the face looks forward");
        helper.assertTrue(heroic.headXRotDegrees < -70.0F,
                "The neck must extend enough to lift the face off the ground");
        helper.assertTrue(heroic.headXRotDegrees >= -110.0F,
                "The neck extension must stay within a limited range");
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
}
