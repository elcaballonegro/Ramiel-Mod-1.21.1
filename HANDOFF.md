# RAMIEL MOD — HANDOFF (read this first in every new chat)

Target: NeoForge 1.21.1 (Java 21). Mod id `ramiel`, package `dev.ramiel`. A boss mod: Ramiel (octahedral Angel,
Rebuild of Evangelion) + craftable handheld Railcannon. Uses the public **Rimulib** API for craters, cutscenes,
screen effects. Fan project; all assets are original/generated.

## 0. Rules for working in this project (saves usage)
- One PHASE per chat. Start with: "Read HANDOFF.md, then do Phase N. Only open the files it lists."
- Do NOT re-upload the 413 reference frames unless a phase says so. Section 3 already contains the analysis.
- Do NOT re-fetch rimulib docs. Section 2 has every signature used.
- Source lives in `ramiel-mod-snapshot.zip` (project knowledge). Unzip it in the sandbox; `view` only needed files.
- Update HANDOFF.md + the snapshot zip ONLY when the user asks (they decide when a phase ends). Until then just build on the current files.
- Sandbox facts: no access to Maven/Gradle (cannot compile against NeoForge). GitHub, pypi, npm, apt work.
  The shell is `sh`: brace expansion `{a,b}` does NOT work (use bash -c or write paths out). Use `javac` only to catch
  SYNTAX errors (filter out "package does not exist"/"cannot find symbol"). Python: numpy + PIL + soundfile(OGG ok).
  Files in /home/claude reset between chats: re-extract the snapshot each time.

## 1. Decisions already made (do not re-debate)
- Renderer: procedural "brick" model, no GeckoLib. 57-brick pool; each brick = frustum (box/pyramid/spike/slab) with
  centre, quaternion, h, bottom half-size (bw,bd), top half-size (tw,td), rgb, glow, alpha. Forms = sets of bricks;
  morph = per-brick lerp (+slerp) with staggered delay and an outward "burst". Shading is CPU: fake reflection via
  fresnel + sky-gradient env + sun spec, drawn with RenderType.entityTranslucent (+ eyes/emissive for glow).
- Brick pool indices: 0-1 halves(top,bottom) | 2-9 plates/slabs(8 boxes) | 10-41 spikes(32) | 42-53 shards(12) |
  54 needle | 55-56 core (top/bottom pyramid). N=57. Unused bricks collapse to zero size.
- Hit boxes: vanilla AABB = ONLY the core (5.2 blocks) so fluid checks stay cheap. Body = 6 stacked `RamielPart`
  PartEntities (like Ender Dragon). Exact hit decided by `RamielHitTest` (octahedron |x|+|y|+|z|<=R*scale + core sphere).
  `HitResolver` (ProjectileImpactEvent) cancels misses, applies shield bounce, tags core/body for damage rules.
- R = 24 blocks (half-diagonal). Hover = config 84 blocks (centre above ground/water).
- Health: real pool `hp` (BASE 3000 x config), vanilla max-health attribute fixed at 1000 (vanilla caps at 1024).
  Body hit x0.25, exposed core x5. Railcannon: 20% max HP body / 50% core (2 core shots kill), ignores shield.
  Phase gate at 2/3 HP -> StunAttack (60 s helpless, urchin form) -> phase 2 = StarAttack only (30-60 s cooldown).
  Stun floor 30%. Railcannon/kill bypass gates.
- Networking: NeoForge native payloads (`Payloads`), NOT rimulib NetApi (1.21.1 NetApi needs RimuPacket wrapper).
  Beams are NOT sent as packets: they are synced entity data (beamKind/len/t + aim yaw/pitch) and drawn client-side.
- Config = ModConfigSpec COMMON (`RamielConfig`), edited live by `Setting` enum (17 rows) via settings screen packet
  (needs op level 2 or single-player owner).
- Far render ("LOD"): server sends `Beacon` every 10 ticks to players within farRange; client draws simplified Ramiel
  by clamping to 0.9*getDepthFar() and scaling by the same factor (technique verified in the MIT addon).
- Arrival from space: server entity sits at its FINAL position; synced `arrival` 0..1 over 280 ticks; renderer offsets
  the model up by `arrivalOffsetY` (2600 blocks, ease-in); intro cutscene + SpaceScene (stars/Earth/sun) driven client-side.
