package br.com.ascendant.pack;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Local file system catalog provider inspecting the ascendant_packs directory.
 */
public final class LocalPackCatalogProvider implements IPackCatalogProvider {
    private static final Gson GSON = new Gson();
    private final Path packsDirectory;

    public LocalPackCatalogProvider(Path packsDirectory) {
        this.packsDirectory = packsDirectory;
    }

    @Override
    public List<PackManifest> listAvailablePacks() {
        List<PackManifest> list = new ArrayList<>();
        if (!Files.exists(packsDirectory) || !Files.isDirectory(packsDirectory)) {
            return list;
        }

        try (var stream = Files.list(packsDirectory)) {
            stream.forEach(path -> {
                if (Files.isDirectory(path)) {
                    Path manifestPath = path.resolve("manifest.json");
                    if (Files.exists(manifestPath)) {
                        try (var reader = Files.newBufferedReader(manifestPath)) {
                            JsonObject json = GSON.fromJson(reader, JsonObject.class);
                            list.add(PackManifest.fromJson(json));
                        } catch (Exception ignored) {
                        }
                    }
                } else if (path.toString().endsWith(".zip")) {
                    try (ZipFile zip = new ZipFile(path.toFile())) {
                        ZipEntry entry = zip.getEntry("manifest.json");
                        if (entry != null) {
                            try (var reader = new InputStreamReader(zip.getInputStream(entry))) {
                                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                                list.add(PackManifest.fromJson(json));
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
            });
        } catch (Exception ignored) {
        }
        return list;
    }

    @Override
    public Optional<Path> fetchPack(String packId, Path destinationDirectory) {
        Path directFolder = packsDirectory.resolve(packId);
        if (Files.isDirectory(directFolder)) {
            return Optional.of(directFolder);
        }
        Path directZip = packsDirectory.resolve(packId + ".zip");
        if (Files.exists(directZip)) {
            return Optional.of(directZip);
        }
        return Optional.empty();
    }
}
