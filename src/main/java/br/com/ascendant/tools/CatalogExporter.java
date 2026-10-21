package br.com.ascendant.tools;

import br.com.ascendant.Ascendant;
import br.com.ascendant.power.AbilityDefinition;
import br.com.ascendant.power.AbilityRegistry;
import br.com.ascendant.power.PowerDefinition;
import br.com.ascendant.power.PowerRegistry;
import br.com.ascendant.suit.SuitDefinition;
import br.com.ascendant.suit.SuitRegistry;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Exports all registered powers, abilities, and suits to standard JSON files.
 * Provides a single source of truth for data-driven modpacks and external agents.
 */
public final class CatalogExporter {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private CatalogExporter() {
    }

    public static void exportToDirectory(Path rootDataDir) {
        Path powersDir = rootDataDir.resolve("powers");
        Path abilitiesDir = rootDataDir.resolve("abilities");
        Path suitsDir = rootDataDir.resolve("suits");

        try {
            Files.createDirectories(powersDir);
            Files.createDirectories(abilitiesDir);
            Files.createDirectories(suitsDir);

            for (PowerDefinition power : PowerRegistry.getAll()) {
                Path file = powersDir.resolve(power.getId().getPath() + ".json");
                try (Writer writer = Files.newBufferedWriter(file)) {
                    GSON.toJson(power.toJson(), writer);
                }
            }

            for (AbilityDefinition ability : AbilityRegistry.getAll()) {
                Path file = abilitiesDir.resolve(ability.getId().getPath() + ".json");
                try (Writer writer = Files.newBufferedWriter(file)) {
                    GSON.toJson(ability.toJson(), writer);
                }
            }

            for (SuitDefinition suit : SuitRegistry.getAll()) {
                Path file = suitsDir.resolve(suit.getId().getPath() + ".json");
                try (Writer writer = Files.newBufferedWriter(file)) {
                    GSON.toJson(suit.toJson(), writer);
                }
            }

            Ascendant.LOGGER.info("[ASC] Exported {} powers, {} abilities, and {} suits to {}",
                    PowerRegistry.size(), AbilityRegistry.size(), SuitRegistry.size(), rootDataDir);
        } catch (IOException e) {
            Ascendant.LOGGER.error("[ASC] Failed to export catalog to {}", rootDataDir, e);
        }
    }
}
