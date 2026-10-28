# Kelp Hunt (Fabric 1.21.11, client-side)

Base-hunting helper for Xaero's World Map + Minimap. It reads kelp growth from chunks your client
has already loaded (vanilla data only, nothing hidden), scores ocean chunks by how mature their kelp
is compared with the ocean-wide average, paints them on the World Map and drops scored Minimap waypoints.

Needs: Fabric Loader 0.18+, Fabric API, Xaero's Minimap 26.6.0 and World Map 1.47.0.

## XaeroPlus (optional)
With XaeroPlus installed, switch on its **Old Chunks** and **New Chunks** modules. Old chunks add +20 to a chunk's
score; new chunks are scaled to 25% (they can't have been loaded for long). Longer loaded = stronger evidence.

## Extra evidence (terrain)
Uses the chunk heightmaps your client already has, no block searching:
- **above-water blocks**: 3+ columns of something solid above the sea surface in an ocean chunk (+15). Icebergs/snow are ignored.
- **rough seabed**: many abrupt 3+ block steps in the sea floor, as left by digging or building (+10).
Natural things (monuments, shipwrecks, ruins, islands) can trigger these too, so they only add to the score.

## Commands
- `/kelphunt scan [radius]`  scan loaded chunks (default 12, max 32). Waypoints are named `KH Low` (score under 35), `KH Confident` (35-69) or `KH High` (70+), followed by why, e.g. `KH Low - 84 pct grown, old chunk, 3 chunks`.
- `/kelphunt auto`           toggle rescanning every ~15 s while you fly around
- `/kelphunt min <score>`    minimum score for a waypoint (default 10)
- `/kelphunt skipnear`      toggle skipping chunks inside your own simulation distance
- `/kelphunt terrain`       toggle the seabed / above-water evidence (on by default)
- `/kelphunt status`        show what is detected and the current settings
- `/kelphunt export`        write candidates (with coordinates) to `kelphunt-candidates.csv` in the game folder
- `/kelphunt clear`          remove results and Kelp Hunt waypoints

## Build
Push to GitHub -> Actions -> "Build mod" -> download the `kelphunt-mod` artifact.
