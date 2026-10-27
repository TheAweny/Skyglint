package dev.skyglint.core

import java.util.Locale

/** Event identity/timing is independent of its detector and of its chosen native HUD form. */
enum class TemporaryEventScope { NETWORK, SERVER }
enum class TemporaryEventTiming { CALENDAR, RANDOM, TRIGGERED }
enum class TemporaryEventPresentation { NOTICE, TIMER_CIRCLE, CONTEXT_WIDGET }

data class TemporaryEventDefinition(
    val id: String, val name: String, val scope: TemporaryEventScope,
    val timing: TemporaryEventTiming, val presentation: TemporaryEventPresentation,
    val aliases: Set<String> = emptySet(), val durationMillis: Long? = null,
)

/**
 * Source inventory: docs/research/TEMPORARY-EVENTS.md and MINING-RANDOM-EVENTS.md.
 * A definition is a vocabulary entry, NOT proof that a detector is implemented. Unknown server
 * Event widgets still work. Never manufacture a start, an end, or a timer from this catalogue.
 */
object TemporaryEventCatalog {
    val definitions: List<TemporaryEventDefinition> = buildList {
        fun calendar(id: String, name: String, vararg aliases: String) = add(TemporaryEventDefinition(
            id, name, TemporaryEventScope.NETWORK, TemporaryEventTiming.CALENDAR,
            TemporaryEventPresentation.NOTICE, aliases.toSet()))
        fun server(id: String, name: String, presentation: TemporaryEventPresentation,
                   duration: Long? = null, vararg aliases: String) = add(TemporaryEventDefinition(
            id, name, TemporaryEventScope.SERVER, TemporaryEventTiming.RANDOM, presentation, aliases.toSet(), duration))
        fun encounter(id: String, name: String, vararg aliases: String) = add(TemporaryEventDefinition(
            id, name, TemporaryEventScope.SERVER, TemporaryEventTiming.TRIGGERED,
            TemporaryEventPresentation.CONTEXT_WIDGET, aliases.toSet()))
        calendar("spooky_festival", "Spooky Festival")
        calendar("season_of_jerry", "Season of Jerry")
        calendar("jerry_workshop", "Jerry's Workshop Opens", "Jerry's Workshop")
        calendar("fishing_festival", "Fishing Festival", "Bonus Fishing Festival")
        calendar("mining_fiesta", "Mining Fiesta", "Bonus Mining Fiesta")
        calendar("mythological_ritual", "Mythological Ritual")
        calendar("traveling_zoo", "Traveling Zoo")
        calendar("dark_auction", "Dark Auction")
        calendar("new_year", "New Year Celebration", "New Year Event!")
        calendar("bingo", "Bingo")
        calendar("carnival", "Carnival")
        calendar("hoppity_hunt", "Hoppity's Hunt")
        calendar("anniversary", "SkyBlock Anniversary")
        calendar("century_raffle", "Century Raffle")
        calendar("year_of_the_seal", "Year of the Seal")
        calendar("year_of_the_pig", "Year of the Pig")
        calendar("year_of_the_witch", "Year of the Witch")
        calendar("great_spook", "The Great Spook")
        calendar("harvest_feast", "Harvest Feast")
        calendar("grand_feast", "Grand Feast")
        calendar("mayor_jerry", "Mayor Jerry")
        calendar("cult_fallen_star", "Cult of the Fallen Star")
        calendar("election_open", "Election Booth Opens")
        calendar("election_over", "Election Over!")
        add(TemporaryEventDefinition("jacob_contest", "Jacob's Farming Contest", TemporaryEventScope.NETWORK,
            TemporaryEventTiming.CALENDAR, TemporaryEventPresentation.CONTEXT_WIDGET, setOf("Jacob's Contest")))
        // Only Double Powder's full duration is corroborated. Other upstream totals disagree;
        // use the observed interval until a reliable full duration is available.
        server("double_powder", "Double Powder", TemporaryEventPresentation.TIMER_CIRCLE, 900_000, "2x Powder")
        server("better_together", "Better Together", TemporaryEventPresentation.TIMER_CIRCLE)
        server("gone_with_the_wind", "Gone with the Wind", TemporaryEventPresentation.TIMER_CIRCLE)
        server("goblin_raid", "Goblin Raid", TemporaryEventPresentation.TIMER_CIRCLE)
        server("raffle", "Raffle", TemporaryEventPresentation.TIMER_CIRCLE)
        server("mithril_gourmand", "Mithril Gourmand", TemporaryEventPresentation.TIMER_CIRCLE)
        server("fallen_star", "Fallen Star", TemporaryEventPresentation.NOTICE)
        server("powder_ghast", "Powder Ghast", TemporaryEventPresentation.NOTICE)
        server("golden_goblin", "Golden Goblin", TemporaryEventPresentation.NOTICE)
        server("diamond_goblin", "Diamond Goblin", TemporaryEventPresentation.NOTICE)
        server("mineshaft_portal", "Glacite Mineshaft", TemporaryEventPresentation.NOTICE)
        server("primal_fear", "Primal Fear", TemporaryEventPresentation.NOTICE)
        server("reindrake", "Reindrake", TemporaryEventPresentation.NOTICE)
        // Weather definitions reserve identities without guessing unseen server packet formats.
        server("breeze", "Breeze", TemporaryEventPresentation.CONTEXT_WIDGET)
        server("mist", "Mist", TemporaryEventPresentation.CONTEXT_WIDGET)
        server("rockfall", "Rockfall", TemporaryEventPresentation.CONTEXT_WIDGET)
        server("snowstorm", "Snowstorm", TemporaryEventPresentation.CONTEXT_WIDGET)
        server("fortunate_freezing", "Fortunate Freezing", TemporaryEventPresentation.CONTEXT_WIDGET)
        encounter("jerry_defense", "Jerry Workshop Defense")
        encounter("carnival_fishing", "Catch a Fish")
        encounter("carnival_fruit", "Fruit Digging")
        encounter("carnival_shootout", "Zombie Shootout")
        encounter("stonks_auction", "Stonks Auction")
        encounter("agatha_contest", "Agatha's Contest")
        encounter("miria_contest", "Miria's Contest")
        encounter("broodmother", "Broodmother")
        encounter("dragon", "Dragon Fight")
        encounter("magma_boss", "Magma Boss")
        encounter("kuudra", "Kuudra")
        encounter("arachne", "Arachne")
        encounter("bacte", "Bacte")
        encounter("slayer", "Slayer Boss")
    }
    private val byName = definitions.flatMap { definition ->
        (definition.aliases + definition.name).map { key(it) to definition }
    }.toMap()
    private val anniversary = Regex("[0-9]{1,4}(?:st|nd|rd|th) Anniversary", RegexOption.IGNORE_CASE)
    fun resolve(name: String): TemporaryEventDefinition? = byName[key(name)]
        ?: definitions.first { it.id == "anniversary" }.takeIf { anniversary.matches(SkyBlockParser.clean(name)) }
    fun byId(id: String): TemporaryEventDefinition? = definitions.firstOrNull { it.id == id }
    private fun key(name: String) = SkyBlockParser.clean(name).replace('’', '\'').lowercase(Locale.ROOT)
}