- Chunk anchor: forces 5x5 chunks around Ramiel (only chunks it forced itself are released).
- Blast-zone ground ring/scars are GEOMETRY (terrain-conforming quads), not post shaders (scenePosAt signature unknown).
  Impact frames use a custom FxApi shader `ramiel_impact.fsh` (only documented uniforms/samplers).
- Railcannon is handheld (hold RMB 40 ticks to charge, release to fire), ammo = plasma cell. Scope overlay + zoom.
- Mini Ramiel item: BEWLR spinning crystal; RMB = summon above nearest deep water (fallback: 110 blocks ahead);
  Shift+RMB = settings screen. Consumed on success (not in creative).

## 2. Rimulib facts (version needed: >= 1.2.2; docs now describe 1.2.4. The user's uploaded jar 1.2.1 has NO API classes!)
Packages for 1.21.1: `cyou.rimuru.rimulib.mc1211.api.{RimulibApi,CarveApi,WardApi,...}`,
`...mc1211.client.api.{FilmApi,FxApi,UiApi,MenuApi}`, `...mc1211.client.cinematic.{Shot,Subject,CameraPose,Cutscene}`,
`...mc1211.bulwark.Threat` (Kind: COLUMN,BOLT,BLAST,FALLING,CUT). Do NOT touch internal classes (docs forbid it).
```
RimulibApi.connect(String modId, Class<?> anchor) -> RimuMod   // keep in static field
CarveApi: UNTOUCHED; interface Floor{int at(int x,int z)}; interface Surface{BlockState at(int x,int y,int z,BlockState was)} (null=keep)
  flatten(level, Vec3 centre, double r, Floor, Surface)        line(level, Vec3 start, Vec3 heading, double length, double halfWidth, Floor, Surface)
  column(level,centre,r) slit wedge lens ; keepBlocks(Predicate<BlockState>) global register
  Cut: named(String) keepBlocks() perTick(int) lazy() atOnce(boolean) prepare() ready() cancel() start() tick() tick(double) done() stop() finish()
  start() self-ticks; prepare() builds early, tick() lands it in one tick. Floors/surfaces run on server thread.
WardApi.warn(ServerLevel, String modId, Threat.Kind, Vec3 at, double radius, int argb, int leadTicks, int durationTicks) -> boolean
FilmApi: enableKey(); follow(Entity, Vec3 target)->Subject; pose(Vec3 position, Vec3 lookAt, float fov)->CameraPose;
  cut(int start,int end,Shot.Path); sweep(int start,int end,int blendIn,Shot.Path); film(Subject,String tier,String title,int argb,List<Shot>)->Cutscene;
  play(Cutscene); active(); time(subject,partial); weight(subject); skip(); stop(subject); hintOnly(modId);
  holdInput(BooleanSupplier); hideHand(BooleanSupplier)
  Shot.Path lambda: (s, partial, k) -> CameraPose ; k runs 0..1 over the shot. Subject: feet(partial) eye(partial) target() forward() right()
  frame(anchor,right,up,forward) aroundTarget(partial,side,up,back); static Subject.orbit(point,pivot,degrees)
FxApi: stage(String modId)->Stage; Stage.add(Layer)/remove; Layer: void draw(Frame); Frame: pass(String program, Consumer<Uniforms>), screen(), partial()
  Uniforms: f1 f2 f3 f4 v3(String,Vec3) vec4s texture. ScreenFx fields (reset each frame): shake aberration desaturate zoomBlur flash flashR flashG flashB
  vignette vignetteR vignetteG vignetteB bloom. Shaders: assets/<mod>/shaders/fx/<name>.fsh, NO #version; given: texCoord fragColor DiffuseSampler DepthSampler Time GTime OutSize scenePosAt
UiApi: enum Font{DISPLAY,LABEL,MONO}; text(GuiGraphics,Font,String,x,y,argb)->float; centred(...); text(g,font,s,x,y,align,scale,argb); measure(g,font,s,scale); box(g,x,y,w,h,argb); width(g)/height(g)
```
Verified-from-compiling-1.21.1-addon patterns (MIT/CC0 "Shooting Star Addition", client): `extends RenderType` to reuse protected state
shards (RENDERTYPE_LIGHTNING_SHADER, ADDITIVE/own TransparencyStateShard, NO_CULL...); `new BufferBuilder(new ByteBufferBuilder(n), Mode.QUADS, fmt)`;
`vc.addVertex(x,y,z).setColor(f,f,f,f).setUv(u,v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(l).setNormal(nx,ny,nz)`; `builder.build()` (nullable)
then `renderType.draw(mesh)`; `RenderLevelStageEvent` (camera = event.getCamera().getPosition(), partial = event.getPartialTick().getGameTimeDeltaPartialTick(true),
far = minecraft.gameRenderer.getDepthFar()); `RenderType.entityTranslucentEmissive(tex)`; `RenderType.eyes`; payloads via `event.registrar("v")`,
`StreamCodec.of(encoder,decoder)`, `PacketDistributor.sendToPlayer`. Constructor: `public Mod(IEventBus bus, ModContainer c, Dist d)`; `c.registerConfig(ModConfig.Type.COMMON, SPEC)`.
Licences of provided example mods: Shooting Star DEMO = All Rights Reserved + obfuscated -> NEVER decompile/copy (declined).
Shooting Star Addition (1.8.0 MIT, 1.8.1 CC0) and TON 618 (CC0, partly obfuscated) = study OK. Downfall 1.20.1 = MIT (has own Ramiel .dfm model; not used).

