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

Not done / next: first compile against NeoForge + Rimulib on the user's machine (Phase 4, HANDOFF section 7 risks), then in-game tuning of item `display` transforms, camera shots, balance.
Known: if a new morph starts while another is mid-way, the model jumps to the previous target first (single from/to pair) - cosmetic, fix by blending three poses if it bothers.
Optional: INTRO film range is 900 but entity tracking is ~512 blocks, so players beyond that get no intro film (lower the range in FilmKind).
