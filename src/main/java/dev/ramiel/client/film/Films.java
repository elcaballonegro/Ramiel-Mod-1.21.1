package dev.ramiel.client.film;

import cyou.rimuru.rimulib.mc1211.client.api.FilmApi;
import dev.ramiel.client.fx.FxState;
import dev.ramiel.entity.RamielEntity;
import dev.ramiel.net.FilmKind;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Every cutscene, built on Rimulib's FilmApi. The server only says "play film N for entity E"
 * (see {@link FilmKind}); this class decides where the camera goes.
 *
 * HOW THE CAMERAS WORK
 *  - Nothing is baked in. Each shot is a function that reads the live Ramiel every frame, so the
 *    camera always agrees with whatever the model is doing (it keeps working if Ramiel drifts,
 *    turns, or is still falling out of space).
 *  - A {@link Rig} remembers where the player stood when the film started. That gives a "player
 *    side" direction and a base distance: far-away players get a long-lens shot from where they
 *    are, near players get pulled back so the whole body fits.
 *  - Shot timing matches the server stages: Lance 50 ticks, Cross/Sunburst/Sentinel 70, Stun 50,
 *    Drill 100, intro 280 (ARRIVAL_TICKS), death 220, star unfolds at charge ticks 1/80/160.
 *    Attack films always end BEFORE the charge stage so players keep their time to run.
 *
 * Only "key" films (intro, stun, first unfolding, death) freeze the player's input, and they are
 * all moments when Ramiel is not about to hurt anyone. Every film can be skipped with Rimulib's
 * shared skip key.
 *
 * Only FilmApi is named here. The shot/subject/pose types are used through `var` so a rename in
 * Rimulib's cinematic package cannot break this file.
 */
public final class Films {
    private Films() {}

    private static final double R = RamielEntity.R;

    private record Pending(int kind, int id, long expires) {}

    private static final List<Pending> PENDING = new ArrayList<>();
    private static final Map<Integer, Long> LAST = new HashMap<>();
    private static long holdUntil = -1L;

    // ==========================================================================================
    // Entry points
    // ==========================================================================================

    /** Called by ClientNet when the server asks for a cutscene. */
    public static void play(int kind, int entityId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        // The film packet can beat the entity-spawn packet by a tick: retry for two seconds.
        if (!tryStart(mc, kind, entityId)) PENDING.add(new Pending(kind, entityId, FxState.now + 40));
    }