## 3. Reference-frame analysis (413 frames; replaces re-viewing them)
Colours: body deep blue (0.16,0.33,0.95) translucent crystal; plates mint cyan-green (0.35,0.95,0.78); dark (0.05,0.09,0.25); cyan (0.4,0.95,1); core red / magenta in star.
- DORMANT: octahedron, a vertex points down, spins slowly on vertical axis.
- DRILL (340-379): lower apex stretches into a thin needle with glowing cyan beads; pink/orange rings + sparks where it meets the ground.
- LANCE (98-121): octa splits at equator; halves separate vertically and re-form as STEPPED RECTANGULAR SLAB STACKS (3 slabs each, decreasing size), small cap
  pyramids top/bottom, a spine of ~6 small beads between them, red core in the gap, pink glow. Beam: thin pink->white.
- CROSS (146-167): halves flatten to plates, fly apart; 6 small cubes form a plus cluster; then 4 long spikes (+X,-X,+Y,-Y in the view plane) with detached
  spinning end plates/diamonds, pink glow on top plate. Beam (208): thick gold-white, orange fringe, ground footprint is a CROSS (user wants 12+ craters).
- Reversion (244-263): cross collapses into stacked plates + elongated shard cluster, then snaps back to the octahedron.
- SUNBURST (417-444): small bright cyan inverted pyramid at centre, ~20 long thin spikes radiating everywhere + floating blue shards, sunburst light rays.
  Beam: thin MAGENTA-pink line(s) sweeping (447-465).
- SENTINEL (520-530, clearer_form4): 6 nested square mint plates stacked like pages (slight offsets) in front of core, 3 big blue pieces behind, thin rod with red tip,
  floating shards to one side. Beam: thick WHITE.
- SHIELD (505-515): body turned so one big flat face points at the threat; golden thin lines + rainbow hexagon cells when hit.
- STAR (725-746, clearer_form5): upside-down 5-point star in the plane facing the viewer (one point down, two up), unfolds in 3 stages (bigger each), magenta/pink
  glowing core with an energy ring. Beam (761-846): huge; pink pressure-disc rings travel along it; mountain erupts orange; debris/smoke streaks along beam direction.
- URCHIN (646-648, 915-963): dark navy spikes in all directions, exposed red core blobs, violet glow. Used for stun + death.
- Hit by railcannon (627-648, 870-892): beam cyan-green with white core, Ramiel flashes pale then dark blue; ring of purple energy at charge (870).
- Explosions (484-500): chain/ring of glowing white-gold spheres, then huge orange fireballs. Ground scars glow red/orange.
- Scope HUD (547-607, 812-815): circular reticle, orange HUD text, NIGHT/ENHANCED sight mode labels, green triangle lock markers, red dashed diagonal lines.

