package dev.ramiel.client.model;

import dev.ramiel.Form;
import org.joml.Quaternionf;

/**
 * Works out the bricks to draw right now, while Ramiel changes shape.
 *
 * NOTHING FADES. Every change is pieces moving:
 *
 *   rest form -> big form     the octahedron SPLITS into the folded pose (stacked plates, caps, a
 *                             bundle of long shards), then the pieces fly out into the new form
 *   big form  -> rest form    the form FOLDS into that same stacked pose, then the pieces SNAP
 *                             back together into the octahedron (this is the reversion, and it is
 *                             the same for every attack: Lance, Cross, Sunburst, Sentinel, ...)
 *   big form  -> big form     fold, then unfold (e.g. the urchin folding into the star after a stun)
 *   anything  -> urchin       no fold: the spikes burst straight out of the body (stun / death)
 *   rest <-> rest             e.g. the drill needle extending: just the pieces involved move
 *
 * "Rest forms" are DORMANT, SHIELD and DRILL (an octahedron); "big forms" are LANCE, CROSS,
 * SUNBURST, SENTINEL, STAR and URCHIN.
 *
 * Inside each stage every piece starts at a slightly different moment (so it looks like shattering
 * and reforming, not one blend) and swings out a little along its own direction as it goes. A piece
 * that only exists at one end flies out of / into the centre (see {@link Brick#lerp}); it is never faded.
 *
 * Works from plain numbers (not an entity) so the horizon version and the item can use it too.
 */
public final class RamielModel {
    private static final float STAGGER = 0.35f;
    private static final float BURST = 0.45f;

    /** Share of the morph spent reaching the folded pose. Splitting open is quick, folding up is slower, the snap shut is last. */
    private static final float SPLIT_OPEN = 0.38f;
    private static final float SPLIT_CLOSE = 0.60f;
    private static final float SPLIT_BETWEEN = 0.50f;

    private enum Ease { SMOOTH, SNAP }

    private final Brick[] from = FormPoses.newPool();
    private final Brick[] to = FormPoses.newPool();
    private final Brick[] mid = FormPoses.newPool();
    private final Quaternionf qa = new Quaternionf(), qb = new Quaternionf();
    private final Brick[] out = FormPoses.newPool();

    /** One shared instance is fine: drawing is single threaded and finishes before the next call. */
    private static final RamielModel SHARED = new RamielModel();
    public static RamielModel shared() { return SHARED; }

    /**
     * @param morph      0 = fully {@code fromForm}, 1 = fully {@code toForm}
     * @param time       animation time in ticks (with partial tick)
     * @param charge     0..1 attack charge
     * @param drill      length of the drill needle in R units
     * @param coreScale  0..1 how exposed the core is
     * @return the shared brick array (valid until the next call)
     */
    public Brick[] compute(Form fromForm, Form toForm, float morph, float time, float charge, float drill, float coreScale) {
        if (fromForm == toForm || morph >= 1f) {
            FormPoses.fill(toForm, out, time, charge, drill, coreScale);
            return out;
        }
        if (morph <= 0f) {
            FormPoses.fill(fromForm, out, time, charge, drill, coreScale);
            return out;
        }

        if (viaFold(fromForm, toForm)) {
            float split = isRest(toForm) ? SPLIT_CLOSE : isRest(fromForm) ? SPLIT_OPEN : SPLIT_BETWEEN;
            FormPoses.fold(mid, time, coreScale);
            if (morph < split) {
                FormPoses.fill(fromForm, from, time, charge, drill, coreScale);
                blend(from, mid, morph / split, STAGGER, BURST, Ease.SMOOTH);
            } else {
                FormPoses.fill(toForm, to, time, charge, drill, coreScale);
                boolean snap = isRest(toForm);       // closing up into the octahedron: the pieces accelerate and click shut
                blend(mid, to, (morph - split) / (1f - split), snap ? 0.12f : STAGGER, snap ? BURST * 0.25f : BURST,
                        snap ? Ease.SNAP : Ease.SMOOTH);
            }
        } else {
            FormPoses.fill(fromForm, from, time, charge, drill, coreScale);
            FormPoses.fill(toForm, to, time, charge, drill, coreScale);
            blend(from, to, morph, STAGGER, toForm == Form.URCHIN ? BURST * 1.8f : BURST, Ease.SMOOTH);
        }
        return out;
    }

    /** Which changes go through the folded pose. */
    static boolean viaFold(Form a, Form b) {
        if (b == Form.URCHIN) return false;                           // stun / death: burst straight out
        return (isBig(a) && (isBig(b) || isRest(b))) || (isRest(a) && isBig(b));
    }

    static boolean isRest(Form f) { return f == Form.DORMANT || f == Form.SHIELD || f == Form.DRILL; }

    static boolean isBig(Form f) {
        switch (f) {
            case LANCE: case CROSS: case SUNBURST: case SENTINEL: case STAR: case URCHIN: return true;
            default: return false;
        }
    }

    /** Blend every brick of {@code a} into {@code b} (m = 0..1) into {@link #out}. */
    private void blend(Brick[] a, Brick[] b, float m, float stagger, float burstAmount, Ease ease) {
        for (int i = 0; i < FormPoses.COUNT; i++) {
            boolean core = i >= FormPoses.CORE_TOP;
            float delay = core ? 0f : FormPoses.hash(i) * stagger;
            float local = Math.max(0f, Math.min(1f, (m - delay) / (1f - stagger)));
            float e = ease == Ease.SNAP ? local * local * local : local * local * (3f - 2f * local);
            float presence = Brick.lerp(a[i], b[i], e, out[i], qa, qb);
            if (!core && out[i].visible()) {
                // swing out a little along a fixed random direction: strongest mid-way, zero at both ends,
                // and only as much as the piece is actually out (a piece still a speck in the core must not jump)
                float burst = burstAmount * presence * (float) Math.sin(Math.PI * e) * (0.3f + 0.7f * FormPoses.hash(i + 101));
                float[] d = FormPoses.fib(i * 7 + 3, 61);
                out[i].x += d[0] * burst; out[i].y += d[1] * burst; out[i].z += d[2] * burst;
            }
        }
    }
}
