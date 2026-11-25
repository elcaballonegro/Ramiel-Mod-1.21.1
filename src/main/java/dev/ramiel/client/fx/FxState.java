package dev.ramiel.client.fx;

import dev.ramiel.net.FxKind;
import dev.ramiel.net.Payloads;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

/**
 * Everything the client is currently showing that came from the server as a one-off event:
 * shockwaves, explosions, scars, warning zones, shield ripples, railgun beams, screen shake and
 * impact frames. {@link #handle} receives packets, {@link #tick} ages things once per game tick,
 * and the *Fx classes draw them every frame.
 *
 * (Beams fired by Ramiel itself are NOT here: they are read straight from the entity.)
 */
public final class FxState {
    private FxState() {}

    /** Game ticks since the client started. All ages are measured against this. */
    public static long now;

    // ---- data ----------------------------------------------------------------------------------
    /** Expanding ring. kind 0 = white-gold shockwave, 1 = pink/orange drill ring. */
    public static final class Wave { public Vec3 pos; public double radius; public int age, life, kind; }
    /** Chain of glowing spheres and fireballs. */
    public static final class Blast { public Vec3 pos; public double radius; public int age, life; public long seed; }
    /** A glowing ground scar that cools over time. */
    public static final class Scar { public Vec3 pos; public double radius; public long born; public int life; public int[] heights; public long refreshed = -999; public int cells; }
    /** The red blast-zone marker. */
    public static final class Zone { public int id; public Vec3 centre; public double radius; public long end; public double[][] heights; public long refreshed = -999; }
    /** A ripple (or, if {@code shatter}, a break) on the shield. {@code dir} points outward from Ramiel. */
    public static final class Ripple { public Vec3 pos, dir; public float strength; public int age; public boolean shatter; }
    /** A short bright flare where a beam hits. */
    public static final class Flare { public Vec3 pos; public double size; public int age, life; public float r, g, b; }
    /** The railgun beam. */
    public static final class Rail { public Vec3 from, to; public float width; public int age, life; }

    public static final List<Wave> waves = new ArrayList<>();
    public static final List<Blast> blasts = new ArrayList<>();
    public static final List<Scar> scars = new ArrayList<>();
    public static final Map<Integer, Zone> zones = new HashMap<>();
    public static final List<Ripple> ripples = new ArrayList<>();
    public static final List<Flare> flares = new ArrayList<>();
    public static final List<Rail> rails = new ArrayList<>();
    /** Recent beam directions per Ramiel, so the sweeping beam can draw its cone. entity id -> [yaw, pitch, length] */
    public static final Map<Integer, ArrayDeque<float[]>> sweep = new HashMap<>();

    // ---- screen state --------------------------------------------------------------------------
    private static float shake, shakeDecay;
    private static int impactLeft, impactTotal;
    private static float impactStrength;

    public static float shake() { return shake; }
    public static boolean impactActive() { return impactLeft > 0; }
    public static float impactStrength() { return impactStrength; }
    /** 0 or 1, alternating each tick, so an impact frame flickers between "negative" and "positive". */
    public static int impactPhase() { return (impactTotal - impactLeft) & 1; }

