# RAMIEL MOD — HANDOFF (read this first in every new chat)

Target: NeoForge 1.21.1 (Java 21). Mod id `ramiel`, package `dev.ramiel`. A boss mod: Ramiel (octahedral Angel, Rebuild of Evangelion) +
a craftable handheld Railcannon. Uses the public **Rimulib** API for craters, cutscenes, screen effects. Fan project; all assets are original/generated.

**STATUS: Phases 1-3 are finished. Every feature is written and syntax-checked, but the project has NEVER been compiled against NeoForge/Rimulib
and NEVER run in game. NEXT = PHASE 4 (section 6): the user builds on their machine and pastes errors.**
Suggested prompt: "Read HANDOFF.md and unzip ramiel-mod-snapshot.zip. Do Phase 4. Here is build-errors.txt: ..."

## 0. Rules for working in this project (saves usage)
- One PHASE per chat. Only open the files the task needs. Do NOT re-upload the 413 reference frames (section 3 has the analysis) and do NOT re-fetch
  the Rimulib docs (section 2 was checked against docs 1.2.4 during Phase 2; re-fetch only for one failing signature).
- Source lives in `ramiel-mod-snapshot.zip` (project knowledge). Unzip it in the sandbox. Update HANDOFF.md + the zip ONLY when the user says the
  phase is done, and then deliver only those two files in outputs (previews live in `docs/previews/` inside the zip).
- Sandbox facts: Maven, Gradle and maven.neoforged.net are NOT reachable, so nothing can be compiled against NeoForge here. GitHub (raw), pypi, npm, apt work.
  The shell is `sh` (no `{a,b}` brace expansion; use explicit paths or `bash -c`). A JDK is NOT preinstalled: `apt-get install -y openjdk-21-jdk-headless`.
  The asset/preview tools need `pip install soundfile pillow numpy --break-system-packages`. /home/claude resets between chats: re-extract the zip each time.
- Tools in `tools/` (all run without Minecraft):
  - `check_syntax.py`: javac with the "library not installed" noise filtered. LIMITS: it cannot catch a wrong call whose arguments involve library types, and it stops at the first parse error.
  - `collect_errors.py`: Phase 4. Groups a Gradle build log (or, with `--game`, a game log) into a short report for pasting.
  - `model-harness/run.sh` with `check` | `forms out.png` | `morph FROM TO out.png` | `interrupt`: runs the brick model against a stub JOML and renders previews.
  - `shock-tests/run.sh`: unit tests for the shockwave maths and tree finding (28 checks).
  - `gen_assets.py`: regenerates every texture and sound.
- LOOK at forms with the harness renderer before changing them. During Phase 3 it found one real bug (an out-of-range direction helper) and `check_syntax.py` found another (a type error in my own change).

## 1. Decisions already made (do not re-debate)
- **Renderer**: procedural "brick" model, no GeckoLib. Pool of **147** bricks; each = frustum (box/pyramid/spike/slab): centre, quaternion, h, bottom half-size (bw,bd),
  top half-size (tw,td), rgb, glow, alpha. CPU shading (fresnel + sky env + sun spec), `RenderType.entityTranslucent` (+ eyes/emissive for glow).
  Index map: 0-1 halves | 2-9 plates | 10-41 spikes | 42-53 shards | 54 needle | 55-144 WHEEL (Sunburst only) | 145-146 core (always last).
- **Transformations are pieces moving, never fading** (`RamielModel`, `Brick.lerp`, `FormPoses.fold`): rest form -> big form = the octahedron SPLITS into the shared "folded" pose
  (stack of thin plates + caps + bundle of long shards + chips + spine), then the pieces fly out. Anything -> dormant = REVERSION: fold, a beat, then the pieces accelerate and SNAP into the octahedron.
  It is identical for EVERY attack (server `Attack.goTo(REVERT)` calls `setForm(afterForm(), duration)`). Big -> big folds then unfolds. Anything -> URCHIN bursts straight out (no fold).
  A piece used by only one form flies out of / into the core keeping its colour and opacity. Rest forms: DORMANT SHIELD DRILL. Big: LANCE CROSS SUNBURST SENTINEL STAR URCHIN.
  STAR stays out in phase 2 (by design); STUN reverts into STAR.
