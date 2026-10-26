# Stormfront (step 1: clouds, wall clouds, tornadoes)

Forge 1.20.1 (47.x) mod. Runs on NeoForge-labelled 1.20.1 installs too.

## Get the jar (no building on your PC)
1. Upload all of these files to your GitHub repo (keep the `.github/workflows/build.yml` path exactly).
2. Open the repo's **Actions** tab -> "Build mod" -> wait for the green check.
3. Open that run -> **Artifacts** -> download `stormfront-mod` -> unzip -> put the `.jar` in your `mods` folder.

## In game (needs op / cheats)
- `/stormfront spawn classic|lp|hp`        storm ~1100 blocks away, heading at you
- `/stormfront spawn classic here`         storm ~380 blocks away
- `/stormfront tornado 4`                  force an EF0-EF5 tornado on the nearest storm
- `/stormfront list` / `/stormfront clear`

## Performance knobs
Config files (`config/stormfront-client.toml`, `stormfront-common.toml`): `cloudDetail`, `maxRenderedStorms`,
`blocksPerTick`, `maxDebris`, `destroyBlocks`.
