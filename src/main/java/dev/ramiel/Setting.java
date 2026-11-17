package dev.ramiel;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/**
 * One row of the settings screen. The screen, the network packet and the config all talk about
 * settings by their ORDINAL in this enum, so adding a setting means adding one line here.
 */
public enum Setting {
    DESTRUCTION("Destruction", "Scales every crater and terrain cut. 0 = Ramiel leaves the terrain alone.",
            0.0, 3.0, 0.05, RamielConfig::destruction, v -> RamielConfig.DESTRUCTION.set(v)),
    DEBRIS("Block physics", "How many blocks are thrown as debris. 0 turns it off.",
            0.0, 3.0, 0.05, RamielConfig::debris, v -> RamielConfig.DEBRIS.set(v)),
    BLAST_RADIUS("Impact radius", "Radius (blocks) of the big impact explosion.",
            24.0, 250.0, 1.0, RamielConfig::blastRadius, v -> RamielConfig.BLAST_RADIUS.set(v)),
    BLAST_SECONDS("Impact charge", "Seconds Ramiel charges the impact. You get a warning the whole time.",
            5.0, 120.0, 1.0, () -> RamielConfig.blastSeconds(), v -> RamielConfig.BLAST_SECONDS.set((int) Math.round(v))),
    DAMAGE("Damage", "Multiplier for everything Ramiel hurts.",
            0.0, 5.0, 0.05, RamielConfig::damage, v -> RamielConfig.DAMAGE.set(v)),
    SPEED("Move speed", "How fast Ramiel drifts between places.",
            0.25, 4.0, 0.05, RamielConfig::speed, v -> RamielConfig.SPEED.set(v)),
    AGGRESSION("Aggression", "Higher = shorter pauses between attacks.",
            0.25, 4.0, 0.05, RamielConfig::aggression, v -> RamielConfig.AGGRESSION.set(v)),
    HEALTH("Health", "Multiplier for Ramiel's health pool.",
            0.25, 5.0, 0.05, RamielConfig::health, v -> RamielConfig.HEALTH.set(v)),
    AGGRO_RADIUS("Notice range", "Distance at which Ramiel notices players.",
            32.0, 512.0, 4.0, RamielConfig::aggroRadius, v -> RamielConfig.AGGRO_RADIUS.set(v)),
    PHASE2_COOLDOWN("Star beam delay", "Seconds between final-phase star beams.",
            10.0, 180.0, 1.0, () -> RamielConfig.phase2Cooldown(), v -> RamielConfig.PHASE2_COOLDOWN.set((int) Math.round(v))),
    CUTSCENES("Cutscenes", "0 = off, 1 = arrival and phase changes, 2 = every transformation.",
            0.0, 2.0, 1.0, () -> RamielConfig.cutscenes(), v -> RamielConfig.CUTSCENES.set((int) Math.round(v))),
    IGNORE_PLAYERS("Ignore players", "Ramiel never picks a player as a target.",
            0.0, 1.0, 1.0, () -> RamielConfig.ignorePlayers() ? 1 : 0, v -> RamielConfig.IGNORE_PLAYERS.set(v >= 0.5)),
    MOBS_FLEE("Mobs flee", "Passive mobs run away from Ramiel.",
            0.0, 1.0, 1.0, () -> RamielConfig.mobsFlee() ? 1 : 0, v -> RamielConfig.MOBS_FLEE.set(v >= 0.5)),
    MOBS_ATTACK("Mobs attack", "Skeletons, pillagers, blazes... shoot at Ramiel.",
            0.0, 1.0, 1.0, () -> RamielConfig.mobsAttack() ? 1 : 0, v -> RamielConfig.MOBS_ATTACK.set(v >= 0.5)),
    INTERRUPT("Damage interrupts", "Enough damage during the impact charge cancels the explosion.",
            0.0, 1.0, 1.0, () -> RamielConfig.interrupt() ? 1 : 0, v -> RamielConfig.INTERRUPT.set(v >= 0.5)),
    FAR_RENDER("Horizon render", "Draw a simplified Ramiel for players too far away to load it.",
            0.0, 1.0, 1.0, () -> RamielConfig.farRender() ? 1 : 0, v -> RamielConfig.FAR_RENDER.set(v >= 0.5)),
    CHUNK_ANCHOR("Keep chunks loaded", "Keep chunks around Ramiel loaded so it works while nobody is near.",
            0.0, 1.0, 1.0, () -> RamielConfig.chunkAnchor() ? 1 : 0, v -> RamielConfig.CHUNK_ANCHOR.set(v >= 0.5));

    public final String label;
    public final String help;
    public final double min;
    public final double max;
    public final double step;
    private final DoubleSupplier getter;
    private final DoubleConsumer setter;

    Setting(String label, String help, double min, double max, double step, DoubleSupplier getter, DoubleConsumer setter) {
        this.label = label;
        this.help = help;
        this.min = min;
        this.max = max;
        this.step = step;
        this.getter = getter;
        this.setter = setter;
    }

    /** True for on/off rows (drawn as a button instead of a slider). */
    public boolean isToggle() { return min == 0.0 && max == 1.0 && step == 1.0; }

    public double get() { return getter.getAsDouble(); }

    public void set(double value) {
        double clamped = Math.max(min, Math.min(max, value));
        setter.accept(clamped);
    }

    public static double[] snapshot() {
        Setting[] all = values();
        double[] out = new double[all.length];
        for (int i = 0; i < all.length; i++) out[i] = all[i].get();
        return out;
    }

    public static void apply(double[] values) {
        Setting[] all = values();
        for (int i = 0; i < Math.min(all.length, values.length); i++) all[i].set(values[i]);
        RamielConfig.DESTRUCTION.save(); // saves the whole config file
    }
}
