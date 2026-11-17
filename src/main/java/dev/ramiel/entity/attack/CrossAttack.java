package dev.ramiel.entity.attack;

import dev.ramiel.Form;
import dev.ramiel.entity.BeamKind;
import dev.ramiel.entity.RamielEntity;
import dev.ramiel.net.FilmKind;
import dev.ramiel.net.FxKind;
import dev.ramiel.world.BeamCaster;
import dev.ramiel.world.Destruction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Attack 2: the full plus-shaped form (frames 146-167), spinning end caps while charging.
 * The beam is thick and golden, and where it meets the ground it makes a cross: one big crater
 * in the middle and THREE craters along each of the four arms (frame 208).
 */
public class CrossAttack extends BeamAttack {
    private Vec3[] arms;       // unit vectors along the ground for the four arms
    private Vec3 hit;

    public CrossAttack(RamielEntity r) { super(r); }

    @Override public Form form() { return Form.CROSS; }
    @Override protected int filmKind() { return FilmKind.CROSS; }
    @Override protected int beamKind() { return BeamKind.CROSS; }
    @Override protected double beamRadius() { return 3.4; }
    @Override protected float beamDamage() { return 80f; }
    @Override protected float turnRate(Stage s) { return 0.7f; }

    @Override
    protected int duration(Stage s) {
        return switch (s) {
            case MORPH -> 70;
            case CHARGE -> 120;
            case PAUSE -> 20;
            case FIRE -> 44;
            case RECOVER -> 120;
            case REVERT -> 50;
        };
    }

    @Override
    protected void onEnter(Stage s) {
        super.onEnter(s);
        // the outer shapes spin faster the longer it charges (the renderer reads this value)
        if (s == Stage.MORPH) r.setSpinSpeed(0.35f);
    }

    @Override
    protected void onFirstHit(BeamCaster.Result res, Vec3 dir) {
        if (!res.hitSolid()) return;
        hit = res.end();
        var level = r.serverLevel();

        // centre crater
        Destruction.crater(level, hit, 15, 9, dir);
        r.fx(FxKind.BEAM_IMPACT, hit, dir.x, dir.y, dir.z, 4f, 0f);
        r.fx(FxKind.SCAR, hit, 22, 1800);
        r.fx(FxKind.SHOCKWAVE, hit, 45, 30);
        r.fx(FxKind.IMPACT_FRAME, hit, 4, 0);
        r.fx(FxKind.SHAKE, hit, 2.0, 40);
        BeamCaster.damageRadius(level, r, hit, 24, 70f);

        // arms of the cross lie along the ground: forward, back, left, right of the beam
        Vec3 h = new Vec3(dir.x, 0, dir.z);
        h = h.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : h.normalize();
        Vec3 side = new Vec3(-h.z, 0, h.x);
        arms = new Vec3[]{h, h.scale(-1), side, side.scale(-1)};
    }

    /** Three craters per arm, one ring of them every 5 ticks, moving outward. */
    @Override
    protected void onBeamTick(int t, BeamCaster.Result res, Vec3 dir) {
        if (hit == null || arms == null || t % 5 != 0) return;
        int k = t / 5 + 1;                       // 1, 2, 3
        if (k > 3) return;
        var level = r.serverLevel();
        for (Vec3 arm : arms) {
            double dist = 17.0 * k;
            double x = hit.x + arm.x * dist, z = hit.z + arm.z * dist;
            int top = Destruction.columnTop(level, Mth.floor(x), Mth.floor(z));
            if (top == Destruction.UNLOADED) continue;
            Vec3 at = new Vec3(x, top, z);
            double radius = 9.0 - k * 1.2;
            Destruction.crater(level, at, radius, 5, arm);
            r.fx(FxKind.BEAM_IMPACT, at, arm.x, 0.2, arm.z, 2f, 0f);
            r.fx(FxKind.SCAR, at, radius * 1.5, 1500);
            BeamCaster.damageRadius(level, r, at, radius * 1.6, 45f);
        }
    }
}
