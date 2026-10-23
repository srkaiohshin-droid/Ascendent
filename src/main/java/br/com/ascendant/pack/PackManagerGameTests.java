package br.com.ascendant.pack;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@PrefixGameTestTemplate(false)
public final class PackManagerGameTests {
    private PackManagerGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void packImportRejectsUnsafeIdsAndPreservesExistingInstall(GameTestHelper helper) {
        Path testRoot = null;
        try {
            testRoot = Files.createTempDirectory("ascendant-pack-import-test-");
            Path packsDirectory = Files.createDirectories(testRoot.resolve("ascendant_packs"));
            Path existingPack = Files.createDirectories(packsDirectory.resolve("safe-pack"));
            Files.writeString(existingPack.resolve("version.txt"), "old");

            Path unsafeIdArchive = testRoot.resolve("unsafe-id.zip");
            createArchive(unsafeIdArchive, "../outside", null, null);
            helper.assertTrue(!PackManager.importPack(unsafeIdArchive, packsDirectory),
                    "Import must reject a manifest ID that can escape the pack directory");
            helper.assertTrue(Files.readString(existingPack.resolve("version.txt")).equals("old")
                            && !Files.exists(testRoot.resolve("outside")),
                    "Rejecting an unsafe manifest must preserve installed data and create no outside path");

            Path unsafeEntryArchive = testRoot.resolve("unsafe-entry.zip");
            createArchive(unsafeEntryArchive, "safe-pack", "..\\outside.txt", "unexpected");
            helper.assertTrue(!PackManager.importPack(unsafeEntryArchive, packsDirectory),
                    "Import must reject a ZIP entry that traverses outside the staging directory");
            helper.assertTrue(Files.readString(existingPack.resolve("version.txt")).equals("old")
                            && !Files.exists(testRoot.resolve("outside.txt")),
                    "Rejecting an unsafe ZIP entry must preserve the previous installation");

            Path replacementArchive = testRoot.resolve("replacement.zip");
            createArchive(replacementArchive, "safe-pack", "content/version.txt", "new");
            helper.assertTrue(PackManager.importPack(replacementArchive, packsDirectory),
                    "A valid pack should replace an existing installation");
            Path installedVersion = packsDirectory.resolve("safe-pack/content/version.txt");
            helper.assertTrue(Files.readString(installedVersion).equals("new")
                            && !Files.exists(existingPack.resolve("version.txt")),
                    "Successful replacement should expose the new pack contents");
            helper.succeed();
        } catch (IOException exception) {
            throw new AssertionError("Pack import regression test failed", exception);
        } finally {
            deleteTestDirectory(testRoot);
        }
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void packInstallRollsBackWhenAtomicReplacementFails(GameTestHelper helper) {
        Path testRoot = null;
        try {
            testRoot = Files.createTempDirectory("ascendant-pack-rollback-test-");
            Path target = Files.createDirectories(testRoot.resolve("installed"));
            Path staged = Files.createDirectories(testRoot.resolve("staged"));
            Path backup = testRoot.resolve("backup");
            Files.writeString(target.resolve("version.txt"), "old");
            Files.writeString(staged.resolve("version.txt"), "new");
            AtomicInteger moves = new AtomicInteger();

            boolean failed = false;
            try {
                PackManager.replaceDirectoryAtomically(staged, target, backup, (source, destination) -> {
                    if (moves.incrementAndGet() == 2) {
                        throw new IOException("injected staged-move failure");
                    }
                    Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
                });
            } catch (IOException expected) {
                failed = true;
            }

            helper.assertTrue(failed, "The simulated replacement failure should be reported");
            helper.assertTrue(Files.readString(target.resolve("version.txt")).equals("old")
                            && !Files.exists(backup),
                    "The previous pack must be restored when installing staged contents fails");
            helper.assertTrue(Files.readString(staged.resolve("version.txt")).equals("new"),
                    "A failed replacement must leave staged data intact for diagnosis or retry");
            helper.succeed();
        } catch (IOException exception) {
            throw new AssertionError("Pack rollback regression test failed", exception);
        } finally {
            deleteTestDirectory(testRoot);
        }
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void packManifestRejectsInvalidIdsCountsAndDependencies(GameTestHelper helper) {
        JsonObject fractionalCount = baseManifest("safe-pack");
        fractionalCount.addProperty("powerCount", 1.5D);
        helper.assertTrue(rejectsManifest(fractionalCount),
                "Manifest content counts must be non-negative integers");

        JsonObject duplicateDependencies = baseManifest("safe-pack");
        JsonArray dependencies = new JsonArray();
        dependencies.add("required-pack");
        dependencies.add("required-pack");
        duplicateDependencies.add("dependencies", dependencies);
        helper.assertTrue(rejectsManifest(duplicateDependencies),
                "Manifest dependencies must be unique safe pack IDs");

        JsonObject invalidId = baseManifest("Unsafe-Pack");
        helper.assertTrue(rejectsManifest(invalidId),
                "Pack IDs must use the documented portable lowercase format");
        helper.assertTrue(rejectsManifest(baseManifest("con.pack")),
                "Pack IDs must not use reserved Windows device names");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ascendant")
    public static void packThemeIsOptionalMetadataAndValidated(GameTestHelper helper) {
        PackManifest plain = PackManifest.fromJson(baseManifest("plain-pack"));
        helper.assertTrue(plain.getTheme() == null,
                "A manifest without a theme must keep the derived presentation");

        JsonObject themedJson = baseManifest("themed-pack");
        themedJson.addProperty("theme", "retro");
        PackManifest themed = PackManifest.fromJson(themedJson);
        helper.assertTrue("retro".equals(themed.getTheme()),
                "A valid theme identifier must round-trip through the manifest");
        helper.assertTrue(themed.toJson().has("theme"),
                "A declared theme must be preserved when the manifest is serialized");

        JsonObject invalidTheme = baseManifest("invalid-theme-pack");
        invalidTheme.addProperty("theme", "Retro Cover!");
        helper.assertTrue(rejectsManifest(invalidTheme),
                "Theme identifiers must stay lowercase and filesystem-safe");
        helper.succeed();
    }

    private static void createArchive(Path archive, String id, String contentPath, String content)
            throws IOException {
        JsonObject manifest = baseManifest(id);

        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("manifest.json"));
            zip.write(manifest.toString().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            if (contentPath != null) {
                zip.putNextEntry(new ZipEntry(contentPath));
                zip.write(content.getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
    }

    private static JsonObject baseManifest(String id) {
        JsonObject manifest = new JsonObject();
        manifest.addProperty("id", id);
        manifest.addProperty("name", "Test Pack");
        manifest.addProperty("version", "1.0.0");
        manifest.addProperty("author", "Ascendant tests");
        return manifest;
    }

    private static boolean rejectsManifest(JsonObject json) {
        try {
            PackManifest.fromJson(json);
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }

    private static void deleteTestDirectory(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException exception) {
            throw new AssertionError("Could not clean up pack test directory " + directory, exception);
        }
    }
}
