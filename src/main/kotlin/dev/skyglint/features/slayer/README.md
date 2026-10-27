# Slayer text package

This pure Kotlin package observes the complete adjacent `Slayer Quest` sidebar block:
quest name/tier, bounded Combat XP or Kills progress, and `Slay the boss!` / `Boss slain!`.
An initial active quest is an observed quest state, not a manufactured skill gain.
The PRIMARY widget has a five-second source lease, refreshed by the real copied block.
It never claims a boss UUID, HP, ownership, spawn timestamp, drop, or mechanic phase.

Exact system-chat start, slain, quest-complete, failed and cancelled lines supply lifecycle
evidence. Outcomes require a same-session quest association whose valid source block was
observed less than five seconds ago. Hidden phase memory lasts at most sixty seconds
without valid block observations and never supplies a stale outcome caption. The first terminal
sidebar alone is not a new success. Completion, failure and slain coalesce to one short
outcome per association; cancellation removes quietly. Missing lines, expiry and world/
profile/session changes remove presentation without an outcome. Same-quest grinding
cannot roll an observed fight back. A fresh exact start re-arms a repeated quest; while
it is pending, an old fight cannot bind the new association. All time bounds here are
defensive freshness/replay policy, not asserted server durations.

All five ordinary Slayer families and Rift Bloodfiend are accepted, including the
source-observed Atoned Horror, Conjoined Brood and Riftstalker Bloodfiend aliases.
Tier availability is bounded per family. Progress is copied from server text; `k` remains
server-rounded in its caption. No XP is summed, no future kills are estimated, and no
skill/SkyBlock/pet XP is modified. Rift and ordinary quest state cannot cross sessions/
modes. No entity, world, menu or inventory scan is used.

Evidence reviewed at pinned upstream revisions (server strings/facts, independently
implemented parser and reducer; no upstream implementation or resources copied):

- [SkyHanni SlayerApi 893cc826](https://github.com/SkyHanniStudios/SkyHanni/blob/893cc82630895a5e735ec6dd3f54986c1eb2a8b2/src/main/java/at/hannibal2/skyhanni/data/SlayerApi.kt#L50-L83): exact start/complete/failed text; sidebar block/stages at L239–389. Its synthesized intermediate transitions are deliberately not reproduced.
- [SkyHanni SlayerType](https://github.com/SkyHanniStudios/SkyHanni/blob/893cc82630895a5e735ec6dd3f54986c1eb2a8b2/src/main/java/at/hannibal2/skyhanni/features/slayer/SlayerType.kt#L9-L81): six families and aliases.
- [SkyHanni SlayerBossSpawnSoon](https://github.com/SkyHanniStudios/SkyHanni/blob/893cc82630895a5e735ec6dd3f54986c1eb2a8b2/src/main/java/at/hannibal2/skyhanni/features/slayer/SlayerBossSpawnSoon.kt#L19-L29): observed `38/40 Kills` and `(0/4.8k) Combat XP` forms.
- [Skyblocker SlayerManager ca1163e2](https://github.com/SkyblockerMod/Skyblocker/blob/ca1163e2b7309113cc4dd3c084829d4417606235/src/main/java/de/hysky/skyblocker/skyblock/slayers/SlayerManager.java#L111-L139): explicit slain/cancelled chat forms and family names at L72.

SkyHanni's LGPL-2.1 and Skyblocker's LGPL-3.0 source licenses were reviewed. This package
uses factual protocol observations only. Fixtures are synthetic examples of those observed
forms, not a live Hypixel validation. Entity mechanics, HP, RNG meter, Slayer levels,
minibosses and timing estimates remain outside this package.
