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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * Deterministic GameTests for the reference flight kernel, its presentation
 * poses, the state machine, maneuvers and the network payload.
 *
 * <p>The physics assertions use the same {@link FlightPhysics#simulate} call
 * that the travel mixin executes, so a regression in the hover drag, boost
 * alignment, free reversal or idle bob fails before a client is involved.</p>
 */
@PrefixGameTestTemplate(false)
public final class FlightGameTests {
    private static final FlightPhysics.Params PARAMS = new FlightPhysics.Params(
            0.10D, 0.20D, 0.91D, 0.08D, 0.025D);
    private static final double HOVER_TERMINAL = 1.011D;
    private static final double HOVER_DISPLACEMENT = 1.111D;
    private static final double BOOST_TERMINAL = 3.992D;
    private static final double BOOST_DISPLACEMENT = 4.032D;

    private static FlightPhysics.Step step(Vec3 velocity, float yaw, float pitch,
                                           int inputMask, boolean boosting, long gameTime) {
        return FlightPhysics.simulate(velocity, yaw, pitch, inputMask,
                boosting, gameTime, PARAMS);
    }

    private static Vec3 settle(Vec3 velocity, float yaw, float pitch,
                               int inputMask, boolean boosting, int ticks) {
        Vec3 current = velocity;
        for (int tick = 0; tick < ticks; tick++) {
            current = step(current, yaw, pitch, inputMask, boosting, tick).velocity();
        }
        return current;
    }

    private static FlightPhysics.Step lastStep(Vec3 velocity, float yaw, float pitch,
                                               int inputMask, boolean boosting, int ticks) {
        Vec3 current = velocity;
        FlightPhysics.Step last = null;
        for (int tick = 0; tick < ticks; tick++) {
            last = step(current, yaw, pitch, inputMask, boosting, tick);
            current = last.velocity();
        }
        return last;
    }

    private static FlightMotionSample sample(float speed, float horizontal, float pitch,
                                             float forward, float right, boolean boosting,
                                             boolean maneuvering) {
        return sampleAtPhase(speed, horizontal, pitch, forward, right, boosting,
                maneuvering, 0.0F);
    }

    private static FlightMotionSample sampleAtPhase(float speed, float horizontal, float pitch,
                                                    float forward, float right, boolean boosting,
                                                    boolean maneuvering, float idlePhase) {
        return FlightMotionSample.builder()
                .active(true).activeTicks(30)
                .speed(speed).horizontalSpeed(horizontal).verticalSpeed(0.0F)
                .viewPitch(pitch).bodyForward(forward).bodyRight(right)
                .boosting(boosting).maneuvering(maneuvering)
                .idlePhase(idlePhase)
                .build();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void activationRequiresUnlockedFlightPower(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        IPlayerPowerData powers = player.getCapability(PlayerPowerDataProvider.CAPABILITY)
                .orElseThrow(() -> new AssertionError("Player power capability is unavailable"));
        helper.assertTrue(!FlightController.canActivate(player),
                "Flight must be denied without its power");
        powers.unlockPower(FlightController.FLIGHT_POWER);
        helper.assertTrue(FlightController.canActivate(player),
                "An eligible player with unlocked flight should be accepted");
        powers.revokePower(FlightController.FLIGHT_POWER);
        helper.assertTrue(!FlightController.canActivate(player),
                "Revoking flight must remove activation permission");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void requestingInactiveOffLeavesUnrelatedStateUntouched(GameTestHelper helper) {
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "inactive_flight_off")) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
        player.setNoGravity(true);
        Vec3 velocity = new Vec3(0.30D, -0.05D, 0.20D);
        player.setDeltaMovement(velocity);
        FlightState state = FlightController.getState(player);
        helper.assertTrue(state != null, "Player must have a flight state capability");
        helper.assertTrue(!state.isActive(), "The test player must start inactive");

        helper.assertTrue(FlightController.requestActive(player, false),
                "Turning an inactive flight off must be handled without error");
        helper.assertTrue(!state.isActive(), "The state must remain inactive");
        helper.assertTrue(player.isNoGravity(),
                "Deactivating an inactive flight must not clear noGravity owned by another system");
        helper.assertTrue(player.getDeltaMovement().equals(velocity),
                "Deactivating an inactive flight must not rewrite the player's velocity");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void flightStateIsInactiveAfterLifecycleClear(GameTestHelper helper) {
        PlayerFlightState state = new PlayerFlightState();
        state.setActive(true);
        state.setHasLeftGround(true);
        state.acceptInput(FlightState.FORWARD | FlightState.BRAKE, 1L);
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

        FlightState state = FlightController.getState(player);
        helper.assertTrue(state != null, "Player must have a flight state capability");
        state.setHadNoGravity(player.isNoGravity());
        state.setActive(true);
        state.setHasLeftGround(true);
        player.setNoGravity(true);

        helper.assertTrue(state.isActive(), "State must be active after manual activation");
        FlightController.deactivateForLifecycle(player);

        helper.assertTrue(!state.isActive(), "State must be inactive after deactivateForLifecycle");
        helper.assertTrue(state.getInputMask() == 0, "Input mask must be cleared after deactivation");
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

        helper.assertTrue(player.fallDistance == 0.0F,
                "Fall distance must be reset to 0 upon deactivation");
        Vec3 velocity = player.getDeltaMovement();
        double hSpeed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        helper.assertTrue(hSpeed <= 0.28001D,
                "Excessive horizontal flight speed must be clamped during transition");
        helper.assertTrue(velocity.y >= -0.20001D,
                "Downward plunge speed must be dampened during transition");
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
    public static void lavaDeniesFlight(GameTestHelper helper) {
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "lava_flight_test")) {
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

        helper.assertTrue(!FlightController.canActivate(player),
                "Flight activation must be denied in lava");
        helper.assertTrue(!FlightController.canMaintainFlight(player),
                "Flight maintenance must be denied in lava");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void hoverForwardPitchControlsClimbAndDive(GameTestHelper helper) {
        Vec3 climb = settle(Vec3.ZERO, 0.0F, -45.0F, FlightState.FORWARD, false, 100);
        helper.assertTrue(climb.y > 0.50D,
                "Looking up while holding forward must produce a real climb, got " + climb.y);
        helper.assertTrue(climb.z > 0.40D,
                "Looking up while holding forward must keep forward motion, got " + climb.z);

        Vec3 dive = settle(Vec3.ZERO, 0.0F, 45.0F, FlightState.FORWARD, false, 100);
        helper.assertTrue(dive.y < -0.60D,
                "Looking down while holding forward must produce a real dive, got " + dive.y);
        helper.assertTrue(dive.z > 0.40D,
                "Looking down while holding forward must keep forward motion, got " + dive.z);

        Vec3 idle = settle(Vec3.ZERO, 0.0F, 45.0F, 0, false, 100);
        helper.assertTrue(Math.abs(idle.z) < 1.0E-9D,
                "Look pitch alone must not move an idle hover");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void idleHoverBobIsDeterministicAndBounded(GameTestHelper helper) {
        Vec3 first = step(Vec3.ZERO, 0.0F, 0.0F, 0, false, 7L).motion();
        Vec3 sameTick = step(Vec3.ZERO, 0.0F, 0.0F, 0, false, 7L).motion();
        Vec3 later = step(Vec3.ZERO, 0.0F, 0.0F, 0, false, 12L).motion();

        helper.assertTrue(first.distanceTo(sameTick) < 1.0E-12D,
                "The hover bob must be deterministic for the same game tick");
        helper.assertTrue(Math.abs(first.y) <= PARAMS.hoverBob() + 1.0E-9D
                        && Math.abs(later.y) <= PARAMS.hoverBob() + 1.0E-9D,
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
    public static void hoverIgnoresBitsOutsideTheReferenceInputMask(GameTestHelper helper) {
        int referenceMask = FlightState.FORWARD | FlightState.BACK
                | FlightState.LEFT | FlightState.RIGHT | FlightState.BRAKE;
        helper.assertTrue(FlightState.VALID_INPUT_MASK == referenceMask,
                "Only the five reference inputs may be accepted");
        helper.assertTrue((FlightState.VALID_INPUT_MASK & (1 << 5)) == 0,
                "Jump/ascend and sneak/descend bits must not exist in the flight mask");

        PlayerFlightState state = new PlayerFlightState();
        state.setActive(true);
        helper.assertTrue(!state.acceptInput(1 << 5, 1L),
                "An unknown vertical bit must be rejected");
        helper.assertTrue(state.acceptInput(FlightState.FORWARD | FlightState.BRAKE, 2L),
                "A valid reference mask must be accepted");
        helper.assertTrue(state.getInputMask()
                        == (FlightState.FORWARD | FlightState.BRAKE),
                "The accepted mask must be stored unchanged");
        helper.assertTrue(!state.acceptInput(FlightState.BACK, 2L),
                "A second input on the same game tick must be ignored");
        helper.assertTrue(state.acceptInput(FlightState.LEFT | FlightState.RIGHT, 3L),
                "Opposing strafes must be accepted and then cancelled");
        helper.assertTrue(state.getInputMask() == 0,
                "Opposing strafe directions must cancel instead of adding an invented sideways drift");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void hoverTerminalSpeedMatchesReference(GameTestHelper helper) {
        FlightPhysics.Step last = lastStep(Vec3.ZERO, 0.0F, 0.0F,
                FlightState.FORWARD, false, 200);
        helper.assertTrue(Math.abs(last.velocity().z - HOVER_TERMINAL) < 0.02D,
                "Hover terminal velocity must match the reference, got " + last.velocity().z);
        helper.assertTrue(Math.abs(last.motion().z - HOVER_DISPLACEMENT) < 0.02D,
                "Hover per-tick displacement must match the reference, got " + last.motion().z);
        helper.assertTrue(last.motion().y > 0.015D && last.motion().y < 0.03D,
                "Level forward hover must keep the reference's small climb, got " + last.motion().y);
        helper.assertTrue(Math.abs(last.motion().x) < 1.0E-9D,
                "Straight hover must not add sideways drift");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void boostTerminalSpeedMatchesReference(GameTestHelper helper) {
        FlightPhysics.Step boost = lastStep(Vec3.ZERO, 0.0F, 0.0F,
                FlightState.FORWARD, true, 200);
        helper.assertTrue(Math.abs(boost.velocity().z - BOOST_TERMINAL) < 0.02D,
                "Boost terminal velocity must match the reference, got " + boost.velocity().z);
        helper.assertTrue(Math.abs(boost.motion().z - BOOST_DISPLACEMENT) < 0.03D,
                "Boost per-tick displacement must match the reference, got " + boost.motion().z);
        helper.assertTrue(boost.motion().z > HOVER_DISPLACEMENT * 3.5D,
                "Boost must stay substantially faster than hover");

        FlightPhysics.Step pitched = lastStep(Vec3.ZERO, 0.0F, -45.0F,
                FlightState.FORWARD, true, 100);
        helper.assertTrue(pitched.motion().y > 1.5D && pitched.motion().z > 1.5D,
                "Boost must follow the full look vector, including pitch, got " + pitched.motion());
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void boostTurnsTowardTheCrosshairWithoutSnapping(GameTestHelper helper) {
        Vec3 start = new Vec3(0.0D, 0.0D, 0.70D);
        FlightPhysics.Step first = step(start, 180.0F, 0.0F, FlightState.FORWARD, true, 0L);
        helper.assertTrue(first.motion().z > 0.30D,
                "A 180-degree boost request must not snap the direction in one tick");
        helper.assertTrue(Math.abs(first.motion().x) < 1.0E-9D,
                "A straight reversal must not add sideways drift");

        Vec3 velocity = first.velocity();
        FlightPhysics.Step last = first;
        for (int tick = 1; tick < 40; tick++) {
            last = step(velocity, 180.0F, 0.0F, FlightState.FORWARD, true, tick);
            velocity = last.velocity();
        }
        helper.assertTrue(last.motion().z < -3.4D,
                "The free boost turn must finish moving in the new look direction, got "
                        + last.motion().z);
        helper.assertTrue(last.motion().length() <= BOOST_DISPLACEMENT + 0.05D,
                "The free turn must respect the reference terminal speed");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void hoverCanReverseOneHundredEightyDegreesWithoutAScript(GameTestHelper helper) {
        Vec3 start = new Vec3(0.0D, 0.0D, 0.70D);
        FlightPhysics.Step first = step(start, 180.0F, 0.0F,
                FlightState.FORWARD, false, 0L);
        helper.assertTrue(first.motion().z > 0.50D,
                "Hover must decelerate through the turn instead of teleporting backwards");
        helper.assertTrue(Math.abs(first.motion().x) < 1.0E-9D,
                "A straight 180-degree turn must not add sideways drift");

        Vec3 velocity = first.velocity();
        FlightPhysics.Step last = first;
        for (int tick = 1; tick < 15; tick++) {
            last = step(velocity, 180.0F, 0.0F, FlightState.FORWARD, false, tick);
            velocity = last.velocity();
        }
        helper.assertTrue(last.motion().z < -0.50D,
                "The free hover reversal must finish moving in the new look direction, got "
                        + last.motion().z);
        helper.assertTrue(FlightManeuvers.duration(4) == 0,
                "The retired scripted reversal kind must have no controller duration");
        PlayerFlightState state = new PlayerFlightState();
        state.setActive(true);
        helper.assertTrue(!state.startManeuver(4, 1, 18),
                "The retired reversal kind must be rejected by the flight state");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void hoverPoseLeansWithSpeedAndUsesTheReferenceHeadAngle(GameTestHelper helper) {
        FlightPose fast = FlightPoseLibrary.target(FlightAnimationState.HOVER,
                sample(1.0F, 1.0F, 0.0F, 1.0F, 0.0F, false, false));
        helper.assertTrue(Math.abs(fast.bodyPitchDegrees + 15.0F) < 1.0E-4F,
                "Full-speed hover must lean 15 degrees forward, got " + fast.bodyPitchDegrees);
        helper.assertTrue(Math.abs(fast.headXRotDegrees + 15.0F) < 1.0E-4F,
                "Hover head must use the reference -15 degree local lean, got "
                        + fast.headXRotDegrees);
        helper.assertTrue(Math.abs(fast.bodyRollDegrees) < 1.0E-4F,
                "Straight hover must not bank");

        FlightMotionSample slowSample = sample(0.1F, 0.1F, 0.0F, 1.0F, 0.0F, false, false);
        FlightPose slow = FlightPoseLibrary.target(FlightAnimationState.HOVER, slowSample);
        float slowIdleGate = FlightPoseLibrary.idleGate(slowSample);
        helper.assertTrue(Math.abs(slow.bodyPitchDegrees + 0.225F
                        + 3.5F * slowIdleGate) < 0.01F,
                "Slow hover must project the raw velocity and layer the reference idle "
                        + "torso pitch, got " + slow.bodyPitchDegrees);
        helper.assertTrue(Math.abs(slow.headXRotDegrees + 0.3F) < 0.01F,
                "The head must use the speed-scaled raw forward projection, got "
                        + slow.headXRotDegrees);

        helper.assertTrue(Math.abs(fast.rightArmXRot - 0.1F * Mth.RAD_TO_DEG) < 1.0E-3F
                        && Math.abs(fast.leftArmXRot - 0.1F * Mth.RAD_TO_DEG) < 1.0E-3F,
                "A forward hover must swing both arms by the reference 0.1-radian offset");
        helper.assertTrue(Math.abs(fast.rightArmZRot
                        - (3.0F + 0.1F * Mth.RAD_TO_DEG)) < 1.0E-3F,
                "The reference hover arm flare must combine the 3-degree baseline with "
                        + "the speed-scaled radian offset, got " + fast.rightArmZRot);
        helper.assertTrue(Math.abs(fast.rightLegXRot - 0.3F * Mth.RAD_TO_DEG) < 1.0E-3F
                        && Math.abs(fast.leftLegXRot - 0.2F * Mth.RAD_TO_DEG) < 1.0E-3F,
                "Forward hover must trail the legs by the asymmetric reference offsets, got "
                        + fast.rightLegXRot + " / " + fast.leftLegXRot);

        FlightPose decel = FlightPoseLibrary.target(FlightAnimationState.HOVER,
                sample(3.6F, 3.6F, 0.0F, 1.0F, 0.0F, false, false));
        helper.assertTrue(Math.abs(decel.bodyPitchDegrees + 30.0F) < 1.0E-4F,
                "Releasing boost must keep the reference deceleration lean, got "
                        + decel.bodyPitchDegrees);
        helper.assertTrue(Math.abs(decel.headXRotDegrees + 30.0F) < 1.0E-4F,
                "The deceleration head angle must follow the same clamped projection, got "
                        + decel.headXRotDegrees);
        helper.assertTrue(Math.abs(decel.rightArmXRot
                        - 0.1F * 3.6F * Mth.RAD_TO_DEG) < 1.0E-3F,
                "The reference limb offsets scale with the raw velocity when the torso lean "
                        + "is already clamped, got " + decel.rightArmXRot);
        helper.assertTrue(decel.rightLegXRot > 60.0F,
                "A boost release must show the reference's strong trailing leg offset, got "
                        + decel.rightLegXRot);

        FlightPose strafe = FlightPoseLibrary.target(FlightAnimationState.HOVER,
                sample(1.0F, 1.0F, 0.0F, 0.0F, 1.0F, false, false));
        helper.assertTrue(Math.abs(strafe.bodyRollDegrees - 15.0F) < 1.0E-4F,
                "A rightward hover drift must bank to the right, got " + strafe.bodyRollDegrees);
        helper.assertTrue(Math.abs(strafe.rightArmZRot
                        - (3.0F + 0.2F * Mth.RAD_TO_DEG)) < 1.0E-3F
                        && Math.abs(strafe.leftArmZRot
                        - (-3.0F + 0.2F * Mth.RAD_TO_DEG)) < 1.0E-3F,
                "A right strafe must flare the right arm and tuck the left arm by the "
                        + "reference radian offsets, got " + strafe.rightArmZRot + " / "
                        + strafe.leftArmZRot);
        helper.assertTrue(Math.abs(strafe.rightLegZRot - 0.4F * Mth.RAD_TO_DEG) < 1.0E-3F
                        && Math.abs(strafe.leftLegZRot - 0.4F * Mth.RAD_TO_DEG) < 1.0E-3F,
                "A right strafe must trail both legs sideways by the reference 0.4-radian "
                        + "offset, got " + strafe.rightLegZRot + " / " + strafe.leftLegZRot);

        FlightPose idle = FlightPoseLibrary.target(FlightAnimationState.HOVER,
                sample(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, false, false));
        helper.assertTrue(Math.abs(idle.bodyPitchDegrees + 3.5F) < 1.0E-4F
                        && Math.abs(idle.rightArmXRot + 10.0F) < 1.0E-4F
                        && Math.abs(idle.leftArmXRot - 5.0F) < 1.0E-4F
                        && Math.abs(idle.rightLegXRot - 20.0F) < 1.0E-4F
                        && Math.abs(idle.leftLegXRot - 5.0F) < 1.0E-4F,
                "An idle hover must apply the reference idle layer instead of a neutral "
                        + "mannequin, got bodyPitch " + idle.bodyPitchDegrees
                        + " arms " + idle.rightArmXRot + "/" + idle.leftArmXRot
                        + " legs " + idle.rightLegXRot + "/" + idle.leftLegXRot);
        helper.assertTrue(Math.abs(idle.rightArmZRot - 9.0F) < 1.0E-4F
                        && Math.abs(idle.leftArmZRot + 13.0F) < 1.0E-4F,
                "The idle reference combines the 3-degree arm baseline with its "
                        + "asymmetric 6/10-degree tuck, got " + idle.rightArmZRot + " / "
                        + idle.leftArmZRot);
        helper.assertTrue(idle.headXRotDegrees == 0.0F && idle.headYRotDegrees == 0.0F,
                "The idle head and body-yaw offsets must stay omitted so the neck keeps "
                        + "the real crosshair");

        FlightPose lookUp = FlightPoseLibrary.target(FlightAnimationState.HOVER,
                sample(1.0F, 1.0F, -60.0F, 1.0F, 0.0F, false, false));
        helper.assertTrue(Math.abs(lookUp.bodyPitchDegrees + 15.0F) < 1.0E-4F,
                "Looking up must not turn the upright hover into a boosted dive, got "
                        + lookUp.bodyPitchDegrees);
        helper.assertTrue(Math.abs(lookUp.headXRotDegrees + 75.0F) < 1.0E-4F,
                "Hover head must keep the -60 degree look pitch and add the -15 degree "
                        + "reference offset, got " + lookUp.headXRotDegrees);

        FlightPose idleLookUp = FlightPoseLibrary.target(FlightAnimationState.HOVER,
                sample(0.0F, 0.0F, -60.0F, 0.0F, 0.0F, false, false));
        helper.assertTrue(Math.abs(idleLookUp.headXRotDegrees + 60.0F) < 1.0E-4F,
                "A stationary hover looking up must keep the real look pitch in the neck, got "
                        + idleLookUp.headXRotDegrees);
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void idleHoverSwayMatchesTheReferenceLayer(GameTestHelper helper) {
        FlightMotionSample positive = sampleAtPhase(0.0F, 0.0F, 0.0F, 0.0F, 0.0F,
                false, false, 0.25F);
        float gate = FlightPoseLibrary.idleGate(positive);
        float sway = FlightPoseLibrary.IDLE_SWAY_SCALE * gate;
        float idle = gate + sway;
        FlightPose pose = FlightPoseLibrary.target(FlightAnimationState.HOVER, positive);

        helper.assertTrue(Math.abs(pose.bodyPitchDegrees - (-3.5F * gate + 5.0F * sway)) < 1.0E-4F,
                "The idle torso pitch must use -3.5 * gate + 5 * sway, got "
                        + pose.bodyPitchDegrees);
        helper.assertTrue(Math.abs(pose.rightArmXRot
                        - (-10.0F * gate - 0.1F * Mth.RAD_TO_DEG * sway)) < 1.0E-4F,
                "The idle right arm must convert its dynamic radian term, got "
                        + pose.rightArmXRot);
        helper.assertTrue(Math.abs(pose.leftArmXRot
                        - (5.0F * gate + 0.08F * Mth.RAD_TO_DEG * sway)) < 1.0E-4F,
                "The idle left arm must convert its dynamic radian term, got "
                        + pose.leftArmXRot);
        helper.assertTrue(Math.abs(pose.rightArmZRot - (3.0F + 6.0F * idle)) < 1.0E-4F
                        && Math.abs(pose.leftArmZRot - (-3.0F - 10.0F * idle)) < 1.0E-4F,
                "The idle arms must add the reference asymmetric 6/10-degree tuck");
        helper.assertTrue(Math.abs(pose.rightLegXRot - 20.0F * idle) < 1.0E-4F
                        && Math.abs(pose.leftLegXRot
                        - (5.0F * gate - 15.0F * sway)) < 1.0E-4F,
                "The idle legs must use the reference 20/5/15-degree offsets, got "
                        + pose.rightLegXRot + " / " + pose.leftLegXRot);
        helper.assertTrue(Math.abs(pose.rightLegYRot - 5.0F * gate) < 1.0E-4F
                        && Math.abs(pose.leftLegYRot + 8.0F * gate) < 1.0E-4F,
                "The idle legs must keep their asymmetric outward rotation");
        helper.assertTrue(pose.headXRotDegrees == 0.0F && pose.headYRotDegrees == 0.0F
                        && pose.headZRotDegrees == 0.0F,
                "The idle head offsets must not override the real look direction");

        FlightMotionSample negative = sampleAtPhase(0.0F, 0.0F, 0.0F, 0.0F, 0.0F,
                false, false, 0.75F);
        FlightPose opposite = FlightPoseLibrary.target(FlightAnimationState.HOVER, negative);
        helper.assertTrue(opposite.bodyPitchDegrees < pose.bodyPitchDegrees
                        && opposite.leftLegXRot > pose.leftLegXRot,
                "The idle sway must move the torso and trailing leg in opposite phases");

        helper.assertTrue(Math.abs(FlightPoseLibrary.idlePhase(0) - 0.4F) < 1.0E-6F,
                "The reference idle phase must start at its 0.4 offset");
        float advanced = FlightPoseLibrary.idlePhase(1);
        helper.assertTrue(advanced > 0.4F && advanced < 0.5F,
                "The reference idle phase must advance with the player tick counter");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void flightBodyYawCorrectionFollowsTheLook(GameTestHelper helper) {
        helper.assertTrue(FlightPoseLibrary.lookYawCorrectionDegrees(40.0F, 40.0F) == 0.0F,
                "A body already facing the look direction needs no yaw correction");
        helper.assertTrue(Math.abs(FlightPoseLibrary.lookYawCorrectionDegrees(30.0F, 80.0F)
                + 50.0F) < 1.0E-4F,
                "The correction must rotate the movement-body frame onto the look frame");
        helper.assertTrue(Math.abs(FlightPoseLibrary.lookYawCorrectionDegrees(170.0F, -170.0F)
                + 20.0F) < 1.0E-4F,
                "The correction must take the shortest arc across the 180-degree seam");
        helper.assertTrue(Math.abs(FlightPoseLibrary.lookYawCorrectionDegrees(-170.0F, 170.0F)
                - 20.0F) < 1.0E-4F,
                "The shortest arc must be symmetric on both sides of the seam");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void boostPoseMatchesTheReferenceSilhouette(GameTestHelper helper) {
        FlightPose level = FlightPoseLibrary.target(FlightAnimationState.BOOST,
                sample(4.0F, 4.0F, 0.0F, 1.0F, 0.0F, true, false));
        helper.assertTrue(Math.abs(level.bodyPitchDegrees + 80.0F) < 1.0E-4F,
                "Level boost must pitch the body -80 degrees, got " + level.bodyPitchDegrees);
        helper.assertTrue(Math.abs(level.headXRotDegrees + 50.0F) < 1.0E-4F,
                "Boost must combine the header's -40 degree head interpolation with the -10 degree offset, got "
                        + level.headXRotDegrees);
        helper.assertTrue(Math.abs(level.rightArmXRot - 8.0F) < 1.0E-4F
                        && Math.abs(level.rightArmYRot - 18.0F) < 1.0E-4F
                        && Math.abs(level.rightArmZRot - 12.0F) < 1.0E-4F,
                "Boost right arm must match the reference pose");
        helper.assertTrue(Math.abs(level.leftArmXRot - 8.0F) < 1.0E-4F
                        && Math.abs(level.leftArmYRot + 18.0F) < 1.0E-4F
                        && Math.abs(level.leftArmZRot + 12.0F) < 1.0E-4F,
                "Boost left arm must mirror the reference pose");
        helper.assertTrue(Math.abs(level.rightLegXRot - 15.0F) < 1.0E-4F
                        && Math.abs(level.leftLegXRot - 15.0F) < 1.0E-4F,
                "Boost legs must trail at the reference angle");
        helper.assertTrue(level.headYRotDegrees == 0.0F && level.headZRotDegrees == 0.0F,
                "Straight boost must not twist the neck independently of the body");
        helper.assertTrue(Math.abs(level.bodyLift - FlightPoseLibrary.BOOST_BODY_LIFT) < 1.0E-4F,
                "Boost must apply the reference vertical body drop, got " + level.bodyLift);

        // 45 degrees of velocity/crosshair mismatch give an 18-degree reference
        // turn offset once the radian reference value is converted to degrees.
        float turn = (float) Math.toDegrees(Math.PI / 4.0D)
                / FlightPoseLibrary.BOOST_TURN_OFFSET_DIVISOR;
        FlightPose turning = FlightPoseLibrary.target(FlightAnimationState.BOOST,
                sample(4.0F, 4.0F, 0.0F, 0.70710677F, 0.70710677F, true, false));
        helper.assertTrue(Math.abs(turning.rightArmXRot - (8.0F + 0.5F * turn)) < 1.0E-4F
                        && Math.abs(turning.leftArmXRot - (8.0F + 0.5F * turn)) < 1.0E-4F,
                "Boost arms must receive the reference 0.5 * abs(turn) offset, got "
                        + turning.rightArmXRot);
        helper.assertTrue(Math.abs(turning.rightLegXRot - (15.0F - 0.3F * turn)) < 1.0E-4F
                        && Math.abs(turning.leftLegXRot - (15.0F + 0.3F * turn)) < 1.0E-4F,
                "Boost legs must receive the mirrored reference 0.3 * turn offset, got "
                        + turning.rightLegXRot + " / " + turning.leftLegXRot);
        helper.assertTrue(turning.bodyRollDegrees > 10.0F,
                "A right-hand boost turn must visibly bank to the right, got "
                        + turning.bodyRollDegrees);
        helper.assertTrue(turning.headZRotDegrees < 0.0F
                        && Math.abs(turning.headZRotDegrees) < turning.bodyRollDegrees,
                "The neck must partially counter-roll the body bank, got "
                        + turning.headZRotDegrees);
        helper.assertTrue(turning.bodyRollDegrees == -turning.headZRotDegrees * 2.0F,
                "The neck counter-roll must be exactly half of the bank");

        FlightPose opposite = FlightPoseLibrary.target(FlightAnimationState.BOOST,
                sample(4.0F, 4.0F, 0.0F, 0.70710677F, -0.70710677F, true, false));
        helper.assertTrue(opposite.bodyRollDegrees == -turning.bodyRollDegrees
                        && opposite.headZRotDegrees == -turning.headZRotDegrees,
                "Opposite boost turns must mirror the bank and neck counter-roll");

        FlightPose sharp = FlightPoseLibrary.target(FlightAnimationState.BOOST,
                sample(4.0F, 4.0F, 0.0F, -0.70710677F, 0.70710677F, true, false));
        helper.assertTrue(Math.abs(sharp.bodyRollDegrees) == FlightPoseLibrary.BOOST_TURN_BANK_MAX_DEGREES,
                "A sharp boost turn must clamp the bank to the readable maximum, got "
                        + sharp.bodyRollDegrees);

        FlightPose pitched = FlightPoseLibrary.target(FlightAnimationState.BOOST,
                sample(4.0F, 4.0F, -30.0F, 1.0F, 0.0F, true, false));
        helper.assertTrue(Math.abs(pitched.bodyPitchDegrees + 50.0F) < 1.0E-4F,
                "Boost body pitch must follow -(viewPitch + 80), got "
                        + pitched.bodyPitchDegrees);
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void boostTimerBlendsHoverIntoBoostOverEightTicks(GameTestHelper helper) {
        helper.assertTrue(FlightPoseLibrary.boostBlend(0.0F) == 0.0F,
                "An empty boost timer must select pure hover");
        helper.assertTrue(Math.abs(FlightPoseLibrary.boostBlend(0.5F) - 0.5F) < 1.0E-6F,
                "The reference curve must pass through one half at the midpoint");
        helper.assertTrue(FlightPoseLibrary.boostBlend(1.0F) == 1.0F,
                "A full boost timer must select pure boost");
        helper.assertTrue(FlightPoseLibrary.boostBlend(-1.0F) == 0.0F
                        && FlightPoseLibrary.boostBlend(2.0F) == 1.0F,
                "The blend must clamp out-of-range timer values");

        float timer = 0.0F;
        for (int tick = 0; tick < FlightPoseLibrary.BOOST_BLEND_TICKS; tick++) {
            timer = FlightPoseLibrary.advanceBoostTimer(timer, true);
        }
        helper.assertTrue(Math.abs(timer - 1.0F) < 1.0E-6F,
                "Eight boosting ticks must fill the reference boost timer");
        for (int tick = 0; tick < FlightPoseLibrary.BOOST_BLEND_TICKS; tick++) {
            timer = FlightPoseLibrary.advanceBoostTimer(timer, false);
        }
        helper.assertTrue(Math.abs(timer) < 1.0E-6F,
                "Eight non-boosting ticks must drain the reference boost timer");

        FlightMotionSample fast = sample(4.0F, 4.0F, 0.0F, 1.0F, 0.0F, true, false);
        FlightPose hover = FlightPoseLibrary.ordinaryPose(fast, 0.0F);
        FlightPose boost = FlightPoseLibrary.ordinaryPose(fast, 1.0F);
        FlightPose middle = FlightPoseLibrary.ordinaryPose(fast, 0.5F);
        helper.assertTrue(hover.bodyPitchDegrees > boost.bodyPitchDegrees,
                "The blended endpoints must be the hover and boost silhouettes");
        helper.assertTrue(Math.abs(middle.bodyPitchDegrees
                - (hover.bodyPitchDegrees + boost.bodyPitchDegrees) * 0.5F) < 1.0E-4F,
                "The half-blend body pitch must sit between the endpoints");
        helper.assertTrue(Math.abs(middle.bodyLift
                - (hover.bodyLift + boost.bodyLift) * 0.5F) < 1.0E-4F,
                "The half-blend body lift must sit between the endpoints");
        helper.assertTrue(Math.abs(FlightPoseLibrary.ordinaryPose(fast, 0.0F).rightArmXRot
                - FlightPoseLibrary.target(FlightAnimationState.HOVER, fast).rightArmXRot) < 1.0E-4F,
                "A zero blend must use the exact hover pose");
        helper.assertTrue(Math.abs(FlightPoseLibrary.ordinaryPose(fast, 1.0F).rightArmXRot
                - FlightPoseLibrary.target(FlightAnimationState.BOOST, fast).rightArmXRot) < 1.0E-4F,
                "A full blend must use the exact boost pose");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void defaultBoostTuningIsSlightlySlowerThanTheReference(GameTestHelper helper) {
        helper.assertTrue(FlightTuning.DEFAULT_BOOST_SPEED < 0.20D
                        && FlightTuning.DEFAULT_BOOST_SPEED > 0.17D,
                "The gameplay boost impulse must stay a slight trim of the reference");
        FlightPhysics.Params tuned = new FlightPhysics.Params(0.10D,
                FlightTuning.DEFAULT_BOOST_SPEED, 0.91D, 0.08D, 0.025D);
        Vec3 velocity = Vec3.ZERO;
        for (int tick = 0; tick < 400; tick++) {
            velocity = FlightPhysics.simulate(velocity, 0.0F, 0.0F, 0, true,
                    tick, tuned).velocity();
        }
        double terminal = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        helper.assertTrue(Math.abs(terminal - 3.6925D) < 0.01D,
                "The tuned boost must settle near 3.69 blocks per tick, got " + terminal);
        helper.assertTrue(terminal < FlightGameTests.BOOST_TERMINAL,
                "The tuned boost must be slower than the 4.0 reference terminal speed");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void animationSelectorCoversReferenceStates(GameTestHelper helper) {
        FlightMotionSample inactive = FlightMotionSample.builder().active(false).build();
        helper.assertTrue(FlightAnimationSelector.select(FlightAnimationState.HOVER, 20, inactive)
                        == FlightAnimationState.EXIT,
                "Inactive flight must select the exit blend");
        helper.assertTrue(FlightAnimationSelector.select(FlightAnimationState.HOVER, 20, null)
                        == FlightAnimationState.EXIT,
                "A missing sample must select the exit blend");

        FlightMotionSample hover = sample(0.2F, 0.2F, 0.0F, 1.0F, 0.0F, false, false);
        helper.assertTrue(FlightAnimationSelector.select(FlightAnimationState.EXIT, 20, hover)
                        == FlightAnimationState.HOVER,
                "Ordinary active flight must hover");

        FlightMotionSample boost = sample(4.0F, 4.0F, 0.0F, 1.0F, 0.0F, true, false);
        helper.assertTrue(FlightAnimationSelector.select(FlightAnimationState.HOVER, 20, boost)
                        == FlightAnimationState.BOOST,
                "Sprint must select the boost pose");

        FlightMotionSample maneuver = sample(1.0F, 1.0F, 0.0F, 1.0F, 0.0F, true, true);
        helper.assertTrue(FlightAnimationSelector.select(FlightAnimationState.BOOST, 20, maneuver)
                        == FlightAnimationState.MANEUVER,
                "A running maneuver must override boost");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void exitTargetReleasesThePoseBackToVanilla(GameTestHelper helper) {
        FlightPose exit = FlightPoseLibrary.target(FlightAnimationState.EXIT,
                sample(0.5F, 0.5F, 0.0F, 1.0F, 0.0F, false, false));
        helper.assertTrue(exit.bodyPitchDegrees == 0.0F && exit.bodyRollDegrees == 0.0F,
                "Exit must target the vanilla body orientation");
        helper.assertTrue(exit.rightArmXRot == 0.0F && exit.leftArmXRot == 0.0F,
                "Exit must target vanilla arm rotations");
        helper.assertTrue(exit.rightLegXRot == 0.0F && exit.leftLegXRot == 0.0F,
                "Exit must target vanilla leg rotations");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void poseInterpolationKeepsJointsBetweenEndpoints(GameTestHelper helper) {
        FlightPose hover = FlightPoseLibrary.target(FlightAnimationState.HOVER,
                sample(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, false, false));
        FlightPose boost = FlightPoseLibrary.target(FlightAnimationState.BOOST,
                sample(4.0F, 4.0F, 0.0F, 1.0F, 0.0F, true, false));
        FlightPose middle = FlightPose.lerp(hover, boost, 0.5F);

        helper.assertTrue(middle.bodyPitchDegrees < hover.bodyPitchDegrees
                        && middle.bodyPitchDegrees > boost.bodyPitchDegrees,
                "Interpolated body pitch must stay between the two poses");
        helper.assertTrue(middle.rightArmXRot > hover.rightArmXRot
                        && middle.rightArmXRot < boost.rightArmXRot,
                "Interpolated arm angle must stay between the two poses");
        helper.assertTrue(FlightPose.lerp(hover, boost, 0.0F) == hover,
                "A zero delta must return the source pose");
        helper.assertTrue(FlightPose.lerp(hover, boost, 1.0F) == boost,
                "A full delta must return the target pose");

        float wrapped = FlightPose.lerpAngleDegrees(350.0F, 10.0F, 0.5F);
        helper.assertTrue(wrapped > 355.0F && wrapped < 365.0F,
                "Interpolating across the wrap must continue forward instead of unwinding");
        helper.assertTrue(FlightPose.lerpAngleDegrees(0.0F, 360.0F, 0.5F) == 0.0F,
                "A full turn is the same orientation and must not interpolate backwards");
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
        helper.assertTrue(!state.startManeuver(FlightState.MANEUVER_ROLL_360, 0,
                        FlightPoseLibrary.MANEUVER_TICKS),
                "Invalid maneuver directions must be rejected");
        helper.assertTrue(state.startManeuver(FlightState.MANEUVER_ROLL_360, 1,
                        FlightPoseLibrary.MANEUVER_TICKS),
                "A valid maneuver must start while flight is active");
        helper.assertTrue(!state.startManeuver(FlightState.MANEUVER_ROLL_360, -1,
                        FlightPoseLibrary.MANEUVER_TICKS),
                "A second maneuver must not interrupt the first");
        state.setManeuverCooldownTicks(FlightController.MANEUVER_COOLDOWN_TICKS);
        for (int tick = 0; tick < FlightPoseLibrary.MANEUVER_TICKS; tick++) {
            state.tickManeuver();
        }
        helper.assertTrue(state.getManeuverTicksRemaining() == 0
                        && state.getManeuverDirection() == 0,
                "The maneuver must finish and release the direction");
        helper.assertTrue(!state.startManeuver(FlightState.MANEUVER_ROLL_360, -1,
                        FlightPoseLibrary.MANEUVER_TICKS),
                "The cooldown must prevent immediate chaining");
        for (int tick = 0; tick < FlightController.MANEUVER_COOLDOWN_TICKS; tick++) {
            state.tickManeuver();
        }
        helper.assertTrue(state.startManeuver(FlightState.MANEUVER_ROLL_360, -1,
                        FlightPoseLibrary.MANEUVER_TICKS),
                "The maneuver must be available again after the cooldown");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void barrelRollOverlayAndPhysicsMatchTheReference(GameTestHelper helper) {
        helper.assertTrue(FlightPoseLibrary.rollOverlay(1, 0.0F) == FlightPose.VANILLA
                        && FlightPoseLibrary.rollOverlay(1, 1.0F) == FlightPose.VANILLA,
                "The barrel-roll accents must start and end neutral");
        helper.assertTrue(FlightPoseLibrary.rollOverlay(0, 0.5F) == FlightPose.VANILLA,
                "An invalid direction must not add a barrel-roll accent");

        FlightPose right = FlightPoseLibrary.rollOverlay(1, 0.5F);
        helper.assertTrue(Math.abs(right.bodyPitchDegrees + 10.0F) < 1.0E-4F,
                "The mid-roll body pitch must use the reference -10 degrees");
        helper.assertTrue(Math.abs(right.rightArmXRot - 20.0F) < 1.0E-4F
                        && Math.abs(right.leftArmXRot + 40.0F) < 1.0E-4F,
                "A right-hand roll must raise the right arm and fold the left arm, got "
                        + right.rightArmXRot + " / " + right.leftArmXRot);
        helper.assertTrue(Math.abs(right.rightLegXRot) < 1.0E-4F
                        && Math.abs(right.leftLegXRot + 20.0F) < 1.0E-4F,
                "A right-hand roll must keep the right leg neutral and fold the left leg, got "
                        + right.rightLegXRot + " / " + right.leftLegXRot);

        FlightPose left = FlightPoseLibrary.rollOverlay(-1, 0.5F);
        helper.assertTrue(Math.abs(left.rightArmXRot + 40.0F) < 1.0E-4F
                        && Math.abs(left.leftArmXRot - 20.0F) < 1.0E-4F,
                "The opposite roll must mirror the arm accents");
        helper.assertTrue(Math.abs(left.rightLegXRot + 20.0F) < 1.0E-4F
                        && Math.abs(left.leftLegXRot) < 1.0E-4F,
                "The opposite roll must mirror the leg accents");

        float early = FlightPoseLibrary.maneuverRollProgress(1, 0.1F);
        helper.assertTrue(Math.abs(early - 360.0F * FlightPoseLibrary.boostBlend(0.1F)) < 1.0E-3F,
                "The roll must follow the reference eased curve, got " + early);
        helper.assertTrue(early < 36.0F,
                "The reference curve must ease into the roll instead of turning linearly");
        helper.assertTrue(FlightPoseLibrary.maneuverRollProgress(1, 0.5F) == 180.0F,
                "The eased mid-roll must be exactly half a turn");

        FlightPhysics.BarrelRoll rightRoll = new FlightPhysics.BarrelRoll(1, 0.5F);
        Vec3 forward = new Vec3(0.0D, 0.0D, 1.0D);
        FlightPhysics.Step plain = step(forward, 0.0F, 0.0F, 0, false, 0L);
        FlightPhysics.Step rightStep = FlightPhysics.simulate(forward, 0.0F, 0.0F,
                0, false, 0L, PARAMS, rightRoll);
        helper.assertTrue(Math.abs(rightStep.motion().x + 0.15D) < 1.0E-6D,
                "A right-hand roll must deflect the velocity to the player's right, got "
                        + rightStep.motion().x);
        helper.assertTrue(Math.abs(rightStep.motion().z - 0.94D) < 1.0E-6D,
                "The mid-roll must scrub 6 percent of the horizontal speed, got "
                        + rightStep.motion().z);
        helper.assertTrue(Math.abs(rightStep.motion().y - plain.motion().y) < 1.0E-9D,
                "The barrel-roll impulse must stay horizontal");

        FlightPhysics.Step leftStep = FlightPhysics.simulate(forward, 0.0F, 0.0F,
                0, false, 0L, PARAMS, new FlightPhysics.BarrelRoll(-1, 0.5F));
        helper.assertTrue(Math.abs(leftStep.motion().x - 0.15D) < 1.0E-6D,
                "The opposite roll must deflect the velocity to the player's left, got "
                        + leftStep.motion().x);

        FlightPhysics.Step start = FlightPhysics.simulate(forward, 0.0F, 0.0F,
                0, false, 0L, PARAMS, new FlightPhysics.BarrelRoll(1, 0.0F));
        FlightPhysics.Step end = FlightPhysics.simulate(forward, 0.0F, 0.0F,
                0, false, 0L, PARAMS, new FlightPhysics.BarrelRoll(1, 1.0F));
        helper.assertTrue(start.motion().equals(plain.motion())
                        && end.motion().equals(plain.motion()),
                "The barrel-roll physics must be neutral outside the active envelope");
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
                FlightState.END_LANDED, 0.32F, 47.5F, true);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            FlightNetwork.FlightStateUpdate.encode(original, buffer);
            FlightNetwork.FlightStateUpdate decoded = FlightNetwork.FlightStateUpdate.decode(buffer);
            helper.assertTrue(decoded.equals(original),
                    "The flight state packet must preserve activation, maneuver and launch fields exactly");
            helper.assertTrue(buffer.readableBytes() == 0,
                    "The decoder must consume the complete state payload");
        } finally {
            buffer.release();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void cameraRollMatchesTheReferenceFormula(GameTestHelper helper) {
        helper.assertTrue(FlightCameraMath.rollTarget(new Vec3(0.0D, 0.0D, 1.0D),
                        new Vec3(0.0D, 0.0D, 1.0D)) == 0.0F,
                "A trajectory aligned with the crosshair must not roll the camera");
        helper.assertTrue(Math.abs(FlightCameraMath.rollTarget(new Vec3(0.0D, 0.0D, 1.0D),
                        new Vec3(1.0D, 0.0D, 0.0D)) + 36.0F) < 1.0E-4F,
                "A 90-degree right offset at full speed must target -36 degrees");
        helper.assertTrue(Math.abs(FlightCameraMath.rollTarget(new Vec3(0.0D, 0.0D, 1.0D),
                        new Vec3(-1.0D, 0.0D, 0.0D)) - 36.0F) < 1.0E-4F,
                "A 90-degree left offset at full speed must target +36 degrees");
        helper.assertTrue(Math.abs(FlightCameraMath.rollTarget(new Vec3(0.0D, 0.0D, 0.5D),
                        new Vec3(1.0D, 0.0D, 0.0D)) + 9.0F) < 1.0E-4F,
                "The roll target must scale with min(speed squared, 1)");
        helper.assertTrue(FlightCameraMath.rollTarget(new Vec3(0.0D, 0.0D, 1.0D),
                        new Vec3(0.0D, 0.0D, -1.0D)) == 0.0F,
                "A perfect head-on reversal has no cross product and reports zero");
        helper.assertTrue(FlightCameraMath.rollTarget(Vec3.ZERO,
                        new Vec3(0.0D, 0.0D, 1.0D)) == 0.0F,
                "A stationary player must not roll the camera");

        helper.assertTrue(Math.abs(FlightCameraMath.smooth(30.0F, 0.0F) - 20.0F) < 1.0E-4F,
                "Camera roll smoothing must use (target + 2 * previous) / 3");
        helper.assertTrue(Math.abs(FlightCameraMath.smooth(0.0F, 36.0F) - 12.0F) < 1.0E-4F,
                "A fresh roll target must be approached over several ticks");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void flightPivotAndReferenceConstantsAreStable(GameTestHelper helper) {
        helper.assertTrue(FlightPoseLibrary.FLIGHT_BODY_PIVOT_Y > FlightPoseLibrary.BODY_PIVOT_Y + 0.2F,
                "Flight must pivot above the locomotion knee-height pivot");
        helper.assertTrue(FlightPoseLibrary.FLIGHT_BODY_PIVOT_Y >= 0.9F
                        && FlightPoseLibrary.FLIGHT_BODY_PIVOT_Y <= 1.1F,
                "The flight pivot should sit at the vanilla torso centre");
        helper.assertTrue(FlightPoseLibrary.BOOST_HEAD_PITCH == -50.0F,
                "The boost head must keep the combined reference -50 degrees");
        helper.assertTrue(Math.abs(FlightPoseLibrary.boostBodyPitch(0.0F) + 80.0F) < 1.0E-4F,
                "The reference boost pitch at a level view is -80 degrees");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void activeFlightTravelMovesThePlayerAndInactiveTravelDoesNot(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        BlockPos start = helper.absolutePos(new BlockPos(3, 5, 3));
        player.setPos(start.getX() + 0.5D, start.getY(), start.getZ() + 0.5D);
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        player.setDeltaMovement(new Vec3(0.0D, 0.0D, 0.70D));
        FlightState state = FlightController.getState(player);
        helper.assertTrue(state != null, "Player must have a flight state capability");
        state.setActive(true);

        double before = player.getZ();
        helper.assertTrue(FlightController.travel(player),
                "The travel hook must handle an active flight state");
        helper.assertTrue(player.getZ() > before + 0.50D,
                "Active flight must move the player forward");
        helper.assertTrue(player.getDeltaMovement().z > 0.60D
                        && player.getDeltaMovement().z < 0.80D,
                "Travel must store the reference post-move drag, got "
                        + player.getDeltaMovement().z);

        state.setActive(false);
        player.setDeltaMovement(Vec3.ZERO);
        double stoppedZ = player.getZ();
        helper.assertTrue(!FlightController.travel(player),
                "Travel must fall back to vanilla when flight is inactive");
        helper.assertTrue(player.getZ() == stoppedZ,
                "Inactive travel must not move the player");
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
