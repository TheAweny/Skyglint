# Third-party components

## Inter typeface

Inter Medium 4.1 by the Inter Project Authors comes from the official
[Inter v4.1 release](https://github.com/rsms/inter/releases/tag/v4.1).
The font is unmodified and distributed under the SIL Open Font License 1.1.
Complete copyright and license text is included at
`src/main/resources/assets/skyglint/font/ofl.txt`, and at `assets/skyglint/font/ofl.txt`
inside the mod JAR. Resource packs can override the `skyglint:ui` font.

## Hypixel Mod API

The official Fabric Mod API `1.0.2+build.1+mc26.1` is nested in the mod JAR and includes
the official core Mod API 1.0.2. Both retain their MIT notices inside the original JARs.
The Fabric artifact is [published for Minecraft 26.2](https://modrinth.com/mod/hypixel-mod-api/version/SlqCF6Or).
Audited source commits:

- [HypixelDev/ModAPI `5245ef1f161883ae9152d719de29885bb09e3f57`](https://github.com/HypixelDev/ModAPI/tree/5245ef1f161883ae9152d719de29885bb09e3f57).
- [HypixelDev/FabricModAPI `86489417a05371eb19908d8d9dcc8a9381d15ee3`](https://github.com/HypixelDev/FabricModAPI/tree/86489417a05371eb19908d8d9dcc8a9381d15ee3).

## Runtime mods in the optional release ZIP

[Fabric API](https://github.com/FabricMC/fabric) `0.161.0+26.2` and
[Fabric Language Kotlin](https://github.com/FabricMC/fabric-language-kotlin)
`1.13.13+kotlin.2.4.10` are redistributed as separate, original JARs, including their
original notices and nested libraries. Their code is not copied into Skyglint sources.

## Hub election anchor data

Eight coordinate/name anchors derive from the MIT-licensed
[SkyHanni-REPO data](https://github.com/SkyHanniStudios/SkyHanni-REPO/blob/46b871288aed9860c38c3f51b5e00b1a1ffa63a2/constants/island_graphs/HUB.json),
commit `46b871288aed9860c38c3f51b5e00b1a1ffa63a2`. The complete copyright and MIT license
are included at `assets/skyglint/data/hub-election-regions-license.txt` in the JAR.
The associated bounded classifier is not used to override server-reported locations.

## Pet and election head metadata

73 default pet names and 13 election candidate names with Mojang texture hashes derive
from the MIT-licensed
[NotEnoughUpdates-REPO](https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO/tree/f19539bbd6c7cb017c5976505436d55c697e3938),
commit `f19539bbd6c7cb017c5976505436d55c697e3938`. No NEU implementation or PNGs are bundled.
Per-entry provenance and complete copyright/license texts are included in these JAR resources:

- `assets/skyglint/data/pet-heads.json` and `pet-heads-license.txt`.
- `assets/skyglint/data/election-heads.json` and `election-heads-license.txt`.

## Research references

[SkyHanni](https://github.com/SkyHanniStudios/SkyHanni/tree/893cc82630895a5e735ec6dd3f54986c1eb2a8b2)
and [Skyblocker](https://github.com/SkyblockerMod/Skyblocker/tree/ca1163e2b7309113cc4dd3c084829d4417606235)
were studied as implementation references. Their mod implementation and assets are not
bundled with Skyglint. Data derived from separate repositories is identified above.
