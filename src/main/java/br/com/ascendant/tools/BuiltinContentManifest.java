package br.com.ascendant.tools;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Declarative list of content that ships with the framework and therefore must
 * be fully localized.
 *
 * <p>Runtime-registered content (GameTests, future pack loading) is validated
 * structurally, but it is not forced to ship translations with the base mod.
 * Keeping the manifest explicit also makes the localization contract visible
 * to content authors instead of hiding it in the validator.</p>
 */
public final class BuiltinContentManifest {

    private BuiltinContentManifest() {
    }

    private static ResourceLocation suit(String path) {
        return new ResourceLocation("ascendant", path);
    }

    /** Built-in suit loadouts. Their granted powers and abilities inherit the same requirement. */
    public static final List<ResourceLocation> SUIT_IDS = List.of(
            suit("classic_ascendant"),
            suit("speedster_mk1"),
            suit("titan_exosuit"),
            suit("atlantean_regalia"),
            suit("solar_aegis"),
            suit("shadow_infiltrator")
    );
}
