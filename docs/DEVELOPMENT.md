# Skyglint development

Minecraft Java **26.2**, Fabric Loader **0.19.3+**, JVM **25**.
Dependency versions are pinned in `build.gradle.kts`.

```sh
./gradlew build
./gradlew packageRelease
```

Windows: `build.bat` uses `scripts/gradle.ps1` and an isolated local JDK25 if present.
Build outputs are in `build/libs` and `build/distributions`.

Kotlin `core` is a copied-observation domain with no Minecraft classes or blocking I/O.
`features` contains independent `SignalModule` detectors; `fabric` supplies native adapters.
Java Mixins bridge boss packets and native render hooks. The HUD is native Minecraft,
with a single ordinary row, native item/head artwork and framebuffer blur.

Source packages are under `dev.skyglint`; assets use `assets/skyglint`. The Fabric mod ID
remains `reborn` so existing installations resolve as the same mod. New runtime data
uses `config/skyglint`: preferences in `config.json`, diagnostics in `logs` and `reports`,
update staging in `downloads`, and retired JARs in `backups/mods`.

Tests use synthetic source-shaped fixtures. They do not establish current live-server
coverage. New parsers need source provenance, bounds, reset/expiry policy and meaningful
regressions. Never turn missing data into zero, snapshots into gains or inferred kills.

Native checks use separate directories and no Hypixel account:

```sh
./gradlew build runSmokeClient -Psmoke
./gradlew build runSmokeClient -Psmoke -PfishingSmoke
./gradlew runInstalledUpdateSmoke -Psmoke
```

Screenshots are written under `run-smoke/screenshots`; installed-update fixtures live
under `run-update-smoke`. These directories, private logs and research notebooks are
excluded from source publication. Preserve LGPL and all third-party notices in releases.

For a release, set `mod_version`, run the checks, generate the update manifest using
`scripts/release-manifest.ps1`, and publish the JAR, manifest and optional bundle under
a stable `vMAJOR.MINOR.PATCH` GitHub release. See [updates](UPDATES.md).
