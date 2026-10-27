package dev.skyglint.features.fishing.encounters

import dev.skyglint.core.*
import kotlin.test.*

class FishingEncounterModuleTest {
    private var now = 0L
    private val module = FishingEncounterModule { now }
    private val thunder = "You hear a massive rumble as Thunder emerges."
    private fun context(mode: String? = "crimson_isle") = module.parse(NormalizedSignal.ContextSnapshot("Observed area", mode))
    private fun chat(text: String = thunder) = module.parse(NormalizedSignal.Chat(text))
    private fun notice(text: String = thunder) = (chat(text).single() as CoreEvent.NoticeObserved).request

    @Test fun `pinned catch fixtures resolve notable creatures without item rewards or fabricated metrics`() {
        // Independent server-message fixtures from pinned Skyblocker SeaCreature.java.
        val fixtures = listOf(
            "Huh? A Catfish!" to "Catfish",
            "Gross! A Sea Leech!" to "Sea Leech",
            "You've discovered a Guardian Defender of the sea." to "Guardian Defender",
            "You have awoken the Deep Sea Protector, prepare for a battle!" to "Deep Sea Protector",
            "The desolate wail of a Banshee breaks the silence." to "Banshee",
            "A swampy mass of slime emerges, the Bayou Sludge!" to "Bayou Sludge",
            "What even is that?! A... gorF?" to "gorF",
            "A Drowned Captain takes hold of your bobber!" to "Drowned Captain",
            "A gang of Liltads!" to "Tadgang",
            "You've hooked an Ent, as ancient as the forest itself." to "Ent",
            "You caught a Stridersurfer." to "Stridersurfer",
            "A Sprawl emerges from the blue, and it's looking for you!" to "Sprawl",
            "The laughter of a Torrid echoes through the air." to "Torrid",
            "Trouble's brewing, it's a Fireproof Witch!" to "Fireproof Witch",
            "A small but fearsome Lava Leech emerges." to "Lava Leech",
            "You feel the heat radiating as a Pyroclastic Worm surfaces." to "Pyroclastic Worm",
            "A Magma Pillar rises from the lava." to "Magma Pillar",
            "A Lava Flame flies out from beneath the lava." to "Lava Flame",
            "A Fire Eel slithers out from the depths." to "Fire Eel",
            "Taurus and his steed emerge." to "Taurus",
            "You hear trotting from beneath the waves, you caught a Nightmare." to "Nightmare",
            "It must be a full moon, a Werewolf appears." to "Werewolf",
            "A striped beast bounds from the depths, the wild Tiger Shark!" to "Tiger Shark",
            "A Snapping Turtle is coming your way, and it's ANGRY!" to "Snapping Turtle",
            "Your Chumcap Bucket trembles, it's an Agarimoo." to "Agarimoo",
            "A Water Worm surfaces!" to "Water Worm",
            "A Poisoned Water Worm surfaces!" to "Poisoned Water Worm",
            "A Flaming Worm surfaces from the depths!" to "Flaming Worm",
            "A Lava Blaze has surfaced from the depths!" to "Lava Blaze",
            "A Lava Pigman arose from the depths!" to "Lava Pigman",
            "The Water Hydra has come to test your strength." to "Water Hydra",
            "A long snout breaks the surface of the water. It's an Alligator!" to "Alligator",
            "A massive Titanoboa surfaces. It's body stretches as far as the eye can see." to "Titanoboa",
            "A Puddle Jumper is preparing for liftoff—cast your rod into it and hold on tight!" to "Puddle Jumper",
            "Bow down before the Frog Prince... or pay the hefty price!" to "Frog Prince",
            "The Loch Emperor arises from the depths." to "The Loch Emperor",
            "You've caused a disturbance in the loch. Could it be... Nessie?" to "Nessie",
            "Something zips through the air - it's a Silkbreeze!" to "Silkbreeze",
            "A Giant Isopod was dredged up from the depths!" to "Giant Isopod",
            thunder to "Thunder",
            "A Fiery Scuttler inconspicuously waddles up to you, friends in tow." to "Fiery Scuttler",
            "You have angered a legendary creature... Lord Jawbus has arrived." to "Lord Jawbus",
            "The sky darkens and the air thickens. The end times are upon us: Ragnarok is here." to "Ragnarok",
            "What is this creature!?" to "Yeti",
            "You found a forgotten Nutcracker laying beneath the ice." to "Nutcracker",
            "The spirit of a long lost Phantom Fisher has come to haunt you." to "Phantom Fisher",
            "This can't be! The manifestation of death himself!" to "Grim Reaper",
            "Hide no longer, a Great White Shark has tracked your scent and thirsts for your blood!" to "Great White Shark",
            "A garish set of tentacles arise. It's a Blue Ringed Octopus!" to "Blue Ringed Octopus",
            "The water bubbles and froths. A massive form emerges- you have disturbed the Wiki Tiki! You shall pay the price." to "Wiki Tiki",
            "Is this even a fish? It's the Carrot King!" to "Carrot King",
            "An Abyssal Miner breaks out of the water!" to "Abyssal Miner",
            "WOAH! A Plhlegblast appeared." to "Plhlegblast",
        )
        context()
        for ((message, name) in fixtures) {
            val request = notice("§9$message")
            assertEquals("$name caught", request.title)
            assertEquals(4_000L, request.durationMillis)
            assertEquals(8_000L, request.freshnessMillis)
            assertEquals("minecraft:fishing_rod", request.item?.vanillaItemId)
            assertNull(request.item?.itemName)
            assertNull(request.rarity)
            assertNull(request.countdown)
            assertNull(request.target)
        }
    }

