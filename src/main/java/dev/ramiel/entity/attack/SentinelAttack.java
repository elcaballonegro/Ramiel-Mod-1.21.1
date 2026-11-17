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
 * Attack 4: a ring of plates in front of the core and three big pieces behind it (frames 520-530).
 * The beam is long and keeps slowly turning toward the target, so the plates always face the player.
 * It drags a scorched trail across the land.
 */
public class SentinelAttack extends BeamAttack {
    public SentinelAttack(RamielEntity r) { super(r); }

    @Override public Form form() { return Form.SENTINEL; }
    @Override protected int filmKind() { return FilmKind.SENTINEL; }
    @Override protected int beamKind() { return BeamKind.SENTINEL; }
    @Override protected double beamRadius() { return 2.6; }
    @Override protected float beamDamage() { return 14f; }
    @Override protected boolean tracksWhileFiring() { return true; }
    @Override protected float firingTurnRate() { return 0.42f; }       // slow: you can out-run it
    @Override protected float turnRate(Stage s) { return 0.7f; }

    @Override
    protected int duration(Stage s) {
        return switch (s) {
            case MORPH -> 70;
            case CHARGE -> 100;
            case PAUSE -> 20;
            case FIRE -> 160;      // eight seconds of beam
            case RECOVER -> 140;
            case REVERT -> 50;
        };
    }

    @Override
    protected void onBeamTick(int t, BeamCaster.Result res, Vec3 dir) {
        if (!res.hitSolid()) return;
        var level = r.serverLevel();
        if (t % 2 == 0) Destruction.scorchBurst(level, res.end(), 5.5, dir);
        if (t % 4 == 0) r.fx(FxKind.BEAM_IMPACT, res.end(), dir.x, dir.y, dir.z, 2.2f, 0f);
        if (t % 12 == 0) r.fx(FxKind.SCAR, res.end(), 9, 1200);
        if (t % 20 == 0) r.fx(FxKind.SHAKE, res.end(), 0.7, 20);
        if (t == 0) r.fx(FxKind.IMPACT_FRAME, res.end(), 3, 0);
    }
}
