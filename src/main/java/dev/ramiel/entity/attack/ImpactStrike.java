package dev.ramiel.entity.attack;

import cyou.rimuru.rimulib.mc1211.api.CarveApi;
import dev.ramiel.Form;
import dev.ramiel.ModRegistry;
import dev.ramiel.RamielConfig;
import dev.ramiel.entity.RamielEntity;
import dev.ramiel.net.FilmKind;
import dev.ramiel.net.FxKind;
import dev.ramiel.world.BeamCaster;
import dev.ramiel.world.Destruction;
import dev.ramiel.world.Wards;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Ramiel's own objective when nobody is bothering it: wipe out a chunk of the world.
 *
 *   MORPH    the lower corner stretches into a needle and stabs the ground (frames 340-379)
 *   CHARGE   the needle is planted. A red blast zone is drawn on the ground and everyone in it is
 *            warned (chat bar + alarm + screen effects) for the whole charge time.
 *   PAUSE    the last two seconds: silence, the zone flashes
 *   FIRE     detonation: one huge crater (built in advance by Rimulib so it lands in one tick),
 *            debris, shockwaves, damage with falloff
 *   RECOVER  smoke and quiet
 *   REVERT   the needle pulls out; the brain then sends Ramiel far away from the crater
 *
 * Enough damage while charging cancels it (setting "damage interrupts"): the strike fizzles and
 * Ramiel is staggered with its core briefly exposed.
 */
public class ImpactStrike extends Attack {
    private Vec3 impact;                    // where the needle touches down
    private double drillLength;             // blocks from the lower corner to the ground
    private CarveApi.Cut cut;               // the crater, built in advance
    private boolean interrupted;
    private boolean detonated;

    public ImpactStrike(RamielEntity r) { super(r); }

    @Override public Form form() { return Form.DRILL; }
    @Override protected int filmKind() { return FilmKind.DRILL; }
    @Override protected boolean aims(Stage s) { return false; }
    @Override protected boolean exposesCore(Stage s) { return interrupted && s == Stage.RECOVER; }

    public Vec3 impactPoint() { return impact; }
    public boolean wasInterrupted() { return interrupted; }
    public boolean isCharging() { return !interrupted && (stage() == Stage.CHARGE || stage() == Stage.PAUSE); }

    @Override
    protected int duration(Stage s) {
        return switch (s) {
            case MORPH -> 100;                                   // needle extends
            case CHARGE -> RamielConfig.blastSeconds() * 20;     // the long warning
            case PAUSE -> 40;
            case FIRE -> 70;
            case RECOVER -> interrupted ? 200 : 60;
            case REVERT -> 80;
        };
    }

    // -------------------------------------------------------------------------------------------
    @Override
    protected void onEnter(Stage s) {
        var level = r.serverLevel();
        switch (s) {
            case MORPH -> {
                double ground = r.groundY();
                impact = new Vec3(r.getX(), ground, r.getZ());
                drillLength = Math.max(10.0, (r.centre().y - RamielEntity.R) - ground + 3.0);
            }
            case CHARGE -> {
                double radius = RamielConfig.blastRadius();
                // Rimulib builds the crater now, over the next ticks, and drops it in all at once at detonation
                cut = Destruction.craterCut(level, impact, radius, Math.min(40.0, radius * 0.4));
                if (cut != null) cut.prepare();
                Wards.warnBlast(level, impact, radius * 1.15, duration(Stage.CHARGE));
                sendZone();
                r.playLoud(ModRegistry.SND_CHARGE.get(), 40f, 0.5f);
            }
            case PAUSE -> r.playLoud(ModRegistry.SND_ALARM.get(), 60f, 0.7f);
            case FIRE -> detonate();
            case RECOVER -> { if (interrupted) r.fx(FxKind.POOF, r.centre(), 20, 0); }
            default -> {}
        }
    }

    @Override
    protected void onTick(Stage s, float p) {
        switch (s) {
            case MORPH -> {
                r.setDrill((float) (drillLength * (p * p * (3 - 2 * p))));
                r.setSpinSpeed(0.35f);
                if (tick == duration(Stage.MORPH) - 2) {         // touchdown
                    r.fx(FxKind.DRILL_SPARK, impact, 4, 0);
                    r.fx(FxKind.SHAKE, impact, 1.5, 40);
                    r.playLoud(ModRegistry.SND_BOOM.get(), 25f, 1.3f);
                    Destruction.scorchBurst(r.serverLevel(), impact, 4.5, Vec3.ZERO);
                }
            }
            case CHARGE, PAUSE -> {
                r.setDrill((float) drillLength);
                r.setSpinSpeed(0.35f + 4f * (s == Stage.CHARGE ? p : 1f));
                if (tick % 10 == 0) r.fx(FxKind.DRILL_SPARK, impact, 2.5, 0);
                if (tick % 20 == 0) warnPlayers(s, remainingTicks(s));
                if (tick % 100 == 0 && s == Stage.CHARGE) sendZone();
                if (tick % 60 == 0) r.fx(FxKind.SHAKE, impact, 0.4 + 0.8 * p, 30);
            }
            case REVERT -> r.setDrill((float) (drillLength * (1f - p)));
            default -> {}
        }
    }