    /** Once per client tick (RamielClient). */
    public static void tick() {
        if (PENDING.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        for (Iterator<Pending> it = PENDING.iterator(); it.hasNext(); ) {
            Pending p = it.next();
            if (mc.level == null || mc.player == null || FxState.now > p.expires() || tryStart(mc, p.kind(), p.id())) it.remove();
        }
    }

    public static void clear() {
        PENDING.clear();
        LAST.clear();
        holdUntil = -1L;
    }

    /** Rimulib asks this every frame: should player input be frozen right now? */
    public static boolean holdsInput() {
        return holdUntil > 0 && FxState.now < holdUntil && FilmApi.active();
    }

    private static void hold(int ticks) { holdUntil = FxState.now + ticks; }

    private static boolean isKey(int kind) { return FilmKind.minCutsceneSetting(kind) <= 1; }

    /** @return false only when the entity is not on this client yet (so the caller should retry). */
    private static boolean tryStart(Minecraft mc, int kind, int id) {
        if (!(mc.level.getEntity(id) instanceof RamielEntity ramiel)) return false;
        // never stack a small cutscene on top of a running one, and never replay one in quick succession
        if (FilmApi.active() && !isKey(kind)) return true;
        Long last = LAST.get(kind);
        if (last != null && FxState.now - last < 20L) return true;
        LAST.put(kind, FxState.now);

        Rig rig = new Rig(ramiel, mc.player.getEyePosition());
        holdUntil = -1L;
        switch (kind) {
            case FilmKind.INTRO -> intro(mc, rig);
            case FilmKind.LANCE -> lance(mc, rig);
            case FilmKind.CROSS -> cross(mc, rig);
            case FilmKind.SUNBURST -> sunburst(mc, rig);
            case FilmKind.SENTINEL -> sentinel(mc, rig);
            case FilmKind.STUN -> stun(mc, rig);
            case FilmKind.UNFOLD -> unfold(mc, rig);
            case FilmKind.UNFOLD_FIRST -> unfoldFirst(mc, rig);
            case FilmKind.DEATH -> death(mc, rig);
            case FilmKind.DRILL -> drill(mc, rig);
            default -> { }
        }
        return true;
    }

    // ==========================================================================================
    // The films.   Shot lambdas are (subject, partialTick, k) where k runs 0..1 across the shot.
    // ==========================================================================================

    /** 0-300: falls out of space, camera follows it down, ends at ground level looking up. */
    private static void intro(Minecraft mc, Rig rig) {
        hold(300);
        var subject = FilmApi.follow(mc.player, rig.centre);
        var shots = List.of(
                // space: a long lens tracking the angel as it falls (SpaceScene switches on above 900 blocks)
                FilmApi.cut(0, 100, (s, p, k) -> {
                    rig.update(p);
                    double e = ease(k);
                    Vec3 m = rig.model();
                    Vec3 pos = ring(m, rig.side, lerp(65, 20, e), lerp(390, 300, e), lerp(110, -10, e));
                    return FilmApi.pose(shake(pos, 0.12, p), m, (float) lerp(36, 32, e));
                }),
                // still following it, swinging below it as the sky turns blue
                FilmApi.sweep(100, 200, 20, (s, p, k) -> {
                    rig.update(p);
                    double e = ease(k);
                    Vec3 m = rig.model();
                    Vec3 pos = ring(m, rig.side, lerp(20, -25, e), lerp(300, 190, e), lerp(-10, -70, e));
                    return FilmApi.pose(shake(pos, 0.2, p), m, (float) lerp(32, 44, e));
                }),
                // cut down to the ground: look up as it settles into its hover height
                FilmApi.sweep(200, 300, 40, (s, p, k) -> {
                    rig.update(p);
                    Vec3 m = rig.model();
                    Vec3 pos = groundCam(rig);
                    double landing = ease((k - 0.5) / 0.5);
                    return FilmApi.pose(shake(pos, 0.04 + 0.2 * landing, p), m,
                            (float) fovFor(pos.distanceTo(m), 1.5 * R, 18, 70));
                }));
        FilmApi.play(FilmApi.film(subject, "ANGEL", "RAMIEL", 0xFF5AA0FF, shots));
    }

    /** 0-50: the octahedron splits at the equator into stepped slabs around the red core. */
    private static void lance(Minecraft mc, Rig rig) {
        var subject = FilmApi.follow(mc.player, rig.centre);
        var shots = List.of(
                FilmApi.cut(0, 26, (s, p, k) -> {
                    rig.update(p);
                    double d = rig.baseDist();
                    Vec3 pos = ring(rig.centre, rig.side, lerp(25, 5, ease(k)), d, lerp(-0.2 * R, 0.3 * R, ease(k)));
                    return FilmApi.pose(shake(pos, 0.08, p), rig.centre, (float) fovFor(d, 1.35 * R, 14, 62));
                }),
                // push into the gap between the halves, straight at the core
                FilmApi.sweep(26, 50, 8, (s, p, k) -> {
                    rig.update(p);
                    double e = ease(k);
                    double d = lerp(rig.baseDist(), Math.max(1.7 * R, rig.baseDist() * 0.4), e);
                    Vec3 pos = ring(rig.centre, rig.side, lerp(5, -12, e), d, lerp(0.3 * R, 0.05 * R, e));
                    return FilmApi.pose(shake(pos, 0.1 + 0.15 * e, p), rig.centre, (float) lerp(fovFor(rig.baseDist(), 1.35 * R, 14, 62), 42, e));
                }));
        FilmApi.play(FilmApi.film(subject, "FORM", "LANCE", 0xFFFF6FB0, shots));
    }

    /** 0-70: halves flatten and fly apart, a plus of cubes forms, four spikes shoot out. Ends face-on. */
    private static void cross(Minecraft mc, Rig rig) {
        var subject = FilmApi.follow(mc.player, rig.centre);
        var shots = List.of(
                FilmApi.cut(0, 30, (s, p, k) -> {
                    rig.update(p);
                    double d = rig.baseDist();
                    Vec3 pos = ring(rig.centre, rig.side, 20, d, lerp(-0.9 * R, -0.7 * R, ease(k)));
                    return FilmApi.pose(shake(pos, 0.08, p), rig.centre.add(0, 0.1 * R, 0), (float) fovFor(d, 1.5 * R, 14, 62));
                }),
                // swing round to the front: the cross is drawn in the plane facing its target
                FilmApi.sweep(30, 70, 10, (s, p, k) -> {
                    rig.update(p);
                    double e = ease(k);
                    double face = angleTo(rig, rig.aim);
                    double d = lerp(rig.baseDist(), Math.max(2.3 * R, rig.baseDist() * 0.7), e);
                    Vec3 pos = ring(rig.centre, rig.side, lerpAngle(20, face, e), d, lerp(-0.7 * R, 0.1 * R, e));
                    return FilmApi.pose(shake(pos, 0.12 + 0.12 * e, p), rig.centre, (float) lerp(40, 46, e));
                }));
        FilmApi.play(FilmApi.film(subject, "FORM", "CROSS", 0xFFFFC86A, shots));
    }

    /** 0-70: a small bright pyramid, then spikes radiating everywhere. Low angle, then pulls back wide. */
    private static void sunburst(Minecraft mc, Rig rig) {
        var subject = FilmApi.follow(mc.player, rig.centre);
        var shots = List.of(
                FilmApi.cut(0, 34, (s, p, k) -> {
                    rig.update(p);
                    double d = rig.baseDist() * 0.95;
                    Vec3 pos = ring(rig.centre, rig.side, -15, d, -1.1 * R);
                    return FilmApi.pose(shake(pos, 0.1, p), rig.centre.add(0, 0.15 * R, 0), (float) fovFor(d, 1.6 * R, 14, 64));
                }),
                FilmApi.sweep(34, 70, 10, (s, p, k) -> {
                    rig.update(p);
                    double e = ease(k);
                    double d = lerp(rig.baseDist() * 0.95, rig.baseDist() * 1.25, e);
                    Vec3 pos = ring(rig.centre, rig.side, lerp(-15, 50, e), d, lerp(-1.1 * R, 0.5 * R, e));
                    return FilmApi.pose(shake(pos, 0.25, p), rig.centre, (float) fovFor(d, 2.0 * R, 16, 70));
                }));
        FilmApi.play(FilmApi.film(subject, "FORM", "SUNBURST", 0xFFFF7AE0, shots));
    }

    /** 0-70: side-on to see the stack of plates, then round to the front, looking down its sights. */
    private static void sentinel(Minecraft mc, Rig rig) {
        var subject = FilmApi.follow(mc.player, rig.centre);
        var shots = List.of(
                FilmApi.cut(0, 30, (s, p, k) -> {
                    rig.update(p);
                    double d = rig.baseDist() * 0.95;
                    Vec3 pos = ring(rig.centre, rig.side, 90, d, lerp(0.05 * R, 0.15 * R, ease(k)));
                    Vec3 look = rig.centre.add(flat(rig.aim).scale(0.4 * R));
                    return FilmApi.pose(shake(pos, 0.08, p), look, (float) fovFor(d, 1.4 * R, 14, 62));
                }),
                FilmApi.sweep(30, 70, 10, (s, p, k) -> {
                    rig.update(p);
                    double e = ease(k);
                    double face = angleTo(rig, rig.aim);
                    double d = lerp(rig.baseDist() * 0.95, Math.max(2.3 * R, rig.baseDist() * 0.8), e);
                    Vec3 pos = ring(rig.centre, rig.side, lerpAngle(90, face, e), d, lerp(0.15 * R, 0.2 * R, e));
                    return FilmApi.pose(shake(pos, 0.1 + 0.1 * e, p), rig.centre, (float) lerp(44, 38, e));
                }));
        FilmApi.play(FilmApi.film(subject, "FORM", "SENTINEL", 0xFFE8F4FF, shots));
    }

    /** 0-56: phase change. Dark spikes burst out in every direction while the camera punches in. */
    private static void stun(Minecraft mc, Rig rig) {
        hold(56);
        var subject = FilmApi.follow(mc.player, rig.centre);
        var shots = List.of(
                FilmApi.cut(0, 22, (s, p, k) -> {
                    rig.update(p);
                    double d = rig.baseDist() * 1.15;
                    Vec3 pos = ring(rig.centre, rig.side, 30, d, 0.2 * R);
                    return FilmApi.pose(shake(pos, 0.15, p), rig.centre, (float) fovFor(d, 1.4 * R, 14, 62));
                }),
                FilmApi.sweep(22, 56, 6, (s, p, k) -> {
                    rig.update(p);
                    double e = ease(k);
                    double d = lerp(rig.baseDist() * 1.15, Math.max(1.8 * R, rig.baseDist() * 0.45), e);
                    Vec3 pos = ring(rig.centre, rig.side, lerp(30, -60, e), d, lerp(0.2 * R, 0.0, e));
                    return FilmApi.pose(shake(pos, 0.3 + 0.9 * e, p), rig.centre, (float) lerp(45, 72, e));
                }));
        FilmApi.play(FilmApi.film(subject, "ANGEL", "BREACH", 0xFFFF4D4D, shots));
    }

    /** 0-60: the short version, for every final-phase beam after the first. No input lock. */
    private static void unfold(Minecraft mc, Rig rig) {
        var subject = FilmApi.follow(mc.player, rig.centre);
        var shots = List.of(
                FilmApi.cut(0, 60, (s, p, k) -> {
                    rig.update(p);
                    double d = lerp(1.15, 0.95, ease(k)) * rig.baseDist();
                    Vec3 pos = ring(rig.centre, rig.side, lerp(10, -10, ease(k)), d, -0.6 * R);
                    return FilmApi.pose(shake(pos, 0.12 + 0.15 * k, p), rig.centre.add(0, 0.05 * R, 0), (float) fovFor(d, 1.9 * R, 14, 68));
                }));
        FilmApi.play(FilmApi.film(subject, "FINAL FORM", "STAR", 0xFFFF5CC8, shots));
    }

    /**
     * 0-210: the very first unfolding. The three stages begin at charge ticks 1, 80 and 160, so the
     * three shots start on those beats. The beam itself fires at tick 280, so there is time to run.
     */
    private static void unfoldFirst(Minecraft mc, Rig rig) {
        hold(210);
        var subject = FilmApi.follow(mc.player, rig.centre);
        var shots = List.of(
                FilmApi.cut(0, 80, (s, p, k) -> {
                    rig.update(p);
                    double e = ease(k);
                    double d = lerp(1.15, 1.05, e) * rig.baseDist();
                    Vec3 pos = ring(rig.centre, rig.side, 15, d, lerp(-0.5 * R, -0.3 * R, e));
                    return FilmApi.pose(shake(pos, 0.15, p), rig.centre, (float) fovFor(d, 1.8 * R, 14, 68));
                }),
                FilmApi.sweep(80, 160, 12, (s, p, k) -> {
                    rig.update(p);
                    double e = ease(k);
                    double d = lerp(1.05, 1.25, e) * rig.baseDist();
                    Vec3 pos = ring(rig.centre, rig.side, lerp(15, -60, e), d, lerp(-0.3 * R, 0.4 * R, e));
                    return FilmApi.pose(shake(pos, 0.25, p), rig.centre, (float) fovFor(d, 2.3 * R, 16, 70));
                }),
                FilmApi.sweep(160, 210, 12, (s, p, k) -> {
                    rig.update(p);
                    double e = ease(k);
                    double face = angleTo(rig, rig.aim);
                    double d = lerp(1.25 * rig.baseDist(), Math.max(2.6 * R, rig.baseDist() * 0.8), e);
                    Vec3 pos = ring(rig.centre, rig.side, lerpAngle(-60, face, e), d, lerp(0.4 * R, 0.0, e));
                    return FilmApi.pose(shake(pos, 0.3 + 0.5 * e, p), rig.centre, (float) lerp(50, 44, e));
                }));
        FilmApi.play(FilmApi.film(subject, "FINAL FORM", "GEOMETRIC SCREAM", 0xFFFF5CC8, shots));
    }

    /** 0-216: urchin spikes, fast spin, explosions. Close and spinning, pull back, then a low wide shot as it fades. */
    private static void death(Minecraft mc, Rig rig) {
        hold(216);
        var subject = FilmApi.follow(mc.player, rig.centre);
        var shots = List.of(
                FilmApi.cut(0, 60, (s, p, k) -> {
                    rig.update(p);
                    Vec3 pos = ring(rig.centre, rig.side, lerp(0, 200, k), lerp(1.7 * R, 2.3 * R, ease(k)), lerp(0.15 * R, 0.5 * R, ease(k)));
                    return FilmApi.pose(shake(pos, 0.5, p), rig.centre, 55f);
                }),
                FilmApi.sweep(60, 140, 10, (s, p, k) -> {
                    rig.update(p);
                    double e = ease(k);
                    Vec3 pos = ring(rig.centre, rig.side, lerp(200, 330, e), lerp(2.3 * R, 4.5 * R, e), lerp(0.5 * R, 1.2 * R, e));
                    return FilmApi.pose(shake(pos, 0.35, p), rig.centre, (float) lerp(55, 45, e));
                }),
                FilmApi.sweep(140, 216, 14, (s, p, k) -> {
                    rig.update(p);
                    double d = Math.max(rig.baseDist(), 4.5 * R) * 1.1;
                    Vec3 pos = ring(rig.centre, rig.side, -20, d, -1.8 * R);
                    Vec3 look = rig.centre.add(0, lerp(0, 0.4 * R, ease(k)), 0);
                    return FilmApi.pose(shake(pos, 0.2, p), look, (float) fovFor(d, 1.8 * R, 16, 66));
                }));
        FilmApi.play(FilmApi.film(subject, "ANGEL", "DEFEATED", 0xFF9AB8FF, shots));
    }

    /** 0-100: the lower corner stretches into a needle and stabs the ground. Ends at its tip. */
    private static void drill(Minecraft mc, Rig rig) {
        var subject = FilmApi.follow(mc.player, rig.centre);
        var shots = List.of(
                // wide enough to hold the whole column: body, needle and the ground below it
                FilmApi.cut(0, 50, (s, p, k) -> {
                    rig.update(p);
                    double drop = rig.centre.y - rig.groundY();
                    double d = Math.max(rig.baseDist(), 0.5 * drop + 3.2 * R);
                    Vec3 mid = rig.centre.add(0, -0.45 * drop, 0);
                    Vec3 pos = aboveGround(ring(mid, rig.side, lerp(60, 50, ease(k)), d, 0), 3);
                    return FilmApi.pose(shake(pos, 0.1, p), mid, (float) fovFor(d, Math.max(2.6 * R, 0.6 * drop), 16, 62));
                }),
                // drop to the tip and watch it touch down
                FilmApi.sweep(50, 100, 10, (s, p, k) -> {
                    rig.update(p);
                    double e = ease(k);
                    Vec3 tip = new Vec3(rig.centre.x, rig.groundY(), rig.centre.z);
                    double drop = rig.centre.y - tip.y;
                    double d = lerp(Math.max(rig.baseDist(), 0.5 * drop + 3.2 * R), 1.7 * R, e);
                    Vec3 pos = aboveGround(ring(tip, rig.side, lerp(50, 20, e), d, 0.3 * R), 3);
                    Vec3 look = tip.add(0, lerp(0.9 * drop * 0.5, 0.7 * R, e), 0);
                    return FilmApi.pose(shake(pos, 0.1 + 0.9 * Math.pow(k, 3), p), look, (float) lerp(40, 58, e));
                }));
        FilmApi.play(FilmApi.film(subject, "OBJECTIVE", "IMPACT", 0xFFFF9A2E, shots));
    }

    // ==========================================================================================
    // What the cameras know about Ramiel
    // ==========================================================================================

    private static final class Rig {
        final int id;
        final Vec3 eye;                     // where the player's eyes were when the film started
        final Vec3 side;                    // flat unit vector from Ramiel towards that spot
        final double flatDist;              // flat distance from Ramiel to that spot
        Vec3 centre;                        // core / body centre, interpolated
        Vec3 aim = new Vec3(0, 0, 1);       // where it is pointing
        double offsetY;                     // how far the model is still above the entity (arrival)

        Rig(RamielEntity e, Vec3 eye) {
            this.id = e.getId();
            this.eye = eye;
            this.centre = e.centre();
            this.aim = e.aimDir();
            Vec3 d = new Vec3(eye.x - centre.x, 0, eye.z - centre.z);
            this.flatDist = d.length();
            this.side = d.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : d.normalize();
        }

        /** Refresh from the live entity (keeps the last values if it is gone, e.g. at the end of the death film). */
        void update(double partial) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && mc.level.getEntity(id) instanceof RamielEntity r && !r.isRemoved()) {
                float p = (float) partial;
                centre = r.getPosition(p).add(0, RamielEntity.CORE_BOX / 2.0, 0);
                aim = r.aimDir(p);
                offsetY = r.arrivalOffsetY(p);
            }
        }

