# OreSeer (Fabric 1.21.11, client side)

Predicts ore positions from a world seed and shows them on your HUD.

- **Open the menu:** Right Shift (rebind under Controls).
- **Modules (left side of the menu):** left click = on/off, right click = open its settings.
  - *Finder* - world seed, chunk radius (1-24), min / max height filter.
  - *Overworld Ores* - coal, iron, copper, gold, redstone, lapis, diamond, emerald.
  - *Deepslate Ores* - the deepslate versions (below Y 0) of iron ... emerald.
  - *Nether Ores* - nether quartz, nether gold, ancient debris.
  - *Markers* - on-screen boxes labelled with vein size and distance.
  - *Ore List* - nearest veins in the top-right corner (ore, vein size `xN`, distance, coords).
- Settings are saved to `config/oreseer.json`. The seed defaults to `6608149111735331168`.
- Works in the Overworld and the Nether.

## How it works
The mod re-runs vanilla's ore decoration for each chunk around you using the world seed, with
vanilla's own feature order / ore sizes / placement data. The menu shows a **Seed check**
percentage: predicted ore blocks that really exist in chunks your client has loaded.

## Known limits
- Terrain isn't simulated: caves and air-exposure are ignored. Veins whose loaded blocks are all
  air (e.g. high coal above the surface, mined ore) are hidden; use the height filter for the rest.
- Ancient debris only generates when hidden from air, so expect some extras in lava/cave areas.
- Biome-only ores (emerald) only show up in chunks you have loaded.
- Markers are drawn on the HUD from the player's view, so they work in first person only.
