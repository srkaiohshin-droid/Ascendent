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
import com.google.gson.JsonParseException;

import java.io.*;
import java.nio.file.*;
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
        packsDir = gameDir.toAbsolutePath().normalize().resolve("ascendant_packs");
        try {
            Files.createDirectories(packsDir);
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

    /** Manifests of packs that actually exist in the configured packs directory. */
    public static List<PackManifest> getInstalledPacks() {
        LocalPackCatalogProvider catalog = localCatalog;
        if (catalog == null) {
            return List.of();
        }
        return List.copyOf(catalog.listAvailablePacks());
    }

    public static synchronized boolean importPack(Path zipFilePath) {
        if (packsDir == null) {
            Ascendant.LOGGER.error("[ASC] Cannot import a pack before PackManager initialization.");
            return false;
        }
        return importPack(zipFilePath, packsDir);
    }

    static boolean importPack(Path zipFilePath, Path configuredPacksDir) {
        if (configuredPacksDir == null) {
            Ascendant.LOGGER.error("[ASC] Cannot import a pack without a configured destination.");
            return false;
        }
        if (zipFilePath == null || !Files.isRegularFile(zipFilePath)
                || !zipFilePath.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip")) {
            Ascendant.LOGGER.warn("[ASC] Invalid pack file: {}", zipFilePath);
            return false;
        }

        Path root = configuredPacksDir.toAbsolutePath().normalize();
        Path stagingRoot = null;
        Path backup = null;
        boolean installed = false;
        try {
            Files.createDirectories(root);
            if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Pack directory is not a regular directory: " + root);
            }

            stagingRoot = Files.createTempDirectory(root, ".ascendant-import-");
            Path contents = Files.createDirectory(stagingRoot.resolve("contents"));
            int entryCount = 0;
            long totalBytes = 0;
            byte[] buffer = new byte[8192];
            Set<String> seenPaths = new HashSet<>();

            try (ZipFile zip = new ZipFile(zipFilePath.toFile())) {
                if (zip.getEntry("manifest.json") == null) {
                    throw new IOException("Pack archive is missing its root manifest.json.");
                }

                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    entryCount++;
                    PackSecurity.checkLimits(entryCount, totalBytes);

                    Path destination = PackSecurity.resolveEntryPath(entry.getName(), contents);
                    String relativePath = contents.toAbsolutePath().normalize().relativize(destination)
                            .toString().replace('\\', '/').toLowerCase(Locale.ROOT);
                    if (!seenPaths.add(relativePath)) {
                        throw new SecurityException("Pack contains duplicate entry paths: " + entry.getName());
                    }

                    if (entry.isDirectory()) {
                        Files.createDirectories(destination);
                        continue;
                    }

                    Files.createDirectories(destination.getParent());
                    try (InputStream in = zip.getInputStream(entry);
                         OutputStream out = Files.newOutputStream(destination,
                                 StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                        int read;
                        while ((read = in.read(buffer)) != -1) {
                            if (read == 0) {
                                continue;
                            }
                            totalBytes += read;
                            PackSecurity.checkLimits(entryCount, totalBytes);
                            out.write(buffer, 0, read);
                        }
                    }
                }
            }

            Path manifestPath = contents.resolve("manifest.json");
            if (!Files.isRegularFile(manifestPath, LinkOption.NOFOLLOW_LINKS)
                    || Files.size(manifestPath) > PackSecurity.MAX_MANIFEST_SIZE_BYTES) {
                throw new IOException("Pack manifest is missing, not a regular file, or exceeds 1 MiB.");
            }

            PackManifest manifest;
            try (var reader = Files.newBufferedReader(manifestPath)) {
                manifest = PackManifest.fromJson(GSON.fromJson(reader, JsonObject.class));
            }

            // Reject packs targeting a different Minecraft/Forge runtime before touching existing data.
            if (!"1.20.1".equals(manifest.getMinMcVersion())
                    || !"47.3.0".equals(manifest.getMinForgeVersion())) {
                throw new IllegalArgumentException("Pack " + manifest.getId()
                        + " targets Minecraft " + manifest.getMinMcVersion()
                        + " / Forge " + manifest.getMinForgeVersion()
                        + "; this build supports 1.20.1 / 47.3.0.");
            }

            Path target = root.resolve(manifest.getId()).normalize();
            if (!target.startsWith(root) || target.equals(root)) {
                throw new SecurityException("Pack ID resolves outside the configured pack directory.");
            }
            if (Files.isSymbolicLink(target)
                    || (Files.exists(target, LinkOption.NOFOLLOW_LINKS)
                    && !Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS))) {
                throw new IOException("Existing pack target is not a regular directory: " + target);
            }

            backup = stagingRoot.resolve("previous");
            replaceDirectoryAtomically(contents, target, backup, PackManager::moveDirectoryAtomically);
            installed = true;
            Ascendant.LOGGER.info("[ASC] Successfully imported content pack: {} v{}",
                    manifest.getId(), manifest.getVersion());
            return true;
        } catch (IOException | IllegalArgumentException | SecurityException | JsonParseException e) {
            Ascendant.LOGGER.error("[ASC] Error importing pack: {}", zipFilePath, e);
            return false;
        } finally {
            if (stagingRoot != null && Files.exists(stagingRoot, LinkOption.NOFOLLOW_LINKS)) {
                boolean preserveBackup = !installed && backup != null
                        && Files.exists(backup, LinkOption.NOFOLLOW_LINKS);
                if (preserveBackup) {
                    Ascendant.LOGGER.error("[ASC] Previous pack data was preserved for manual recovery at {}",
                            backup);
                } else {
                    try {
                        deleteDirectoryRecursively(stagingRoot);
                    } catch (IOException cleanupFailure) {
                        Ascendant.LOGGER.error("[ASC] Failed to clean pack import staging directory: {}",
                                stagingRoot, cleanupFailure);
                    }
                }
            }
        }
    }

    static void replaceDirectoryAtomically(Path staged, Path target, Path backup,
                                           DirectoryMover mover) throws IOException {
        Objects.requireNonNull(staged, "staged");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(backup, "backup");
        Objects.requireNonNull(mover, "mover");

        if (Files.exists(backup, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Pack backup path already exists: " + backup);
        }

        boolean previousMoved = false;
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            mover.move(target, backup);
            previousMoved = true;
        }

        try {
            mover.move(staged, target);
        } catch (IOException | RuntimeException installFailure) {
            if (previousMoved) {
                try {
                    mover.move(backup, target);
                } catch (IOException | RuntimeException rollbackFailure) {
                    installFailure.addSuppressed(rollbackFailure);
                }
            }
            throw installFailure;
        }
    }

    private static void moveDirectoryAtomically(Path source, Path destination) throws IOException {
        Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
    }

    @FunctionalInterface
    interface DirectoryMover {
        void move(Path source, Path destination) throws IOException;
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
        if (!Files.exists(dir, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (var walk = Files.walk(dir)) {
            Iterator<Path> paths = walk.sorted(Comparator.reverseOrder()).iterator();
            while (paths.hasNext()) {
                Files.deleteIfExists(paths.next());
            }
        }
    }
}
