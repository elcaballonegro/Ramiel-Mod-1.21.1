package dev.ramiel.entity.attack;

import dev.ramiel.ModRegistry;
import dev.ramiel.entity.BeamKind;
import dev.ramiel.net.FxKind;
import dev.ramiel.RamielConfig;
import dev.ramiel.world.BeamCaster;
import dev.ramiel.world.Wards;
import net.minecraft.world.phys.Vec3;

/**
 * Shared behaviour of the "one straight beam out of the core" attacks (lance, cross, sentinel, star).
 * Subclasses pick the numbers and add their ground effects in {@link #onFirstHit} / {@link #onBeamTick}.
 */
public abstract class BeamAttack extends Attack {
    protected int fireTick;

    protected BeamAttack(dev.ramiel.entity.RamielEntity r) { super(r); }

    protected abstract int beamKind();
    protected abstract double beamRadius();
    protected abstract float beamDamage();
    protected boolean pierces() { return false; }
    /** Does the aim keep following the target while the beam is on? */
    protected boolean tracksWhileFiring() { return false; }
    protected float firingTurnRate() { return 0.4f; }
    protected void onFirstHit(BeamCaster.Result result, Vec3 dir) {}
    protected void onBeamTick(int fireTick, BeamCaster.Result result, Vec3 dir) {}

    @Override
    protected void onEnter(Stage s) {
        switch (s) {
            case CHARGE -> r.playLoud(ModRegistry.SND_CHARGE.get(), 25f, 1f);
            case PAUSE -> warnWards();
            case FIRE -> {
                fireTick = 0;
                r.playLoud(ModRegistry.SND_BEAM.get(), 60f, 1f);
                dev.ramiel.world.Sfx.laser(r.serverLevel(), r.centre(), beamRadius() > 5 ? 2f : 1f);
                r.fx(FxKind.IMPACT_FRAME, r.centre(), 3, 0);
                r.fx(FxKind.SHAKE, r.centre(), 1.2, 30, 0, 0f, 0f);
            }
            case RECOVER -> r.clearBeam();
            default -> {}
        }
    }

    @Override
    protected void onTick(Stage s, float p) {
        if (s == Stage.PAUSE) {
            // a faint red line shows where the beam is about to go
            var res = BeamCaster.march(r.serverLevel(), r.centre(), r.aimDir(), 500, pierces());
            r.setBeam(BeamKind.TELEGRAPH, (float) res.length(), p);
        } else if (s == Stage.FIRE) {
            if (tracksWhileFiring()) r.aimAtTarget(firingTurnRate());
            Vec3 origin = r.centre();
            Vec3 dir = r.aimDir();
            BeamCaster.Result res = BeamCaster.march(r.serverLevel(), origin, dir, 700, pierces());
            r.setBeam(beamKind(), (float) res.length(), p);

            if (fireTick % 2 == 0) {
                BeamCaster.damageAlong(r.serverLevel(), r, origin, res.end(), beamRadius(),
                        (float) (beamDamage() * RamielConfig.damage()), 1.5);
            }
            if (fireTick == 0) onFirstHit(res, dir);
            onBeamTick(fireTick, res, dir);
            if (fireTick % 20 == 10) dev.ramiel.world.Sfx.laserHum(r.serverLevel(), r.centre(), beamRadius() > 5 ? 2f : 1f);
            fireTick++;
        }
    }

    private void warnWards() {
        var res = BeamCaster.march(r.serverLevel(), r.centre(), r.aimDir(), 500, pierces());
        Wards.warnBeam(r.serverLevel(), res.end(), beamRadius() * 4 + 6, duration(Stage.PAUSE));
    }
}
