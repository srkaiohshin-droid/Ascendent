package br.com.ascendant.lore;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Built-in, original Ascendant lore. Entries are tied to real powers, abilities
 * and suits so the archive unlocks through gameplay instead of a separate
 * progression counter. Text lives in the language files, never in code.
 */
public final class NativeLoreData {

    private NativeLoreData() {
    }

    public static void registerAll() {
        reg("ascendant_protocol", LoreCategory.ORIGIN, LoreUnlock.always(), 10,
                List.of("flight"), List.of("classic_ascendant"));
        reg("prime_accord", LoreCategory.FACTION, LoreUnlock.always(), 20,
                List.of(), List.of("classic_ascendant"));
        reg("kinetic_dynamics_brief", LoreCategory.FACTION,
                LoreUnlock.suit(id("speedster_mk1")), 30,
                List.of("kinetic_sprint"), List.of("speedster_mk1"));
        reg("forge_tech_ledger", LoreCategory.FACTION,
                LoreUnlock.suit(id("titan_exosuit")), 40,
                List.of("titan_strength"), List.of("titan_exosuit"));
        reg("abyssal_realm", LoreCategory.FACTION,
                LoreUnlock.suit(id("atlantean_regalia")), 50,
                List.of("aquatic_respiration"), List.of("atlantean_regalia"));
        reg("cosmic_vanguard", LoreCategory.FACTION,
                LoreUnlock.suit(id("solar_aegis")), 60,
                List.of("solar_healing"), List.of("solar_aegis"));
        reg("syndicate_shadow", LoreCategory.THREAT,
                LoreUnlock.suit(id("shadow_infiltrator")), 70,
                List.of("shadow_teleport"), List.of("shadow_infiltrator"));
        reg("null_wake", LoreCategory.THREAT,
                LoreUnlock.power(id("gravitational_slam")), 80,
                List.of("gravitational_slam"), List.of("titan_exosuit"));
        reg("first_flight", LoreCategory.ARCHIVE,
                LoreUnlock.power(id("flight")), 90,
                List.of("flight"), List.of("classic_ascendant"));
        reg("kinetic_field_manual", LoreCategory.ARCHIVE,
                LoreUnlock.power(id("kinetic_sprint")), 100,
                List.of("kinetic_sprint"), List.of("speedster_mk1"));
        reg("trench_signal", LoreCategory.DOSSIER,
                LoreUnlock.ability(id("torrent_propel")), 110,
                List.of(), List.of("atlantean_regalia"));
        reg("solar_flare_log", LoreCategory.DOSSIER,
                LoreUnlock.ability(id("solar_rejuvenation")), 120,
                List.of(), List.of("solar_aegis"));
    }

    private static void reg(String path, LoreCategory category, LoreUnlock unlock, int order,
                            List<String> relatedPowers, List<String> relatedSuits) {
        LoreRegistry.register(new LoreEntry(id(path), category, unlock,
                relatedPowers.stream().map(NativeLoreData::id).toList(),
                relatedSuits.stream().map(NativeLoreData::id).toList(),
                order));
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation("ascendant", path);
    }
}
