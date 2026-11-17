package dev.ramiel;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * All tunable numbers live here as a normal NeoForge config (config/ramiel-common.toml).
 * The in-game settings screen edits these same values, so the file and the screen never disagree.
 *
 * Read them through the small static getters at the bottom; they fall back to the default if the
 * config has not finished loading yet.
 */
public final class RamielConfig {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue DESTRUCTION = B
            .comment("Scales every crater and terrain cut. 0 = Ramiel never changes the terrain.")
            .defineInRange("destruction", 1.0, 0.0, 3.0);
    public static final ModConfigSpec.DoubleValue DEBRIS = B
            .comment("How many blocks get launched as physical debris. 0 = none.")
            .defineInRange("debris", 1.0, 0.0, 3.0);
    public static final ModConfigSpec.DoubleValue BLAST_RADIUS = B
            .comment("Radius in blocks of the big impact explosion.")
            .defineInRange("blastRadius", 96.0, 24.0, 250.0);
    public static final ModConfigSpec.IntValue BLAST_SECONDS = B
            .comment("Seconds Ramiel charges the impact explosion (players get a warning during this time).")
            .defineInRange("blastChargeSeconds", 25, 5, 120);
    public static final ModConfigSpec.DoubleValue DAMAGE = B
            .comment("Multiplier for all damage Ramiel deals.")
            .defineInRange("damage", 1.0, 0.0, 5.0);
    public static final ModConfigSpec.DoubleValue SPEED = B
            .comment("Multiplier for how fast Ramiel drifts around.")
            .defineInRange("speed", 1.0, 0.25, 4.0);
    public static final ModConfigSpec.DoubleValue AGGRESSION = B
            .comment("Higher = shorter pauses between attacks.")
            .defineInRange("aggression", 1.0, 0.25, 4.0);
    public static final ModConfigSpec.DoubleValue HEALTH = B
            .comment("Multiplier for Ramiel's health pool.")
            .defineInRange("health", 1.0, 0.25, 5.0);
    public static final ModConfigSpec.DoubleValue AGGRO_RADIUS = B
            .comment("Horizontal distance at which Ramiel notices players.")
            .defineInRange("aggroRadius", 192.0, 32.0, 512.0);
    public static final ModConfigSpec.IntValue PHASE2_COOLDOWN = B
            .comment("Seconds between star beams in the final phase.")
            .defineInRange("phase2CooldownSeconds", 45, 10, 180);
    public static final ModConfigSpec.BooleanValue IGNORE_PLAYERS = B
            .comment("Ramiel never targets players (it may still hit them if they stand in the way).")
            .define("ignorePlayers", false);
    public static final ModConfigSpec.BooleanValue MOBS_FLEE = B
            .comment("Passive mobs run away from Ramiel.")
            .define("mobsFlee", true);
    public static final ModConfigSpec.BooleanValue MOBS_ATTACK = B
            .comment("Skeletons, pillagers, blazes and other ranged mobs shoot at Ramiel.")
            .define("mobsAttack", true);
    public static final ModConfigSpec.BooleanValue INTERRUPT = B
            .comment("Enough damage while Ramiel charges the impact explosion cancels it.")
            .define("damageInterruptsImpact", true);
    public static final ModConfigSpec.IntValue CUTSCENES = B
            .comment("0 = no cutscenes, 1 = arrival and phase changes only, 2 = every transformation too.")
            .defineInRange("cutscenes", 2, 0, 2);
    public static final ModConfigSpec.BooleanValue FAR_RENDER = B
            .comment("Draw a simplified Ramiel on the horizon for players who are too far away to load it.")
            .define("farRender", true);
    public static final ModConfigSpec.IntValue FAR_RANGE = B
            .comment("How far away (blocks) the horizon version is still sent to players.")
            .defineInRange("farRenderRange", 3000, 512, 8000);
    public static final ModConfigSpec.BooleanValue CHUNK_ANCHOR = B
            .comment("Keep the chunks around Ramiel loaded so it keeps working when nobody is near.")
            .define("chunkAnchor", true);
    public static final ModConfigSpec.DoubleValue HOVER = B
            .comment("How many blocks above the ground the centre of Ramiel hovers.")
            .defineInRange("hoverHeight", 84.0, 40.0, 200.0);

    public static final ModConfigSpec SPEC = B.build();

    private RamielConfig() {}

    // ---- safe getters ----------------------------------------------------------------------
    public static double destruction() { return d(DESTRUCTION); }
    public static double debris() { return d(DEBRIS); }
    public static double blastRadius() { return d(BLAST_RADIUS); }
    public static int blastSeconds() { return i(BLAST_SECONDS); }
    public static double damage() { return d(DAMAGE); }
    public static double speed() { return d(SPEED); }
    public static double aggression() { return d(AGGRESSION); }
    public static double health() { return d(HEALTH); }
    public static double aggroRadius() { return d(AGGRO_RADIUS); }
    public static int phase2Cooldown() { return i(PHASE2_COOLDOWN); }
    public static boolean ignorePlayers() { return b(IGNORE_PLAYERS); }
    public static boolean mobsFlee() { return b(MOBS_FLEE); }
    public static boolean mobsAttack() { return b(MOBS_ATTACK); }
    public static boolean interrupt() { return b(INTERRUPT); }
    public static int cutscenes() { return i(CUTSCENES); }
    public static boolean farRender() { return b(FAR_RENDER); }
    public static int farRange() { return i(FAR_RANGE); }
    public static boolean chunkAnchor() { return b(CHUNK_ANCHOR); }
    public static double hover() { return d(HOVER); }

    private static double d(ModConfigSpec.DoubleValue v) {
        try { return v.get(); } catch (IllegalStateException notLoaded) { return v.getDefault(); }
    }
    private static int i(ModConfigSpec.IntValue v) {
        try { return v.get(); } catch (IllegalStateException notLoaded) { return v.getDefault(); }
    }
    private static boolean b(ModConfigSpec.BooleanValue v) {
        try { return v.get(); } catch (IllegalStateException notLoaded) { return v.getDefault(); }
    }
}