- **Model continuity** (`RamielModel.computeFor(key,...)`): remembers the last pose per Ramiel. It holds morph at 0 while the clock restarts (the entity's morph glides from its old value for one tick,
  which used to flash a near-finished pose at the start of EVERY transformation) and starts an interrupting transformation from the pose on screen. Jumps are now < 0.01 R.
- **Sunburst = the wheel**: small glassy central octahedron housing the core; a ring of 20 small octahedra (radius 0.8 R, spinning with the body) joined to it by thin spokes; they alternate
  between a shield (mint kite leaning outward) and a long spike up + short spike down. The whole wheel fits inside 1.0 R. While SUNBURST the hit volume is a flat disc (`RamielHitTest.rayWheel`).
- Hit boxes: vanilla AABB = ONLY the core (5.2 blocks). Body = 6 stacked `RamielPart` PartEntities (like the Ender Dragon; widths 48/32/16, 8 tall). Exact hit by `RamielHitTest`
  (octahedron |x|+|y|+|z| <= R*scale, or the disc for Sunburst, + core sphere). `HitResolver` (ProjectileImpactEvent) cancels misses, applies shield bounce, tags core/body.
- R = 24 blocks (half-diagonal). Hover = config 84 blocks.
- Health: real pool `hp` (BASE 3000 x config); vanilla max-health attribute fixed at 1000. Body hit x0.25, exposed core x5. Railcannon: 20% max HP body / 50% core (2 core shots kill), ignores shield.
  Phase gate at 2/3 HP -> StunAttack (60 s helpless, urchin) -> phase 2 = StarAttack only (30-60 s cooldown). Stun floor 30%. Railcannon/kill bypass gates.
- Networking: NeoForge native payloads (`Payloads`), NOT rimulib NetApi. Beams are not packets: synced entity data (beamKind/len/t + aim) drawn client-side.
- Config = ModConfigSpec COMMON (`RamielConfig`), edited live via the `Setting` enum (19 rows; new ones are APPENDED so ordinals stay stable) through the settings screen packet (op level 2 or single-player owner).
- Far render ("LOD"): `Beacon` every 10 ticks to players within farRange; the client draws a simplified Ramiel clamped to 0.9*getDepthFar().
- Arrival: the server entity sits at its FINAL position; synced `arrival` 0..1 over 280 ticks; the renderer offsets the model up `arrivalOffsetY` (2600, ease-in); camera above 900 blocks turns `SpaceScene` on.
  Intro film range is 480 (entities are only tracked to ~512).
- Chunk anchor: forces 5x5 chunks around Ramiel. Blast-zone ground ring/scars are GEOMETRY, not post shaders. Impact frames use `ramiel_impact.fsh` (FxApi).
- Railcannon: handheld (hold RMB 40 ticks, release), plasma cell ammo, scope overlay + zoom (`ScopeLayer`). Mini Ramiel: BEWLR spinning crystal (created lazily); RMB summons (nearest deep water, else 110 ahead);
  Shift+RMB opens settings. Angel Core = trophy/crafting material (drops from Ramiel; makes 6 plasma cells), NOT a summoner.
- **Shockwave** (`docs/SHOCKWAVE.md`): `Shockwave.launch(level, GROUND centre, bias, radius, life, strength, trees, cause)` is called next to each `FxKind.SHOCKWAVE` with the SAME radius and duration; the ring follows the
  client's curve R*(1-(1-t/life)^2.2). It throws entities away from the blast (terrain cover, knockback resistance, sneaking and shields reduce it; speed cap 2.8 b/t, lift cap 0.9; damage only in the inner 30%).
  Trees: `TreeScan` (logs, then leaves by the vanilla `distance`; natural = >= 3 non-persistent leaves and the base stands on dirt) -> `ToppleEntity` carries the REAL blocks (one synced CompoundTag, also saved);
  the client turns them 90 degrees about the base block (`ToppleRenderer`, vanilla block renderer, no custom models); the server places the blocks rotated when it ends (`ShockMath.rotateOffset`, the same rotation).
  Budget 6 trees/tick, 140/wave. Settings: Shockwave (0-3) and Trees fall (needs Destruction > 0).

