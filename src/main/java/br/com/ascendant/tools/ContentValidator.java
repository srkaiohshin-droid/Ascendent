package br.com.ascendant.tools;

import br.com.ascendant.lore.LoreEntry;
import br.com.ascendant.lore.LoreRegistry;
import br.com.ascendant.lore.LoreUnlock;
import br.com.ascendant.power.AbilityDefinition;
import br.com.ascendant.power.AbilityRegistry;
import br.com.ascendant.power.PowerDefinition;
import br.com.ascendant.power.PowerRegistry;
import br.com.ascendant.power.component.IPowerComponent;
import br.com.ascendant.power.component.TemplateComponent;
import br.com.ascendant.suit.SuitDefinition;
import br.com.ascendant.suit.SuitRegistry;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Automated validation tool for content schemas, duplicate check, reference
 * integrity, functional vs template breakdown, lore references and
 * translation coverage.
 */
public final class ContentValidator {

    private static final int TRANSLATION_SAMPLE_LIMIT = 10;
    private static final String LANG_ROOT = "/assets/ascendant/lang/";

    private ContentValidator() {
    }

    /**
     * @param curatedKeys            name/description/body keys that every supported language must provide
     * @param missingCuratedKeys     curated keys absent from at least one language
     * @param missingCatalogSamples  bounded sample of missing power/ability name or description keys
     */
    public record TranslationCoverage(
            boolean enUsAvailable,
            boolean ptBrAvailable,
            int enUsKeys,
            int ptBrKeys,
            int curatedKeys,
            List<String> missingCuratedKeys,
            int missingPowerNames,
            int missingPowerDescriptions,
            int missingAbilityNames,
            int missingAbilityDescriptions,
            List<String> missingCatalogSamples
    ) {
        public TranslationCoverage {
            missingCuratedKeys = List.copyOf(missingCuratedKeys);
            missingCatalogSamples = List.copyOf(missingCatalogSamples);
        }

        public boolean isComplete() {
            return enUsAvailable && ptBrAvailable && missingCuratedKeys.isEmpty();
        }
    }

    public record ValidationReport(
            int totalPowers,
            int functionalPowers,
            int templatePowers,
            int totalAbilities,
            int functionalAbilities,
            int templateAbilities,
            int totalSuits,
            int totalLore,
            List<String> errors,
            List<String> warnings,
            int functionalPowersWithoutExecutableComponent,
            TranslationCoverage translations
    ) {
        public boolean isValid() {
            return errors.isEmpty();
        }

        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }

