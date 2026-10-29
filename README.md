# Kelp Hunt (Fabric 1.21.11, client-side)

Base-hunting helper for Xaero's World Map + Minimap. It reads kelp growth from chunks your client
has already loaded (vanilla data only, nothing hidden), scores ocean chunks by how mature their kelp
is compared with the ocean-wide average, paints them on the World Map and drops scored Minimap waypoints.

Needs: Fabric Loader 0.18+, Fabric API, Xaero's Minimap 26.6.0 and World Map 1.47.0.

## XaeroPlus (optional)
With XaeroPlus installed, switch on its **Old Chunks** and **New Chunks** modules. Old chunks add +20 to a chunk's
score; new chunks are scaled to 25% (they can't have been loaded for long). Longer loaded = stronger evidence.

## Amethyst and caves
Budding amethyst grows its buds only while its chunk is loaded, and each growth step takes tens of minutes, so
fully grown clusters mean hours of loading (kelp saturates after roughly half an hour). Geodes are underground, so this
sees underground bases. It reads the block data your client already has and reports one score per chunk, never
individual geode positions. Open cave space in the chunk adds up to +15 (natural caves count too, so it only nudges).
The waypoint Y is the geode depth. Toggle with `/kelphunt amethyst`.

## Mobs
Counts mobs your client can currently see when a scan finishes (players excluded). A wandering trader adds +25 (a trader
llama alone +10), a skeleton horse +15, and 25+ mobs in one chunk +10 (60+ gives +20). A chunk with only mob evidence gets a
`Mobs ...` waypoint, e.g. `Mobs Confident - wandering trader, 31 mobs`. These can be completely innocent (a trader wandering by,
a lightning trap on a quiet hill), so they only raise the score. Your client only knows about entities near you, so scan while you
are in the area. `/kelphunt mobs` toggles it.

## Seed-predicted geodes (optional)
Kelp Hunt can't predict geodes itself. Run an outside tool that does (for example
[geode-finder](https://github.com/soulsoiledit/geode-finder), a Rust program) with your world seed, save its
positions to `kelphunt-geodes.csv` in the game folder (one per line, the first two integers are used), then
`/kelphunt geodes load` (chunk coordinates, the usual tool output) or `/kelphunt geodes load block`.
The 100 nearest targets get `Geode ...` waypoints: light blue = not scanned yet (go and load it), green = scanned and
amethyst found (with how grown), gray = scanned and nothing was there. `/kelphunt geodes clear` removes them.
This does not give you chunks you haven't loaded; the server never sends those.

## Underground bases
A base loads the chunks above it, so kelp over it keeps growing. The waypoint sits at the middle of the strong patch
(a parked player keeps a square of chunks ticking around them) and the name shows the patch size, e.g. `21x21 chunk patch`.
Y is the kelp/seabed height, not the base depth. Turn on `/kelphunt skipnear` so your own loading doesn't count.

## Extra evidence (terrain)
Uses the chunk heightmaps your client already has, no block searching:
- **above-water blocks**: 3+ columns of something solid above the sea surface in an ocean chunk (+15). Icebergs/snow are ignored.
- **rough seabed**: many abrupt 3+ block steps in the sea floor, as left by digging or building (+10).
Natural things (monuments, shipwrecks, ruins, islands) can trigger these too, so they only add to the score.

## Commands
- `/kelphunt scan [radius]`  scan loaded chunks (default 12, max 32). Waypoints are named `Amethyst High - 92 pct grown, caves 11 pct` or `Kelp Low - 84 pct grown, old chunk`: the evidence type, a confidence label (Low under 35, Confident 35-69, High 70+), then why. Amethyst waypoints point at the geode itself; kelp waypoints point at the middle of the grown patch.
- `/kelphunt auto`           toggle rescanning every ~15 s while you fly around
- `/kelphunt min <score>`    minimum score for a waypoint (default 10)
- `/kelphunt skipnear`      toggle skipping chunks inside your own simulation distance
- `/kelphunt mobs`          toggle the mob evidence (on by default)
- `/kelphunt amethyst`      toggle amethyst + cave evidence (on by default)
- `/kelphunt terrain`       toggle the seabed / above-water evidence (off by default; only helps with surface builds)
- `/kelphunt status`        show what is detected and the current settings
- `/kelphunt export`        write candidates (with coordinates) to `kelphunt-candidates.csv` in the game folder
- `/kelphunt clear`          remove results and Kelp Hunt waypoints

## Build
Push to GitHub -> Actions -> "Build mod" -> download the `kelphunt-mod` artifact.
