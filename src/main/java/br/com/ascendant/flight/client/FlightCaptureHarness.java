package br.com.ascendant.flight.client;

import br.com.ascendant.flight.FlightController;
import br.com.ascendant.flight.FlightPoseSample;
import br.com.ascendant.flight.FlightState;
import br.com.ascendant.powers.PlayerPowerDataProvider;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Development-only visual capture harness for flight and maneuvers.
 *
 * <p>The class is registered by {@code Ascendant} only in a development client
 * and only when the JVM property {@code ascendant.flightCapture} is set.
 * It drives the integrated server through hover, sprint boost, a 360-degree
 * roll, a vertical loop, a ground takeoff, an idle hover, a free 180-degree
 * turn and a real landing, then writes labelled screenshots to
 * {@code run/screenshots/} plus an HTML contact sheet and a manifest.</p>
 *
 * <p>The harness calls the same server-authoritative entry points used by the
 * network handlers. It does not grant the client authority and it does not
 * replace human visual inspection; it only produces evidence for that review.
 * The free turn is driven by simulated movement keys and camera yaw, exactly
 * like player input, so there is no pre-programmed turn path to photograph.</p>
 *
 * <p>Run it with:</p>
 *
 * <pre>
 * $env:JAVA_TOOL_OPTIONS='-Dascendant.flightCapture=true'
 * .\gradlew.bat runClient --args="--quickPlaySingleplayer `"New World`""
 * </pre>
 */
public final class FlightCaptureHarness {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String ENABLE_PROPERTY = "ascendant.flightCapture";
    private static final String FILE_PREFIX = "ascendant-flight-";
    private static final String HTML_NAME = "ascendant-flight-capture.html";
    private static final String MANIFEST_NAME = "ascendant-flight-capture.txt";
    private static final DateTimeFormatter SESSION_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    /** Top surface of the capture platform; the player's feet rest here. */
    private static final double GROUND_Y = 219.0D;
    private static final double CAPTURE_Y = GROUND_Y + 1.0D;
    /** Clear air used by hover, turns and the start of the landing descent. */
    private static final double FLIGHT_Y = CAPTURE_Y + 10.0D;
    private static final double ENTRY_SPEED = 0.70D;
    /** Yaw change per tick while the harness performs the free 180-degree turn. */
    private static final float TURN_RATE_DEGREES = 10.0F;
    private static final int WORLD_LOAD_TIMEOUT_TICKS = 600;
    private static final int PLAN_TIMEOUT_TICKS = 80;
    /** Abort and retry a landing that stops descending instead of hanging the run. */
    private static final int LANDING_TIMEOUT_TICKS = 160;
    private static final int LANDING_RETRIES = 2;

    private static boolean running;
    private static volatile boolean prepared;
    private static volatile boolean finished;
    private static int ticksWithoutWorld;
    private static int ticksInWorld;
    private static int prepareAttempts;
    private static int step;
    private static int waitTicks;
    private static String session = "capture";
    private static Plan plan;
    private static StaticCapture staticCapture;
    private static int imageIndex;
    private static int turnTicks;
    private static boolean turnEarlyCaptured;
    private static boolean turnMiddleCaptured;
    private static boolean turnEndCaptured;
    private static boolean approachCaptured;
    private static boolean contactCaptured;
    private static boolean hoverCaptured;
    private static int recoveryWait;
    private static int landingTicks;
    private static int landingWaitTicks;
    private static int landingRetries;
    private static boolean landingSawActive;
    private static boolean holdForward;
    private static boolean holdSprint;
    private static final List<CapturedImage> images = new ArrayList<>();
    private static final List<String> manifest = new ArrayList<>();

    private FlightCaptureHarness() {
    }

    /** True only in a development client that explicitly asked for captures. */
    public static boolean enabled() {
        return !FMLEnvironment.production && Boolean.getBoolean(ENABLE_PROPERTY);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !enabled()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (finished) {
            return;
        }
        if (minecraft.player == null || minecraft.level == null) {
            if (++ticksWithoutWorld > WORLD_LOAD_TIMEOUT_TICKS) {
                LOGGER.error("[flight-capture] no world loaded; stopping the client");
                finished = true;
                minecraft.stop();
            }
            return;
        }
        MinecraftServer server = minecraft.getSingleplayerServer();
        if (server == null) {
            return;
        }

        if (!running) {
            begin(minecraft, server);
            return;
        }
        if (!prepared) {
            ticksInWorld++;
            if (ticksInWorld > 20 && ticksInWorld % 20 == 0) {
                requestPreparation(minecraft, server);
            }
            return;
        }

        tickPlan();
        // Simulated key state is fragile when the automated client loses focus
        // or the window manager consumes a release. Reassert the intended keys
        // every tick so a long stage cannot silently turn into an idle hover.
        applyHeldKeys(minecraft);
        tickStep(minecraft, server);
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (!enabled() || event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL
                || !prepared || finished) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        float partialTick = event.getPartialTick();
        FlightAnimationController.ManeuverView view =
                FlightAnimationController.maneuverView(player, partialTick);
        if (plan != null && view != null && view.kind() == plan.kind) {
            for (Target target : plan.targets) {
                if (!target.captured && view.progress() >= target.progress) {
                    target.captured = true;
                    capture(minecraft, player, target.label, partialTick, view);
                }
            }
        }
        if (staticCapture != null) {
            StaticCapture pending = staticCapture;
            staticCapture = null;
            capture(minecraft, player, pending.label, partialTick, view);
        }
    }

    private static void begin(Minecraft minecraft, MinecraftServer server) {
        running = true;
        session = LocalDateTime.now().format(SESSION_FORMAT);
        Path directory = minecraft.gameDirectory.toPath().resolve("screenshots");
        try {
            Files.createDirectories(directory);
            deletePrevious(directory);
        } catch (IOException exception) {
            LOGGER.warn("[flight-capture] could not clean previous captures", exception);
        }
        minecraft.options.hideGui = true;
        minecraft.options.pauseOnLostFocus = false;
        minecraft.setScreen(null);
        minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        LOGGER.info("[flight-capture] session {} started in {}", session,
                minecraft.gameDirectory.getAbsolutePath());
        requestPreparation(minecraft, server);
    }

    private static void requestPreparation(Minecraft minecraft, MinecraftServer server) {
        UUID playerId = minecraft.player == null ? null : minecraft.player.getUUID();
        if (playerId == null) {
            return;
        }
        prepareAttempts++;
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                return;
            }
            prepareServerPlayer(server, player);
            prepared = true;
            LOGGER.info("[flight-capture] player prepared at y={}", player.getY());
        });
    }

    private static void prepareServerPlayer(MinecraftServer server, ServerPlayer player) {
        prepareServerPlayer(server, player, CAPTURE_Y);
    }

    private static void prepareServerPlayer(MinecraftServer server, ServerPlayer player,
                                            double startY) {
        player.setGameMode(GameType.SURVIVAL);
        player.getAbilities().mayfly = false;
        player.getAbilities().flying = false;
        player.getAbilities().instabuild = false;
        player.getAbilities().invulnerable = true;
        player.onUpdateAbilities();
        player.setInvulnerable(true);
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.clearFire();
        player.setSprinting(false);
        // The capture save may carry an elytra from unrelated testing. Its
        // wings hide the arms and torso exactly where the flight pose is being
        // reviewed, so start every session with an empty inventory.
        player.getInventory().clearContent();
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);

        player.getCapability(PlayerPowerDataProvider.CAPABILITY)
                .ifPresent(data -> data.unlockPower(FlightController.FLIGHT_POWER));

        server.setDifficulty(Difficulty.PEACEFUL, true);
        server.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        server.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        server.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        ServerLevel level = server.overworld();
        level.setDayTime(6000L);
        level.setWeatherParameters(12000, 0, false, false);
        buildCaptureArea(level);
        player.teleportTo(level, 0.5D, startY, 0.5D, 0.0F, 0.0F);
        player.setOldPosAndRot();
        player.setYHeadRot(0.0F);

        FlightState state = FlightController.getState(player);
        if (state != null) {
            state.clearInput();
            state.clearManeuver();
            state.setManeuverAborted(false);
            state.setManeuverCooldownTicks(0);
        }
        if (!FlightController.requestActive(player, true)) {
            LOGGER.warn("[flight-capture] flight activation was refused");
        }
    }

    /**
     * Builds a small stone platform and clears the air above it. The platform
     * is what makes the takeoff launch and the final landing real server-side
     * events instead of poses staged in mid-air.
     */
    private static void buildCaptureArea(ServerLevel level) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int baseY = (int) GROUND_Y;
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                level.setBlockAndUpdate(cursor.set(x, baseY, z), Blocks.SMOOTH_STONE.defaultBlockState());
            }
        }
        for (int y = baseY + 1; y <= (int) FLIGHT_Y + 8; y++) {
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    BlockPos position = cursor.set(x, y, z);
                    if (!level.isEmptyBlock(position)) {
                        level.setBlockAndUpdate(position, Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
    }

    private static void tickPlan() {
        if (plan == null) {
            return;
        }
        plan.ticks++;
        boolean captured = plan.allCaptured();
        if (captured && plan.ticks > 30 || plan.ticks > PLAN_TIMEOUT_TICKS) {
            if (!captured) {
                LOGGER.warn("[flight-capture] plan for kind {} timed out with {}/{} captures",
                        plan.kind, plan.capturedCount(), plan.targets.size());
            }
            plan = null;
        }
    }

    private static void tickStep(Minecraft minecraft, MinecraftServer server) {
        // A screenshot is only consumed on the next render pass. Advancing the
        // script before that would let a later stage overwrite the pending
        // frame and label the image with a state it never showed.
        if (staticCapture != null) {
            return;
        }
        switch (step) {
            case 0 -> {
                step = 1;
                waitTicks = 40;
            }
            case 1 -> {
                if (--waitTicks <= 0) {
                    stageEntry(minecraft, server, true);
                    // Let real forward input settle the hover pose to full
                    // weight before photographing it.
                    setHeldKeys(minecraft, true, false);
                    step = 2;
                    waitTicks = 30;
                }
            }
            case 2 -> {
                if (--waitTicks <= 0) {
                    minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                    staticCapture = new StaticCapture("hover-level-back");
                    step = 3;
                    waitTicks = 10;
                }
            }
            case 3 -> {
                if (--waitTicks <= 0) {
                    minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                    staticCapture = new StaticCapture("hover-level-front");
                    // Hold the front hover long enough for the screenshot to be
                    // consumed before the boost stage teleports the player.
                    step = 32;
                    waitTicks = 10;
                }
            }
            case 32 -> {
                if (--waitTicks <= 0) {
                    startBoost(minecraft, server);
                    step = 21;
                    waitTicks = 24;
                }
            }
            case 21 -> {
                if (--waitTicks <= 0) {
                    staticCapture = new StaticCapture("boost-back");
                    step = 22;
                    waitTicks = 6;
                }
            }
            case 22 -> {
                if (--waitTicks <= 0) {
                    minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                    staticCapture = new StaticCapture("boost-front");
                    step = 30;
                    waitTicks = 8;
                }
            }
            case 30 -> {
                if (--waitTicks <= 0) {
                    // Releasing sprint leaves the reference's raw velocity for
                    // a few ticks, which is what drives the deceleration lean
                    // and the asymmetric trailing legs.
                    releaseMovementKeys(minecraft);
                    minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                    step = 31;
                    waitTicks = 4;
                }
            }
            case 31 -> {
                if (--waitTicks <= 0) {
                    staticCapture = new StaticCapture("boost-release");
                    step = 23;
                    waitTicks = 8;
                }
            }
            case 23 -> {
                if (--waitTicks <= 0) {
                    minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                    releaseMovementKeys(minecraft);
                    stageEntry(minecraft, server, false);
                    startPlan(minecraft, server, FlightState.MANEUVER_ROLL_360, 1,
                            new Target(0.15F, "roll-15"),
                            new Target(0.40F, "roll-40"),
                            new Target(0.65F, "roll-65"),
                            new Target(0.80F, "roll-80"));
                    step = 4;
                }
            }
            case 4 -> {
                if (plan == null) {
                    step = 5;
                    waitTicks = 30;
                }
            }
            case 5 -> {
                if (--waitTicks <= 0) {
                    teleportToStart(minecraft, server);
                    step = 6;
                    waitTicks = 10;
                }
            }
            case 6 -> {
                if (--waitTicks <= 0) {
                    startPlan(minecraft, server, FlightState.MANEUVER_LOOP, 1,
                            new Target(0.125F, "loop-125"),
                            new Target(0.30F, "loop-300"),
                            new Target(0.50F, "loop-500"),
                            new Target(0.70F, "loop-700"),
                            new Target(0.875F, "loop-875"));
                    step = 7;
                }
            }
            case 7 -> {
                if (plan == null) {
                    step = 8;
                    waitTicks = 40;
                }
            }
            case 8 -> {
                if (--waitTicks <= 0) {
                    prepareGroundStart(minecraft, server);
                    step = 9;
                    waitTicks = 12;
                }
            }
            case 9 -> {
                if (--waitTicks <= 0) {
                    beginGroundTakeoff(minecraft, server);
                    step = 10;
                    waitTicks = 2;
                }
            }
            case 10 -> {
                if (--waitTicks <= 0) {
                    staticCapture = new StaticCapture("takeoff-lift");
                    step = 11;
                    waitTicks = 6;
                }
            }
            case 11 -> {
                if (--waitTicks <= 0) {
                    staticCapture = new StaticCapture("takeoff-climb");
                    step = 12;
                    waitTicks = 8;
                }
            }
            case 12 -> {
                if (--waitTicks <= 0) {
                    releaseMovementKeys(minecraft);
                    stageHover(minecraft, server);
                    staticCapture = new StaticCapture("takeoff-level");
                    step = 13;
                    waitTicks = 30;
                }
            }
            case 13 -> {
                if (--waitTicks <= 0) {
                    staticCapture = new StaticCapture("hover-settle");
                    step = 14;
                    waitTicks = 28;
                }
            }
            case 14 -> {
                if (--waitTicks <= 0) {
                    if (!hoverCaptured) {
                        // Photograph the settled hover before the free-turn setup
                        // injects forward velocity.
                        hoverCaptured = true;
                        staticCapture = new StaticCapture("hover-bob");
                        waitTicks = 4;
                    } else {
                        beginHoverLookUp(minecraft, server);
                        step = 24;
                        waitTicks = 20;
                    }
                }
            }
            case 24 -> {
                if (--waitTicks <= 0) {
                    staticCapture = new StaticCapture("hover-look-up");
                    step = 25;
                    waitTicks = 24;
                }
            }
            case 25 -> {
                if (--waitTicks <= 0) {
                    beginBoostLookUp(minecraft, server);
                    step = 26;
                    waitTicks = 24;
                }
            }
            case 26 -> {
                if (--waitTicks <= 0) {
                    staticCapture = new StaticCapture("boost-look-up");
                    step = 28;
                    waitTicks = 6;
                }
            }
            case 28 -> {
                if (--waitTicks <= 0) {
                    beginBoostTurn(minecraft, server);
                    // First establish a straight-line boost, then yaw away from
                    // it so the turn offsets and bank are measurable.
                    step = 33;
                    waitTicks = 20;
                }
            }
            case 33 -> {
                if (--waitTicks <= 0) {
                    setYaw(minecraft, server, 75.0F);
                    setHeldKeys(minecraft, true, true);
                    step = 29;
                    waitTicks = 5;
                }
            }
            case 29 -> {
                if (--waitTicks <= 0) {
                    staticCapture = new StaticCapture("boost-turn");
                    step = 27;
                    waitTicks = 6;
                }
            }
            case 27 -> {
                if (--waitTicks <= 0) {
                    beginFreeTurn(minecraft, server);
                    step = 15;
                }
            }
            case 15 -> tickFreeTurn(minecraft, server);
            case 16 -> {
                if (--waitTicks <= 0) {
                    staticCapture = new StaticCapture("turn-180-settled");
                    releaseMovementKeys(minecraft);
                    step = 17;
                    waitTicks = 8;
                }
            }
            case 17 -> {
                if (--waitTicks <= 0) {
                    landingRetries = 0;
                    beginLandingApproach(minecraft, server);
                    step = 18;
                }
            }
            case 18 -> tickLandingWatch(minecraft, server);
            case 19 -> {
                if (--waitTicks <= 0) {
                    staticCapture = new StaticCapture("landing-recovered");
                    step = 20;
                    waitTicks = 12;
                }
            }
            case 20 -> {
                if (--waitTicks <= 0) {
                    finish(minecraft);
                }
            }
            default -> finish(minecraft);
        }
    }

    private static void tickFreeTurn(Minecraft minecraft, MinecraftServer server) {
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        turnTicks++;
        float yaw = Math.min(180.0F, turnTicks * TURN_RATE_DEGREES);
        setYaw(minecraft, server, yaw);
        if (!turnEarlyCaptured && yaw >= 60.0F) {
            turnEarlyCaptured = true;
            staticCapture = new StaticCapture("turn-60");
        }
        if (!turnMiddleCaptured && yaw >= 120.0F) {
            turnMiddleCaptured = true;
            staticCapture = new StaticCapture("turn-120");
        }
        if (yaw >= 180.0F) {
            if (!turnEndCaptured) {
                turnEndCaptured = true;
                staticCapture = new StaticCapture("turn-180");
            }
            step = 16;
            waitTicks = 12;
        }
    }

    /**
     * Watches the real server-side landing. The approach frame is taken while
     * the descent is still active; the contact and recovery frames are taken
     * after the server has already deactivated the flight with the landed
     * reason, so they photograph the actual recovery animation.
     */
    private static void tickLandingWatch(Minecraft minecraft, MinecraftServer server) {
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        FlightState state = FlightController.getState(player);
        boolean active = state != null && state.isActive();
        landingWaitTicks++;
        if (active) {
            landingSawActive = true;
            landingTicks++;
            // Keep the real descent input and orientation locked. A lost key or
            // a drifting camera otherwise turns the approach into a hover and
            // the capture session hangs above the pad.
            setHeldKeys(minecraft, true, false);
            lockLandingLook(minecraft, server);
            if (!approachCaptured && player.getY() < CAPTURE_Y + 2.0D
                    && player.getDeltaMovement().y < -0.02D) {
                approachCaptured = true;
                staticCapture = new StaticCapture("landing-approach");
            }
            if (landingTicks > LANDING_TIMEOUT_TICKS) {
                if (landingRetries < LANDING_RETRIES) {
                    landingRetries++;
                    LOGGER.warn("[flight-capture] landing stalled at y={}; retry {}",
                            player.getY(), landingRetries);
                    beginLandingApproach(minecraft, server);
                } else {
                    LOGGER.warn("[flight-capture] landing never contacted the ground at y={}; "
                            + "finishing without a contact frame", player.getY());
                    releaseMovementKeys(minecraft);
                    staticCapture = new StaticCapture("landing-timeout");
                    step = 20;
                    waitTicks = 12;
                }
            }
            return;
        }
        if (!landingSawActive && landingWaitTicks > LANDING_TIMEOUT_TICKS) {
            LOGGER.warn("[flight-capture] flight did not resume for the landing stage");
            releaseMovementKeys(minecraft);
            staticCapture = new StaticCapture("landing-timeout");
            step = 20;
            waitTicks = 12;
            return;
        }
        if (!contactCaptured && landingSawActive) {
            contactCaptured = true;
            releaseMovementKeys(minecraft);
            staticCapture = new StaticCapture("landing-contact");
            recoveryWait = 4;
            return;
        }
        if (contactCaptured && recoveryWait > 0 && --recoveryWait == 0) {
            staticCapture = new StaticCapture("landing-recovery");
            releaseMovementKeys(minecraft);
            step = 19;
            waitTicks = 16;
        }
    }

    /**
     * Reasserts the straight-down approach each tick. The local rotation is
     * what the client sends to the integrated server; the server copy is only
     * refreshed periodically to avoid flooding its task queue.
     */
    private static void lockLandingLook(Minecraft minecraft, MinecraftServer server) {
        LocalPlayer local = minecraft.player;
        if (local != null) {
            local.setYRot(0.0F);
            local.setYHeadRot(0.0F);
            local.setYBodyRot(0.0F);
            local.setXRot(90.0F);
            local.setSprinting(false);
        }
        if (landingTicks % 5 != 1) {
            return;
        }
        UUID playerId = local == null ? null : local.getUUID();
        if (playerId == null) {
            return;
        }
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                return;
            }
            player.setYRot(0.0F);
            player.setYHeadRot(0.0F);
            player.setYBodyRot(0.0F);
            player.setXRot(90.0F);
            player.setSprinting(false);
        });
    }

    private static void prepareGroundStart(Minecraft minecraft, MinecraftServer server) {
        releaseMovementKeys(minecraft);
        UUID playerId = minecraft.player == null ? null : minecraft.player.getUUID();
        if (playerId != null) {
            server.execute(() -> {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player == null) {
                    return;
                }
                FlightController.requestActive(player, false);
                player.teleportTo(player.serverLevel(), 0.5D, CAPTURE_Y, 0.5D, 0.0F, 0.0F);
                player.setOldPosAndRot();
                player.setOnGround(true);
                player.setDeltaMovement(Vec3.ZERO);
                player.setSprinting(false);
            });
        }
        LocalPlayer local = minecraft.player;
        if (local != null) {
            local.setPos(0.5D, CAPTURE_Y, 0.5D);
            local.setOldPosAndRot();
            local.setDeltaMovement(Vec3.ZERO);
            local.setSprinting(false);
            local.setYRot(0.0F);
            local.setYHeadRot(0.0F);
            local.setYBodyRot(0.0F);
            local.setXRot(0.0F);
        }
    }

    private static void beginGroundTakeoff(Minecraft minecraft, MinecraftServer server) {
        releaseMovementKeys(minecraft);
        UUID playerId = minecraft.player == null ? null : minecraft.player.getUUID();
        if (playerId != null) {
            server.execute(() -> {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player == null) {
                    return;
                }
                player.teleportTo(player.serverLevel(), 0.5D, CAPTURE_Y, 0.5D, 0.0F, 0.0F);
                player.setOldPosAndRot();
                // A teleport keeps the previous onGround flag. The player is
                // physically on the platform, so mark it before activation or
                // the launch would be classified as an air start.
                player.setOnGround(true);
                player.setDeltaMovement(Vec3.ZERO);
                player.setYRot(0.0F);
                player.setYHeadRot(0.0F);
                player.setYBodyRot(0.0F);
                // Looking up while holding forward is the reference way to
                // leave the ground; jump has no vertical role in this flight.
                player.setXRot(-20.0F);
                player.setSprinting(false);
                boolean active = FlightController.requestActive(player, true);
                LOGGER.info("[flight-capture] ground takeoff active={} onGround={}",
                        active, player.onGround());
            });
        }
        LocalPlayer local = minecraft.player;
        if (local != null) {
            local.setPos(0.5D, CAPTURE_Y, 0.5D);
            local.setOldPosAndRot();
            local.setDeltaMovement(Vec3.ZERO);
            local.setSprinting(false);
            local.setYRot(0.0F);
            local.setYHeadRot(0.0F);
            local.setYBodyRot(0.0F);
            local.setXRot(-20.0F);
        }
        // Forward input (not jump) provides the lift impulse. The pitch also
        // tilts the impulse upward, so the capture shows a real ascent.
        setHeldKeys(minecraft, true, false);
    }

    private static void startBoost(Minecraft minecraft, MinecraftServer server) {
        releaseMovementKeys(minecraft);
        minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        UUID playerId = minecraft.player == null ? null : minecraft.player.getUUID();
        if (playerId != null) {
            server.execute(() -> {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player == null) {
                    return;
                }
                FlightState state = FlightController.getState(player);
                if (state == null || !state.isActive()) {
                    FlightController.requestActive(player, true);
                }
                player.teleportTo(player.serverLevel(), 0.5D, FLIGHT_Y, 0.5D, 0.0F, 0.0F);
                player.setOldPosAndRot();
                player.setDeltaMovement(0.0D, 0.0D, 0.0D);
                player.setYRot(0.0F);
                player.setYHeadRot(0.0F);
                player.setYBodyRot(0.0F);
                player.setXRot(0.0F);
                player.setSprinting(true);
            });
        }
        LocalPlayer local = minecraft.player;
        if (local != null) {
            local.setPos(0.5D, FLIGHT_Y, 0.5D);
            local.setOldPosAndRot();
            local.setDeltaMovement(Vec3.ZERO);
            local.setYRot(0.0F);
            local.setYHeadRot(0.0F);
            local.setYBodyRot(0.0F);
            local.setXRot(0.0F);
            local.setSprinting(true);
        }
        setHeldKeys(minecraft, true, true);
    }

    /**
     * Establish a straight sprint boost before the crosshair is moved away from
     * the trajectory. The following yaw step then exposes the reference turn
     * offsets and bank without the previous stage's residual direction.
     */
    private static void beginBoostTurn(Minecraft minecraft, MinecraftServer server) {
        releaseMovementKeys(minecraft);
        minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        float yaw = 0.0F;
        UUID playerId = minecraft.player == null ? null : minecraft.player.getUUID();
        if (playerId != null) {
            server.execute(() -> {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player == null) {
                    return;
                }
                FlightState state = FlightController.getState(player);
                if (state == null || !state.isActive()) {
                    FlightController.requestActive(player, true);
                }
                player.teleportTo(player.serverLevel(), 0.5D, FLIGHT_Y, 0.5D, yaw, 0.0F);
                player.setOldPosAndRot();
                player.setDeltaMovement(0.0D, 0.0D, 2.2D);
                player.setSprinting(true);
                player.setYRot(yaw);
                player.setYHeadRot(yaw);
                player.setYBodyRot(yaw);
                player.setXRot(0.0F);
            });
        }
        LocalPlayer local = minecraft.player;
        if (local != null) {
            local.setPos(0.5D, FLIGHT_Y, 0.5D);
            local.setOldPosAndRot();
            local.setDeltaMovement(0.0D, 0.0D, 2.2D);
            local.setSprinting(true);
            local.setYRot(yaw);
            local.setYHeadRot(yaw);
            local.setYBodyRot(yaw);
            local.setXRot(0.0F);
        }
        setHeldKeys(minecraft, true, true);
    }

    private static void stageHover(Minecraft minecraft, MinecraftServer server) {
        releaseMovementKeys(minecraft);
        UUID playerId = minecraft.player == null ? null : minecraft.player.getUUID();
        if (playerId != null) {
            server.execute(() -> {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player == null) {
                    return;
                }
                FlightState state = FlightController.getState(player);
                if (state == null || !state.isActive()) {
                    FlightController.requestActive(player, true);
                }
                player.teleportTo(player.serverLevel(), 0.5D, FLIGHT_Y, 0.5D, 0.0F, 0.0F);
                player.setOldPosAndRot();
                player.setDeltaMovement(Vec3.ZERO);
                player.setSprinting(false);
                player.setYRot(0.0F);
                player.setYHeadRot(0.0F);
                player.setYBodyRot(0.0F);
                player.setXRot(0.0F);
            });
        }
        LocalPlayer local = minecraft.player;
        if (local != null) {
            local.setPos(0.5D, FLIGHT_Y, 0.5D);
            local.setOldPosAndRot();
            local.setDeltaMovement(Vec3.ZERO);
            local.setSprinting(false);
            local.setYRot(0.0F);
            local.setYHeadRot(0.0F);
            local.setYBodyRot(0.0F);
            local.setXRot(0.0F);
        }
    }

    /**
     * Hovering forward while looking steeply up. The torso must stay upright
     * like the reference hover, the neck must follow the real -60 degree look,
     * and the pose must not turn the ascent into a boost silhouette.
     */
    private static void beginHoverLookUp(Minecraft minecraft, MinecraftServer server) {
        releaseMovementKeys(minecraft);
        stageLookUp(minecraft, server, false, -60.0F);
        minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        setHeldKeys(minecraft, true, false);
    }

    /** Sprinting forward while looking up: the reference body pitch follows the view. */
    private static void beginBoostLookUp(Minecraft minecraft, MinecraftServer server) {
        releaseMovementKeys(minecraft);
        stageLookUp(minecraft, server, true, -60.0F);
        minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        setHeldKeys(minecraft, true, true);
    }

    private static void stageLookUp(Minecraft minecraft, MinecraftServer server,
                                    boolean sprinting, float pitch) {
        UUID playerId = minecraft.player == null ? null : minecraft.player.getUUID();
        if (playerId != null) {
            server.execute(() -> {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player == null) {
                    return;
                }
                FlightState state = FlightController.getState(player);
                if (state == null || !state.isActive()) {
                    FlightController.requestActive(player, true);
                }
                player.teleportTo(player.serverLevel(), 0.5D, FLIGHT_Y, 0.5D, 0.0F, pitch);
                player.setOldPosAndRot();
                player.setDeltaMovement(Vec3.ZERO);
                player.setSprinting(sprinting);
                player.setYRot(0.0F);
                player.setYHeadRot(0.0F);
                player.setYBodyRot(0.0F);
                player.setXRot(pitch);
            });
        }
        LocalPlayer local = minecraft.player;
        if (local != null) {
            local.setPos(0.5D, FLIGHT_Y, 0.5D);
            local.setOldPosAndRot();
            local.setDeltaMovement(Vec3.ZERO);
            local.setSprinting(sprinting);
            local.setYRot(0.0F);
            local.setYHeadRot(0.0F);
            local.setYBodyRot(0.0F);
            local.setXRot(pitch);
        }
    }

    private static void beginFreeTurn(Minecraft minecraft, MinecraftServer server) {
        turnTicks = 0;
        turnEarlyCaptured = false;
        turnMiddleCaptured = false;
        turnEndCaptured = false;
        releaseMovementKeys(minecraft);
        minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        UUID playerId = minecraft.player == null ? null : minecraft.player.getUUID();
        if (playerId != null) {
            server.execute(() -> {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player == null) {
                    return;
                }
                FlightState state = FlightController.getState(player);
                if (state == null || !state.isActive()) {
                    FlightController.requestActive(player, true);
                }
                player.teleportTo(player.serverLevel(), 0.5D, FLIGHT_Y, 0.5D, 0.0F, 0.0F);
                player.setOldPosAndRot();
                player.setDeltaMovement(0.0D, 0.0D, 0.55D);
                player.setSprinting(false);
                player.setYRot(0.0F);
                player.setYHeadRot(0.0F);
                player.setYBodyRot(0.0F);
                player.setXRot(0.0F);
            });
        }
        LocalPlayer local = minecraft.player;
        if (local != null) {
            local.setPos(0.5D, FLIGHT_Y, 0.5D);
            local.setOldPosAndRot();
            local.setDeltaMovement(0.0D, 0.0D, 0.55D);
            local.setSprinting(false);
            local.setYRot(0.0F);
            local.setYHeadRot(0.0F);
            local.setYBodyRot(0.0F);
            local.setXRot(0.0F);
        }
        // Forward input is the only thing that turns the trajectory. The yaw is
        // rotated gradually below, so this is ordinary free flight: no maneuver
        // packet, no scripted arc and no server-owned turn radius.
        setHeldKeys(minecraft, true, false);
    }

    private static void setYaw(Minecraft minecraft, MinecraftServer server, float yaw) {
        LocalPlayer local = minecraft.player;
        if (local != null) {
            local.setYRot(yaw);
            local.setYHeadRot(yaw);
            local.setYBodyRot(yaw);
        }
        UUID playerId = local == null ? null : local.getUUID();
        if (playerId != null) {
            server.execute(() -> {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player != null) {
                    player.setYRot(yaw);
                    player.setYHeadRot(yaw);
                    player.setYBodyRot(yaw);
                }
            });
        }
    }

    private static void beginLandingApproach(Minecraft minecraft, MinecraftServer server) {
        releaseMovementKeys(minecraft);
        approachCaptured = false;
        contactCaptured = false;
        recoveryWait = 0;
        landingTicks = 0;
        landingWaitTicks = 0;
        landingSawActive = false;
        // Straight-down look removes the horizontal hover impulse, so the
        // descent stays above the prepared pad instead of drifting away.
        double startY = FLIGHT_Y;
        UUID playerId = minecraft.player == null ? null : minecraft.player.getUUID();
        if (playerId != null) {
            server.execute(() -> {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player == null) {
                    return;
                }
                FlightState state = FlightController.getState(player);
                if (state == null || !state.isActive()) {
                    FlightController.requestActive(player, true);
                }
                player.teleportTo(player.serverLevel(), 0.5D, startY, 0.5D, 0.0F, 0.0F);
                player.setOldPosAndRot();
                player.setOnGround(false);
                player.setDeltaMovement(Vec3.ZERO);
                player.setSprinting(false);
                player.setYRot(0.0F);
                player.setYHeadRot(0.0F);
                player.setYBodyRot(0.0F);
                // Looking straight down while holding forward is the reference
                // vertical descent; sneak is not a vertical control.
                player.setXRot(90.0F);
            });
        }
        LocalPlayer local = minecraft.player;
        if (local != null) {
            local.setPos(0.5D, startY, 0.5D);
            local.setOldPosAndRot();
            local.setOnGround(false);
            local.setDeltaMovement(Vec3.ZERO);
            local.setSprinting(false);
            local.setYRot(0.0F);
            local.setYHeadRot(0.0F);
            local.setYBodyRot(0.0F);
            local.setXRot(90.0F);
        }
        setHeldKeys(minecraft, true, false);
    }

    private static void setHeldKeys(Minecraft minecraft, boolean forward, boolean sprint) {
        holdForward = forward;
        holdSprint = sprint;
        applyHeldKeys(minecraft);
    }

    private static void applyHeldKeys(Minecraft minecraft) {
        minecraft.options.keyUp.setDown(holdForward);
        minecraft.options.keySprint.setDown(holdSprint);
    }

    private static void releaseMovementKeys(Minecraft minecraft) {
        holdForward = false;
        holdSprint = false;
        minecraft.options.keyUp.setDown(false);
        minecraft.options.keyDown.setDown(false);
        minecraft.options.keyLeft.setDown(false);
        minecraft.options.keyRight.setDown(false);
        minecraft.options.keyJump.setDown(false);
        minecraft.options.keyShift.setDown(false);
        minecraft.options.keySprint.setDown(false);
    }

    private static void startPlan(Minecraft minecraft, MinecraftServer server, int kind,
                                  int direction, Target... targets) {
        Plan next = new Plan(kind, targets);
        plan = next;
        UUID playerId = minecraft.player == null ? null : minecraft.player.getUUID();
        if (playerId == null) {
            return;
        }
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                LOGGER.warn("[flight-capture] server player vanished before maneuver {}", kind);
                return;
            }
            prepareServerPlayer(server, player, FLIGHT_Y);
            player.setDeltaMovement(0.0D, 0.0D, ENTRY_SPEED);
            player.setYRot(0.0F);
            player.setYHeadRot(0.0F);
            player.setXRot(0.0F);
            player.setSprinting(false);
            boolean accepted = FlightController.requestManeuver(player, kind, direction);
            LOGGER.info("[flight-capture] maneuver kind {} direction {} accepted={}",
                    kind, direction, accepted);
            if (!accepted) {
                player.displayClientMessage(Component
                        .literal("Capture maneuver refused: " + kind)
                        .withStyle(ChatFormatting.RED), false);
            }
        });
    }

    private static void stageEntry(Minecraft minecraft, MinecraftServer server, boolean resetPosition) {
        releaseMovementKeys(minecraft);
        LocalPlayer local = minecraft.player;
        if (local != null) {
            local.setSprinting(false);
        }
        UUID playerId = minecraft.player == null ? null : minecraft.player.getUUID();
        if (playerId == null) {
            return;
        }
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                return;
            }
            if (resetPosition) {
                player.teleportTo(player.serverLevel(), 0.5D, FLIGHT_Y, 0.5D, 0.0F, 0.0F);
                FlightState state = FlightController.getState(player);
                if (state == null || !state.isActive()) {
                    FlightController.requestActive(player, true);
                }
            }
            player.setDeltaMovement(0.0D, 0.0D, ENTRY_SPEED);
            player.setYRot(0.0F);
            player.setYHeadRot(0.0F);
            player.setXRot(0.0F);
            player.setSprinting(false);
        });
        if (local == null) {
            return;
        }
        if (resetPosition) {
            local.setPos(0.5D, FLIGHT_Y, 0.5D);
            local.setOldPosAndRot();
        }
        local.setDeltaMovement(0.0D, 0.0D, ENTRY_SPEED);
        local.setYRot(0.0F);
        local.setYHeadRot(0.0F);
        local.setXRot(0.0F);
    }

    private static void teleportToStart(Minecraft minecraft, MinecraftServer server) {
        server.execute(() -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.teleportTo(player.serverLevel(), 0.5D, FLIGHT_Y, 0.5D, 0.0F, 0.0F);
                player.setOldPosAndRot();
                player.setDeltaMovement(0.0D, 0.0D, 0.0D);
                player.setYHeadRot(0.0F);
            }
        });
        LocalPlayer local = minecraft.player;
        if (local != null) {
            local.setPos(0.5D, FLIGHT_Y, 0.5D);
            local.setOldPosAndRot();
            local.setDeltaMovement(Vec3.ZERO);
            local.setYHeadRot(0.0F);
            local.setYBodyRot(0.0F);
            local.setXRot(0.0F);
        }
    }

    private static void capture(Minecraft minecraft, LocalPlayer player, String label,
                                float partialTick,
                                FlightAnimationController.ManeuverView view) {
        String name = String.format(Locale.ROOT, "%s%s-%02d-%s.png",
                FILE_PREFIX, session, ++imageIndex, label);
        String kind = view == null ? "-" : Integer.toString(view.kind());
        String progress = view == null ? "-" : String.format(Locale.ROOT, "%.3f", view.progress());
        FlightPoseSample sample = FlightAnimationController.sample(player, partialTick);
        String pose = sample == null ? "weight=-" : String.format(Locale.ROOT,
                "weight=%.2f bodyPitch=%.1f bodyRoll=%.1f headPitch=%.1f "
                        + "maneuverPitch=%.1f maneuverRoll=%.1f rollPitch=%.1f rollRArm=%.1f",
                sample.weight(), sample.pose().bodyPitchDegrees,
                sample.pose().bodyRollDegrees, sample.pose().headXRotDegrees,
                sample.maneuverPitchDegrees(), sample.maneuverRollDegrees(),
                sample.rollOverlay().bodyPitchDegrees, sample.rollOverlay().rightArmXRot);
        double stepX = player.getX() - player.xOld;
        double stepY = player.getY() - player.yOld;
        double stepZ = player.getZ() - player.zOld;
        double stepSpeed = Math.sqrt(stepX * stepX + stepY * stepY + stepZ * stepZ);
        String line = String.format(Locale.ROOT,
                "%s | %s | kind=%s | progress=%s | pos=%.2f,%.2f,%.2f | yaw=%.1f | pitch=%.1f | step=%.2f | camera=%s | %s",
                name, label, kind, progress,
                player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot(), stepSpeed,
                minecraft.options.getCameraType().name(), pose);
        images.add(new CapturedImage(name, label, line));
        manifest.add(line);
        LOGGER.info("[flight-capture] {}", line);
        Screenshot.grab(minecraft.gameDirectory, name, minecraft.getMainRenderTarget(),
                message -> LOGGER.info("[flight-capture] saved {}", name));
    }

    private static void finish(Minecraft minecraft) {
        if (finished) {
            return;
        }
        finished = true;
        releaseMovementKeys(minecraft);
        writeReport(minecraft);
        minecraft.options.hideGui = false;
        LOGGER.info("[flight-capture] complete: {} images in {}", images.size(),
                minecraft.gameDirectory.toPath().resolve("screenshots"));
        minecraft.stop();
    }

    private static void writeReport(Minecraft minecraft) {
        Path directory = minecraft.gameDirectory.toPath().resolve("screenshots");
        try {
            Files.createDirectories(directory);
            Files.writeString(directory.resolve(MANIFEST_NAME),
                    String.join(System.lineSeparator(), manifest) + System.lineSeparator(),
                    StandardCharsets.UTF_8);
            Files.writeString(directory.resolve(HTML_NAME), buildHtml(), StandardCharsets.UTF_8);
            LOGGER.info("[flight-capture] report written to {}",
                    directory.resolve(HTML_NAME).toAbsolutePath());
        } catch (IOException exception) {
            LOGGER.warn("[flight-capture] could not write the capture report", exception);
        }
    }

    private static String buildHtml() {
        StringBuilder html = new StringBuilder(4096);
        html.append("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">")
                .append("<title>Ascendant flight capture ").append(session).append("</title>")
                .append("<style>body{margin:0;padding:24px;background:#0b0d12;color:#e7ecf5;")
                .append("font:14px/1.5 system-ui,sans-serif}h1{font-size:22px;margin:0 0 6px}")
                .append("h2{font-size:15px;color:#8ea3c4;margin:26px 0 10px;font-weight:600}")
                .append("p{color:#9fb0c9;max-width:1100px}.grid{display:grid;")
                .append("grid-template-columns:repeat(auto-fit,minmax(310px,1fr));gap:14px}")
                .append("figure{margin:0;background:#131826;border:1px solid #253044;")
                .append("border-radius:8px;overflow:hidden}img{display:block;width:100%;height:auto}")
                .append("figcaption{padding:8px 10px;font-size:12px;color:#b9c8de}")
                .append("code{color:#d7e3f5}</style></head><body>")
                .append("<h1>Ascendant flight visual capture</h1>")
                .append("<p>Dev-only harness output, session <code>").append(session)
                .append("</code>. These frames are evidence for human homologation, not a ")
                .append("substitute for interactive review. Camera: third-person; GUI hidden; ")
                .append("survival flight above a stone capture platform with clear noon weather. ")
                .append("Takeoff and landing are real ground contacts; the 180-degree turn is ")
                .append("ordinary forward input with a gradually rotated camera yaw.</p>");
        String lastGroup = null;
        boolean gridOpen = false;
        for (CapturedImage image : images) {
            String group = groupOf(image.label);
            if (!group.equals(lastGroup)) {
                if (gridOpen) {
                    html.append("</div>");
                }
                html.append("<h2>").append(group).append("</h2><div class=\"grid\">");
                lastGroup = group;
                gridOpen = true;
            }
            html.append("<figure><img src=\"").append(image.file).append("\" alt=\"")
                    .append(image.label).append("\"><figcaption>")
                    .append(image.label).append("</figcaption></figure>");
        }
        if (gridOpen) {
            html.append("</div>");
        }
        html.append("</body></html>");
        return html.toString();
    }

    private static String groupOf(String label) {
        if (label.contains("roll")) {
            return "360-degree roll";
        }
        if (label.contains("loop")) {
            return "Vertical loop";
        }
        if (label.contains("takeoff")) {
            return "Ground takeoff";
        }
        if (label.contains("boost")) {
            return "Sprint boost";
        }
        if (label.contains("hover")) {
            return "Hover and forward hover";
        }
        if (label.contains("turn")) {
            return "Free 180-degree turn";
        }
        if (label.contains("landing")) {
            return "Landing";
        }
        return "Other";
    }

    private static void deletePrevious(Path directory) throws IOException {
        try (DirectoryStream<Path> stream =
                     Files.newDirectoryStream(directory, FILE_PREFIX + "*.png")) {
            for (Path path : stream) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static final class Target {
        private final float progress;
        private final String label;
        private volatile boolean captured;

        private Target(float progress, String label) {
            this.progress = progress;
            this.label = label;
        }
    }

    private static final class Plan {
        private final int kind;
        private final List<Target> targets;
        private int ticks;

        private Plan(int kind, Target[] targets) {
            this.kind = kind;
            this.targets = List.of(targets);
        }

        private boolean allCaptured() {
            for (Target target : targets) {
                if (!target.captured) {
                    return false;
                }
            }
            return true;
        }

        private int capturedCount() {
            int count = 0;
            for (Target target : targets) {
                if (target.captured) {
                    count++;
                }
            }
            return count;
        }
    }

    private record StaticCapture(String label) {
    }

    private record CapturedImage(String file, String label, String detail) {
    }
}
