# Contributing

Skyglint targets **Minecraft Java 26.2, Fabric and JVM 25**. The HUD and settings are
native Minecraft interfaces, implemented in Kotlin with small Java Mixin bridges.
Start with the [source map and structural audit](docs/STRUCTURE-AUDIT.md), then the
[build instructions](docs/DEVELOPMENT.md).

## Choose the layer

| Change | Starting point |
| --- | --- |
| New observed mechanic or event | `features/<domain>/`: a `SignalModule`, its parser and source-shaped tests |
| Shared immutable observation or lifecycle policy | `core/`, with no platform or I/O dependencies |
| Minecraft packet, menu, inventory or texture input | `fabric/`: copy game objects into immutable observations |
| Existing widget content or grouping | `ModuleWidget`, `WidgetPresentation` and `WidgetMetric`; reuse generic presentation |
| New presentation form | `ui/presentation/` and native renderer; custom widget renderers register in `UiWidgetRegistry` |
| Persistent preference | `config/`: normalize and round-trip it before exposing a settings control |
| Synthetic demonstration | `preview/`, isolated from the live controller |
| Release check or verified download | `updates/github/`; platform delivery stays in `fabric/` |

## Add a detector

1. Record the exact server input and its source/commit or an anonymized capture. Keep
   parser fixtures small; never commit real chat, private diagnostics or account data.
2. Implement `SignalModule` in the relevant feature package. Return no events for
   unrelated input. Use the injected monotonic clock for expiry; do not read the game,
   network, disk or GUI from a parser.
3. Register a contextual detector in `ContextModules.create(clock)`. Shared built-in
   feature wiring belongs in `BuiltInModules`; contributors should not add feature
   branches to the client initializer. External companion mods use the
   `skyglint:modules` entrypoint and `SkyglintModule.register(ModuleRegistry)`.
4. Prefer `WidgetObserved(ModuleWidget(...))` and stable widget/metric IDs for existing
   presentation forms. Related additive metrics use `WidgetMetric.group`; unrelated
   counters should remain separate. Only introduce a new core event/state when the
   existing contract cannot express the observation.
5. Define source freshness, explicit removal and session reset. Clear detector state
   through `onSessionStarted`/`onSessionEnded`. Do not replay cached actionbars into
   XP or delta detectors to refresh activity.
6. Add regressions for the recognized input, unrelated/malformed input, repeated
   observations, unknown values, expiry and session transitions as relevant.

Unknown is `null`, not zero. Absolute snapshots never establish gains, kills or
level-ups. Keep skill, SkyBlock and pet XP independent. Player chat is diagnostic
input only. Boss replacements claim only the UUIDs actually represented; preserve
every other bossbar. The default HUD remains one ordinary row at 100% of its agreed
size, with short notices and additive helpers.

## Verify the change

Run `./gradlew build` on Java 25 (`build.bat` on Windows). The ordinary build runs
JUnit regressions, a source dependency guard for production core, production-JAR
metadata/bytecode/isolation checks, and compiles the isolated smoke fixtures.

Native UI, Mixins or Minecraft API changes also require `smoke-test.bat` or
`./gradlew build runSmokeClient -Psmoke`. Use the relevant isolated world scene when
changing gameplay composition, item artwork or animation. Commands and limitations
are in [development notes](docs/DEVELOPMENT.md). Compile checks do not establish that
the graphical client or a live Hypixel format works.

Describe the player-visible result, architectural boundary affected, checks run and
remaining live-server limitations in the pull request. Keep dependency versions
pinned. Contributions use LGPL-3.0-only; preserve source/asset provenance and
third-party notices.
