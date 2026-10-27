# Fishing loot protocol evidence

Checked 2026-10-27. Implementation is original; no upstream implementation or art was copied.
The local research checkouts are pinned to SkyHanni
`893cc82630895a5e735ec6dd3f54986c1eb2a8b2` (LGPL-2.1) and Skyblocker
`ca1163e2b7309113cc4dd3c084829d4417606235` (LGPL-3.0). Only observed message
forms and their meaning were studied. No upstream runtime dependency is introduced.

## Pinned source fixtures

- [SkyHanni RareDropMessages lines 55–62](https://github.com/SkyHanniStudios/SkyHanni/blob/893cc82630895a5e735ec6dd3f54986c1eb2a8b2/src/main/java/at/hannibal2/skyhanni/features/chat/RareDropMessages.kt#L55):
  personal `U+E025 GREAT CATCH! You caught a [Lvl 1] Squid!`; the item-name
  color is distinct from the heading and level prefix.
- [SkyHanni AttributeShardsData lines 274–282](https://github.com/SkyHanniStudios/SkyHanni/blob/893cc82630895a5e735ec6dd3f54986c1eb2a8b2/src/main/java/at/hannibal2/skyhanni/features/inventory/attribute/AttributeShardsData.kt#L274):
  `U+E025 GOOD CATCH! You caught Water Snake Shard x3!` and singular article forms.
- [SkyHanni FishingProfitTracker lines 55–62](https://github.com/SkyHanniStudios/SkyHanni/blob/893cc82630895a5e735ec6dd3f54986c1eb2a8b2/src/main/java/at/hannibal2/skyhanni/features/fishing/tracker/FishingProfitTracker.kt#L55):
  `U+E025 GOOD CATCH! You caught 36,064 Coins!`.
- [SkyHanni ChatFilter lines 310–320](https://github.com/SkyHanniStudios/SkyHanni/blob/893cc82630895a5e735ec6dd3f54986c1eb2a8b2/src/main/java/at/hannibal2/skyhanni/features/chat/ChatFilter.kt#L310):
  historical `GOOD CATCH! You found a Fish Bait.` / other bait and bottle forms.
  These remain recognized for compatibility, but are not evidence that Fish Bait
  is currently catchable: the [Fish Bait history](https://hypixelskyblock.minecraft.wiki/w/Fish_Bait#History)
  records its removal from treasure in March 2025.
- [SkyHanni TrophyFishMessages lines 27–35](https://github.com/SkyHanniStudios/SkyHanni/blob/893cc82630895a5e735ec6dd3f54986c1eb2a8b2/src/main/java/at/hannibal2/skyhanni/features/fishing/trophy/TrophyFishMessages.kt#L27):
  `U+E02A TROPHY FISH! You caught Blobfish BRONZE!` and `Lavahorse SILVER!`.
  Trophy grade is separate from the fish's explicit item-name color.

## Junk extension and current wiki evidence

The official wiki address now redirects to Hypixel's
[July 2026 closure announcement](https://hypixel.net/threads/end-of-the-official-hypixel-wiki-july-2026.6112020/).
The successor community wiki's [Rusty Coin Game Message](https://hypixelskyblock.minecraft.wiki/w/Rusty_Coin#Game_Message)
records `⛃ GOOD JUNK CATCH! You caught a Rusty Coin!`. This is wiki evidence,
not a fixture present in either pinned mod checkout or a new live-server capture.
`⛃` and the pinned `U+E025` treasure icon are accepted. The same exact personal
article/item/exclamation grammar accepts ordinary named treasure items.

[Junk Sinker](https://hypixelskyblock.minecraft.wiki/w/Junk_Sinker#Junk),
[Busted Belt Buckle](https://hypixelskyblock.minecraft.wiki/w/Busted_Belt_Buckle),
[Old Leather Boot](https://hypixelskyblock.minecraft.wiki/w/Old_Leather_Boot), and
[Moby-Duck](https://hypixelskyblock.minecraft.wiki/w/Moby-Duck) distinguish the
JUNK catch category and GOOD/GREAT/OUTSTANDING quality from item rarity. For
example Rusty Coin is UNCOMMON, Busted Belt Buckle RARE, Old Leather Boot EPIC,
and Moby-Duck RARE. The parser does not hardcode these rarity mappings or prices;
only an explicit color on the item-name span sets rarity. Uncolored messages keep
rarity unknown. Neither JUNK nor catch quality implies an item is cheap.

## Deliberate limits

- Only exact personal system-chat messages are accepted. Player chat, spawn lines,
  actionbar, inventory snapshots and unprefixed `You caught a Raw Cod!` are not
  item reward evidence. The existing RareDropSignalModule is the sole route.
- One item is observed only for singular article/found/pet/trophy forms; shard
  quantity and coins are read from their own explicit fields. No collection,
  sack, inventory or money total is converted into loot. Values are positive
  and bounded (one million shards; one trillion coins); malformed quantities
  cannot fall back to becoming part of an item name.
- All fishing observations pass through RewardObserved and RewardRulePolicy.
  ALL remains the default, including Junk; exact item overrides still apply.
- Catch parsing is stateless. It never merges two actual catch packets.
  Presentation queue capacity remains a separate policy, not a lossless catch ledger.
- There is no source-proven general chat notification for each ordinary fish or
  materialized sinker item. Upstream profit tracking correlates other catch and
  ItemAdd inputs; a current inventory increase alone is insufficient. Such catches
  remain unknown here, pending an authoritative catch/item adapter and live fixtures.
- These source-shaped regression fixtures do not claim a live Hypixel fishing run.
