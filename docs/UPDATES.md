# Updates

Skyglint 0.2.0 introduces GitHub release checks and optional in-game downloads.
Updates come only from [TheAweny/Skyglint](https://github.com/TheAweny/Skyglint/releases).

## In game

On the first confirmed SkyBlock entry of each Minecraft launch, Skyglint checks the latest
stable release. A newer compatible version produces one chat link. There is no automatic
download or popup. Disable automatic checks in the update screen if preferred.

Open `/skyglint update` to check the result, download the offered version, or visit GitHub.
`/skyglint update check` requests a check; manual checks have a ten-minute cooldown.
`/skyglint update download` downloads the previously checked offer.

Downloads run on a worker, first into `config/skyglint/downloads`. Size, SHA-256,
release metadata, mod identity, Minecraft target, Java, and Fabric Loader requirements
are checked before the completed JAR is published into `mods`. The running JAR is not
patched. A **full Minecraft restart** is required; reconnecting to SkyBlock is insufficient.

If the installation layout, permissions, filesystem, network, or release metadata prevent
an in-game update, use the GitHub Releases button to install the JAR manually.
Updates target Minecraft **26.2** only. Fabric Loader and other mods are not updated.
The development client does not perform automatic release downloads.

## Older installations

After a successfully loaded packaged version starts, a separate startup pass examines loose
JARs directly inside `mods`. It archives every verified, strictly older compatible version
of this mod into `config/skyglint/backups/mods`. This includes older Reborn releases;
the internal Fabric mod ID remains `reborn` for continuity.

Comparison uses the JAR's mod metadata and semantic version, not its filename or an
immediately previous version list. Loaded origins, equal/newer versions, other mods,
incompatible Minecraft targets, nested files, existing backups, and configurations are
preserved. This is startup cleanup, not hot replacement of the running mod.

On first launch, `config/reborn/config.json` is copied to `config/skyglint/config.json`
only if the destination does not exist. No legacy logs or backups are moved, and the
original configuration remains available.

## Publishing a release

1. Set the stable `mod_version` in `gradle.properties`.
2. Run the build, parser/reducer tests, native smoke checks, and installed-update smoke
   described in [development notes](DEVELOPMENT.md).
3. Run `./gradlew packageRelease`, then `scripts/release-manifest.ps1` using PowerShell.
4. Publish the JAR, `update-manifest.json`, and `SHA256SUMS.txt` together under a stable
   `vMAJOR.MINOR.PATCH` release. The source JAR and dependency bundle are optional assets.

The manifest uses schema `1` and records `version`, `minecraft`, `java`, `fabricLoader`,
`file`, `sha256`, and `size`. The updater requires the exact
`skyglint-MAJOR.MINOR.PATCH.jar` asset named by the manifest. Drafts and prereleases are
not offered by the stable updater.