## 4. Gameplay spec (implemented in server code)
Idle: slow spin, never tracks anyone. Shield (phase 1) when threatened & idle: SHIELD form faces last hit; durability 1500; projectile/explosion hits wear it;
break => 600 ticks vulnerable. Attacks (cooldown 10-20 s / aggression): Lance 40%, Cross 20%, Sunburst 15-40% (crowds near), Sentinel 25%.
Stages MORPH(cutscene)/CHARGE/PAUSE(aim locked + red telegraph beam)/FIRE/RECOVER(core exposed)/REVERT. ImpactStrike (own objective when nobody is around):
needle extends+stabs, 25 s charge with red blast-zone ring + action-bar warnings + alarm, crater pre-built via Cut.prepare() and landed with Cut.tick(), then walks 350-600 blocks away.
Damage >=8% max HP (or any core hit) during charge cancels it (setting). Mobs: animals/villagers flee (speed boost); skeletons/pillagers/illusioners/blazes/ghasts shoot at it.
Commands: /ramiel summon | summon_here | kill | phase2 | heal | settings (op 2).

Immediate-mode drawing rule (from the addon): keep ONE static `ByteBufferBuilder`, make a new `BufferBuilder(bytes, Mode, format)` per draw, `mesh = bb.build()` (nullable),
`renderType.draw(mesh)`; only one builder open at a time (flush before starting another). Camera-relative vertices work at RenderLevelStageEvent.Stage.AFTER_SKY / AFTER_PARTICLES.
Sounds: layered vanilla sounds live in `world/Sfx.java` (slam, deflect, laser, laserHum, boom). The slam plays when an attack's REVERT ends back in DORMANT (user request).
Custom synthesized .ogg (alarm charge beam boom morph rail shield) also exist and are played by `playLoud(ModRegistry.SND_*)`; user prefers vanilla layers where they sound better.

