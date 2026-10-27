# Fishing catch notices

`FishingEncounterModule(clock)` emits one four-second generic notice from an exact
server game-chat catch line in a fresh official fishing-capable island context.
The selected catalog covers 53 creatures: pinned rare, epic, legendary and mythic
catches. Common and uncommon catches are silent. It does not count kills, assume nearby
ownership, create rewards, infer HP/lifetime or keep an idle encounter dashboard.
Reindrake is intentionally left to the existing global summon occurrence module.
Only the immediately following chat line within 100 ms can consume a Double Hook
message; intervening lines lose that annotation conservatively. It is never an
inventory amount or a separate reward. Duplicate bursts coalesce for 500 ms;
later genuine announcements receive new notice IDs. Session or mode change clears
context, pending Double Hook and duplicate memory. Same-mode subarea changes do not.

Protocol provenance was checked in the local pinned upstream trees:

- [Skyblocker SeaCreature.java, ca1163e2](https://github.com/SkyblockerMod/Skyblocker/blob/ca1163e2b7309113cc4dd3c084829d4417606235/src/main/java/de/hysky/skyblocker/skyblock/fishing/SeaCreature.java): exact catch messages and species, used as server protocol facts for the selected mapping.
- [SkyHanni SeaCreatureManager.kt, 893cc826](https://github.com/SkyHanniStudios/SkyHanni/blob/893cc82630895a5e735ec6dd3f54986c1eb2a8b2/src/main/java/at/hannibal2/skyhanni/features/fishing/SeaCreatureManager.kt): exact Double Hook variants and short correlation window. This module uses a stricter next-line rule.
- [SkyHanni IslandType.kt, 893cc826](https://github.com/SkyHanniStudios/SkyHanni/blob/893cc82630895a5e735ec6dd3f54986c1eb2a8b2/src/main/java/at/hannibal2/skyhanni/data/IslandType.kt): official island mode identifiers.

Skyblocker LGPL-3.0 and SkyHanni LGPL-2.1 license files were reviewed. Skyglint's
state machine, notice policy and tests were independently written; no upstream
implementation, full resource catalog, graphics or runtime dependency was copied.
The protocol fixtures are source verified, not live Minecraft 26.2 captures.
Unknown/new message variants remain unknown. Golden Fish phases, entity/cast/reel
tracking and speculative respawn windows are outside this module's scope.
