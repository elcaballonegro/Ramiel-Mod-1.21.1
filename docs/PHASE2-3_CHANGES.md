# What changed since the Phase 1 snapshot (read with HANDOFF.md, which is NOT yet updated)

DONE in this round (HANDOFF section 5 "T" items that are no longer todo): `client/RamielClient`, `client/film/Films`, `client/gui/{RamielSettingsScreen,ScopeLayer,WarningHud}`,
lang, sounds.json, item models, recipes, damage types + tags, railgun `Sfx.laser` hook, README/ARCHITECTURE/TUNING.

Model changes (client/model): transformations are pieces moving (no fading); every big form reverts through the shared folded pose then snaps into the octahedron
(`FormPoses.fold`, `RamielModel`, `Brick.lerp`). Forms refined from the reference frames: Lance = two chunky U-rimmed blocks; Cross arms narrow to the centre with flat bases outward;
Sentinel = third backing prism + long drifting blades; Sunburst = 16 big dark blades + 4 bright needles. Fixed latent bug: `FormPoses.fib` was called with indices past n
(vertical degenerate directions); it now wraps.

SUNBURST redesigned (user spec): small glassy central octahedron housing the core; ring of 20 small octahedra joined to it by thin spokes; the octahedra alternate between
outward-facing shields and (long up-spike + short down-spike). Needed: pool grown 57 -> 147 bricks (core stays last: CORE_TOP 145), a disc-shaped hit volume
(`RamielHitTest.rayWheel`, unit-tested), core size for the form reduced to fit inside the central octahedron. Ring is in the horizontal plane and spins with the body
(the entity already spins this form and speeds up with charge).

SHOCKWAVE (docs/SHOCKWAVE.md): new `world/{Shockwave,ShockMath,TreeScan}`, `entity/ToppleEntity` (+ registry entry `TOPPLE`), `client/render/ToppleRenderer`,
settings SHOCKWAVE and TREES_FALL (appended to `Setting`, so ordinals are stable; both on the settings screen), client camera jolt (`FxState.pressureWaves`), and launch calls at:
impact strike detonation, star beam (muzzle, first impact with down-range bias, repeating pulses, unfolding pulses), stun burst, lance impact, arrival slam, death.
MODEL CONTINUITY: `RamielModel.computeFor` (see ARCHITECTURE). INTRO film range 900 -> 480 (entities are tracked to ~512 blocks).
TOOLS: `tools/check_syntax.py` (found a real type error in my own change the first time it ran), `tools/shock-tests`, `tools/model-harness` (+ `interrupt`).

UNTESTED IN GAME (new risks, add to HANDOFF section 7): (14) `ToppleEntity` data sync through a synched CompoundTag and `level().holderLookup(Registries.BLOCK)`;
(15) `renderSingleBlock` draws leaves with the default foliage colour; (16) `Level.getHeight(MOTION_BLOCKING_NO_LEAVES)` on trunks, `LeavesBlock.DISTANCE/PERSISTENT` access,
`Block.UPDATE_SUPPRESS_DROPS`; (17) `LevelTickEvent.Post` and `ServerStoppingEvent` package names; (18) leaves that fall below ground are lifted up to 3 blocks when placed,
so they visibly settle upward at landing; (19) velocity above ~3 blocks/tick may be rubber-banded, so the caps in ShockMath are deliberately conservative.

Not done / next: first compile against NeoForge + Rimulib on the user's machine (Phase 4, HANDOFF section 7 risks), then in-game tuning of item `display` transforms, camera shots, balance.
(Fixed this round: the mid-morph jump and the INTRO range.) Ideas not built: crushing entities under a landing tree, diagonal falls, dust particles along the ring.
