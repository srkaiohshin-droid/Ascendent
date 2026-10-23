# Ascendant UI assets

The catalog and radial use original comic-print presentation built from one
public-domain comic page plus a procedurally generated halftone screen. No
runtime download is performed; every texture ships inside the mod jar.

## Source artwork

| File | Source | Author | Date | License | Use |
| --- | --- | --- | --- | --- | --- |
| `Race_for_the_Moon_02_page_22.jpg` | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Race_for_the_Moon_02_page_22.jpg) · [original file](https://upload.wikimedia.org/wikipedia/commons/7/7b/Race_for_the_Moon_02_page_22.jpg) | Jack Kirby (pencils), Al Williamson (inks) | September 1958 | Public domain in the United States: published before 1964 and copyright not renewed ([PD-US-not renewed](https://commons.wikimedia.org/wiki/Template:PD-US-not_renewed/en)) | Source for both themed backgrounds |

Publication: *Race for the Moon* #2, Harvey Comics, story "Island in the Sky".
The Commons file page records the source as
[Comic Book Plus](http://comicbookplus.com/?dlid=16921).

### Derivatives shipped in this repository

| Shipped file | Derivation |
| --- | --- |
| `src/main/resources/assets/ascendant/textures/gui/theme_cosmic.png` | Crop of the planet/rocket panel from the source page, resized to 1024×576, colour-graded and darkened for UI legibility. |
| `src/main/resources/assets/ascendant/textures/gui/theme_retro.png` | Full source page, resized to 1024×576, colour-graded toward warm pulp tones and darkened for UI legibility. |
| `src/main/resources/assets/ascendant/textures/gui/halftone.png` | Procedural 64×64 dot screen generated for this project; no third-party source. |

The crop, resize, colour grading and overlay are modifications made for
Ascendant. They do not imply endorsement by the original creators or Harvey
Comics.

## Attribution requirements

The source is public domain in the United States, so attribution is not
legally required there. Ascendant still credits the creators and source here
and in `CREDITS.txt`, and the in-game theme names identify the visual families
as "Cosmic print", "Retro pulp" and "Classic print" rather than claiming a
specific character or publisher.

## Rejected candidates

- `Out_of_This_World_1_page.jpg` (Wikimedia Commons) was downloaded while
  researching options but was not integrated: the available crop showed
  unrelated "Grom the Barbarian" artwork.
- `Autotypie_1882_Meisenbach.png` was considered for a historical halftone
  screen but the download was rate-limited (HTTP 429). The shipped halftone is
  therefore procedural.

## Fallback behaviour

`CatalogTheme.CLASSIC` has no background texture. If a theme has no texture or
the texture is unavailable, `ComicUi.backdrop` still paints the dark base,
readability veil, accent bands and halftone screen, so the catalog remains
usable without any external art.
