# Kelp Hunt (Fabric 1.21.11, client-side)

Base-hunting helper for Xaero's World Map + Minimap. It reads kelp growth from chunks your client
has already loaded (vanilla data only, nothing hidden), scores ocean chunks by how mature their kelp
is compared with the ocean-wide average, paints them on the World Map and drops scored Minimap waypoints.

Needs: Fabric Loader 0.18+, Fabric API, Xaero's Minimap 26.6.0 and World Map 1.47.0.

## Commands
- `/kelphunt scan [radius]`  scan loaded chunks (default 12, max 32). Waypoints are named `KH <score>`.
- `/kelphunt auto`           toggle rescanning every ~15 s while you fly around
- `/kelphunt min <score>`    minimum score for a waypoint (default 35)
- `/kelphunt clear`          remove results and Kelp Hunt waypoints

## Build
Push to GitHub -> Actions -> "Build mod" -> download the `kelphunt-mod` artifact.
