package dev.ramiel.entity.attack;

import cyou.rimuru.rimulib.mc1211.api.CarveApi;
import dev.ramiel.Form;
import dev.ramiel.ModRegistry;
import dev.ramiel.RamielConfig;
import dev.ramiel.entity.BeamKind;
import dev.ramiel.entity.RamielEntity;
import dev.ramiel.net.FilmKind;
import dev.ramiel.net.FxKind;
import dev.ramiel.world.BeamCaster;
import dev.ramiel.world.Destruction;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The final form (frames 725-746): the upside-down star unfolds three times, larger each time
 * (the renderer draws three layers that grow with the charge), then fires the mountain-breaker
 * from a standstill. It is used for the whole final phase, once every 30-60 seconds.
 *
 * The beam pierces terrain and cuts a trench along its whole path with Rimulib's CarveApi.line.
 */
public class StarAttack extends BeamAttack {
    private final boolean first;

    public StarAttack(RamielEntity r, boolean first) {
        super(r);
        this.first = first;
    }

    @Override public Form form() { return Form.STAR; }
    @Override protected Form afterForm() { return Form.STAR; }          // the star stays out
    @Override protected int filmKind() { return first ? FilmKind.UNFOLD_FIRST : FilmKind.UNFOLD; }
    @Override protected int beamKind() { return BeamKind.STAR; }
    @Override protected double beamRadius() { return 7.5; }
    @Override protected float beamDamage() { return 700f; }
    @Override protected boolean pierces() { return true; }
    @Override protected float turnRate(Stage s) { return 0.5f; }

    @Override
    protected int duration(Stage s) {
        return switch (s) {
            case MORPH -> 0;
            case CHARGE -> 240;    // three unfoldings, 80 ticks each
            case PAUSE -> 40;
            case FIRE -> 100;
            case RECOVER -> 200;
            case REVERT -> 0;
        };
    }

    @Override
    protected void onEnter(Stage s) {
        super.onEnter(s);
        if (s == Stage.CHARGE) r.fx(FxKind.SHAKE, r.centre(), 0.5, 240);
        if (s == Stage.FIRE) r.fx(FxKind.SHAKE, r.centre(), 3.0, 100);
    }

    @Override
    protected void onTick(Stage s, float p) {
        super.onTick(s, p);
        // each unfolding is announced with a pulse
        if (s == Stage.CHARGE && (tick == 1 || tick == 80 || tick == 160)) {
            r.playLoud(ModRegistry.SND_MORPH.get(), 30f, 0.6f + 0.2f * (tick / 80));
            r.fx(FxKind.SHOCKWAVE, r.centre(), 60 + 40 * (tick / 80), 30);
            dev.ramiel.world.Shockwave.launch(r.serverLevel(), new Vec3(r.getX(), r.groundY(), r.getZ()), 60 + 40 * (tick / 80), 30, 0.5, false, r);
        }
    }

    @Override
    protected void onFirstHit(BeamCaster.Result res, Vec3 dir) {
        var level = r.serverLevel();
        Vec3 origin = r.centre();
        r.fx(FxKind.IMPACT_FRAME, origin, 6, 0);
        // the pressure wave: a muzzle blast under Ramiel, and the big one where the beam meets the ground (thrown down-range)
        Vec3 below = new Vec3(origin.x, r.groundY(), origin.z);
        r.fx(FxKind.SHOCKWAVE, below, 120, 50);
        dev.ramiel.world.Shockwave.launch(level, below, 120, 50, 1.2, true, r);
        if (res.hitSolid()) {
            r.fx(FxKind.SHOCKWAVE, res.end(), 220, 90);
            dev.ramiel.world.Shockwave.launch(level, res.end(), dev.ramiel.world.Shockwave.along(dir, 0.4), 220, 90, 2.0, true, r);
        }
        if (RamielConfig.destruction() <= 0.001) return;

        // horizontal footprint of the beam, and how steeply it falls
        double hLen = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        if (hLen < 1e-3) {                                // straight down: one big hole instead of a trench
            Destruction.crater(level, res.end(), 40, 30, dir);
            return;
        }
        final double hx = dir.x / hLen, hz = dir.z / hLen;
        final double slope = dir.y / hLen;                // change in height per block travelled horizontally
        final double halfWidth = 15.0 * Math.min(RamielConfig.destruction(), 1.5);
        final double length = Math.min(520.0, res.length() * hLen);
        final double oy = origin.y, ox = origin.x, oz = origin.z;

        CarveApi.Floor floor = (x, z) -> {
            double dx = x + 0.5 - ox, dz = z + 0.5 - oz;
            double along = dx * hx + dz * hz;
            double side = Math.abs(-dx * hz + dz * hx);
            if (along < 0 || along > length || side > halfWidth) return CarveApi.UNTOUCHED;
            double beamY = oy + slope * along;
            double roundness = Math.sqrt(Math.max(0.0, 1.0 - (side * side) / (halfWidth * halfWidth)));
            return (int) Math.floor(beamY - halfWidth * 0.75 * roundness);   // round-bottomed trench
        };
        CarveApi.Surface glass = (x, y, z, was) ->
                ((x * 31 + z * 17) & 7) == 0 ? Blocks.MAGMA_BLOCK.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();

        // throw debris from the first stretch of terrain the beam crosses, then cut the trench
        for (double along = 30; along < length; along += 60) {
            double x = ox + hx * along, z = oz + hz * along;
            int top = Destruction.columnTop(level, Mth.floor(x), Mth.floor(z));
            if (top == Destruction.UNLOADED) continue;
            Vec3 at = new Vec3(x, top, z);
            Destruction.launchDebris(level, at, 24, dir, 55);
            r.fx(FxKind.SCAR, at, 20, 2400);
            r.fx(FxKind.BEAM_IMPACT, at, dir.x, dir.y, dir.z, 5f, 0f);
        }
        CarveApi.line(level, new Vec3(ox, oy, oz), new Vec3(hx, 0, hz), length, halfWidth, floor, glass)
                .named("Ramiel star beam").keepBlocks().perTick(4).lazy().start();
    }

    @Override
    protected void onBeamTick(int t, BeamCaster.Result res, Vec3 dir) {
        if (t % 10 == 0) {
            r.fx(FxKind.SHOCKWAVE, res.end(), 40, 24);
            if (res.hitSolid()) dev.ramiel.world.Shockwave.launch(r.serverLevel(), res.end(), dev.ramiel.world.Shockwave.along(dir, 0.3), 40, 24, 0.45, false, r);
        }
        if (t == 0) r.fx(FxKind.BLAST, res.end(), 50, 0);
    }
}