        public String summary() {
            return String.format(
                    "Content Validation Report:%n" +
                    "- Powers Registered: %d (Functional: %d, Templates: %d)%n" +
                    "- Abilities Registered: %d (Functional: %d, Templates: %d)%n" +
                    "- Suits Registered: %d%n" +
                    "- Lore Registered: %d%n" +
                    "- Errors: %d%n" +
                    "- Warnings: %d%n" +
                    "- Functional powers w/o executable component: %d%n" +
                    "- Curated translation keys: %d (missing: %d)%n" +
                    "- Missing catalog descriptions: %d powers, %d abilities%n" +
                    "- Status: %s",
                    totalPowers, functionalPowers, templatePowers,
                    totalAbilities, functionalAbilities, templateAbilities,
                    totalSuits, totalLore, errors.size(), warnings.size(),
                    functionalPowersWithoutExecutableComponent,
                    translations.curatedKeys(), translations.missingCuratedKeys().size(),
                    translations.missingPowerDescriptions(), translations.missingAbilityDescriptions(),
                    isValid() ? "PASS" : "FAIL"
            );
        }
    }

    public static ValidationReport validate() {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Set<ResourceLocation> seenPowerIds = new HashSet<>();
        Set<ResourceLocation> seenAbilityIds = new HashSet<>();
        Set<ResourceLocation> seenSuitIds = new HashSet<>();
        Set<ResourceLocation> seenLoreIds = new HashSet<>();

        int totalPowers = 0;
        int functionalPowers = 0;
        int templatePowers = 0;
        int functionalPowersWithoutExecutableComponent = 0;

        for (PowerDefinition power : PowerRegistry.getAll()) {
            totalPowers++;
            if (!seenPowerIds.add(power.getId())) {
                errors.add("Duplicate Power ID: " + power.getId());
            }
            if (power.getId().getPath().isBlank()) {
                errors.add("Power has empty path: " + power.getId());
            }
            if (power.getCategory() == null) {
                errors.add("Power has null category: " + power.getId());
            }
            if (power.getCooldownTicks() < 0) {
                errors.add("Power has negative cooldown: " + power.getId());
            }
            if (power.getComponents().isEmpty()) {
                errors.add("Power has no components defined: " + power.getId());
            }

            boolean hasExecutableComponent = false;
            for (IPowerComponent comp : power.getComponents()) {
                if (comp.isFunctional() && !(comp instanceof TemplateComponent)) {
                    hasExecutableComponent = true;
                }
            }
            if (power.isFunctional() && !hasExecutableComponent) {
                functionalPowersWithoutExecutableComponent++;
                warnings.add("Functional power " + power.getId() + " has no executable (non-template) component");
            }

            if (power.isFunctional()) {
                functionalPowers++;
            } else {
                templatePowers++;
            }
        }

        int totalAbilities = 0;
        int functionalAbilities = 0;
        int templateAbilities = 0;

        for (AbilityDefinition ability : AbilityRegistry.getAll()) {
            totalAbilities++;
            if (!seenAbilityIds.add(ability.getId())) {
                errors.add("Duplicate Ability ID: " + ability.getId());
            }
            if (ability.getId().getPath().isBlank()) {
                errors.add("Ability has empty path: " + ability.getId());
            }
            if (ability.getCategory() == null) {
                errors.add("Ability has null category: " + ability.getId());
            }
            if (ability.getTrigger() == null) {
                errors.add("Ability has null trigger: " + ability.getId());
            }
            if (ability.getCooldownTicks() < 0) {
                errors.add("Ability has negative cooldown: " + ability.getId());
            }
            if (ability.getActions().isEmpty()) {
                errors.add("Ability has no actions defined: " + ability.getId());
            }
            // Check reference to parent power
            if (ability.getPowerId() != null && !PowerRegistry.contains(ability.getPowerId())) {
                errors.add("Ability " + ability.getId() + " references unknown power: " + ability.getPowerId());
            }

            boolean hasExecutableAction = false;
            for (IPowerComponent action : ability.getActions()) {
                if (action.isFunctional() && !(action instanceof TemplateComponent)) {
                    hasExecutableAction = true;
                }
            }
            if (ability.isFunctional() && !hasExecutableAction) {
                warnings.add("Functional ability " + ability.getId() + " has no executable (non-template) action");
            }

            if (ability.isFunctional()) {
                functionalAbilities++;
            } else {
                templateAbilities++;
            }
        }

        int totalSuits = 0;
        for (SuitDefinition suit : SuitRegistry.getAll()) {
            totalSuits++;
            if (!seenSuitIds.add(suit.getId())) {
                errors.add("Duplicate Suit ID: " + suit.getId());
            }
            if (suit.getId().getPath().isBlank()) {
                errors.add("Suit has empty path: " + suit.getId());
            }
            if (suit.getRarity() == null) {
                errors.add("Suit has null rarity: " + suit.getId());
            }
            for (ResourceLocation pId : suit.getGrantedPowers()) {
                if (!PowerRegistry.contains(pId)) {
                    errors.add("Suit " + suit.getId() + " references unknown power: " + pId);
                }
            }
            for (ResourceLocation aId : suit.getGrantedAbilities()) {
                if (!AbilityRegistry.contains(aId)) {
                    errors.add("Suit " + suit.getId() + " references unknown ability: " + aId);
                }
            }
        }

        int totalLore = 0;
        for (LoreEntry entry : LoreRegistry.getAll()) {
            totalLore++;
            if (!seenLoreIds.add(entry.id())) {
                errors.add("Duplicate Lore ID: " + entry.id());
            }
            if (entry.id().getPath().isBlank()) {
                errors.add("Lore has empty path: " + entry.id());
            }
            LoreUnlock unlock = entry.unlock();
            if (unlock.type() != LoreUnlock.Type.ALWAYS) {
                ResourceLocation requirement = unlock.id();
                boolean known = switch (unlock.type()) {
                    case POWER -> PowerRegistry.contains(requirement);
                    case ABILITY -> AbilityRegistry.contains(requirement);
                    case SUIT -> SuitRegistry.contains(requirement);
                    case ALWAYS -> true;
                };
                if (!known) {
                    errors.add("Lore " + entry.id() + " unlocks through unknown " + unlock.type()
                            + ": " + requirement);
                }
            }
            for (ResourceLocation powerId : entry.relatedPowers()) {
                if (!PowerRegistry.contains(powerId)) {
                    errors.add("Lore " + entry.id() + " references unknown power: " + powerId);
                }
            }
            for (ResourceLocation suitId : entry.relatedSuits()) {
                if (!SuitRegistry.contains(suitId)) {
                    errors.add("Lore " + entry.id() + " references unknown suit: " + suitId);
                }
            }
        }

        TranslationCoverage translations = validateTranslations(errors, warnings);

        return new ValidationReport(
                totalPowers, functionalPowers, templatePowers,
                totalAbilities, functionalAbilities, templateAbilities,
                totalSuits, totalLore, errors, warnings,
                functionalPowersWithoutExecutableComponent, translations
        );
    }

    /**
     * Curated content (suits, their granted entries, lore) must be fully
     * localized. The remaining catalog is measured honestly instead of being
     * treated as complete: template descriptions are reported as coverage gaps.
     */
    private static TranslationCoverage validateTranslations(List<String> errors, List<String> warnings) {
        Set<String> enUs = loadTranslationKeys("en_us");
        Set<String> ptBr = loadTranslationKeys("pt_br");
        boolean enUsAvailable = enUs != null;
        boolean ptBrAvailable = ptBr != null;
        Set<String> en = enUs == null ? Set.of() : enUs;
        Set<String> pt = ptBr == null ? Set.of() : ptBr;

        if (!enUsAvailable || !ptBrAvailable) {
            warnings.add("Translation resources unavailable on the classpath (en_us=" + enUsAvailable
                    + ", pt_br=" + ptBrAvailable + "); coverage could not be verified");
        }

        Set<String> curated = new LinkedHashSet<>();
        for (ResourceLocation suitId : BuiltinContentManifest.SUIT_IDS) {
            SuitDefinition suit = SuitRegistry.get(suitId);
            if (suit == null) {
                errors.add("Built-in suit from the localization manifest is not registered: " + suitId);
                continue;
            }
            curated.add(suit.getNameKey());
            curated.add(suit.getDescriptionKey());
            for (ResourceLocation powerId : suit.getGrantedPowers()) {
                PowerDefinition power = PowerRegistry.get(powerId);
                if (power != null) {
                    curated.add(power.getNameKey());
                    curated.add(power.getDescriptionKey());
                }
            }
            for (ResourceLocation abilityId : suit.getGrantedAbilities()) {
                AbilityDefinition ability = AbilityRegistry.get(abilityId);
                if (ability != null) {
                    curated.add(ability.getNameKey());
                    curated.add(ability.getDescriptionKey());
                }
            }
        }
        for (LoreEntry entry : LoreRegistry.getAll()) {
            curated.add(entry.getTitleKey());
            curated.add(entry.getSummaryKey());
            curated.add(entry.getBodyKey());
            curated.add(entry.category().getTranslationKey());
            curated.add(entry.unlock().requirementKey());
        }

        List<String> missingCurated = curated.stream()
                .filter(key -> !en.contains(key) || !pt.contains(key))
                .sorted()
                .toList();
        if (!missingCurated.isEmpty()) {
            errors.add("Missing curated translations in en_us/pt_br: "
                    + sample(missingCurated) + " (" + missingCurated.size() + " keys)");
        }

        int missingPowerNames = 0;
        int missingPowerDescriptions = 0;
        int missingAbilityNames = 0;
        int missingAbilityDescriptions = 0;
        List<String> samples = new ArrayList<>();
        for (PowerDefinition power : PowerRegistry.getAll()) {
            if (!en.contains(power.getNameKey()) || !pt.contains(power.getNameKey())) {
                missingPowerNames++;
                addSample(samples, power.getNameKey());
            }
            if (!en.contains(power.getDescriptionKey()) || !pt.contains(power.getDescriptionKey())) {
                missingPowerDescriptions++;
                addSample(samples, power.getDescriptionKey());
            }
        }
        for (AbilityDefinition ability : AbilityRegistry.getAll()) {
            if (!en.contains(ability.getNameKey()) || !pt.contains(ability.getNameKey())) {
                missingAbilityNames++;
                addSample(samples, ability.getNameKey());
            }
            if (!en.contains(ability.getDescriptionKey()) || !pt.contains(ability.getDescriptionKey())) {
                missingAbilityDescriptions++;
                addSample(samples, ability.getDescriptionKey());
            }
        }
        if (missingPowerDescriptions > 0 || missingAbilityDescriptions > 0) {
            warnings.add("Catalog description coverage: " + missingPowerDescriptions + " power and "
                    + missingAbilityDescriptions + " ability keys are missing in en_us/pt_br "
                    + "(template content is expected to stay untranslated until implemented)");
        }

        return new TranslationCoverage(
                enUsAvailable, ptBrAvailable, en.size(), pt.size(), curated.size(), missingCurated,
                missingPowerNames, missingPowerDescriptions, missingAbilityNames, missingAbilityDescriptions,
                List.copyOf(samples)
        );
    }

    private static void addSample(List<String> samples, String key) {
        if (samples.size() < TRANSLATION_SAMPLE_LIMIT) {
            samples.add(key);
        }
    }

    private static String sample(List<String> keys) {
        return String.join(", ", keys.subList(0, Math.min(TRANSLATION_SAMPLE_LIMIT, keys.size())));
    }

    /** @return the translated key set, or {@code null} when the resource is unavailable */
    private static Set<String> loadTranslationKeys(String locale) {
        try (InputStream stream = ContentValidator.class.getResourceAsStream(LANG_ROOT + locale + ".json")) {
            if (stream == null) {
                return null;
            }
            JsonObject json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            Set<String> keys = new HashSet<>();
            for (String key : json.keySet()) {
                if (json.get(key).isJsonPrimitive()
                        && json.get(key).getAsJsonPrimitive().isString()
                        && !json.get(key).getAsString().isBlank()) {
                    keys.add(key);
                }
            }
            return keys;
        } catch (Exception exception) {
            return null;
        }
    }
}
