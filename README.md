# Skyglint

A compact, adaptive HUD for **Hypixel SkyBlock**, built for **Minecraft Java 26.2 · Fabric · Java 25**.

Skyglint brings useful information into one native, animated row: the current activity,
small contextual helpers, and short notices when something happens.

- Skill and SkyBlock progression, active pets, and a brief welcome carousel.
- Mining events with smooth local countdowns, drill warnings, and Nucleus confirmations.
- Garden/Jacob helpers, compact dungeon information, Slayer phases, and election candidates.
- Fishing bait previews, rare sea-creature notices, and recognized loot, including Junk.
- Native item/head icons, background blur, configurable scale, and per-item reward rules.

Coverage depends on the server information Skyglint actually observes. It does not yet
handle every SkyBlock event.

Version 0.3.0 also adds Booster Cookie / God Potion warnings
at observed **30 / 10 / 1 minutes**, plus separate confirmed-inactivity notices.
**Modules → Cookie & God Potion** controls these independently;
**Modules → Jerry's Workshop** controls defense, results and Reindrake presentation.
Jerry Defense shows observed waves/cubes and colored point indicators in A→E display order.
The captured wave-win chat block produces a one-capsule claimable loot carousel with item filters and
brief record/rank, damage and accuracy pages. **Available** rewards still require
collection at the gift piles. Reindrake numeric variants still need server verification;
unknown bars keep their original text, UUID and packet fill. Isolated previews are
`/skyglint preview winter`, `/skyglint preview reindrake`, `/skyglint preview buffs`,
`/skyglint preview buffwarning`, `/skyglint preview godwarning`, `/skyglint preview defenseresults`.

## Install

Download **`skyglint-0.3.0.jar`** from [GitHub Releases](https://github.com/TheAweny/Skyglint/releases/latest)
and put it in your Minecraft profile's `mods` folder together with:

- [Fabric API](https://modrinth.com/mod/fabric-api), for **26.2**; tested with `0.161.0+26.2`.
- [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin), **`1.13.13+kotlin.2.4.10` or newer**.

Use **Fabric Loader 0.19.3 or newer** and **Java 25**. The official Hypixel Mod API is included.
The optional `Skyglint-0.3.0-Minecraft-26.2.zip` contains the three required mod JARs;
Fabric Loader still needs to be installed. The `-sources.jar` is for development.

## Use

Join SkyBlock, then run **`/skyglint`** to open settings. `/skyglint update` opens its Updates page.
The old `/reborn` command remains an alias for existing users.

Version 0.3.0 adds a native settings workspace for modules, events, appearance and diagnostics and
includes search, native item icons, smooth Inter text, and animated page transitions.
Updates shares the same sidebar and content area; opening it from settings preserves
the current workspace. **HUD layout → Open HUD editor** previews the actual HUD: drag the
linked row, use the scale slider, scroll over the HUD or enter an exact percentage.
Apply saves position/scale; Cancel or Esc discards the draft. **100% remains 45 physical
pixels** for the ordinary row, independent of Minecraft GUI scale. Future independent
widgets can keep their own placement; current elements remain one linked row.

When the private-island HUD has no objective or activity helpers, SkyBlock expands
to its observed level and XP contour alongside the pet. Fishing bait always shows
its server-reported remaining quantity, including the short name introduction.

Module/event settings control presentation while observations continue updating.
Reward item rules are editable under **Modules → Rewards → Item rules**. Important
and Critical releases have distinct cards and announce once per version per game launch;
downloading always requires your action.

Preferences live in `config/skyglint/config.json`. On first launch, existing
`config/reborn/config.json` preferences are copied if the new file is absent.
The original configuration, logs, and backups are preserved.

## Updates

Skyglint checks GitHub for a newer stable release on the first SkyBlock entry of each launch.
A small chat link opens the update screen. Downloading requires your click; the checked
JAR becomes active after a **full Minecraft restart**. No separate updater mod is needed.
Automatic checks can be disabled in settings. [Update details](docs/UPDATES.md)

## Development

Source is Kotlin with small Java Mixin bridges. See [development notes](docs/DEVELOPMENT.md)
and [contributing](CONTRIBUTING.md) for build and verification instructions.

Licensed under **LGPL-3.0-only**; see [LICENSE](LICENSE), [COPYING](COPYING), and
[third-party notices](THIRD_PARTY_NOTICES.md).
