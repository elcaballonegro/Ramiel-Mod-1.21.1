package dev.ramiel.entity.attack;

import dev.ramiel.Form;
import dev.ramiel.entity.RamielEntity;
import dev.ramiel.net.FilmKind;
import dev.ramiel.net.FxKind;
import net.minecraft.sounds.SoundEvents;

/**
 * Happens once, at two thirds health (frames 646-648). Ramiel spikes outward into a dark urchin,
 * cannot act or shield for a whole minute, and has its core exposed the entire time. Then it
 * reforms into the star and the final phase begins.
 */
public class StunAttack extends Attack {
    public StunAttack(RamielEntity r) { super(r); }

    @Override public Form form() { return Form.URCHIN; }
    @Override protected Form afterForm() { return Form.STAR; }
    @Override protected int filmKind() { return FilmKind.STUN; }
    @Override protected boolean aims(Stage s) { return false; }
    @Override protected boolean exposesCore(Stage s) { return s == Stage.RECOVER; }

    @Override
    protected int duration(Stage s) {
        return switch (s) {
            case MORPH -> 50;       // the spikes burst out
            case CHARGE, PAUSE, FIRE -> 0;
            case RECOVER -> 1200;   // one minute helpless
            case REVERT -> 60;      // folds into the star
        };
    }

    @Override
    protected void onEnter(Stage s) {
        if (s == Stage.MORPH) {
            r.setSpinSpeed(0.25f);
            r.fx(FxKind.IMPACT_FRAME, r.centre(), 5, 0);
            r.fx(FxKind.SHAKE, r.centre(), 3.0, 60);
            r.fx(FxKind.SHOCKWAVE, r.centre(), 90, 40);
            dev.ramiel.world.Shockwave.launch(r.serverLevel(), new net.minecraft.world.phys.Vec3(r.getX(), r.groundY(), r.getZ()), 90, 40, 0.9, false, r);
            r.playLoud(SoundEvents.ENDER_DRAGON_GROWL, 40f, 0.5f);
        }
        if (s == Stage.RECOVER) r.setSpinSpeed(0.12f);
    }

    @Override
    protected void onTick(Stage s, float p) {
        // a pulse of sparks every few seconds shows it is helpless, not gone
        if (s == Stage.RECOVER && tick % 80 == 0) r.fx(FxKind.POOF, r.centre(), 8, 0);
    }
}
