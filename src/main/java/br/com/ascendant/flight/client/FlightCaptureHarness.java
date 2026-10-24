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
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
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
 * It drives the integrated server through survival flight, a 360-degree roll, a
 * vertical loop and a 180-degree reversal, then writes labelled screenshots to
 * {@code run/screenshots/} plus an HTML contact sheet and a manifest.</p>
 *
 * <p>The harness calls the same server-authoritative entry points used by the
 * network handlers. It does not grant the client authority and it does not
 * replace human visual inspection; it only produces evidence for that review.
 * Run it with:</p>
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
    private static final double CAPTURE_Y = 220.0D;
    private static final double ENTRY_SPEED = 0.70D;
    private static final int WORLD_LOAD_TIMEOUT_TICKS = 600;
    private static final int PLAN_TIMEOUT_TICKS = 80;

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
        player.teleportTo(level, 0.5D, CAPTURE_Y, 0.5D, 0.0F, 0.0F);
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
        switch (step) {
            case 0 -> {
                step = 1;
                waitTicks = 40;
            }
            case 1 -> {
                if (--waitTicks <= 0) {
                    stageEntry(minecraft, server, false);
                    staticCapture = new StaticCapture("level-back");
                    step = 2;
                    waitTicks = 5;
                }
            }
            case 2 -> {
                if (--waitTicks <= 0) {
                    minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                    stageEntry(minecraft, server, false);
                    staticCapture = new StaticCapture("level-front");
                    step = 3;
                    waitTicks = 8;
                }
            }
            case 3 -> {
                if (--waitTicks <= 0) {
                    minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
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
                    teleportToStart(server);
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
                    waitTicks = 32;
                }
            }
            case 8 -> {
                if (--waitTicks <= 0) {
                    teleportToStart(server);
                    step = 9;
                    waitTicks = 10;
                }
            }
            case 9 -> {
                if (--waitTicks <= 0) {
                    startPlan(minecraft, server, FlightState.MANEUVER_REVERSAL, 1,
                            new Target(0.15F, "reversal-15"),
                            new Target(0.40F, "reversal-40"),
                            new Target(0.65F, "reversal-65"),
                            new Target(0.90F, "reversal-90"));
                    step = 10;
                }
            }
            case 10 -> {
                if (plan == null) {
                    step = 11;
                    waitTicks = 30;
                }
            }
            case 11 -> {
                if (--waitTicks <= 0) {
                    stageEntry(minecraft, server, true);
                    staticCapture = new StaticCapture("final-level");
                    step = 12;
                    waitTicks = 40;
                }
            }
            case 12 -> {
                if (--waitTicks <= 0) {
                    finish(minecraft);
                }
            }
            default -> finish(minecraft);
        }
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
            prepareServerPlayer(server, player);
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
                player.teleportTo(player.serverLevel(), 0.5D, CAPTURE_Y, 0.5D, 0.0F, 0.0F);
            }
            player.setDeltaMovement(0.0D, 0.0D, ENTRY_SPEED);
            player.setYRot(0.0F);
            player.setYHeadRot(0.0F);
            player.setXRot(0.0F);
            player.setSprinting(false);
        });
        LocalPlayer local = minecraft.player;
        if (resetPosition) {
            local.setPos(0.5D, CAPTURE_Y, 0.5D);
        }
        local.setDeltaMovement(0.0D, 0.0D, ENTRY_SPEED);
        local.setYRot(0.0F);
        local.setYHeadRot(0.0F);
        local.setXRot(0.0F);
    }

    private static void teleportToStart(MinecraftServer server) {
        server.execute(() -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.teleportTo(player.serverLevel(), 0.5D, CAPTURE_Y, 0.5D, 0.0F, 0.0F);
                player.setDeltaMovement(0.0D, 0.0D, 0.0D);
                player.setYHeadRot(0.0F);
            }
        });
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
                "weight=%.2f bodyPitch=%.1f bodyRoll=%.1f maneuverPitch=%.1f maneuverRoll=%.1f",
                sample.weight(), sample.pose().bodyPitchDegrees,
                sample.pose().bodyRollDegrees, sample.maneuverPitchDegrees(),
                sample.maneuverRollDegrees());
        String line = String.format(Locale.ROOT,
                "%s | %s | kind=%s | progress=%s | pos=%.2f,%.2f,%.2f | yaw=%.1f | pitch=%.1f | speed=%.2f | camera=%s | %s",
                name, label, kind, progress,
                player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot(), player.getDeltaMovement().length(),
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
                .append("survival flight at y=220 with clear noon weather.</p>");
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
        if (label.contains("level")) {
            return "Level flight";
        }
        if (label.contains("roll")) {
            return "360-degree roll";
        }
        if (label.contains("loop")) {
            return "Vertical loop";
        }
        if (label.contains("reversal")) {
            return "180-degree reversal";
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
