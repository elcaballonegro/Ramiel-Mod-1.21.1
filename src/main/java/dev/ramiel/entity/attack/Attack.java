package dev.ramiel.entity.attack;

import dev.ramiel.Form;
import dev.ramiel.ModRegistry;
import dev.ramiel.entity.RamielEntity;
import net.minecraft.util.Mth;

/**
 * An attack is a short script with six stages. Every attack shares this timeline, so a new attack
 * only has to say how long each stage lasts and what happens in them.
 *
 *   MORPH    Ramiel changes shape (a cutscene plays for nearby players). Harmless.
 *   CHARGE   the core is exposed and energy builds. Players run, hide or fight back.
 *   PAUSE    aim is locked; a short breather so players can still get away. A red line shows the aim.
 *   FIRE     the beam. Damage and craters happen here.
 *   RECOVER  the core stays exposed: this is the main chance to hurt it.
 *   REVERT   it folds back into its calm shape.
 *
 * A stage with duration 0 is skipped.
 */
public abstract class Attack {
    public enum Stage { MORPH, CHARGE, PAUSE, FIRE, RECOVER, REVERT }
    private static final Stage[] STAGES = Stage.values();

    protected final RamielEntity r;
    private Stage stage;                // null until the first tick
    protected int tick;                 // ticks spent in the current stage
    private boolean done;

    protected Attack(RamielEntity r) { this.r = r; }

    // ---- what a subclass must say ------------------------------------------------------------
    /** The shape Ramiel takes for this attack. */
    public abstract Form form();
    /** Length of each stage in ticks. 20 ticks = 1 second. */
    protected abstract int duration(Stage s);

    // ---- what a subclass may change ----------------------------------------------------------
    /** The shape Ramiel returns to afterwards. */
    protected Form afterForm() { return Form.DORMANT; }
    /** Is the core exposed (and therefore weak) during this stage? */
    protected boolean exposesCore(Stage s) { return s != Stage.MORPH && s != Stage.REVERT; }
    /** Does Ramiel turn toward its target during this stage? */
    protected boolean aims(Stage s) { return s == Stage.CHARGE; }
    /** Maximum turning speed in degrees per tick while aiming. Small = slow, easy to dodge. */
    protected float turnRate(Stage s) { return 0.9f; }
    /** Which cutscene plays when this attack starts, or -1 for none. */
    protected int filmKind() { return -1; }

    protected void onEnter(Stage s) {}
    protected void onTick(Stage s, float progress) {}
    protected void onFinish() {}

    // ---- driver ------------------------------------------------------------------------------
    public final Stage stage() { return stage; }
    public final boolean isDone() { return done; }

    /** Advance one tick. @return true once the attack is completely finished. */
    public final boolean tick() {
        if (done) return true;
        if (stage == null) begin();
        if (done) return true;

        int dur = Math.max(1, duration(stage));
        float p = Mth.clamp(tick / (float) dur, 0f, 1f);

        r.setCoreOpen(exposesCore(stage));
        r.setCharge(switch (stage) {
            case MORPH, REVERT -> 0f;
            case CHARGE -> p;
            case PAUSE, FIRE -> 1f;
            case RECOVER -> 1f - p;
        });
        if (aims(stage)) r.aimAtTarget(turnRate(stage));

        onTick(stage, p);
        if (done) return true;

        if (++tick >= dur) goTo(next(stage));
        return done;
    }

    /** Stop immediately (the boss was staggered into another state, or died). */
    public final void abort() {
        if (done) return;
        done = true;
        cleanUp();
    }

    /** Jump straight to a later stage, e.g. when a charge gets interrupted. */
    protected final void jumpTo(Stage s) { goTo(s); }

    // ---- internals ---------------------------------------------------------------------------
    private void begin() {
        r.setForm(form(), Math.max(1, duration(Stage.MORPH)));
        int film = filmKind();
        if (film >= 0) r.playFilm(film);
        if (duration(Stage.MORPH) > 0) r.playLoud(ModRegistry.SND_MORPH.get(), 20f, 1f);
        goTo(Stage.MORPH);
    }

    private void goTo(Stage s) {
        while (s != null && duration(s) <= 0) s = next(s);
        if (s == null) {
            done = true;
            cleanUp();
            r.setForm(afterForm(), 24);
            onFinish();
            return;
        }
        stage = s;
        tick = 0;
        if (s == Stage.REVERT) {
            r.setForm(afterForm(), duration(Stage.REVERT));
            r.setCoreOpen(false);
        }
        onEnter(s);
    }

    private void cleanUp() {
        r.clearBeam();
        r.setCharge(0f);
        r.setCoreOpen(false);
        r.setDrill(0f);
        r.setSpinSpeed(0.35f);
    }

    private static Stage next(Stage s) {
        int i = s.ordinal() + 1;
        return i < STAGES.length ? STAGES[i] : null;
    }
}