    // -------------------------------------------------------------------------------------------
    /** Called when damage cancels the strike. */
    public void interrupt() {
        if (interrupted || detonated) return;
        interrupted = true;
        if (cut != null) { cut.cancel(); cut = null; }
        r.fx(FxKind.SHOCKWAVE, impact, 40, 30);
        r.fx(FxKind.IMPACT_FRAME, r.centre(), 3, 0);
        r.playLoud(net.minecraft.sounds.SoundEvents.ENDER_DRAGON_HURT, 40f, 0.5f);
        // tell the clients to drop the red zone
        r.fx(FxKind.WARN_ZONE, impact, RamielConfig.blastRadius(), 0, 0, r.getId(), 0f);
        jumpTo(Stage.RECOVER);
    }

    private int remainingTicks(Stage s) {
        int left = duration(s) - tick;
        if (s == Stage.CHARGE) left += duration(Stage.PAUSE);
        return left;
    }

    private void sendZone() {
        r.fx(FxKind.WARN_ZONE, impact, RamielConfig.blastRadius(), remainingTicks(Stage.CHARGE), 0, r.getId(), 0f);
    }

    /** Action-bar warning + alarm for everyone inside (or near) the blast zone. */
    private void warnPlayers(Stage s, int ticksLeft) {
        var level = r.serverLevel();
        double radius = RamielConfig.blastRadius();
        int seconds = Math.max(0, ticksLeft / 20);
        for (ServerPlayer p : level.players()) {
            double d = Math.hypot(p.getX() - impact.x, p.getZ() - impact.z);
            if (d <= radius * 1.3) {
                p.displayClientMessage(Component.translatable("ramiel.warn.blast", seconds, (int) radius), true);
                level.playSound(null, p.getX(), p.getY(), p.getZ(), ModRegistry.SND_ALARM.get(), SoundSource.HOSTILE, 2.5f, 1f);
            } else if (d <= radius * 4 && tick % 100 == 0) {
                p.displayClientMessage(Component.translatable("ramiel.warn.near", (int) (d - radius)), true);
            }
        }
    }

    private void detonate() {
        detonated = true;
        var level = r.serverLevel();
        double radius = RamielConfig.blastRadius();
        double destruction = RamielConfig.destruction();

        r.playLoud(ModRegistry.SND_BOOM.get(), 250f, 0.55f);
        dev.ramiel.world.Sfx.boom(level, impact, 3f);
        r.fx(FxKind.IMPACT_FRAME, impact, 8, 0);
        r.fx(FxKind.BLAST, impact, radius, 0);
        r.fx(FxKind.SHOCKWAVE, impact, radius * 1.9, 80);
        r.fx(FxKind.SHAKE, impact, 5.0, 90);
        r.fx(FxKind.WARN_ZONE, impact, radius, 0, 0, r.getId(), 0f);   // zone off
        r.fx(FxKind.SCAR, impact, radius * 1.05, 6000);

        // debris leaves first (taking blocks out of the ground), then the prepared crater lands in one tick
        Destruction.launchDebris(level, impact, radius * 0.8, Vec3.ZERO, 260);
        if (cut != null) {
            cut.tick();
        } else if (destruction > 0.001) {
            Destruction.crater(level, impact, radius, Math.min(40.0, radius * 0.4), Vec3.ZERO);
        }
        // a ring of smaller craters around the rim
        if (destruction > 0.001) {
            int ring = 7;
            for (int i = 0; i < ring; i++) {
                double a = (i + r.getRandom().nextDouble() * 0.6) / ring * Math.PI * 2.0;
                double dist = radius * (0.7 + r.getRandom().nextDouble() * 0.3);
                double x = impact.x + Math.cos(a) * dist, z = impact.z + Math.sin(a) * dist;
                int top = Destruction.columnTop(level, Mth.floor(x), Mth.floor(z));
                if (top == Destruction.UNLOADED) continue;
                Vec3 at = new Vec3(x, top, z);
                Destruction.crater(level, at, radius * 0.16, 7, new Vec3(Math.cos(a), 0.2, Math.sin(a)));
                r.fx(FxKind.SCAR, at, radius * 0.2, 3000);
            }
        }
        BeamCaster.damageRadius(level, r, impact, radius * 1.25, (float) (1000f * RamielConfig.damage()));
    }

    @Override protected void onFinish() { cut = null; }
}
