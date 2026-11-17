package dev.ramiel.entity.attack;

import dev.ramiel.Form;
import dev.ramiel.ModRegistry;
import dev.ramiel.RamielConfig;
import dev.ramiel.entity.BeamKind;
import dev.ramiel.entity.RamielEntity;
import dev.ramiel.net.FilmKind;
import dev.ramiel.net.FxKind;
import dev.ramiel.world.BeamCaster;
import dev.ramiel.world.Destruction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Attack 3: spikes burst out on every side (frames 417-444). It does not aim at anyone: it spins
 * faster and faster, then sweeps one thin magenta beam through a full 360 degrees at a downward
 * angle, drawing a cone. It is an area attack meant to scatter anything close to Ramiel.
 */
public class SunburstAttack extends Attack {
    private static final float CONE_PITCH = 28f;       // degrees below horizontal (positive = down)
    private float startYaw;

    public SunburstAttack(RamielEntity r) { super(r); }

    @Override public Form form() { return Form.SUNBURST; }
    @Override protected int filmKind() { return FilmKind.SUNBURST; }
    @Override protected boolean aims(Stage s) { return false; }               // no targeting at all
    @Override protected boolean exposesCore(Stage s) { return s == Stage.CHARGE || s == Stage.FIRE || s == Stage.RECOVER; }

    @Override
    protected int duration(Stage s) {
        return switch (s) {
            case MORPH -> 70;
            case CHARGE -> 160;    // spin-up
            case PAUSE -> 0;
            case FIRE -> 90;       // the sweep
            case RECOVER -> 120;
            case REVERT -> 50;
        };
    }

    @Override
    protected void onEnter(Stage s) {
        if (s == Stage.CHARGE) r.playLoud(ModRegistry.SND_CHARGE.get(), 30f, 0.7f);
        if (s == Stage.FIRE) {
            startYaw = r.aimYaw();
            r.setSpinSpeed(22f);
            r.playLoud(ModRegistry.SND_BEAM.get(), 60f, 1.3f);
            r.fx(FxKind.IMPACT_FRAME, r.centre(), 3, 0);
            r.fx(FxKind.SHAKE, r.centre(), 1.0, 90);
        }
        if (s == Stage.RECOVER) { r.clearBeam(); r.setSpinSpeed(2f); }
    }

    @Override
    protected void onTick(Stage s, float p) {
        if (s == Stage.CHARGE) {
            // faster and faster: 3 -> 38 degrees per tick (visual only)
            r.setSpinSpeed(Mth.lerp(p * p, 3f, 38f));
            return;
        }
        if (s != Stage.FIRE) return;

        float yaw = startYaw + 360f * p;
        r.setAim(yaw, CONE_PITCH);
        var level = r.serverLevel();
        Vec3 origin = r.centre();
        Vec3 dir = r.aimDir();
        BeamCaster.Result res = BeamCaster.march(level, origin, dir, 600, false);
        r.setBeam(BeamKind.SUNBURST, (float) res.length(), p);

        BeamCaster.damageAlong(level, r, origin, res.end(), 1.8, (float) (18f * RamielConfig.damage()), 1.4);
        if (res.hitSolid()) {
            Destruction.scorchBurst(level, res.end(), 4.5, dir);
            if (tick % 3 == 0) r.fx(FxKind.BEAM_IMPACT, res.end(), dir.x, dir.y, dir.z, 1.8f, 0f);
            if (tick % 8 == 0) r.fx(FxKind.SCAR, res.end(), 8, 1000);
        }
        if (tick % 20 == 5) r.playLoud(ModRegistry.SND_BEAM.get(), 40f, 1.4f);
    }
}
