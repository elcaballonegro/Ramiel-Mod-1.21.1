# Ramiel (NeoForge 1.21.1)

A boss mod: **Ramiel**, the octahedral Angel from *Rebuild of Evangelion*, plus a craftable handheld **Railcannon**.
Fan project; every texture, model and sound is original/generated. Mod id `ramiel`, package `dev.ramiel`, Java 21.

## Requirements
- NeoForge 1.21.1
- **Rimulib >= 1.2.2** (the 1.2.1 jar has no API classes). Put the jar at `libs/rimulib.jar` to build; players need it in `mods/`.

## Playing
| Item | How to get it | Use |
|---|---|---|
| **Mini Ramiel** | 4 lapis blocks + 4 diamonds + nether star | Right click: Ramiel falls from space onto the nearest deep water (or 110 blocks ahead). Sneak + right click: settings screen. Consumed on use (not in creative). |
| **Railcannon** | `DNR / IBI / RND` = diamond block, netherite ingot, redstone block, iron block, beacon | Hold right click ~2 s (scope opens, view zooms), release to fire. Pierces everything, ignores Ramiel's shield. 20% of Ramiel's health on the body, 50% on the exposed core. |
| **Plasma Cell** | nether star + diamond + blaze powder + ender pearl -> 2 | Railcannon ammo (one per shot) |
| **Angel Core** | dropped by a defeated Ramiel | angel core + blaze powder + ender pearl -> 6 plasma cells |

**Shockwaves:** the biggest explosions (the impact strike, and above all the star beam) send a pressure wave outward. It throws players and mobs
back (less if you hide behind terrain, crouch or raise a shield), hurts anything close, and knocks natural trees over, away from the blast. Player-built
wooden structures are never touched. Design in `docs/SHOCKWAVE.md`.

Ramiel idles, spins slowly and never tracks anyone until threatened. Left alone it drills into the ground, charges a huge blast
(red ring on the ground, "BLAST ZONE" HUD, alarm) and leaves a crater. When attacked, or when a player comes close, it changes shape
(pieces visibly split, fold and re-form) and fires: Lance, Cross, Sunburst, Sentinel. Below 2/3 health it is stunned for a minute,
then stays in the star form and fires mountain-destroying beams until killed.
Weak point: the red core, exposed while charging and recovering. Shield (phase 1): projectiles bounce until its durability breaks.

## Commands (op level 2)
`/ramiel summon` | `summon_here` (anywhere, no water search) | `kill` | `phase2` | `heal` | `settings`

## Settings
19 live settings (destruction, impact radius/charge time, damage, health, speed, aggression, notice range, mobs, cutscenes, horizon render, shockwave strength, trees fall...).
Open with Sneak + right click on the Mini Ramiel, or `/ramiel settings`. Saved to `ramiel-common.toml`. See TUNING.md.

## Build
First time: read `docs/FIRST_BUILD.md`. In short: JDK 21, Rimulib >= 1.2.2 saved as `libs/rimulib.jar`, then `./gradlew build` (or `python3 tools/collect_errors.py` for a short pasteable error report).
`./gradlew runClient` / `runServer` start the game. Layout and design: ARCHITECTURE.md, TUNING.md, docs/SHOCKWAVE.md. Project memory for the AI-assisted workflow: HANDOFF.md.