    @Test fun `common creatures and global reindrake announcements remain silent`() {
        context("winter")
        for (message in listOf("A Squid appeared.", "You caught a Sea Walker.", "It's a snowman! He looks harmless.",
            "The Grinch stole Jerry's Gifts...get them back!",
            "A Reindrake forms from the depths.", "WOAH! Someone summoned a Reindrake from the depths!")) {
            assertTrue(chat(message).isEmpty(), message)
        }
    }

    @Test fun `strict whole-line protocol rejects player prefixes snippets alterations and multiline injection`() {
        context()
        for (message in listOf("[MVP+] Someone: $thunder", "From Someone: $thunder", "[NPC] Someone: $thunder",
            "prefix $thunder", "$thunder suffix", thunder.lowercase(), "$thunder\n$thunder", "$thunder\t", "x".repeat(513))) {
            assertTrue(chat(message).isEmpty(), message)
        }
        assertEquals("Thunder caught", notice("§9You hear a massive rumble as §r§6Thunder §r§9emerges.").title)
    }

    @Test fun `only freshly confirmed eligible context permits announcements`() {
        assertTrue(chat().isEmpty())
        context(null); assertTrue(chat().isEmpty())
        context("dungeon"); assertTrue(chat().isEmpty())
        context("kuudra"); assertTrue(chat().isEmpty())
        context("crimson_isle")
        now = 5_000
        assertTrue(chat().isEmpty())
        context()
        assertEquals("Thunder caught", notice().title)
        now = 4_999
        assertTrue(chat().isEmpty())
    }

    @Test fun `actual chat is required inventory boss sidebar held and actionbar cannot manufacture catch`() {
        context()
        val item = MenuItemSnapshot(0, "Thunder", listOf(thunder))
        for (signal in listOf(NormalizedSignal.ActionBar(thunder), NormalizedSignal.ActionBarPresence(thunder),
            NormalizedSignal.TabSnapshot(listOf(thunder)), NormalizedSignal.SidebarSnapshot(listOf(thunder)),
            NormalizedSignal.HeldItemSnapshot(item), NormalizedSignal.InventorySnapshot(listOf(item)),
            NormalizedSignal.MenuSnapshot(thunder, listOf(item)), NormalizedSignal.BossSnapshot(emptyList()))) {
            assertTrue(module.parse(signal).isEmpty())
        }
    }

    @Test fun `duplicate burst is suppressed but later real identical catch is a new notice`() {
        context()
        val first = notice().id
        now = 100
        context() // Reaffirming context must not clear dedupe.
        assertTrue(chat().isEmpty())
        now = 499
        assertTrue(chat().isEmpty())
        now = 500
        assertNotEquals(first, notice().id)
    }

    @Test fun `both exact double hook variants annotate only the next catch within their window`() {
        for (hook in listOf("It's a Double Hook!", "It's a Double Hook! Woot woot!")) {
            module.onSessionStarted(hook)
            context()
            assertTrue(chat(hook).isEmpty())
            now += 100
            assertEquals("Thunder caught · Double Hook", notice().title)
            now += 500
            assertEquals("Thunder caught", notice().title)
        }
    }

    @Test fun `stale spoofed or consumed double hook cannot donate an annotation to a later catch`() {
        val interruptions = listOf("A Squid appeared.", "unrecognized server line", "$thunder\n", "[MVP+] User: It's a Double Hook!")
        for (interrupt in interruptions) {
            module.onSessionStarted(interrupt)
            context()
            chat("It's a Double Hook!")
            chat(interrupt)
            now += 1
            assertEquals("Thunder caught", notice().title)
        }
        now += 500
        chat("It's a Double Hook!")
        now += 101
        assertEquals("Thunder caught", notice().title)
    }

    @Test fun `session mode and stale context clear pending metadata while subarea updates preserve dedupe`() {
        context()
        notice()
        chat("It's a Double Hook!")
        module.onSessionEnded()
        assertTrue(chat().isEmpty())
        module.onSessionStarted("new")
        assertTrue(chat().isEmpty())
        context()
        assertEquals("Thunder caught", notice().title)
        chat("It's a Double Hook!")
        context("hub")
        assertEquals("Thunder caught", notice().title)
        now = 5_000
        chat("It's a Double Hook!") // Expired context never saves a token.
        context()
        assertEquals("Thunder caught", notice().title)
    }

    @Test fun `notice preemption resumes only remaining presentation and stale waiting catch expires`() {
        context()
        val request = notice()
        val queue = NoticeQueue()
        queue.offer(request, 0)
        queue.offer(NoticeRequest("level", "SkyBlock level up!", priority = 100), 1_000)
        assertEquals("level", queue.active(4_999)?.id)
        assertEquals(request.id, queue.active(5_000)?.id)
        assertEquals(8_000L, queue.active(5_000)?.expiresAt)
        assertNull(queue.active(8_000))
        queue.offer(NoticeRequest("long", "Level up", priority = 100, durationMillis = 15_000), 10_000)
        queue.offer(request.copy(id = "later"), 10_001)
        assertNull(queue.active(25_000))
    }
}
