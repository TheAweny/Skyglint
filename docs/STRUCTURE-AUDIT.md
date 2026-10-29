# Structure and extension guide

Historical audit baseline: Skyglint 0.2.2, 28 October 2026. Target: Minecraft Java
**26.2**, Fabric, JVM **25**. The findings and development sequence below describe
that baseline.

In 0.3.0, the searchable native settings workspace, inline Updates page and linked-row
HUD editor are implemented. The editor previews the real HUD, edits an unsaved layout
draft and persists only on Apply. Module/event/reward preferences use the shared
settings catalog. Independent placement of future standalone widgets remains future
work; the ordinary HUD is still one linked row.

## Data flow and ownership

```text
Minecraft/Fabric copied observations
                 |
        ModuleRegistry / SignalModule
                 |
             CoreEvent
                 |
        CoreController + lifecycle policies
                 |
             HudSnapshot
                 |
       composition -> layout -> native rendering
```

Network and file work stays outside the domain. External workers return copied
results to the client thread. A renderer never reads server messages, invents
activity or changes gameplay state.

| Package | Responsibility and extension point |
| --- | --- |
| `core` | Immutable observations/events/snapshots, registry, reducer and shared lifecycle policies. No dependency on another project layer, Minecraft/Fabric or I/O packages. |
| `features/<domain>` | Source-shaped parsers and independent `SignalModule` detectors. Keep lifecycle, source freshness and removal close to each mechanic. |
| `features/BuiltInModules` | Built-in feature registration. `ContextModules.create(clock)` creates the contextual detector set and supplies its IDs for bounded freshness revalidation. |
| `preview` | Typed `PreviewScenario`, `HudPreviewController` and synthetic timelines with separate controllers and real feature registration. Demonstrations never populate the live controller. |
| `fabric` | Platform bootstrap, packet/context adapters, copied inventory/menu observations, native artwork and client-thread delivery of worker results. |
| `fabric/commands`, `fabric/ClientSettings` | Command tree and the runtime binding for settings actions, separate from the client initializer. |
| `ui/presentation` | Selection, grouping, captions and geometry derived from a snapshot; no new gameplay facts. |
| `ui/layout` | Pure `HudRowLayout` placement and shared `HudBounds` for drawing/tooltip coordinates. No persisted free positioning yet. |
| `ui/motion`, `ui/effects`, `ui/text` | Interruptible motion, bounded framebuffer effects, and consistent text measurement/drawing. |
| `ui/settings` | `SettingsControls` catalog, `SettingsControl` descriptions and `SettingsActions`; preferences remain separate from screen geometry and game session state. |
| `config` | Versioned preferences, normalization, migrations and atomic persistence. Unknown future schemas preserve the existing file. |
| `updates/github` | Bounded release checks, manifest/hash/compatibility validation and staged downloads; no Minecraft dependencies. |
| `diagnostics` | Bounded asynchronous capture/export, with independent persisted raw/chat opt-ins and visible loss. |
| `api`, `ui/UiWidgetRegistry` | Companion module registration and optional custom native widget renderers. |
| `mixin` (Java) | Small bridges into native packet/render hooks; gameplay policy belongs elsewhere. |

Source roots are intentionally separate: `src/main` ships in the mod, `src/test`
contains JVM regressions, and `src/smoke` is an opt-in offline native test mod.
Preview code ships because users can explicitly open a synthetic preview; the
smoke test mod does not ship.

## Findings and changes

Priorities describe maintenance impact, not newly discovered live gameplay bugs.

| Priority | Baseline evidence | Resolution or remaining work |
| --- | --- | --- |
| P1 | Several `core/*PreviewTimeline` classes imported `features.ContextModules`; `ModuleRegistry.defaults()` referenced feature level-up parsers. This created a core ↔ features cycle despite the intended domain boundary. | Timelines moved to `preview`; shared level-up parsing belongs to core with compatibility wrappers in features. A source dependency guard now rejects the reverse dependency. |
| P1 | `CoreController` stored a contributor's `WidgetObserved` object with its metric list and boss-UUID sets, allowing caller-owned mutable collections to alter accepted state after ingestion. | `WidgetSnapshots` takes an owned copy of metrics and widget/metric UUID sets; accepted events use that copy. Top-level snapshot collection views are read-only. Mutation/change-detection/expiry regressions protect this boundary. Notice pages already had defensive copying. |
| P1 | `fabric/SkyglintClient` combined bootstrap, commands, ten preview timelines, preference actions, observation refresh and session state. New commands/demonstrations required editing the runtime orchestrator. | Commands and preview orchestration are extracted into their own platform concerns; built-in feature registration has one shared catalog. Observation/session orchestration remains a further extraction candidate. |
| P1 | `SkyglintHud` performed placement inside the drawing path; there was no separate row layout boundary for later transforms. | The initial row layout seam separates placement calculations while preserving the current row. Persisted per-component transforms and editor interactions are future work. |
| P2 | Native settings controls repeated preference labels/actions directly in the screen; a growing options list would increase screen-specific branching. | A settings catalog provides one place to describe current controls. A complete searchable/category-based settings menu still needs design and implementation. |
| P2 | Ordinary `check` did not compile the custom smoke source set. Production-JAR identity/bytecode/isolation evidence came from manual release verification or opt-in remote tests. | `check` now compiles smoke Java and verifies the locally built JAR, without starting Minecraft or accessing a player's profile. Native execution is still required for native changes. |
| P2 | Public `CONTRIBUTING.md` contained only brief rules; architecture/module guides existed only as ignored local documents. Parts of those guides still described implemented Slayer/Fishing work as unsupported. | The public contribution guide and this source map are self-contained. Local module/architecture descriptions are corrected without publishing private research or diagnostics. |
| P2 | Automatic update checks already had worker delivery, verified hashes and an explicit download action, but availability was delivered only through chat. | Update availability also uses a short native HUD notice, with release importance supplied by validated manifest metadata. No automatic download is introduced. |