    // ==========================================================================================
    public static void handle(Payloads.Fx p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Vec3 pos = new Vec3(p.x(), p.y(), p.z());
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        double dist = cam.distanceTo(pos);

        switch (p.kind()) {
            case FxKind.IMPACT_FRAME -> {
                double reach = 400 + p.a() * 70;
                if (dist < reach) startImpact((int) Math.max(2, p.a()), (float) (1.0 - dist / reach));
            }
            case FxKind.SHAKE -> {
                if (dist < 800) addShake((float) (p.a() * (1.0 - dist / 800.0)), (int) p.b());
            }
            case FxKind.SHOCKWAVE -> add(waves, wave(pos, p.a(), (int) Math.max(8, p.b()), 0));
            case FxKind.DRILL_SPARK -> {
                add(waves, wave(pos.add(0, 0.5, 0), Math.max(4, p.a() * 3), 22, 1));
                add(waves, wave(pos.add(0, 0.5, 0), Math.max(4, p.a() * 5), 34, 1));
                sparks(pos, new Vec3(0, 1, 0), 10);
            }
            case FxKind.BLAST -> {
                Blast b = new Blast();
                b.pos = pos; b.radius = p.a(); b.life = (int) Math.max(40, p.a() * 0.9); b.seed = Double.doubleToLongBits(pos.x * 31 + pos.z);
                blasts.add(b);
            }
            case FxKind.POOF -> {
                Blast b = new Blast();
                b.pos = pos; b.radius = Math.max(4, p.a()); b.life = 26; b.seed = Double.doubleToLongBits(pos.y * 17 + pos.x);
                blasts.add(b);
            }
            case FxKind.SCAR -> {
                if (dist < 2500) {
                    Scar s = new Scar();
                    s.pos = pos; s.radius = p.a(); s.born = now; s.life = (int) Math.max(200, p.b());
                    scars.add(s);
                    if (scars.size() > 90) scars.remove(0);
                }
            }
            case FxKind.WARN_ZONE -> {
                int id = (int) p.f1();
                if (p.b() <= 0) { zones.remove(id); }
                else {
                    Zone z = zones.computeIfAbsent(id, k -> new Zone());
                    z.id = id; z.centre = pos; z.radius = p.a(); z.end = now + (long) p.b();
                }
            }
            case FxKind.SHIELD_HIT -> {
                Ripple r = new Ripple();
                r.pos = pos; r.dir = new Vec3(p.a(), p.b(), p.c()).normalize(); r.strength = Math.max(0.3f, p.f1());
                ripples.add(r);
                if (ripples.size() > 24) ripples.remove(0);
            }
            case FxKind.SHIELD_BREAK -> {
                Ripple r = new Ripple();
                r.pos = pos; r.dir = pos.subtract(cam).normalize(); r.strength = 1f; r.shatter = true;
                ripples.add(r);
            }
            case FxKind.BEAM_IMPACT -> {
                Vec3 dir = new Vec3(p.a(), p.b(), p.c());
                Flare f = new Flare();
                f.pos = pos; f.size = Math.max(3, p.f1() * 5); f.life = 10; f.r = 1f; f.g = 0.8f; f.b = 0.45f;
                flares.add(f);
                sparks(pos, dir.lengthSqr() > 1e-6 ? dir.normalize().scale(-1) : new Vec3(0, 1, 0), (int) Math.min(14, 4 + p.f1() * 2));
            }
            case FxKind.RAIL_BEAM -> {
                Rail r = new Rail();
                r.from = pos; r.to = new Vec3(p.a(), p.b(), p.c()); r.width = Math.max(0.5f, p.f1()); r.life = (int) Math.max(6, p.f2());
                rails.add(r);
                Flare f = new Flare();
                f.pos = pos; f.size = 5; f.life = 8; f.r = 0.6f; f.g = 1f; f.b = 0.9f;
                flares.add(f);
            }
            default -> { }
        }
    }

    private static Wave wave(Vec3 pos, double radius, int life, int kind) {
        Wave w = new Wave();
        w.pos = pos; w.radius = radius; w.life = life; w.kind = kind;
        return w;
    }

    private static <T> void add(List<T> list, T item) {
        list.add(item);
        if (list.size() > 80) list.remove(0);
    }

    private static void sparks(Vec3 at, Vec3 dir, int count) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        var rnd = level.random;
        for (int i = 0; i < count; i++) {
            Vec3 v = dir.scale(0.25 + rnd.nextDouble() * 0.5).add((rnd.nextDouble() - 0.5) * 0.5, rnd.nextDouble() * 0.4, (rnd.nextDouble() - 0.5) * 0.5);
            level.addParticle(i % 3 == 0 ? ParticleTypes.LAVA : ParticleTypes.FLAME, true, at.x, at.y + 0.5, at.z, v.x, v.y, v.z);
        }
    }

    // ---- screen effects ----------------------------------------------------------------------
    private static void startImpact(int ticks, float strength) {
        if (strength <= 0.02f) return;
        impactLeft = Math.max(impactLeft, ticks);
        impactTotal = impactLeft;
        impactStrength = Math.max(impactStrength * 0.5f, Math.min(1f, strength + 0.15f));
    }

    private static void addShake(float strength, int ticks) {
        if (strength <= 0.01f) return;
        shake = Math.max(shake, strength);
        shakeDecay = Math.max(shakeDecay, strength / Math.max(4, ticks));
    }

    // ==========================================================================================
    /** Called once per client game tick. */
    public static void tick() {
        now++;
        shake = Math.max(0f, shake - shakeDecay);
        if (shake <= 0f) shakeDecay = 0f;
        if (impactLeft > 0) impactLeft--; else impactStrength = 0f;

        age(waves); age(blasts); age(flares); age(rails);
        for (Iterator<Ripple> it = ripples.iterator(); it.hasNext(); ) {
            Ripple r = it.next();
            if (++r.age > (r.shatter ? 30 : 22)) it.remove();
        }
        scars.removeIf(s -> now - s.born > s.life);
        zones.values().removeIf(z -> now > z.end);
    }

    private static <T> void age(List<T> list) {
        for (Iterator<T> it = list.iterator(); it.hasNext(); ) {
            T o = it.next();
            boolean dead;
            if (o instanceof Wave w) { dead = ++w.age >= w.life; }
            else if (o instanceof Blast b) { dead = ++b.age >= b.life; }
            else if (o instanceof Flare f) { dead = ++f.age >= f.life; }
            else if (o instanceof Rail r) { dead = ++r.age >= r.life; }
            else dead = true;
            if (dead) it.remove();
        }
    }

    public static void clear() {
        waves.clear(); blasts.clear(); scars.clear(); zones.clear(); ripples.clear(); flares.clear(); rails.clear(); sweep.clear();
        shake = 0; impactLeft = 0;
    }
}
