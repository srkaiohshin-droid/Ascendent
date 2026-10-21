package br.com.ascendant.pack;

import br.com.ascendant.Ascendant;
import br.com.ascendant.power.AbilityDefinition;
import br.com.ascendant.power.AbilityRegistry;
import br.com.ascendant.power.PowerDefinition;
import br.com.ascendant.power.PowerRegistry;
import br.com.ascendant.suit.SuitDefinition;
import br.com.ascendant.suit.SuitRegistry;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * High-level manager for discovering, importing, exporting, and loading content packs.
 */
public final class PackManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path packsDir;
    private static LocalPackCatalogProvider localCatalog;

    private PackManager() {
    }

    public static synchronized void init(Path gameDir) {
        packsDir = gameDir.resolve("ascendant_packs");
        try {
            if (!Files.exists(packsDir)) {
                Files.createDirectories(packsDir);
            }
        } catch (IOException e) {
            Ascendant.LOGGER.error("[ASC] Failed to create packs directory", e);
        }
        localCatalog = new LocalPackCatalogProvider(packsDir);
    }

    public static Path getPacksDir() {
        return packsDir;
    }

    public static LocalPackCatalogProvider getLocalCatalog() {
        return localCatalog;
    }

    public static boolean importPack(Path zipFilePath) {
        if (!Files.exists(zipFilePath) || !zipFilePath.toString().endsWith(".zip")) {
            Ascendant.LOGGER.warn("[ASC] Invalid pack file: {}", zipFilePath);
            return false;
        }

        try (ZipFile zip = new ZipFile(zipFilePath.toFile())) {
            ZipEntry manifestEntry = zip.getEntry("manifest.json");
            if (manifestEntry == null) {
                Ascendant.LOGGER.error("[ASC] Pack zip missing manifest.json: {}", zipFilePath);
                return false;
            }

            PackManifest manifest;
            try (var reader = new InputStreamReader(zip.getInputStream(manifestEntry))) {
                manifest = PackManifest.fromJson(GSON.fromJson(reader, JsonObject.class));
            }

            Path targetFolder = packsDir.resolve(manifest.getId());
            Path tempFolder = packsDir.resolve(manifest.getId() + "_temp_" + System.currentTimeMillis());
            Files.createDirectories(tempFolder);

            int entryCount = 0;
            long totalBytes = 0;
            byte[] buffer = new byte[8192];

            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                entryCount++;
                PackSecurity.validateEntryName(entry.getName(), tempFolder);

                Path destFile = tempFolder.resolve(entry.getName()).normalize();
                if (entry.isDirectory()) {
                    Files.createDirectories(destFile);
                } else {
                    Files.createDirectories(destFile.getParent());
                    try (InputStream in = zip.getInputStream(entry);
                         OutputStream out = Files.newOutputStream(destFile)) {
                        int read;
                        while ((read = in.read(buffer)) != -1) {
                            totalBytes += read;
                            out.write(buffer, 0, read);
                        }
                    }
                }
                PackSecurity.checkLimits(entryCount, totalBytes);
            }

            // Atomic switch
            if (Files.exists(targetFolder)) {
                deleteDirectoryRecursively(targetFolder);
            }
            Files.move(tempFolder, targetFolder, StandardCopyOption.ATOMIC_MOVE);
            Ascendant.LOGGER.info("[ASC] Successfully imported content pack: {} v{}", manifest.getId(), manifest.getVersion());
            return true;

        } catch (Exception e) {
            Ascendant.LOGGER.error("[ASC] Error importing pack: {}", zipFilePath, e);
            return false;
        }
    }

    public static boolean exportPack(Path sourceFolder, Path destinationZip) {
        if (!Files.isDirectory(sourceFolder)) {
            return false;
        }
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(destinationZip))) {
            Path normSource = sourceFolder.normalize();
            try (var stream = Files.walk(normSource)) {
                stream.forEach(path -> {
                    if (!Files.isDirectory(path)) {
                        String relative = normSource.relativize(path).toString().replace('\\', '/');
                        try {
                            zos.putNextEntry(new ZipEntry(relative));
                            Files.copy(path, zos);
                            zos.closeEntry();
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }
                });
            }
            return true;
        } catch (Exception e) {
            Ascendant.LOGGER.error("[ASC] Failed to export content pack: {}", sourceFolder, e);
            return false;
        }
    }

    public static void loadAllInstalledPacks() {
        if (packsDir == null || !Files.exists(packsDir)) {
            return;
        }

        try (var stream = Files.list(packsDir)) {
            stream.filter(Files::isDirectory).forEach(PackManager::loadSinglePackFolder);
        } catch (Exception e) {
            Ascendant.LOGGER.error("[ASC] Error reading packs directory", e);
        }
    }

    public static void loadSinglePackFolder(Path packFolder) {
        Path manifestPath = packFolder.resolve("manifest.json");
        if (!Files.exists(manifestPath)) {
            return;
        }

        try {
            // Load powers
            Path powersDir = packFolder.resolve("powers");
            if (Files.isDirectory(powersDir)) {
                try (var pStream = Files.list(powersDir)) {
                    pStream.filter(p -> p.toString().endsWith(".json")).forEach(p -> {
                        try (var reader = Files.newBufferedReader(p)) {
                            JsonObject json = GSON.fromJson(reader, JsonObject.class);
                            PowerDefinition power = PowerDefinition.fromJson(json);
                            PowerRegistry.register(power);
                        } catch (Exception ex) {
                            Ascendant.LOGGER.warn("[ASC] Failed to load power file: {}", p, ex);
                        }
                    });
                }
            }

            // Load abilities
            Path abilitiesDir = packFolder.resolve("abilities");
            if (Files.isDirectory(abilitiesDir)) {
                try (var aStream = Files.list(abilitiesDir)) {
                    aStream.filter(p -> p.toString().endsWith(".json")).forEach(p -> {
                        try (var reader = Files.newBufferedReader(p)) {
                            JsonObject json = GSON.fromJson(reader, JsonObject.class);
                            AbilityDefinition ability = AbilityDefinition.fromJson(json);
                            AbilityRegistry.register(ability);
                        } catch (Exception ex) {
                            Ascendant.LOGGER.warn("[ASC] Failed to load ability file: {}", p, ex);
                        }
                    });
                }
            }

            // Load suits
            Path suitsDir = packFolder.resolve("suits");
            if (Files.isDirectory(suitsDir)) {
                try (var sStream = Files.list(suitsDir)) {
                    sStream.filter(p -> p.toString().endsWith(".json")).forEach(p -> {
                        try (var reader = Files.newBufferedReader(p)) {
                            JsonObject json = GSON.fromJson(reader, JsonObject.class);
                            SuitDefinition suit = SuitDefinition.fromJson(json);
                            SuitRegistry.register(suit);
                        } catch (Exception ex) {
                            Ascendant.LOGGER.warn("[ASC] Failed to load suit file: {}", p, ex);
                        }
                    });
                }
            }

        } catch (Exception e) {
            Ascendant.LOGGER.error("[ASC] Error loading pack from folder: {}", packFolder, e);
        }
    }

    private static void deleteDirectoryRecursively(Path dir) throws IOException {
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }
}
