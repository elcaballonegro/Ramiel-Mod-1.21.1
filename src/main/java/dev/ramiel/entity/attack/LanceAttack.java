package dev.ramiel.entity.attack;

import dev.ramiel.Form;
import dev.ramiel.entity.BeamKind;
import dev.ramiel.entity.RamielEntity;
import dev.ramiel.net.FilmKind;
import dev.ramiel.net.FxKind;
import dev.ramiel.world.BeamCaster;
import dev.ramiel.world.Destruction;
import net.minecraft.world.phys.Vec3;

/**
 * Attack 1: the most common and least destructive one (frames 98-121).
 * The body splits into stepped slabs; the core fires one thin beam at the target.
 */
public class LanceAttack extends BeamAttack {
    public LanceAttack(RamielEntity r) { super(r); }

    @Override public Form form() { return Form.LANCE; }
    @Override protected int filmKind() { return FilmKind.LANCE; }
    @Override protected int beamKind() { return BeamKind.LANCE; }
    @Override protected double beamRadius() { return 1.6; }
    @Override protected float beamDamage() { return 45f; }
    @Override protected float turnRate(Stage s) { return 0.8f; }

    @Override
    protected int duration(Stage s) {
        return switch (s) {
            case MORPH -> 50;      // cutscene
            case CHARGE -> 100;    // players run / hide / shoot the core
            case PAUSE -> 20;      // aim locked, last chance to get away
            case FIRE -> 30;
            case RECOVER -> 100;   // core still exposed
            case REVERT -> 40;
        };
    }

    @Override
    protected void onFirstHit(BeamCaster.Result res, Vec3 dir) {
        if (!res.hitSolid()) return;
        var level = r.serverLevel();
        Destruction.crater(level, res.end(), 10, 6, dir);
        r.fx(FxKind.BEAM_IMPACT, res.end(), dir.x, dir.y, dir.z, 2.5f, 0f);
        r.fx(FxKind.SCAR, res.end(), 14, 1400);
        r.fx(FxKind.SHOCKWAVE, res.end(), 28, 24);
        dev.ramiel.world.Shockwave.launch(level, res.end(), dev.ramiel.world.Shockwave.along(dir, 0.3), 28, 24, 0.45, false, r);
        r.fx(FxKind.IMPACT_FRAME, res.end(), 3, 0);
        BeamCaster.damageRadius(level, r, res.end(), 16, 40f);
    }
}