## 5. FILE STATUS  (D = done, T = todo)  — java under src/main/java/dev/ramiel
D RamielMod, ModRegistry, RamielConfig, Setting, Form, net/{Payloads,FxKind,FilmKind}
D entity/{RamielEntity, RamielPart, RamielBrain, Orientation(+forAngles), RamielHitTest, BeamKind}
D entity/attack/{Attack, BeamAttack, Lance, Cross, Sunburst, Sentinel, Star, Stun, ImpactStrike}
D world/{Damage, BeamCaster, Destruction, ChunkAnchor, RamielTracker, Wards, Sfx, RamielSpawner, HitResolver, RailcannonShot, ServerEvents}
D ai/{FleeRamielGoal, ShootRamielGoal, MobHooks}   item/{MiniRamielItem, RailcannonItem, PlasmaCellItem, AngelCoreItem}
D client/model/{Brick, FormPoses, RamielModel, RamielMesh}   client/render/{RamielRenderTypes, RamielRenderer, MiniRamielRenderer}
D client/fx/{Draw, FxState, FxRender, GroundFx, BeamFx, GlowFx, ShieldFx, SpaceScene, FarRender, ScreenEffects}   client/ClientNet
D resources: assets/ramiel/shaders/fx/ramiel_impact.fsh ; textures (entity/crystal, fx/earth, item/{plasma_cell,angel_core,railcannon}) ; sounds/*.ogg (7)
D tools/gen_assets.py  (regenerates every texture + sound: `python3 tools/gen_assets.py`)
D build.gradle, settings.gradle, gradle.properties, META-INF/neoforge.mods.toml, pack.mcmeta
T client/RamielClient  -> `public static void init(IEventBus modBus)` (called from RamielMod when Dist is client). Must register:
    modbus: EntityRenderersEvent.RegisterRenderers (ModRegistry.RAMIEL -> RamielRenderer::new); RegisterClientExtensionsEvent (Mini item -> MiniRamielRenderer BEWLR);
            RegisterGuiLayersEvent (WarningHud, ScopeLayer)
    game bus: RenderLevelStageEvent -> FxRender.render(event); ClientTickEvent.Post -> FxRender.tick(); ClientPlayerNetworkEvent.LoggingOut -> FxRender.clear();
            ComputeFovModifierEvent (scope zoom); RenderHandEvent (cancel while scoped); RenderGuiLayerEvent.Pre (cancel crosshair while scoped)
    also: ScreenEffects.init(); FilmApi.enableKey(); FilmApi.holdInput(()->Films.holdsInput()); FilmApi.hideHand(()->Films.holdsInput())
T client/film/Films -> `public static void play(int kind, int entityId)` (ClientNet calls it) + `static boolean holdsInput()`. One film per FilmKind (see sec 5 table).
    Camera paths: `(s, partial, k) -> FilmApi.pose(pos, lookAt, fov)`; helper params for k must be `double`, partial `float`. Subject = FilmApi.follow(entity, playerPos).
    INTRO must use the entity's `arrival(partial)` + `arrivalOffsetY(partial)` (model is drawn that many blocks ABOVE the entity); camera altitude > 900 above sea level
    automatically switches SpaceScene on. Attack-morph films last the MORPH stage (50-100 ticks) and end BEFORE charge.
T client/gui/RamielSettingsScreen(double[] values, boolean canEdit): 2 columns over `Setting.values()`; slider (min,max,step) or on/off button if `isToggle()`;
    tooltip = Setting.help; on change/Save send `PacketDistributor.sendToServer(new Payloads.SettingsSet(double[]))`; read-only note when !canEdit; title via UiApi.
T client/gui/ScopeLayer (railcannon scope: black outside a circle, reticle ticks, red dashed diagonals, orange HUD text, charge %, NIGHT/ENHANCED label, cells left)
    active when `mc.player.isUsingItem() && useItem is RailcannonItem`; `player.getTicksUsingItem()` = charge ticks (full = RailcannonItem.FULL_CHARGE = 40).
T client/gui/WarningHud: reads public `FxState.zones` (Map<Integer,Zone>: centre, radius, end) + `FxState.now`; if the player is inside: big red text via UiApi
    ("BLAST ZONE", seconds left, "RUN") — the action-bar message and screen vignette already exist.
T resources: lang/en_us.json (keys below) ; sounds.json (7 events -> ogg; optional vanilla aliases via {"type":"event"}) ; models/item/{mini_ramiel (parent builtin/entity, gui_light front, display),
    railcannon (3D elements model using textures/item/railcannon.png 4x4 swatches), plasma_cell & angel_core (item/generated)} ; data/ramiel/recipe/*.json ; data/ramiel/damage_type/{railcannon,angel_beam}.json ;
    data/minecraft/tags/damage_type/bypasses_armor.json (+ railcannon).  1.21.1 folder names are SINGULAR (recipe, damage_type, tags/damage_type). Recipes planned:
    railcannon (2 netherite ingot, 1 beacon, 2 diamond block, 2 iron block, 3 redstone block), plasma_cell x2 (nether star+diamond+blaze powder+ender pearl, shapeless),
    plasma_cell cheap (angel_core + ...), mini_ramiel (nether star + lapis blocks + diamonds).
T RailcannonShot should also call Sfx.laser(level, pos, 1.2f) (not hooked yet).   T docs: README.md, ARCHITECTURE.md, TUNING.md
Lang keys used by code: entity.ramiel.ramiel itemGroup.ramiel ramiel.settings.saved ramiel.warn.blast(%s sec,%s radius) ramiel.warn.near(%s) ramiel.summon.{dimension,already,water,land}
ramiel.rail.{no_ammo,charged,unstable} item.ramiel.{mini_ramiel,railcannon,plasma_cell,angel_core} (+ .tip1/.tip2 on first two, .tip on plasma_cell/angel_core).
FxKind: 1 IMPACT_FRAME(a=ticks) 2 SHOCKWAVE(a=radius,b=ticks) 3 BLAST(a=radius) 4 SCAR(a=radius,b=lifetime) 5 WARN_ZONE(a=radius,b=ticksLeft,f1=zoneId; b<=0 removes)
6 SHIELD_HIT((a,b,c)=outward dir,f1) 7 SHIELD_BREAK 8 BEAM_IMPACT((a,b,c)=dir,f1=size) 9 DRILL_SPARK(a=size) 10 SHAKE(a=strength,b=ticks) 11 RAIL_BEAM(pos=start,(a,b,c)=end,f1=width,f2=ticks) 12 POOF(a=radius).
Film kinds: 0 INTRO 1 LANCE 2 CROSS 3 SUNBURST 4 SENTINEL 5 STUN 6 UNFOLD 7 DEATH 8 DRILL 9 UNFOLD_FIRST. Beam kinds: 0 none 1 LANCE 2 CROSS 3 SUNBURST 4 SENTINEL 5 STAR 9 TELEGRAPH.
Entity getters the client uses: form() formFrom() morph(p) spin(p) aimYaw(p) aimPitch(p) aimDir(p) charge(p) coreAnim(p) arrival(p) arrivalOffsetY(p) coreOpen() shieldUp()
shieldRatio() drillLen() phase() beamKind() beamLen() beamT() deathTicks() centre() healthRatio() isBusy(); constants R, CORE_BOX, CORE_RADIUS, ARRIVAL_TICKS, DEATH_TICKS.
Checks done in this chat: `javac -proc:none` over all 59 files = ZERO syntax errors; every unresolved symbol is a Minecraft/NeoForge/rimulib type (not installed in the sandbox) except the
3 TODO client classes above. No methods of my own classes are missing. Nothing has been compiled against NeoForge or run in-game.

## 6. NEXT PHASES (re-numbered; old P1 + P2 are finished)
P2  Client wiring + UI + cutscenes: RamielClient, Films, RamielSettingsScreen, ScopeLayer, WarningHud (spec above). Opens only: HANDOFF.md, ClientNet, FxState, RamielEntity(getters), Setting, Payloads.
P3  Resources + docs: lang, sounds.json, item/block models, recipes, damage types + tags, README/ARCHITECTURE/TUNING, railgun Sfx hook, optional vanilla-sound aliases.
P4  Integration on the user's machine: user installs rimulib >= 1.2.2 (docs: 1.2.4) as libs/rimulib.jar, builds, pastes compile errors; fix loop; first in-game test; balance pass.
Suggested prompt: "Read HANDOFF.md and unzip ramiel-mod-snapshot.zip. Do Phase 2 only; list the files you open."

## 7. KNOWN RISKS (check first on compile / first run)  — all untested
1. rimulib jar must be >= 1.2.2 at libs/rimulib.jar (the user's 1.2.1 has no API). `RimulibApi.connect` result stored as Object.
2. `Mob.goalSelector` visibility (MobHooks): NeoForge normally exposes it; else add an access transformer.
3. `ModConfigSpec.ConfigValue.save()` (Setting.apply); `DoubleValue.set(double)` boxing.
4. `FallingBlockEntity.dropItem` public field + `fall(Level,BlockPos,BlockState)` static (1.21.1).
5. 1.21 projectile constructors: LargeFireball(level,mob,Vec3,int), SmallFireball(level,mob,Vec3) (ShootRamielGoal) — adjust on compile error.
6. PartEntity API (`getParent`, `getParts`, `isMultipartEntity`, `setId` override).
7. Shot.Path lambda types (keep k as double-accepting helpers); FilmApi.pose fov float.
8. gradle `neo_version` + moddev plugin version are guesses: copy the 1.21.1 MDK values.
9. Shader packs (Iris/Oculus) may break custom immediate-mode drawing; test with shaders OFF first.
10. `Sfx.java` assumes SoundEvents.* constants other than GENERIC_EXPLODE are plain SoundEvent (not Holder). If a constant is a Holder, add `.value()`.
11. ScreenEffects uses rimulib ScreenFx field names from the docs: shake flash aberration vignette vignetteR/G/B bloom zoomBlur; `frame.partial()`, `frame.pass(name, u->{u.f1(..)})`.
12. Client API assumptions: `Camera.getLeftVector()/getUpVector()`, `Level.addParticle(opts, force, x,y,z,vx,vy,vz)`, `ClientLevel.entitiesForRendering()`,
    `RenderType.create(...)` + RenderStateShard constants (RENDERTYPE_LIGHTNING_SHADER, NO_DEPTH_TEST, COLOR_WRITE, LIGHTMAP, OVERLAY), `PoseStack.Pose` overloads of addVertex/setNormal.
13. Balance numbers (HP 3000, 25 s charge, craters, damage) are first guesses; tune via the settings screen / TUNING.md after first run.