The domain already had useful foundations: independent signal modules, session
hooks, TTL/removal, generic widget presentation, semantic metric groups, bounded
notice preemption, renderer fallback, separate diagnostic capture and verified
update staging. Those contracts are preserved and extended rather than replaced.

## Boundaries to preserve as the project grows

**Observed data and presentation are separate.** `ModuleWidget` is domain content;
`HudElement` is a selected presentation; layout determines its position; native
rendering draws it. A feature should not choose screen coordinates or read a saved
layout. A future editor should modify layout preferences, not detector state.

**Stable IDs have different jobs.** Module/widget/metric IDs identify source data;
presentation identity controls content transitions; a future layout ID identifies
an editable component or group. A rotating main slot must not acquire a new saved
position for every boss UUID or notice. Do not persist transient caption text,
monotonic deadlines or session-generated identities as layout keys.

**The default remains one compact row.** Current selection/priority/grouping rules,
100% scale, short notices, native icons and framebuffer blur remain the baseline.
Independent placement will be an explicit layout mode. It must not accidentally
hide an original bossbar whose replacement was clipped, disabled or unselected.

**Preferences are not live state.** Keep saved settings separate from source leases,
candidate votes, XP snapshots, notice queues and session state. Use one normalization
and migration path for screen, command and future import/export edits. Config writes
are currently atomic and synchronous; a high-frequency drag/slider editor will need
bounded/coalesced persistence before saving every interaction.

**Custom drawing is not an editor contract yet.** `UiWidgetRegistry` supports native
rendering with fallback and optional `WidgetWidthMeasure` (bounded to 30–320 design
units at the existing 30-unit height). It does not currently supply independent
selection handles, transforms or persistence. Those require an explicit component
input/layout contract, not more feature-specific branches in the renderer.

**Composition still contains feature-specific policy.** `HudComposition` recognizes
some built-in IDs and contexts (for example compact dungeon filtering and pet
suppression). Keep these rules visible and tested. When new independent components
need the same policy, replace repeated ID checks with explicit presentation metadata;
do not move them into parsers or create a second competing composition pipeline.

## Development sequence

1. **Expand observed event coverage.** Add source-backed detectors through the shared
   catalog, with unknown/duplicate/expiry/reset cases. Reuse notices and generic
   semantic helpers. Maintain an explicit coverage list; a prototype catalog or a
   generic bossbar is not proof that an encounter is implemented.
2. **Build complete native settings.** Extend the settings catalog into categories
   for behavior, capsules/elements, appearance, diagnostics and updates. Keep the
   requested restrained translucent material and interruptible animations. Provide
   clear defaults/reset and expose only options with implemented semantics.
3. **Introduce independent component layout.** Define stable editable components and
   groups, anchors, coordinate units, bounds and per-component scale. Version their
   saved layout separately from live data. Preserve the existing row as the default
   layout and test GUI scale/window changes before adding interaction.
4. **Add the editor.** Selection, dragging, resizing, snapping, safe-area clamping,
   preview, cancel/apply and reset should share the same measured layout/render path.
   Interaction must not trigger disk writes every frame or mutate the live gameplay
   snapshot. The exact editing controls remain a later product decision.

This iteration provides structure for that sequence. It does not implement the
future settings redesign, free component placement or a Photoshop/Figma-style editor.

## Verification

`./gradlew build` (`build.bat` on Windows) runs JVM tests, the production core source
dependency guard, smoke Java compilation and `verifyProductionJar`. The artifact
guard checks the mod identity/version, exact Minecraft target, JVM 25 class headers,
production Mixin resource and absence of smoke classes. These checks protect real
architecture/release boundaries; they do not establish native rendering correctness.

Native UI/API/Mixin work also needs the isolated graphical smoke client. See
[development commands](DEVELOPMENT.md) and [update behavior](UPDATES.md). Source-shaped
fixtures and synthetic world scenes do not establish complete current Hypixel
coverage; live-server limitations must be reported with the individual change.

Validation on 28 October 2026: `build.bat` passed (11 seconds), with **629 test cases:
625 passed, 4 skipped, 0 failures/errors**. The skips were two Windows symlink
privilege cases and two explicitly disabled live GitHub probes. `smoke-test.bat`
passed (1 minute 14 seconds) with the native runtime and contributor-fallback
markers. Inspected 854×480 captures confirm readable settings, all three chat
importance levels, an unclipped critical-update heading/version and the restart
state. No Hypixel connection, live release download or user Minecraft profile was
used. Full free-placement/editor behavior remains outside this iteration.