## 2. Rimulib facts (version needed: >= 1.2.2; docs describe 1.2.4. The user's old 1.2.1 jar has NO API classes!)
Packages for 1.21.1 (confirmed on the docs "Getting started" page): `cyou.rimuru.rimulib.mc1211.api.{RimulibApi,CarveApi,WardApi,...}`, `...mc1211.client.api.{FilmApi,FxApi,UiApi,MenuApi}`,
`...mc1211.client.cinematic.{Shot,Subject,CameraPose,Cutscene}`, `...mc1211.bulwark.Threat` (Kind: COLUMN,BOLT,BLAST,FALLING,CUT). The docs' API reference page omits the `mc1211` segment: use it. Do NOT touch internal classes.
NeoForge has no separate client source set: keep client.api calls in code that only runs on the client.
```
RimulibApi.connect(String modId, Class<?> anchor) -> RimuMod   // keep in a static field (stored as Object)
CarveApi: UNTOUCHED; interface Floor{int at(int x,int z)}; interface Surface{BlockState at(int x,int y,int z,BlockState was)} (null=keep)
  flatten(level, Vec3 centre, double r, Floor, Surface)   line(level, Vec3 start, Vec3 heading, double length, double halfWidth, Floor, Surface)
  column(level,centre,r) slit wedge lens ; keepBlocks(Predicate<BlockState>) global register
  Cut: named(String) keepBlocks() perTick(int) lazy() atOnce(boolean) prepare() ready() cancel() start() tick() tick(double) done() stop() finish()
  start() self-ticks; prepare() builds early, tick() lands it in one tick. Floors/surfaces run on the server thread.
WardApi.warn(ServerLevel, String modId, Threat.Kind, Vec3 at, double radius, int argb, int leadTicks, int durationTicks) -> boolean
FilmApi: enableKey(); follow(Entity, Vec3 target)->Subject; pose(Vec3 position, Vec3 lookAt, float fov)->CameraPose;
  cut(int start,int end,Shot.Path); sweep(int start,int end,int blendIn,Shot.Path); film(Subject,String tier,String title,int argb,List<Shot>)->Cutscene;
  play(Cutscene); active(); time(subject,partial); weight(subject); skip(); stop(subject); hintOnly(modId); holdInput(BooleanSupplier); hideHand(BooleanSupplier)
  Shot.Path lambda: (s, partial, k) -> CameraPose ; partial is float, k runs 0..1 over the shot (our helpers take double: widening works for either type).
  Films.java uses `var` for Shot/Subject/CameraPose so only FilmApi is imported by name. Every film draws Rimulib's title card (tier/title); `hintOnly(modId)` turns all cards off.
FxApi: stage(String modId)->Stage; Stage.add(Layer)/remove; Layer: void draw(Frame); Frame: pass(String program, Consumer<Uniforms>), screen(), partial()
  Uniforms: f1 f2 f3 f4 v3(String,Vec3) vec4s texture. ScreenFx fields (reset each frame): shake aberration desaturate zoomBlur flash flashR/G/B vignette vignetteR/G/B bloom.
  Shaders: assets/<mod>/shaders/fx/<name>.fsh, NO #version; given: texCoord fragColor DiffuseSampler DepthSampler Time GTime OutSize scenePosAt
UiApi: enum Font{DISPLAY,LABEL,MONO}; overlay(GuiGraphics, Runnable) (ALL UiApi drawing goes inside it; it lays out in its own fixed frame, y = baseline);
  text(g,Font,String,x,y,argb)->float; centred(...); text(g,font,s,x,y,align,scale,argb) (align 0/0.5/1); measure; box(g,x,y,w,h,argb); width(g)/height(g) = the frame size.
  So text positions are FRACTIONS of width/height; anything that must line up with the mouse or screen pixels uses vanilla GuiGraphics.
```
Verified-from-compiling addon patterns (MIT/CC0 "Shooting Star Addition"): `extends RenderType` to reuse protected state shards; `new BufferBuilder(new ByteBufferBuilder(n), Mode.QUADS, fmt)`;
`vc.addVertex(x,y,z).setColor(..).setUv(..).setOverlay(..).setLight(..).setNormal(..)`; `builder.build()` (nullable) then `renderType.draw(mesh)`; `RenderLevelStageEvent`; payloads via `event.registrar("v")`.
Licences: Shooting Star DEMO = All Rights Reserved + obfuscated -> NEVER decompile or copy. Shooting Star Addition (MIT/CC0) and TON 618 (CC0, partly obfuscated) = study OK. Downfall 1.20.1 = MIT (own Ramiel model; unused).
Official MDK (CC0) values now used in the build: moddev plugin 2.0.148, neo_version 21.1.267, Gradle 9.2.1, foojay 1.0.0 (github.com/NeoForgeMDKs/MDK-1.21.1-ModDevGradle).

