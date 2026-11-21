# Cassiopeia: a Shooting Star addon (Fabric 1.21.11)

Adds **SC-01 · Cassiopeia** to the Stellar Remote of *The Shooting Star [Demo]*, in the style of SS-04 Seven Stars.

Aim at the ground and cast. The W of Cassiopeia is drawn on the land in light. Its five bright stars (Caph, Schedar,
Gamma Cassiopeiae, Ruchbah, Segin) then fall one by one, dimmest first and Schedar last. Each crater is as wide as its
star is bright and has a glowing star core standing in it. After the last star, veins of light run along the W and are
branded into the land. The caster gets a seven-shot cutscene through RimuLib's film director (title card, skip key and
the menu's Cutscenes switch all work). It has its own key (**Y**), menu card, cooldown, and power / damage / speed
sliders.

It only uses RimuLib's public API (`SkillApi`, `AimApi`, `FilmApi`). It never touches The Shooting Star's own classes,
so The Shooting Star's jar is not needed to build it.

## Build on GitHub (no Gradle on your PC)

1. Make sure the repo is **private**, then upload `rimulib-1.3.0-fabric.jar` into the `libs` folder on github.com
   (Add file > Upload files). The workflow stops with a clear message if it's missing.
2. Open the **Actions** tab > "Build add-on" > the latest run (or "Run workflow"). When it's green, download the
   `cassiopeia-addon` artifact at the bottom of the run page, unzip it and put the jar (not the `-sources` one) in `mods`.
3. If the run is red, the error is shown at the top of the run page. Paste it to Claude.

## Build on your own PC

1. Put the RimuLib file you downloaded (`rimulib-1.3.0-fabric.jar`) in `libs/`. Keep exactly one `rimulib*.jar` there.
   The build script takes the 1.21.11 build out of it by itself.
2. `./gradlew build` gives `build/libs/shooting_star_cassiopeia-0.1.0.jar`.
   (`./gradlew runClient` starts a dev game with RimuLib loaded. Put The Shooting Star's jar in `run/mods/` to cast.)

Don't ship RimuLib inside your jar and don't upload it anywhere: it is All Rights Reserved.

## Install

A Fabric 1.21.11 instance with Fabric API, RimuLib 1.3.0+, The Shooting Star [Demo], and this jar. Install on the
server and every client.

## Config

`config/shooting_star_cassiopeia.properties` is written the first time the game starts:

| key | default | meaning |
|---|---|---|
| `carve_terrain` | `true` | dig the craters, set the cores, lay the veins. `false` = light, sound and damage only |
| `cooldown_seconds` | `45` | the remote's cooldown |
| `max_crater_radius` | `30` | widest crater, in blocks, whatever the power slider says |
| `max_power` | `2.5` | top of the power slider (1.0 is a W 56 blocks wide) |

**World damage is permanent.** Back up worlds you care about.

## Where things are

| file | what |
|---|---|
| `Timeline.java` | the schedule (ticks) that the server, the client sky and the film all share |
| `Plan.java` | where the stars land and how big each crater is; also the packet sent to nearby players |
| `Strike.java` | the server side of one cast: damage, craters, veins |
| `Crater.java` | digs one crater a few columns per tick, lines it and stands the core in it |
| `client/Show.java` | the sky: the W in light, the falling stars, blasts (vanilla particles) |
| `client/Film.java` | the caster's cutscene (shots, camera paths, impact shake) |
| `assets/shooting_star_cassiopeia/lang/en_us.json` | menu name, description, key label |

## Status

This was written and checked against the API in your RimuLib 1.3.0 jar, but **it has not been compiled or run**: the
machine it was written on can't download Minecraft or Fabric. Expect to fix a few small compile errors on the first
build. The likeliest spots, with the one-line fix for each:

- **`net.minecraft.resources.Identifier`** (used in `CassiopeiaMod` and `Sfx`): this is the 1.21.11 Mojang-mappings name
  for what older versions call `ResourceLocation`.
- **`addAlwaysVisibleParticle`** in `Show.spawn`: if missing, use `level.addParticle(...)`. Stars then only show on their
  last 32 blocks.
- **`Gradle` / `loom_version` / `fabric_api_version`** in `gradle.properties` and `gradle/wrapper`: bump them to the
  latest for 1.21.11 if Gradle complains.
