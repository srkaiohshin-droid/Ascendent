package br.com.ascendant.tools;

import br.com.ascendant.power.AbilityDefinition;
import br.com.ascendant.power.AbilityRegistry;
import br.com.ascendant.power.PowerDefinition;
import br.com.ascendant.power.PowerRegistry;
import br.com.ascendant.suit.SuitDefinition;
import br.com.ascendant.suit.SuitRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/**
 * Automated validation tool for content schemas, duplicate check,
 * reference integrity, and functional vs template breakdown.
 */
public final class ContentValidator {

    private ContentValidator() {
    }

    public record ValidationReport(
            int totalPowers,
            int functionalPowers,
            int templatePowers,
            int totalAbilities,
            int functionalAbilities,
            int templateAbilities,
            int totalSuits,
            List<String> errors
    ) {
        public boolean isValid() {
            return errors.isEmpty();
        }

        public String summary() {
            return String.format(
                    "Content Validation Report:%n" +
                    "- Powers Registered: %d (Functional: %d, Templates: %d)%n" +
                    "- Abilities Registered: %d (Functional: %d, Templates: %d)%n" +
                    "- Suits Registered: %d%n" +
                    "- Errors: %d%n" +
                    "- Status: %s",
                    totalPowers, functionalPowers, templatePowers,
                    totalAbilities, functionalAbilities, templateAbilities,
                    totalSuits, errors.size(), isValid() ? "PASS" : "FAIL"
            );
        }
    }

    public static ValidationReport validate() {
        List<String> errors = new ArrayList<>();
        Set<ResourceLocation> seenPowerIds = new HashSet<>();
        Set<ResourceLocation> seenAbilityIds = new HashSet<>();
        Set<ResourceLocation> seenSuitIds = new HashSet<>();

        int totalPowers = 0;
        int functionalPowers = 0;
        int templatePowers = 0;

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
        }

        return new ValidationReport(
                totalPowers, functionalPowers, templatePowers,
                totalAbilities, functionalAbilities, templateAbilities,
                totalSuits, errors
        );
    }
}