        /** Where the model is DRAWN: the entity position plus whatever is left of the fall from space. */
        Vec3 model() { return centre.add(0, offsetY, 0); }

        /** Camera distance for the normal shots: near players get pulled back, far players get a long lens. */
        double baseDist() { return Mth.clamp(flatDist, 2.4 * R, 5.5 * R); }

        /** Ground height under Ramiel as seen by this client (falls back to the usual hover height). */
        double groundY() {
            Level level = Minecraft.getInstance().level;
            int x = Mth.floor(centre.x), z = Mth.floor(centre.z);
            if (level != null && level.hasChunk(x >> 4, z >> 4)) {
                return level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
            }
            return centre.y - 84.0;
        }
    }

    // ==========================================================================================
    // Camera maths (all double, so it does not matter whether Rimulib hands us float or double)
    // ==========================================================================================

    /** A point on a ring around {@code centre}. angle 0 = towards {@code side}, positive turns towards its left. */
    private static Vec3 ring(Vec3 centre, Vec3 side, double angleDeg, double dist, double height) {
        double a = Math.toRadians(angleDeg);
        double px = -side.z, pz = side.x;
        return centre.add(side.x * Math.cos(a) * dist + px * Math.sin(a) * dist,
                height,
                side.z * Math.cos(a) * dist + pz * Math.sin(a) * dist);
    }

    /** The ring angle that points the same way as {@code dir}; 0 if dir is nearly vertical. */
    private static double angleTo(Rig rig, Vec3 dir) {
        double len = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        if (len < 0.2) return 0.0;
        double fx = dir.x / len, fz = dir.z / len;
        double along = fx * rig.side.x + fz * rig.side.z;
        double across = fx * -rig.side.z + fz * rig.side.x;
        return Math.toDegrees(Math.atan2(across, along));
    }

    private static Vec3 flat(Vec3 v) {
        double len = Math.sqrt(v.x * v.x + v.z * v.z);
        return len < 1e-4 ? new Vec3(0, 0, 1) : new Vec3(v.x / len, 0, v.z / len);
    }

    /** Ground-level camera for the end of the intro: where the player stood, but never closer than 2.4 R. */
    private static Vec3 groundCam(Rig rig) {
        if (rig.flatDist >= 2.4 * R) return rig.eye;
        return new Vec3(rig.centre.x + rig.side.x * 2.4 * R, rig.eye.y, rig.centre.z + rig.side.z * 2.4 * R);
    }

    /** Keeps a camera point out of the terrain. */
    private static Vec3 aboveGround(Vec3 p, double clearance) {
        Level level = Minecraft.getInstance().level;
        if (level == null) return p;
        int x = Mth.floor(p.x), z = Mth.floor(p.z);
        if (!level.hasChunk(x >> 4, z >> 4)) return p;
        double top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) + clearance;
        return p.y < top ? new Vec3(p.x, top, p.z) : p;
    }

    /** Field of view (degrees) that fits something of half-size {@code half} at {@code dist}. */
    private static double fovFor(double dist, double half, double min, double max) {
        double deg = Math.toDegrees(2.0 * Math.atan2(half, Math.max(1.0, dist)));
        return Mth.clamp(deg, min, max);
    }

    /** A little handheld wobble. */
    private static Vec3 shake(Vec3 p, double amount, double partial) {
        if (amount <= 0.0) return p;
        double t = FxState.now + partial;
        double x = Math.sin(t * 1.7) * 0.6 + Math.sin(t * 3.9 + 1.3) * 0.4;
        double y = Math.sin(t * 2.1 + 2.0) * 0.6 + Math.sin(t * 4.7) * 0.4;
        double z = Math.sin(t * 1.3 + 4.0) * 0.6 + Math.sin(t * 3.3 + 0.7) * 0.4;
        return p.add(x * amount, y * amount, z * amount);
    }

    private static double clamp01(double k) { return k < 0 ? 0 : Math.min(k, 1); }
    private static double lerp(double a, double b, double k) { return a + (b - a) * k; }
    private static double ease(double k) { k = clamp01(k); return k * k * (3.0 - 2.0 * k); }

    /** Linear blend between two angles in degrees, taking the short way round. */
    private static double lerpAngle(double a, double b, double k) {
        double diff = ((b - a + 540.0) % 360.0 + 360.0) % 360.0 - 180.0;
        return a + diff * k;
    }
}