## 3. Reference-frame analysis (413 frames; replaces re-viewing them)
Colours: body deep blue (0.16,0.33,0.95) translucent crystal; plates mint cyan-green (0.35,0.95,0.78); dark (0.05,0.09,0.25); cyan (0.4,0.95,1); core red / magenta in the star.
- DORMANT: octahedron, a vertex points down, spins slowly on the vertical axis.
- DRILL (340-379): the lower apex stretches into a thin needle with glowing cyan beads; pink/orange rings + sparks at the ground.
- LANCE (98-121): the octa splits at the equator; the halves separate and re-form as two CHUNKY stepped blocks (base slab + a U-shaped raised rim open to the front), small cap pyramids outside,
  a spine of ~6 beads between them, red core in the gap, pink glow. Beam: thin pink->white.
- CROSS (146-167): halves flatten and fly apart; 6 small cubes form a plus; then 4 long pyramid ARMS whose points meet at the centre, flat bases outward (+X,-X,+Y,-Y in the view plane), with detached
  spinning end plates/diamonds beyond. Beam (208): thick gold-white, the ground footprint is a CROSS (user wants 12+ craters).
- SUNBURST: (user's spec) central glassy octahedron + ring of ~20 small octahedra alternating shield / (long up spike + short down spike), thin spokes. It spins faster and faster while charging, then a sweeping
  thin MAGENTA beam arcs 360 degrees in a cone (447-465).
- SENTINEL (520-530): 6 nested square mint plates stacked like pages in front of the core, 3 big blue backing pieces (two pyramids + a prism), a thin rod with a red tip, long blades and chips drifting to one side. Beam: thick WHITE.
- SHIELD (505-515): one big flat face points at the threat; golden thin lines + rainbow hexagon cells when hit.
- STAR (725-746): upside-down 5-point star facing the viewer (one point down, two up), unfolds in 3 stages (charge ticks 1/80/160), magenta core with an energy ring. Beam (761-846): huge; pink pressure-disc rings travel along it;
  the mountain erupts orange; debris/smoke along the beam.
- URCHIN (646-648, 915-963): dark navy spikes in all directions, exposed red core blobs, violet glow. Used for stun + death.
- Reversion (244-263): the form collapses into stacked plates + an elongated shard cluster, then snaps back to the octahedron. This is now the universal reversion for every attack.
- Hit by railcannon (627-648, 870-892): beam cyan-green with white core, Ramiel flashes pale then dark blue; ring of purple energy at the charge (870). Explosions (484-500): glowing white-gold spheres then orange fireballs; ground scars glow red/orange.
- Scope HUD (547-607): circular reticle, orange HUD text, NIGHT/ENHANCED sight labels, green triangle lock markers, red dashed diagonals.

## 4. Gameplay spec (implemented in server code)
Idle: slow spin, never tracks anyone. Shield (phase 1) when threatened & idle: SHIELD form faces the last hit; durability 1500; projectile/explosion hits wear it; broken => 600 ticks vulnerable.
Attacks (cooldown 10-20 s / aggression): Lance 40%, Cross 20%, Sunburst 15-40% (crowds near), Sentinel 25%. Stages MORPH(cutscene)/CHARGE/PAUSE(aim locked + red telegraph)/FIRE/RECOVER(core exposed)/REVERT
(Lance 40, Cross/Sunburst/Sentinel 50, Drill 80 ticks). ImpactStrike (its own objective when nobody is around): the needle extends and stabs, 25 s charge with red blast-zone ring + HUD + alarm, the crater is pre-built
(`Cut.prepare()`, landed with `Cut.tick()`), then it walks 350-600 blocks away. Damage >= 8% max HP (or any core hit) during the charge cancels it (setting). Mobs: animals/villagers flee (speed boost);
skeletons/pillagers/illusioners/blazes/ghasts shoot at it. Commands: `/ramiel summon | summon_here | kill | phase2 | heal | settings` (op 2).
Shockwaves (strength x setting): impact strike R=1.9 x blast radius, 80 ticks, 1.6, trees; star beam first impact R220/90t/2.0 with down-range bias 0.4, trees; muzzle R120/50t/1.2, trees; beam pulses R40/24t/0.45;
unfolding pulses R60-140/30t/0.5; stun R90/40t/0.9; Lance R28/24t/0.45; arrival slam R140/70t/0.9, trees; death R180/90t/1.4, trees.
Immediate-mode drawing rule: ONE static `ByteBufferBuilder`, a new `BufferBuilder(bytes, Mode, format)` per draw, `mesh = bb.build()` (nullable), `renderType.draw(mesh)`; one builder open at a time.
Sounds: layered vanilla sounds in `world/Sfx.java`; custom synthesized .ogg (alarm charge beam boom morph rail shield) played by `playLoud(ModRegistry.SND_*)`; the slam plays when REVERT ends back in DORMANT.
FxKind: 1 IMPACT_FRAME(a=ticks) 2 SHOCKWAVE(a=radius,b=ticks) 3 BLAST(a=radius) 4 SCAR 5 WARN_ZONE 6 SHIELD_HIT 7 SHIELD_BREAK 8 BEAM_IMPACT 9 DRILL_SPARK 10 SHAKE 11 RAIL_BEAM 12 POOF.
Film kinds: 0 INTRO 1 LANCE 2 CROSS 3 SUNBURST 4 SENTINEL 5 STUN 6 UNFOLD 7 DEATH 8 DRILL 9 UNFOLD_FIRST. Beam kinds: 0 none 1 LANCE 2 CROSS 3 SUNBURST 4 SENTINEL 5 STAR 9 TELEGRAPH.
Recipes: railcannon `DNR/IBI/RND` (diamond block, netherite ingot, redstone block, iron block, beacon); plasma cell x2 (nether star + diamond + blaze powder + ender pearl, shapeless); x6 from an angel core (+ blaze powder + ender pearl);
mini ramiel `LDL/DSD/LDL` (lapis block, diamond, nether star).

## 5. FILE STATUS: everything below is WRITTEN. Nothing is compiled or run yet.
Java under src/main/java/dev/ramiel (69 files):
RamielMod ModRegistry RamielConfig Setting Form | net/{Payloads FxKind FilmKind} |
entity/{RamielEntity RamielPart ToppleEntity RamielBrain Orientation RamielHitTest BeamKind} |
entity/attack/{Attack BeamAttack LanceAttack CrossAttack SunburstAttack SentinelAttack StarAttack StunAttack ImpactStrike} |
world/{Damage BeamCaster Destruction ChunkAnchor RamielTracker Wards Sfx RamielSpawner HitResolver RailcannonShot ServerEvents Shockwave ShockMath TreeScan} | ai/{FleeRamielGoal ShootRamielGoal MobHooks} |
item/{MiniRamielItem RailcannonItem PlasmaCellItem AngelCoreItem} | client/{RamielClient ClientNet} | client/model/{Brick FormPoses RamielModel RamielMesh} |
client/render/{RamielRenderer MiniRamielRenderer RamielRenderTypes ToppleRenderer} | client/fx/{Draw FxState FxRender GroundFx BeamFx GlowFx ShieldFx SpaceScene FarRender ScreenEffects} |
client/film/Films | client/gui/{ScopeLayer WarningHud RamielSettingsScreen}
Resources: lang/en_us.json, sounds.json (7 events), models/item/{mini_ramiel (builtin/entity), railcannon (3D elements on the 4x4 swatch texture), plasma_cell, angel_core}, textures, shaders/fx/ramiel_impact.fsh,
data/ramiel/{recipe (4), damage_type (railcannon, angel_beam)}, data/minecraft/tags/damage_type/{bypasses_armor,bypasses_shield}. 1.21.1 folder names are SINGULAR (recipe, damage_type).
Build: build.gradle settings.gradle gradle.properties gradlew(.bat) gradle/wrapper (MDK values), libs/README.txt (put rimulib.jar there), META-INF/neoforge.mods.toml, pack.mcmeta, .gitignore
Docs: README ARCHITECTURE TUNING docs/SHOCKWAVE.md docs/FIRST_BUILD.md docs/previews/*.png

## 6. PHASES
P1-P3: DONE. (P2 = client wiring, UI, cutscenes. P3 = resources, docs, the shockwave, the model work.)

**P4: first compile and first run (on the USER's machine; Claude fixes what they paste)**
The user does (docs/FIRST_BUILD.md says the same in plain words): JDK 21; unzip; Rimulib >= 1.2.2 (NeoForge 1.21.1) saved as `libs/rimulib.jar`; run `python3 tools/collect_errors.py` (it builds, then writes build-errors.txt) and paste that file.
Claude does:
 1. Read the report. Fix section 1 of it (unresolved imports) FIRST: they cause most of the other errors. Look the real class up (NeoForge source on GitHub); do not guess twice.
 2. Then the "other errors", batched by file. Ask for a rerun after each batch. Write the API facts learned into section 7.
 3. When it compiles: `./gradlew runClient` (a crash -> `python3 tools/collect_errors.py --game run/logs/latest.log`), then the smoke tests below. Also run `./gradlew runServer` once (no client classes may load).
Smoke tests, in order (creative world, cheats on, shaders OFF): (a) boots; the log shows the Rimulib connect; items + creative tab; recipes craft. (b) `/ramiel summon_here`: Ramiel appears, spins, translucent, no black/purple faces.
(c) `/ramiel summon` with the Mini item: intro cutscene from space, lands over water. (d) provoke it: each form morphs with moving pieces, the revert snaps shut, slam sound. (e) `/ramiel phase2`: stun spikes, urchin -> star, star beam,
the shockwave pushes you, trees fall, ring + camera shake. (f) railcannon: scope, charge, shot, 2 core hits kill. (g) leave it alone: drill, blast-zone HUD + ring, crater. (h) Shift+RMB settings screen: tabs, sliders, save.
(i) hold transforms of mini_ramiel and railcannon in first and third person (tune `display` in models/item/*.json). (j) far render (walk 300 blocks away). (k) `runServer` boots.
Exit: it compiles, client and server boot, and (b)-(h) work. Then tell the user to say "phase done" so HANDOFF and the zip get updated.

**P5: in-game polish and balance**: item display transforms, camera shots (Films), HP/damage/charge numbers (TUNING.md), toppled-tree leaf tint, performance (147 bricks, shockwave scans), shader packs (Iris),
mod compatibility, release packaging (authors, license, logo, version, `./gradlew build` jar). Ideas not built: crushing entities under a landing tree, diagonal tree falls, dust along the ring.

## 7. KNOWN RISKS: all unverified until P4. Quick fixes are in the last paragraph.
1. Rimulib jar >= 1.2.2 at `libs/rimulib.jar`; the `RimulibApi.connect` result is stored as Object. 2. `Mob.goalSelector` visibility (MobHooks): add an access transformer if it is private.
3. `ModConfigSpec.ConfigValue.save()` (Setting.apply); `DoubleValue.set(double)` boxing. 4. `FallingBlockEntity.dropItem` + `fall(Level,BlockPos,BlockState)` (1.21.1). 5. 1.21 projectile constructors in ShootRamielGoal.
6. PartEntity API (`getParent`, `getParts`, `isMultipartEntity`, `setId`). 7. Shot.Path lambda types / `FilmApi.pose` fov float. 8. (DONE: gradle values now come from the MDK.) 9. Shader packs may break immediate-mode drawing: test shaders OFF first.
10. `Sfx.java` assumes SoundEvents.* constants are plain SoundEvent (GENERIC_EXPLODE is a Holder: add `.value()` if needed). 11. ScreenEffects uses ScreenFx field names from the docs (shake flash aberration vignette* bloom zoomBlur).
12. Client API assumptions: `Camera.getLeftVector()/getUpVector()`, `Level.addParticle(opts, force, ...)`, `ClientLevel.entitiesForRendering()`, `RenderType.create(...)` + RenderStateShard constants, `PoseStack.Pose` overloads of addVertex/setNormal.
13. Balance numbers are first guesses.
14. Phase 2 UI/event APIs: `RegisterGuiLayersEvent#registerAboveAll(ResourceLocation, LayeredDraw.Layer)`, `Layer#render(GuiGraphics, DeltaTracker)`, `RenderGuiLayerEvent.Pre#getName` + `VanillaGuiLayers.CROSSHAIR`,
    `ComputeFovModifierEvent` (getPlayer/getNewFovModifier/setNewFovModifier), `RenderHandEvent`, `ClientTickEvent.Post`, `ClientPlayerNetworkEvent.LoggingOut`, `RegisterClientExtensionsEvent#registerItem(IClientItemExtensions, Item...)`,
    `AbstractSliderButton` (constructor, protected `value`, applyValue/updateMessage), `Button` protected constructor + `DEFAULT_NARRATION`, `Screen#renderBackground/rebuildWidgets`, `GuiGraphics#drawWordWrap/guiWidth`, `Tooltip.create`, `Player#getViewYRot`.
15. Phase 3 APIs: `LevelTickEvent.Post` and `ServerStoppingEvent` packages; `Level#holderLookup(Registries.BLOCK)` + `NbtUtils.readBlockState`; `Entity#hurtMarked/hasImpulse`; `EntityType.Builder.clientTrackingRange/updateInterval/noSummon`;
    `Heightmap.Types.MOTION_BLOCKING_NO_LEAVES`; `LeavesBlock.DISTANCE/PERSISTENT`; `Block.UPDATE_SUPPRESS_DROPS`; `ServerLevel#isLoaded(BlockPos)`; `BlockRenderDispatcher#renderSingleBlock` (5 args); `EntityRendererProvider.Context#getBlockRenderDispatcher`.
16. ToppleEntity: the tree data syncs through a synched CompoundTag right after the spawn packet (the renderer waits for it); leaves are drawn with the default foliage colour; leaves that land inside terrain are lifted up to 3 blocks (they visibly settle upward).
17. Item model JSON: the `neoforge_data` glow keys may need renaming; every `display` transform is a guess. 18. Velocities above ~3 b/t may be rubber-banded (the ShockMath caps are conservative).
19. Title cards: every film shows Rimulib's title card, which may be too frequent on attack morphs (`FilmApi.hintOnly(MODID)` switches them all off). The settings screen needs ~218 GUI units of height (clips near 720p at GUI scale 4).

Quick fixes: `package X does not exist` on net.neoforged.* -> the class moved or was renamed in 21.1: find it in NeoForge's source (github.com/neoforged/NeoForge) and fix the import. `cannot find symbol ... of type <a Minecraft class>` -> 1.21.1 names the method differently: read that class's source.
Anything under `cyou.rimuru.rimulib` -> the jar is missing, too old, or the package differs: list its classes (`unzip -l libs/rimulib.jar | grep mc1211`). `Could not resolve net.neoforged:neoforge` -> check `neo_version` in gradle.properties.
`Unsupported class file / Java version` -> JDK 21 is needed. NoClassDefFoundError `net/minecraft/client` on a server -> a client class leaked (only RamielClient may touch client code). Black or purple faces -> RenderType or texture problem; try shaders OFF.
