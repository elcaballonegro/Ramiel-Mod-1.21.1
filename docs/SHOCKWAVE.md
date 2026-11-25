# Shockwave mechanic - design plan

Goal: the biggest explosions (above all the star beam) should be felt as a pressure wave that **travels outward**,
**throws players and mobs back**, and **knocks trees over away from the blast**. It must stay readable, survivable and cheap.

## 1. The wave
One shockwave = one expanding ring on the server, using the *same curve the client already draws* (`GroundFx.waves`):
`radius(t) = R * (1 - (1 - t/life)^2.2)`. Fast at the start, slowing as it spreads. Because force and visual share the curve,
players are pushed at the moment the ring reaches them (`ShockMath.radiusAt`).

Each tick the ring covers the band `[r(t-1), r(t)]`. Everything in the band is processed **once** (entities by id, trees by base position).

## 2. Pushing players and mobs
For an entity at horizontal distance `d` from the epicentre:

| step | rule |
|---|---|
| direction | away from the epicentre; beam impacts blend in the beam's travel direction (`bias`, ~0.3-0.4) so debris and people are thrown *down-range* |
| falloff | `(1 - d/R)^1.6` |
| cover | march a ray epicentre -> entity; each solid sample (every 3 blocks) removes strength; 36 blocks of rock leaves 12%. Hiding behind a hill really works |
| speed | `2.4 x strength x falloff x cover`, horizontal cap 2.8 blocks/tick (the server rubber-bands anything faster) |
| lift | `0.12 + 0.30 x strength x falloff`, cap 0.9 (about 5 blocks of air) so people tumble instead of flying off the map |
| reductions | knockback-resistance attribute, sneaking (-20%), raising a shield (-40%), non-living entities (-40%) |
| damage | only inside 30% of R: `10 x strength x (1 - d/(0.3R))`, scaled by the Damage setting. Everything else is "pushed", not hurt |
| skipped | Ramiel and its parts, spectators, flying creative players |
| applied by | `setDeltaMovement` + `hurtMarked = true` (vanilla knockback path: the velocity packet is sent for us) |
| feedback | a boom at the player's position, volume by falloff; the client shakes the camera when the ring passes (`FxState`) |

## 3. Trees
A tree is felled when the ring reaches it, so a **ripple of falling trees** runs outward through a forest.

**Finding a tree** (`TreeScan`, pure Java, unit-tested): from a log, flood the connected logs (26-neighbours), then add leaves outward
following the vanilla `distance` property (a leaf at distance *n* joins only through a neighbour at *n-1*). That keeps two touching canopies apart.
A structure counts as a **natural tree** only if it has at least 3 leaves with `persistent = false` (worldgen leaves) and its lowest log stands on
dirt/grass. Player-built log houses have neither, so they are never touched. Anything over 420 blocks is ignored.

**Felling** (`ToppleEntity`): the tree's blocks are removed from the world and replaced by ONE entity that carries them. The client rotates the whole
tree about the centre of its base block by 90 degrees toward "away from the blast" (cardinal direction: blocks cannot lean diagonally), slow at first
then accelerating like a real fall (`angle = 90deg x p^2.2`). Duration scales with height (20-60 ticks).
When the animation ends the server **places the blocks rotated into the fallen position** (`ShockMath.rotateOffset`; the same maths as the render rotation, so
there is no pop). Logs get their axis turned. A block whose target is solid is lifted up to 3 blocks; if it still does not fit, a log drops as an item and a
leaf vanishes. Fluid blocks are never replaced. Saved with the world, so a tree mid-fall finishes after a reload.

Budget: candidate trunks are found by sampling the ring one block apart (heightmap `MOTION_BLOCKING_NO_LEAVES`, loaded chunks only), queued, and
felled at most 6 per tick and 140 per wave.

## 4. Which explosions (strength x Shockwave setting)
| Event | Epicentre | R (blocks) | ticks | strength | bias | trees |
|---|---|---|---|---|---|---|
| Impact strike detonation | crater centre | 1.9 x impact radius (182) | 80 | 1.6 | 0 | yes |
| **Star beam first impact** | beam end | 220 | 90 | **2.0** | 0.4 | yes |
| Star beam muzzle blast | ground under Ramiel | 120 | 50 | 1.2 | 0 | yes |
| Star beam pulses (every 0.5 s) | beam end | 40 | 24 | 0.45 | 0.3 | no |
| Star unfolding pulses (x3) | ground under Ramiel | 60-100 | 30 | 0.5 | 0 | no |
| Arrival slam | ground under Ramiel | 140 | 70 | 0.9 | 0 | yes |
| Death | ground under Ramiel | 180 | 90 | 1.4 | 0 | yes |
| Stun burst | ground under Ramiel | 90 | 40 | 0.9 | 0 | no |
| Lance impact | beam end | 28 | 24 | 0.45 | 0.3 | no |

## 5. Settings (appended to `Setting`, so existing ordinals do not move)
- **Shockwave** 0-3 (default 1): strength multiplier; 0 turns the whole mechanic off.
- **Trees fall** on/off (default on): needs Destruction above 0, because it changes the world.

## 6. Risks and how they are handled
- Server rubber-banding on huge velocities: capped at 2.8 b/t horizontal, 0.9 vertical.
- Chunk loading: nothing ever loads a chunk (`hasChunk` / `hasChunkAt` guards, also inside the tree flood).
- Many topple entities: caps above; the entity has no AI and does nothing until it lands.
- Player-built wood is safe (natural-leaf test + dirt-base test).
- Untested in game: the entity-data sync of the tree (CompoundTag), `renderSingleBlock` leaf tint (leaves use the default foliage colour), shader packs.

## 7. Not in this version (ideas)
Crushing entities under a landing tree; trees falling diagonally (needs real rotated rendering of placed blocks); bushes/flowers blown flat;
dust and debris particles along the ring; letting players "ride" the wave with a shield (surf).
