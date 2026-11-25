# Architecture

```
dev.ramiel
  RamielMod, ModRegistry, RamielConfig, Setting, Form      boot, registries, config (17 live settings), form enum
  entity/   RamielEntity (+6 RamielPart hit boxes), RamielBrain (idle AI), RamielHitTest, Orientation, BeamKind
  entity/attack/  Attack (stage machine) -> LanceAttack CrossAttack SunburstAttack SentinelAttack StarAttack StunAttack ImpactStrike
  world/    Damage BeamCaster Destruction (Rimulib CarveApi craters) ChunkAnchor RamielTracker Wards Sfx RamielSpawner HitResolver RailcannonShot ServerEvents
            Shockwave (the pressure wave) ShockMath + TreeScan (pure logic, unit-tested)
  ai/       FleeRamielGoal ShootRamielGoal MobHooks         other mobs react to Ramiel
  item/     MiniRamielItem RailcannonItem PlasmaCellItem AngelCoreItem
  net/      Payloads (native payloads) FxKind FilmKind
  client/   RamielClient (all client wiring)  ClientNet (payload handlers)
    model/  Brick FormPoses RamielModel RamielMesh            the procedural model (below)
    render/ RamielRenderer MiniRamielRenderer RamielRenderTypes
    fx/     FxState FxRender GroundFx BeamFx GlowFx ShieldFx SpaceScene FarRender ScreenEffects Draw
    film/   Films                                             every cutscene (Rimulib FilmApi)
    gui/    ScopeLayer WarningHud RamielSettingsScreen
```

## The model (no GeckoLib)
A fixed pool of 147 "bricks" (frustums: box / pyramid / spike / slab). A form is a function that fills the pool (`FormPoses.fill`).
Index map: 0-1 halves | 2-9 plates | 10-41 spikes | 42-53 shards | 54 needle | 55-144 wheel (Sunburst only) | 145-146 core (always last).
Wheel: 55-94 twenty small octahedra (2 pyramids each) | 95-114 spokes | 115-124 shields | 125-144 long/short spikes.

**Transformations (`RamielModel.compute`) are pieces moving, never fading:**
- A piece used by both forms slides, turns and resizes into place. A piece used by only one flies out of / back into the centre
  (scaled about the core, keeping its own colour and opacity: `Brick.lerp`).
- Every big form goes through ONE shared pose, `FormPoses.fold`: a stack of thin plates, small caps, a bundle of long shards, loose chips, a thin spine.
  - rest form -> big form: octahedron SPLITS into the stack (38% of the morph), pieces fly out into the new form.
  - big form -> rest form (reversion, identical for EVERY attack): form FOLDS into the stack (60%), then the pieces accelerate and SNAP into the octahedron.
  - big -> big: fold then unfold (stun urchin -> star). Anything -> urchin: no fold, the spikes burst straight out.
  - Rest forms: DORMANT, SHIELD, DRILL. Big forms: LANCE CROSS SUNBURST SENTINEL STAR URCHIN.
- Pieces start at staggered moments and swing out along a per-piece direction (`BURST`), so it reads as shattering and re-forming.
- Server side: `Attack.goTo(REVERT)` calls `setForm(afterForm(), duration(REVERT))`, so any attack reverts for free. `StarAttack` stays in STAR by design.
- Preview/test without Minecraft: `tools/model-harness/run.sh` (see its header).

## Sunburst (the wheel)
A small glassy octahedron housing the core, a ring of 20 small octahedra (radius 0.8 R, spinning with the body) joined to it by thin crystal spokes.
The octahedra alternate: a SHIELD (a mint kite standing just outside, leaning outward) / a long spike up + a short spike down. Spikes grow with charge.
The whole wheel fits inside 1.0 R so the vanilla collision slabs cover it; `RamielHitTest.rayWheel` uses a matching flat disc (radius ~1 R, half-height ~0.36 R)
instead of the octahedron while the form is SUNBURST. The long up-spikes are decoration (not hittable).

## Shockwave and falling trees (see docs/SHOCKWAVE.md)
`Shockwave.launch(level, groundCentre, bias, radius, life, strength, trees, cause)` is called next to each `FxKind.SHOCKWAVE` with the SAME radius and duration.
Each server tick the ring grows along the client's curve (`ShockMath.radiusAt`); entities in the new band are thrown (`setDeltaMovement` + `hurtMarked`), and
trees are sampled, queued and felled (<= 6 per tick, 140 per wave). `TreeScan` finds one natural tree (logs, then leaves by vanilla `distance`);
`ToppleEntity` carries its blocks (one synced CompoundTag, also saved) and the client turns it 90 degrees about the base block (`ToppleRenderer`);
at the end the server places the blocks rotated (`ShockMath.rotateOffset` == the render rotation). The client shakes the camera when a ring passes it (`FxState.pressureWaves`).

## Model continuity
`RamielModel.computeFor(key, ...)` remembers the last drawn pose per Ramiel. The entity's morph value glides from its old value to 0 over the tick a new form pair
arrives, which used to flash a near-finished pose for a frame at the start of every transformation; it is now held at 0 until the clock restarts, and a
transformation that interrupts an unfinished one starts from the pose on screen. (`tools/model-harness/run.sh interrupt` measures it.)

## Tools (no Minecraft needed; need a JDK)
`tools/check_syntax.py` (javac with the library noise filtered; read its LIMITS), `tools/model-harness/run.sh` (preview and test the brick model),
`tools/shock-tests/run.sh` (shockwave maths and tree finding), `tools/gen_assets.py` (textures and sounds).

## Networking
Native payloads. Beams are not packets: they are synced entity data (kind, length, t, aim) drawn client-side.
FX are `Payloads.fx(kind, a, b, c, f1, f2)` (see `FxKind`). Films are `Payloads.film(kind, entityId)` -> `Films.play`.

## Cutscenes (`client/film/Films`)
Camera paths are live functions of the Ramiel entity (so they follow arrival from space, drift, turning). A `Rig` snapshots the player's
side/distance at the start. Shot lengths match server stage lengths and attack films end before the charge stage.
Input is frozen only for intro, stun, first star unfolding and death. All films are skippable.

## Far render, arrival, chunks
Far players get a `Beacon` every 10 ticks and see a simplified Ramiel clamped to 0.9 x depth-far. Arrival: the entity sits at its final position; the model is drawn
`arrivalOffsetY` (2600 blocks) higher and eases down over 280 ticks; camera above 900 blocks switches `SpaceScene` on. `ChunkAnchor` keeps 5x5 chunks loaded.
