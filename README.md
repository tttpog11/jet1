# OreSeer (NeoForge 1.21.11, client side)

Predicts ore positions from a world seed and shows them on your HUD.

- **Open the menu:** Right Shift (rebind under Controls > OreSeer).
- **Modules (left side of the menu):** left click = on/off, right click = open its settings.
  - *Ore Finder* – world seed, chunk radius (1-24), which ores to look for.
  - *Markers* – on-screen boxes (distance label, max distance, box size).
  - *Ore List* – "nearest ores" list in the top-right corner.
- Settings are saved to `config/oreseer.json`. The seed defaults to `6608149111735331168`.
- Overworld only.

## How it works
The mod re-runs vanilla's ore decoration (`applyBiomeDecoration` + `OreFeature`) for each chunk
around you, using vanilla's own feature order / ore sizes / placement modifiers and the world seed.
The menu shows a **Seed check** percentage: predicted ore blocks that really exist in chunks your
client has loaded. A high number means the seed is right.

## Known limits
- Terrain isn't simulated: caves and air-exposure are ignored, so a few blobs (and the later blobs
  of a feature in the same chunk) can be off, or already mined out.
- Biome-only ores (emerald) only show up in chunks you have loaded.
- Markers are drawn on the HUD from the player's view, so they work in first person only.

Build: `gradle build` (Java 21). GitHub Actions builds the jar on every push.
