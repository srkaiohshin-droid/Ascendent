package br.com.ascendant.ui.client;

import br.com.ascendant.pack.PackManifest;
import br.com.ascendant.suit.SuitDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Locale;

/**
 * Visual identity for the catalog, derived from content metadata rather than hardcoded
 * hero names. Packs may declare {@code theme} in their manifest; otherwise the pack id,
 * pack name or suit universe is matched against a small set of visual families.
 *
 * <p>Only the presentation changes. No gameplay data is inferred from a theme.</p>
 */
@OnlyIn(Dist.CLIENT)
public record CatalogTheme(String id, ResourceLocation background, int accent, int accentBright,
                           int panel, int panelStrong, int ink, int muted) {

    public static final CatalogTheme COSMIC = new CatalogTheme("cosmic",
            new ResourceLocation("ascendant", "textures/gui/theme_cosmic.png"),
            0xFF58D7FF, 0xFFB9F4FF, 0xE6101824, 0xF01A2637, 0xFFF2F7FF, 0xFF9FB4C8);

    public static final CatalogTheme RETRO = new CatalogTheme("retro",
            new ResourceLocation("ascendant", "textures/gui/theme_retro.png"),
            0xFFFFC24A, 0xFFFFE6A8, 0xE61A1510, 0xF0282017, 0xFFFFF4DC, 0xFFD2BE9A);

    public static final CatalogTheme CLASSIC = new CatalogTheme("classic", null,
            0xFFFFD24A, 0xFFFFF0B8, 0xE6121622, 0xF01C2333, 0xFFFFF8E8, 0xFFB9B39F);

    public static CatalogTheme fallback() {
        return COSMIC;
    }

    public static CatalogTheme forPack(PackManifest pack) {
        if (pack == null) {
            return fallback();
        }
        String declared = pack.getTheme();
        String haystack = declared != null ? declared
                : pack.getId() + " " + pack.getName();
        return resolve(haystack);
    }

    public static CatalogTheme forSuit(SuitDefinition suit) {
        if (suit == null) {
            return fallback();
        }
        return resolve(suit.getUniverse() + " " + suit.getId().getPath());
    }

    public static CatalogTheme current() {
        ResourceLocation equipped = CatalogClientState.getEquippedSuit();
        if (equipped != null) {
            SuitDefinition suit = br.com.ascendant.suit.SuitRegistry.get(equipped);
            if (suit != null) {
                return forSuit(suit);
            }
        }
        return fallback();
    }

    static CatalogTheme resolve(String raw) {
        if (raw == null) {
            return fallback();
        }
        String value = raw.toLowerCase(Locale.ROOT);
        if (value.contains("noir") || value.contains("shadow") || value.contains("classic")
                || value.contains("mono")) {
            return CLASSIC;
        }
        if (value.contains("retro") || value.contains("pulp") || value.contains("moon")
                || value.contains("gold") || value.contains("atom")) {
            return RETRO;
        }
        return COSMIC;
    }

    public String labelKey() {
        return "gui.ascendant.theme." + id;
    }
}
