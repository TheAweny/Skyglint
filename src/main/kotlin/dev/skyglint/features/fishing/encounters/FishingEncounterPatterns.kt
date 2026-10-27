package dev.skyglint.features.fishing.encounters

import dev.skyglint.core.SkyBlockParser

/** Selected server protocol facts, independently mapped; see this package's provenance notes. */
object FishingEncounterPatterns {
    data class Catch(val id: String, val name: String)

    private val catches = listOf(
        Triple("catfish", "Catfish", "Huh? A Catfish!"),
        Triple("sea_leech", "Sea Leech", "Gross! A Sea Leech!"),
        Triple("guardian_defender", "Guardian Defender", "You've discovered a Guardian Defender of the sea."),
        Triple("deep_sea_protector", "Deep Sea Protector", "You have awoken the Deep Sea Protector, prepare for a battle!"),
        Triple("banshee", "Banshee", "The desolate wail of a Banshee breaks the silence."),
        Triple("bayou_sludge", "Bayou Sludge", "A swampy mass of slime emerges, the Bayou Sludge!"),
        Triple("gorf", "gorF", "What even is that?! A... gorF?"),
        Triple("drowned_captain", "Drowned Captain", "A Drowned Captain takes hold of your bobber!"),
        Triple("tadgang", "Tadgang", "A gang of Liltads!"),
        Triple("ent", "Ent", "You've hooked an Ent, as ancient as the forest itself."),
        Triple("stridersurfer", "Stridersurfer", "You caught a Stridersurfer."),
        Triple("sprawl", "Sprawl", "A Sprawl emerges from the blue, and it's looking for you!"),
        Triple("torrid", "Torrid", "The laughter of a Torrid echoes through the air."),
        Triple("fireproof_witch", "Fireproof Witch", "Trouble's brewing, it's a Fireproof Witch!"),
        Triple("lava_leech", "Lava Leech", "A small but fearsome Lava Leech emerges."),
        Triple("pyroclastic_worm", "Pyroclastic Worm", "You feel the heat radiating as a Pyroclastic Worm surfaces."),
        Triple("magma_pillar", "Magma Pillar", "A Magma Pillar rises from the lava."),
        Triple("lava_flame", "Lava Flame", "A Lava Flame flies out from beneath the lava."),
        Triple("fire_eel", "Fire Eel", "A Fire Eel slithers out from the depths."),
        Triple("taurus", "Taurus", "Taurus and his steed emerge."),
        Triple("nightmare", "Nightmare", "You hear trotting from beneath the waves, you caught a Nightmare."),
        Triple("werewolf", "Werewolf", "It must be a full moon, a Werewolf appears."),
        Triple("tiger_shark", "Tiger Shark", "A striped beast bounds from the depths, the wild Tiger Shark!"),
        Triple("snapping_turtle", "Snapping Turtle", "A Snapping Turtle is coming your way, and it's ANGRY!"),
        Triple("agarimoo", "Agarimoo", "Your Chumcap Bucket trembles, it's an Agarimoo."),
        Triple("water_worm", "Water Worm", "A Water Worm surfaces!"),
        Triple("poisoned_water_worm", "Poisoned Water Worm", "A Poisoned Water Worm surfaces!"),
        Triple("flaming_worm", "Flaming Worm", "A Flaming Worm surfaces from the depths!"),
        Triple("lava_blaze", "Lava Blaze", "A Lava Blaze has surfaced from the depths!"),
        Triple("lava_pigman", "Lava Pigman", "A Lava Pigman arose from the depths!"),
        Triple("water_hydra", "Water Hydra", "The Water Hydra has come to test your strength."),
        Triple("alligator", "Alligator", "A long snout breaks the surface of the water. It's an Alligator!"),
        Triple("titanoboa", "Titanoboa", "A massive Titanoboa surfaces. It's body stretches as far as the eye can see."),
        Triple("puddle_jumper", "Puddle Jumper", "A Puddle Jumper is preparing for liftoff—cast your rod into it and hold on tight!"),
        Triple("frog_prince", "Frog Prince", "Bow down before the Frog Prince... or pay the hefty price!"),
        Triple("loch_emperor", "The Loch Emperor", "The Loch Emperor arises from the depths."),
        Triple("nessie", "Nessie", "You've caused a disturbance in the loch. Could it be... Nessie?"),
        Triple("silkbreeze", "Silkbreeze", "Something zips through the air - it's a Silkbreeze!"),
        Triple("giant_isopod", "Giant Isopod", "A Giant Isopod was dredged up from the depths!"),
        Triple("thunder", "Thunder", "You hear a massive rumble as Thunder emerges."),
        Triple("fiery_scuttler", "Fiery Scuttler", "A Fiery Scuttler inconspicuously waddles up to you, friends in tow."),
        Triple("lord_jawbus", "Lord Jawbus", "You have angered a legendary creature... Lord Jawbus has arrived."),
        Triple("ragnarok", "Ragnarok", "The sky darkens and the air thickens. The end times are upon us: Ragnarok is here."),
        Triple("yeti", "Yeti", "What is this creature!?"),
        Triple("nutcracker", "Nutcracker", "You found a forgotten Nutcracker laying beneath the ice."),
        Triple("phantom_fisher", "Phantom Fisher", "The spirit of a long lost Phantom Fisher has come to haunt you."),
        Triple("grim_reaper", "Grim Reaper", "This can't be! The manifestation of death himself!"),
        Triple("great_white_shark", "Great White Shark", "Hide no longer, a Great White Shark has tracked your scent and thirsts for your blood!"),
        Triple("blue_ringed_octopus", "Blue Ringed Octopus", "A garish set of tentacles arise. It's a Blue Ringed Octopus!"),
        Triple("wiki_tiki", "Wiki Tiki", "The water bubbles and froths. A massive form emerges- you have disturbed the Wiki Tiki! You shall pay the price."),
        Triple("carrot_king", "Carrot King", "Is this even a fish? It's the Carrot King!"),
        Triple("abyssal_miner", "Abyssal Miner", "An Abyssal Miner breaks out of the water!"),
        Triple("plhlegblast", "Plhlegblast", "WOAH! A Plhlegblast appeared."),
    ).associate { (id, name, message) -> message to Catch(id, name) }

    fun cleanLine(text: String): String? = text.takeIf {
        it.length <= 512 && it.none(Char::isISOControl)
    }?.let(SkyBlockParser::clean)

    fun parseCatch(line: String): Catch? = catches[line]
    fun doubleHook(line: String): Boolean = line == "It's a Double Hook!" || line == "It's a Double Hook! Woot woot!"
}
