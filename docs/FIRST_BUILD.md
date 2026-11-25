# First build (Phase 4): what to do on your computer

You do not need to understand the errors. The goal is to get one short report to paste into the chat; the chat fixes the code.

## One-time setup
1. **Java 21.** Install a JDK 21 (for example Temurin 21). If you skip this, Gradle may download one for you.
2. **Unzip** `ramiel-mod-snapshot.zip` somewhere with no spaces in the path.
3. **Rimulib.** Download Rimulib **1.2.2 or newer** for **NeoForge 1.21.1** and save it as `libs/rimulib.jar`
   (see `libs/README.txt`). The 1.2.1 jar will not work: it has no API classes.
4. **Python 3** is only needed for the helper script below.

## Build and send me the result
Open a terminal in the project folder and run:

    python3 tools/collect_errors.py

It runs the build (the first run downloads Minecraft and NeoForge: several minutes), then writes `build-errors.txt`.
**Paste the contents of `build-errors.txt` into the chat.** Nothing else is needed.
No Python? Run `./gradlew compileJava` (Windows: `gradlew.bat compileJava`) and paste the whole output instead.

Repeat after each round of fixes until it says BUILD SUCCESSFUL.

## First run
    ./gradlew runClient        (Windows: gradlew.bat runClient)

Make a **creative** world with cheats on and shaders OFF. Try, in order:
1. The game starts and the Ramiel creative tab has 4 items.
2. `/ramiel summon_here`: Ramiel appears and spins.
3. Hit it, or stand close: it changes shape; the pieces should move (no fading) and snap back to the octahedron afterwards.
4. `/ramiel phase2`: the stun, then the star beam. You should be thrown back, and trees should fall.
5. Craft or `/give` the railcannon and plasma cells; hold right click to charge, release to fire.
6. Sneak + right click the Mini Ramiel: the settings screen.
(The full checklist is in HANDOFF.md, section 6.)

If the game crashes or looks wrong, run:

    python3 tools/collect_errors.py --game run/logs/latest.log

and paste `build-errors.txt`. If something just looks wrong (a black face, a floating item), describe it or send a screenshot.

When it all works, tell the chat "phase done" so the handoff and the project zip get updated.
