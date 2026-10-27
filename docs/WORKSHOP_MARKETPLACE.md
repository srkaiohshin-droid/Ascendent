# Ascendant Workshop marketplace and comic archive

The in-game entry point is \`WorkshopMarketplaceScreen\`, opened by the existing F6 catalog key.

## Included behavior

- Marketplace landing page with separate DC, Marvel and Other Universes archive routes.
- Returning to the marketplace is required to switch universes.
- Searchable, filterable, paginated character archive loaded from \`assets/ascendant/catalog/hero_archive.json\`.
- A character detail view with team, era, description and power metadata.
- The Community Packs panel lists local Ascendant manifests discovered by \`PackManager\`.
- Existing power/suit catalog remains the authoritative screen for actual in-game power and suit actions.
- No image or executable downloads are performed by the UI.

## Add cover artwork

Character art is loaded as local Minecraft textures. Put each approved, distributable cover at the exact path declared in \`hero_archive.json\`, for example:

- \`src/main/resources/assets/ascendant/textures/gui/covers/dc/superman.png\`
- \`src/main/resources/assets/ascendant/textures/gui/covers/dc/batman.png\`
- \`src/main/resources/assets/ascendant/textures/gui/covers/marvel/spider_man.png\`
- \`src/main/resources/assets/ascendant/textures/gui/covers/marvel/iron_man.png\`
- \`src/main/resources/assets/ascendant/textures/gui/covers/other/invincible.png\`

Universe banners are optional:

- \`textures/gui/covers/featured/multiverse.png\`
- \`textures/gui/covers/universes/dc.png\`
- \`textures/gui/covers/universes/marvel.png\`
- \`textures/gui/covers/universes/others.png\`

When an image is absent, the screen draws a comic-styled fallback card instead of crashing. Suggested art ratio for character covers is 2:3 or 3:4; banners should be wide (16:9). Keep covers reasonably compressed (for example, 512x768 PNG for safe Minecraft texture compatibility) rather than bundling large print-resolution scans.

Only add artwork you have the right to redistribute in a public mod jar. Permission to use an image inside a private build does not necessarily grant permission to redistribute it publicly.

## Add more characters (including 1,000+ entries)

Add another object inside the \`characters\` array in \`src/main/resources/assets/ascendant/catalog/hero_archive.json\`:

\`\`\`json
{
  "id": "dc-example-hero",
  "name": "Example Hero",
  "universe": "DC",
  "alignment": "HERO",
  "era": "MODERN",
  "team": "Example Team",
  "description": "Short description shown on the dossier page.",
  "powers": ["Flight", "Super Strength"],
  "cover": "textures/gui/covers/dc/example_hero.png",
  "accent": "#4A91FF"
}
\`\`\`

Supported \`universe\` values are \`DC\`, \`Marvel\`, and \`Others\`. \`alignment\` should be \`HERO\` or \`VILLAIN\`. The archive is paginated and does not draw every cover at once; the JSON file and cover assets still contribute to mod size and should be organized carefully.

## Community packs

The featured pack area currently uses \`PackManager.getInstalledPacks()\`, because these manifests are the supported Ascendant content-pack format. These are not arbitrary Minecraft mods/modpacks. Add custom Ascendant \`.zip\` packs in the \`ascendant_packs\` directory using the existing manifest contract. The screen links back to the existing catalog; it does not silently install external files or download JARs.
