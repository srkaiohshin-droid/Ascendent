package br.com.ascendant.animation;

import br.com.ascendant.power.AbilityRegistry;
import br.com.ascendant.power.PowerRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/**
 * Validates that every registered power and ability has an associated animation
 * profile, and reports coverage statistics.
 */
public final class AnimationCoverageValidator {

    private AnimationCoverageValidator() {
    }

    public record CoverageReport(
            int totalPowers,
            int powersWithProfile,
            int powersWithoutProfile,
            int totalAbilities,
            int abilitiesWithProfile,
            int abilitiesWithoutProfile,
            int totalProfiles,
            int sharedProfiles,
            List<String> powersWithoutProfileIds,
            List<String> abilitiesWithoutProfileIds
    ) {
        public double powerCoveragePercent() {
            return totalPowers == 0 ? 100.0 : (powersWithProfile * 100.0 / totalPowers);
        }

        public double abilityCoveragePercent() {
            return totalAbilities == 0 ? 100.0 : (abilitiesWithProfile * 100.0 / totalAbilities);
        }

        public boolean isComplete() {
            return powersWithoutProfileIds.isEmpty() && abilitiesWithoutProfileIds.isEmpty();
        }

        public String summary() {
            return String.format(
                    "Animation Coverage Report:%n" +
                    "- Powers: %d total, %d with profile (%.1f%%), %d without%n" +
                    "- Abilities: %d total, %d with profile (%.1f%%), %d without%n" +
                    "- Total profiles: %d (shared: %d)%n" +
                    "- Status: %s",
                    totalPowers, powersWithProfile, powerCoveragePercent(), powersWithoutProfile,
                    totalAbilities, abilitiesWithProfile, abilityCoveragePercent(), abilitiesWithoutProfile,
                    totalProfiles, sharedProfiles,
                    isComplete() ? "COMPLETE" : "INCOMPLETE"
            );
        }
    }

    public static CoverageReport validate() {
        Set<ResourceLocation> profileIds = new HashSet<>();
        for (AnimationProfile profile : AnimationRegistry.getAll()) {
            profileIds.add(profile.getId());
        }

        List<String> powersWithoutProfile = new ArrayList<>();
        int powersWithProfile = 0;
        int totalPowers = 0;

        for (br.com.ascendant.power.PowerDefinition power : PowerRegistry.getFunctionalPowers()) {
            totalPowers++;
            if (AnimationRegistry.hasProfile(power.getId())) {
                powersWithProfile++;
            } else {
                powersWithoutProfile.add(power.getId().toString());
            }
        }

        List<String> abilitiesWithoutProfile = new ArrayList<>();
        int abilitiesWithProfile = 0;
        int totalAbilities = 0;

        for (br.com.ascendant.power.AbilityDefinition ability : AbilityRegistry.getFunctionalAbilities()) {
            totalAbilities++;
            if (AnimationRegistry.hasProfile(ability.getId())) {
                abilitiesWithProfile++;
            } else {
                abilitiesWithoutProfile.add(ability.getId().toString());
            }
        }

        int totalProfiles = AnimationRegistry.size();
        // Shared profiles = profiles that are not directly keyed to a registered power/ability id
        int sharedProfiles = Math.max(0, totalProfiles - powersWithProfile - abilitiesWithProfile);

        return new CoverageReport(
                totalPowers, powersWithProfile, powersWithoutProfile.size(),
                totalAbilities, abilitiesWithProfile, abilitiesWithoutProfile.size(),
                totalProfiles, sharedProfiles,
                powersWithoutProfile, abilitiesWithoutProfile
        );
    }
}
