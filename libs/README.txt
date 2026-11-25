Put the Rimulib jar here, named exactly:   libs/rimulib.jar

  - It must be Rimulib 1.2.2 or newer, the NeoForge 1.21.1 build (the 1.2.1 jar has no API classes and will not compile).
  - It is used to compile (build.gradle: implementation files('libs/rimulib.jar')) and is loaded as a mod in ./gradlew runClient.
  - Players need the same Rimulib in their mods folder; the mod declares it as a required dependency.
